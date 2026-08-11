import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    type ReactNode,
} from "react";

import {
    finishPlaySession,
    heartbeatPlaySession,
    startPlaySession,
    type SongCard,
} from "../api/music";

import {
    PlayerContext,
    type PlayerContextValue,
} from "./PlayerContext";

const DEFAULT_VOLUME = 0.8;

/**
 * 前端 heartbeat 只负责告诉服务端：
 * “播放器此刻仍然在真正播放”。
 *
 * 服务端不相信 currentTime，也不相信客户端上报“我已经播放了多少秒”；
 * 有效时长由 Redis 中两次 heartbeat 的服务器时间差累计。
 */
interface ActivePlaySession {
    songId: string;
    sessionId: string;
    heartbeatIntervalSeconds: number;
    counted: boolean;
}

export function PlayerProvider({
                                   children,
                               }: {
    children: ReactNode;
}) {
    const audioRef =
        useRef<HTMLAudioElement | null>(
            null,
        );

    const queueRef =
        useRef<SongCard[]>([]);

    const currentSongRef =
        useRef<SongCard | null>(
            null,
        );

    const currentIndexRef =
        useRef(-1);

    const nextRef =
        useRef<() => void>(() => {});

    const playSessionRef =
        useRef<ActivePlaySession | null>(
            null,
        );

    const playSessionGenerationRef =
        useRef(0);

    const heartbeatTimerRef =
        useRef<number | null>(
            null,
        );

    const heartbeatBusyRef =
        useRef(false);

    const [queue, setQueue] =
        useState<SongCard[]>([]);

    const [currentSong, setCurrentSong] =
        useState<SongCard | null>(null);

    const [currentIndex, setCurrentIndex] =
        useState(-1);

    const [isPlaying, setIsPlaying] =
        useState(false);

    const [currentTime, setCurrentTime] =
        useState(0);

    const [duration, setDuration] =
        useState(0);

    const [volume, setVolume] =
        useState(DEFAULT_VOLUME);

    const [playlistOpen, setPlaylistOpen] =
        useState(false);

    const [playbackError, setPlaybackError] =
        useState("");

    const stopHeartbeatTimer =
        useCallback((): void => {
            if (
                heartbeatTimerRef.current
                !== null
            ) {
                window.clearInterval(
                    heartbeatTimerRef.current,
                );

                heartbeatTimerRef.current =
                    null;
            }
        }, []);

    const updatePlayCountEverywhere =
        useCallback(
            (
                songId: string,
                playCount: number,
            ): void => {
                const updatedQueue =
                    queueRef.current.map(
                        (song) =>
                            song.id === songId
                                ? {
                                    ...song,
                                    playCount,
                                }
                                : song,
                    );

                queueRef.current =
                    updatedQueue;

                setQueue(updatedQueue);

                const active =
                    currentSongRef.current;

                if (
                    active !== null
                    && active.id === songId
                ) {
                    const updatedSong = {
                        ...active,
                        playCount,
                    };

                    currentSongRef.current =
                        updatedSong;

                    setCurrentSong(
                        updatedSong,
                    );
                }

                /*
                 * HomePage / ArtistPage 各自维护查询结果，
                 * 用事件同步最新播放量。
                 */
                window.dispatchEvent(
                    new CustomEvent(
                        "avemusic:play-counted",
                        {
                            detail: {
                                songId,
                                playCount,
                            },
                        },
                    ),
                );
            },
            [],
        );

    const sendHeartbeat =
        useCallback(
            async (): Promise<void> => {
                const session =
                    playSessionRef.current;

                if (
                    session === null
                    || session.counted
                    || heartbeatBusyRef.current
                ) {
                    return;
                }

                heartbeatBusyRef.current =
                    true;

                try {
                    const result =
                        await heartbeatPlaySession(
                            session.sessionId,
                        );

                    if (
                        result.counted
                        && result.playCount
                        !== null
                    ) {
                        session.counted = true;

                        updatePlayCountEverywhere(
                            session.songId,
                            result.playCount,
                        );

                        /*
                         * 本次 playSession 已经成功记过一次播放量，
                         * 后续无需继续 heartbeat。
                         */
                        stopHeartbeatTimer();
                    }
                } catch {
                    /*
                     * heartbeat 偶发失败不影响音乐播放。
                     * 下一次周期继续尝试即可。
                     */
                } finally {
                    heartbeatBusyRef.current =
                        false;
                }
            },
            [
                stopHeartbeatTimer,
                updatePlayCountEverywhere,
            ],
        );

    const startHeartbeatTimer =
        useCallback((): void => {
            stopHeartbeatTimer();

            const session =
                playSessionRef.current;

            if (
                session === null
                || session.counted
            ) {
                return;
            }

            /*
             * playing / seeked / resume 时立即发一次：
             *
             * - 如果之前暂停很久，服务端会把这次视为“重置基线”，不累计暂停时间；
             * - 下一次 5 秒 heartbeat 才开始重新累计。
             */
            void sendHeartbeat();

            heartbeatTimerRef.current =
                window.setInterval(
                    () => {
                        void sendHeartbeat();
                    },
                    Math.max(
                        2,
                        session
                            .heartbeatIntervalSeconds,
                    ) * 1000,
                );
        }, [
            sendHeartbeat,
            stopHeartbeatTimer,
        ]);

    const finishCurrentPlaySession =
        useCallback((): void => {
            stopHeartbeatTimer();

            const session =
                playSessionRef.current;

            playSessionRef.current = null;

            if (session !== null) {
                /*
                 * best-effort 清理。
                 * 即使请求失败，Redis key 自身也有 TTL。
                 */
                void finishPlaySession(
                    session.sessionId,
                ).catch(() => {});
            }
        }, [stopHeartbeatTimer]);

    const createServerPlaySession =
        useCallback(
            async (
                songId: string,
            ): Promise<void> => {
                const generation =
                    ++playSessionGenerationRef
                        .current;

                finishCurrentPlaySession();

                try {
                    const created =
                        await startPlaySession(
                            songId,
                        );

                    /*
                     * 如果创建期间用户已经切到另一首歌，
                     * 当前返回的 session 立即清理，不能挂到新歌上。
                     */
                    if (
                        generation
                        !== playSessionGenerationRef
                            .current
                    ) {
                        void finishPlaySession(
                            created.sessionId,
                        ).catch(() => {});

                        return;
                    }

                    playSessionRef.current = {
                        songId,
                        sessionId:
                        created.sessionId,
                        heartbeatIntervalSeconds:
                        created
                            .heartbeatIntervalSeconds,
                        counted: false,
                    };

                    const audio =
                        audioRef.current;

                    if (
                        audio !== null
                        && !audio.paused
                        && !audio.seeking
                        && audio.readyState >= 3
                    ) {
                        startHeartbeatTimer();
                    }
                } catch {
                    /*
                     * Redis / session 接口异常不阻断真正的音频播放；
                     * 只是本次无法产生播放量。
                     */
                }
            },
            [
                finishCurrentPlaySession,
                startHeartbeatTimer,
            ],
        );

    useEffect(() => {
        const audio =
            new Audio();

        audio.preload = "metadata";
        audio.volume = DEFAULT_VOLUME;

        audioRef.current = audio;

        function handleTimeUpdate() {
            setCurrentTime(
                Number.isFinite(
                    audio.currentTime,
                )
                    ? audio.currentTime
                    : 0,
            );
        }

        function handleDurationChange() {
            if (
                Number.isFinite(
                    audio.duration,
                )
                && audio.duration > 0
            ) {
                setDuration(
                    audio.duration,
                );
            }
        }

        function handlePlay() {
            setIsPlaying(true);
        }

        /**
         * 只有真正触发 playing 后才发送 heartbeat。
         * play 事件本身可能随后进入 waiting，因此不能以 play 事件作为“正在实际播放”的依据。
         */
        function handlePlaying() {
            startHeartbeatTimer();
        }

        function handlePause() {
            /*
             * 暂停前立即补一个 heartbeat，
             * 服务端最多只会累计距离上次 heartbeat
             * 真实经过的短时间。
             */
            void sendHeartbeat();
            stopHeartbeatTimer();

            setIsPlaying(false);
        }

        /**
         * 缓冲期间没有真正输出音频：
         * 先结算到当前时刻，然后停止 heartbeat。
         */
        function handleWaiting() {
            void sendHeartbeat();
            stopHeartbeatTimer();
        }

        function handleStalled() {
            void sendHeartbeat();
            stopHeartbeatTimer();
        }

        /**
         * 拖动进度条不会让播放量增加：
         * seeking 开始时停止 heartbeat，
         * 服务端根本不接收 currentTime。
         */
        function handleSeeking() {
            void sendHeartbeat();
            stopHeartbeatTimer();
        }

        function handleSeeked() {
            if (
                !audio.paused
                && audio.readyState >= 3
            ) {
                startHeartbeatTimer();
            }
        }

        function handleEnded() {
            /*
             * 结束前补最后一个 heartbeat，
             * 再清理当前 session。
             */
            void sendHeartbeat()
                .finally(() => {
                    finishCurrentPlaySession();
                    nextRef.current();
                });
        }

        function handleError() {
            stopHeartbeatTimer();
            finishCurrentPlaySession();

            setIsPlaying(false);

            setPlaybackError(
                "音乐文件加载失败，请确认文件服务正常运行",
            );
        }

        audio.addEventListener(
            "timeupdate",
            handleTimeUpdate,
        );

        audio.addEventListener(
            "loadedmetadata",
            handleDurationChange,
        );

        audio.addEventListener(
            "durationchange",
            handleDurationChange,
        );

        audio.addEventListener(
            "play",
            handlePlay,
        );

        audio.addEventListener(
            "playing",
            handlePlaying,
        );

        audio.addEventListener(
            "pause",
            handlePause,
        );

        audio.addEventListener(
            "waiting",
            handleWaiting,
        );

        audio.addEventListener(
            "stalled",
            handleStalled,
        );

        audio.addEventListener(
            "seeking",
            handleSeeking,
        );

        audio.addEventListener(
            "seeked",
            handleSeeked,
        );

        audio.addEventListener(
            "ended",
            handleEnded,
        );

        audio.addEventListener(
            "error",
            handleError,
        );

        return () => {
            stopHeartbeatTimer();
            finishCurrentPlaySession();

            audio.pause();

            audio.removeEventListener(
                "timeupdate",
                handleTimeUpdate,
            );

            audio.removeEventListener(
                "loadedmetadata",
                handleDurationChange,
            );

            audio.removeEventListener(
                "durationchange",
                handleDurationChange,
            );

            audio.removeEventListener(
                "play",
                handlePlay,
            );

            audio.removeEventListener(
                "playing",
                handlePlaying,
            );

            audio.removeEventListener(
                "pause",
                handlePause,
            );

            audio.removeEventListener(
                "waiting",
                handleWaiting,
            );

            audio.removeEventListener(
                "stalled",
                handleStalled,
            );

            audio.removeEventListener(
                "seeking",
                handleSeeking,
            );

            audio.removeEventListener(
                "seeked",
                handleSeeked,
            );

            audio.removeEventListener(
                "ended",
                handleEnded,
            );

            audio.removeEventListener(
                "error",
                handleError,
            );

            audio.removeAttribute("src");
            audio.load();

            audioRef.current = null;
        };
    }, [
        finishCurrentPlaySession,
        sendHeartbeat,
        startHeartbeatTimer,
        stopHeartbeatTimer,
    ]);

    const startTrack = useCallback(
        (
            targetQueue: SongCard[],
            index: number,
        ): void => {
            const song =
                targetQueue[index];

            if (
                song === undefined
                || !song.audioUrl
            ) {
                setPlaybackError(
                    "该音乐暂无可播放文件",
                );
                return;
            }

            const audio =
                audioRef.current;

            if (audio === null) {
                setPlaybackError(
                    "播放器尚未初始化完成",
                );
                return;
            }

            queueRef.current =
                targetQueue;

            currentSongRef.current =
                song;

            currentIndexRef.current =
                index;

            setQueue(targetQueue);
            setCurrentIndex(index);
            setCurrentSong(song);

            setCurrentTime(0);

            setDuration(
                Number(
                    song.durationSeconds,
                ) > 0
                    ? Number(
                        song.durationSeconds,
                    )
                    : 0,
            );

            setPlaybackError("");

            /*
             * 一首歌一次主动开始播放，就创建一个新的服务端 playSession。
             */
            void createServerPlaySession(
                song.id,
            );

            audio.pause();
            audio.src = song.audioUrl;
            audio.currentTime = 0;
            audio.load();

            void audio.play()
                .catch(() => {
                    setIsPlaying(false);

                    setPlaybackError(
                        "浏览器阻止了播放，请再次点击播放按钮",
                    );
                });
        },
        [createServerPlaySession],
    );

    const playQueue = useCallback(
        (
            songs: SongCard[],
            index: number,
        ): void => {
            const target =
                songs[index];

            if (
                target === undefined
                || !target.audioUrl
            ) {
                setPlaybackError(
                    "该音乐暂无可播放文件",
                );
                return;
            }

            const playableQueue =
                songs.filter(
                    (song) =>
                        Boolean(
                            song.audioUrl,
                        ),
                );

            const resolvedIndex =
                playableQueue.findIndex(
                    (song) =>
                        song.id
                        === target.id,
                );

            if (resolvedIndex < 0) {
                setPlaybackError(
                    "该音乐暂无可播放文件",
                );
                return;
            }

            startTrack(
                playableQueue,
                resolvedIndex,
            );
        },
        [startTrack],
    );

    const playAt = useCallback(
        (index: number): void => {
            const targetQueue =
                queueRef.current;

            if (
                index < 0
                || index
                >= targetQueue.length
            ) {
                return;
            }

            startTrack(
                targetQueue,
                index,
            );
        },
        [startTrack],
    );

    const enqueueNext = useCallback(
        (song: SongCard): void => {
            if (!song.audioUrl) {
                setPlaybackError(
                    "该音乐暂无可播放文件",
                );
                return;
            }

            const currentQueue =
                queueRef.current;

            const currentIndexValue =
                currentIndexRef.current;

            if (
                currentQueue.length === 0
                || currentIndexValue < 0
            ) {
                startTrack(
                    [song],
                    0,
                );
                return;
            }

            const current =
                currentQueue[
                    currentIndexValue
                    ];

            /*
             * 当前歌曲本身执行“下一首播放”时不重复插入，
             * 避免播放列表出现相同 key。
             */
            if (
                current !== undefined
                && current.id === song.id
            ) {
                return;
            }

            /*
             * 如果目标歌曲已经在队列其他位置，
             * 先移除，再插到当前歌曲后面。
             */
            const cleaned =
                currentQueue.filter(
                    (item, index) =>
                        index
                        === currentIndexValue
                        || item.id
                        !== song.id,
                );

            const currentId =
                current?.id;

            const resolvedCurrentIndex =
                currentId === undefined
                    ? currentIndexValue
                    : cleaned.findIndex(
                        (item) =>
                            item.id
                            === currentId,
                    );

            const safeCurrentIndex =
                resolvedCurrentIndex < 0
                    ? Math.min(
                        currentIndexValue,
                        cleaned.length - 1,
                    )
                    : resolvedCurrentIndex;

            const nextQueue = [
                ...cleaned.slice(
                    0,
                    safeCurrentIndex + 1,
                ),

                song,

                ...cleaned.slice(
                    safeCurrentIndex + 1,
                ),
            ];

            queueRef.current =
                nextQueue;

            currentIndexRef.current =
                safeCurrentIndex;

            setQueue(nextQueue);
            setCurrentIndex(
                safeCurrentIndex,
            );
        },
        [startTrack],
    );

    const togglePlay = useCallback(
        (): void => {
            const audio =
                audioRef.current;

            if (
                audio === null
                || currentIndexRef
                    .current < 0
            ) {
                return;
            }

            setPlaybackError("");

            if (audio.paused) {
                void audio.play()
                    .catch(() => {
                        setPlaybackError(
                            "无法继续播放该音乐",
                        );
                    });
            } else {
                audio.pause();
            }
        },
        [],
    );

    const previous = useCallback(
        (): void => {
            const targetQueue =
                queueRef.current;

            if (
                targetQueue.length === 0
            ) {
                return;
            }

            const current =
                currentIndexRef.current;

            const targetIndex =
                current <= 0
                    ? targetQueue.length - 1
                    : current - 1;

            startTrack(
                targetQueue,
                targetIndex,
            );
        },
        [startTrack],
    );

    const next = useCallback(
        (): void => {
            const targetQueue =
                queueRef.current;

            if (
                targetQueue.length === 0
            ) {
                return;
            }

            const current =
                currentIndexRef.current;

            const targetIndex =
                current < 0
                || current
                >= targetQueue.length - 1
                    ? 0
                    : current + 1;

            startTrack(
                targetQueue,
                targetIndex,
            );
        },
        [startTrack],
    );

    useEffect(() => {
        nextRef.current = next;
    }, [next]);

    const seek = useCallback(
        (seconds: number): void => {
            const audio =
                audioRef.current;

            if (audio === null) {
                return;
            }

            const maxDuration =
                Number.isFinite(
                    audio.duration,
                )
                && audio.duration > 0
                    ? audio.duration
                    : duration;

            const nextTime =
                Math.min(
                    Math.max(
                        seconds,
                        0,
                    ),
                    Math.max(
                        maxDuration,
                        0,
                    ),
                );

            audio.currentTime =
                nextTime;

            setCurrentTime(
                nextTime,
            );
        },
        [duration],
    );

    const changeVolume = useCallback(
        (
            nextVolume: number,
        ): void => {
            const resolved =
                Math.min(
                    Math.max(
                        nextVolume,
                        0,
                    ),
                    1,
                );

            setVolume(resolved);

            if (audioRef.current) {
                audioRef.current.volume =
                    resolved;
            }
        },
        [],
    );

    const togglePlaylist =
        useCallback((): void => {
            setPlaylistOpen(
                (current) =>
                    !current,
            );
        }, []);

    const value =
        useMemo<PlayerContextValue>(
            () => ({
                queue,
                currentSong,
                currentIndex,
                isPlaying,
                currentTime,
                duration,
                volume,
                playlistOpen,
                playbackError,
                playQueue,
                playAt,
                enqueueNext,
                togglePlay,
                previous,
                next,
                seek,
                changeVolume,
                togglePlaylist,
            }),
            [
                queue,
                currentSong,
                currentIndex,
                isPlaying,
                currentTime,
                duration,
                volume,
                playlistOpen,
                playbackError,
                playQueue,
                playAt,
                enqueueNext,
                togglePlay,
                previous,
                next,
                seek,
                changeVolume,
                togglePlaylist,
            ],
        );

    return (
        <PlayerContext.Provider
            value={value}
        >
            {children}
        </PlayerContext.Provider>
    );
}
