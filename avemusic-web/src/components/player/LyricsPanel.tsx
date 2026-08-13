import {
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";

import {
    getSongLyrics,
    translateSongLyrics,
    type SongLyrics,
} from "../../api/music";

import {
    getApiError,
} from "../../auth/api/http";

import {
    usePlayer,
} from "../../player/usePlayer";

import "../../styles/components/LyricsPanel.css";


interface LyricsPanelProps {
    open: boolean;
    songId: string;
    songName: string;
    onClose(): void;
}


interface TimedLyricLine {
    time: number;
    text: string;
    translation: string;
}


interface PlainLyricLine {
    text: string;
    translation: string;
}


function parseSyncedLyrics(
    value: string,
    translations: string[],
): TimedLyricLine[] {
    const result: Array<{
        time: number;
        text: string;
    }> = [];

    for (
        const rawLine
        of value.split(/\r?\n/)
        ) {
        /*
         * 支持：
         *
         * [00:17]
         * [00:17.12]
         * [00:17.123]
         */
        const pattern =
            /\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?\]/g;

        const timestamps: number[] =
            [];

        let lastEnd = -1;

        let match:
            RegExpExecArray | null;

        while (
            (
                match =
                    pattern.exec(
                        rawLine,
                    )
            ) !== null
            ) {
            const minutes =
                Number(
                    match[1],
                );

            const seconds =
                Number(
                    match[2],
                );

            const fraction =
                match[3]
                    ? Number(
                        `0.${match[3]}`,
                    )
                    : 0;

            timestamps.push(
                minutes * 60
                + seconds
                + fraction,
            );

            lastEnd =
                pattern.lastIndex;
        }

        if (
            timestamps.length === 0
            || lastEnd < 0
        ) {
            continue;
        }

        const text =
            rawLine
                .slice(lastEnd)
                .trim();

        if (!text) {
            continue;
        }

        for (
            const time
            of timestamps
            ) {
            result.push({
                time,
                text,
            });
        }
    }

    result.sort(
        (left, right) =>
            left.time
            - right.time,
    );

    return result.map(
        (line, index) => ({
            ...line,

            translation:
                translations[index]
                ?? "",
        }),
    );
}


function parsePlainLyrics(
    value: string,
    translations: string[],
): PlainLyricLine[] {
    return value
        .split(/\r?\n/)
        .map(
            (line) =>
                line.trim(),
        )
        .filter(Boolean)
        .map(
            (text, index) => ({
                text,

                translation:
                    translations[index]
                    ?? "",
            }),
        );
}


export default function LyricsPanel({
                                        open,
                                        songId,
                                        songName,
                                        onClose,
                                    }: LyricsPanelProps) {

    /*
     * PlayerProvider 本来就实时维护 currentTime，
     * 所以歌词同步不需要自己创建 audio。
     */
    const {
        currentTime,
        seek,
    } = usePlayer();

    const [
        lyrics,
        setLyrics,
    ] = useState<
        SongLyrics | null
    >(null);

    const [
        loading,
        setLoading,
    ] = useState(false);

    const [
        translating,
        setTranslating,
    ] = useState(false);

    const [
        error,
        setError,
    ] = useState("");

    const lineRefs =
        useRef<
            Array<
                HTMLButtonElement
                | null
            >
        >([]);


    useEffect(() => {
        if (
            !open
            || !songId
        ) {
            return;
        }

        let cancelled = false;

        async function load():
            Promise<void> {

            setLoading(true);
            setTranslating(false);
            setError("");
            setLyrics(null);

            try {
                const result =
                    await getSongLyrics(
                        songId,
                    );

                if (cancelled) {
                    return;
                }

                /*
                 * 原歌词立即显示。
                 */
                setLyrics(result);
                setLoading(false);

                const hasLyrics =
                    Boolean(
                        result.syncedLyrics
                            ?.trim()
                        || result.plainLyrics
                            ?.trim(),
                    );

                const needsTranslation =
                    result.status
                    === "MATCHED"
                    && !result.instrumental
                    && hasLyrics
                    && (
                        result
                            .translatedLines
                            ?.length
                        ?? 0
                    ) === 0;

                if (!needsTranslation) {
                    return;
                }

                /*
                 * AI 翻译后台进行，
                 * 不阻塞原歌词显示。
                 */
                setTranslating(true);

                try {
                    const translated =
                        await translateSongLyrics(
                            songId,
                        );

                    if (!cancelled) {
                        setLyrics(
                            translated,
                        );
                    }

                } catch (
                    translationError
                    ) {
                    /*
                     * 翻译失败时原歌词仍然正常使用。
                     */
                    console.warn(
                        "歌词翻译失败",
                        translationError,
                    );

                } finally {
                    if (!cancelled) {
                        setTranslating(
                            false,
                        );
                    }
                }

            } catch (
                requestError
                ) {
                if (!cancelled) {
                    setError(
                        getApiError(
                            requestError,
                        ).message,
                    );

                    setLoading(false);
                }
            }
        }

        void load();

        return () => {
            cancelled = true;
        };
    }, [
        open,
        songId,
    ]);


    const timedLines =
        useMemo(
            () => {
                if (
                    !lyrics
                        ?.syncedLyrics
                ) {
                    return [];
                }

                return parseSyncedLyrics(
                    lyrics.syncedLyrics,

                    lyrics
                        .translatedLines
                    ?? [],
                );
            },
            [
                lyrics?.syncedLyrics,
                lyrics?.translatedLines,
            ],
        );


    const plainLines =
        useMemo(
            () => {
                if (
                    timedLines.length > 0
                    || !lyrics
                        ?.plainLyrics
                ) {
                    return [];
                }

                return parsePlainLyrics(
                    lyrics.plainLyrics,

                    lyrics
                        .translatedLines
                    ?? [],
                );
            },
            [
                timedLines.length,
                lyrics?.plainLyrics,
                lyrics?.translatedLines,
            ],
        );


    /*
     * 当前播放时间落在哪一行。
     */
    const activeIndex =
        useMemo(() => {
            let active = -1;

            for (
                let index = 0;
                index < timedLines.length;
                index++
            ) {
                if (
                    timedLines[index]
                        .time
                    <= currentTime + 0.05
                ) {
                    active = index;
                } else {
                    break;
                }
            }

            return active;
        }, [
            currentTime,
            timedLines,
        ]);


    /*
     * 只有当前歌词行变化时才滚动，
     * 不会随着每一次 currentTime 更新不停滚动。
     */
    useEffect(() => {
        if (
            !open
            || activeIndex < 0
        ) {
            return;
        }

        lineRefs.current[
            activeIndex
            ]?.scrollIntoView({
            behavior: "smooth",
            block: "center",
        });

    }, [
        activeIndex,
        open,
    ]);


    if (!open) {
        return null;
    }


    return (
        <section
            className="lyrics-panel"
            aria-label={`${songName} 歌词`}
        >
            <header className="lyrics-panel-header">
                <div>
                    <strong>
                        {songName}
                    </strong>

                    <span>
                        {translating
                            ? "AI 翻译中..."
                            : "歌词"}
                    </span>
                </div>

                <button
                    type="button"
                    aria-label="关闭歌词"
                    onClick={onClose}
                >
                    ×
                </button>
            </header>


            <div className="lyrics-panel-body">
                {loading && (
                    <div className="lyrics-state">
                        正在获取歌词...
                    </div>
                )}


                {!loading
                    && error
                    && (
                        <div className="lyrics-state error">
                            {error}
                        </div>
                    )}


                {!loading
                    && !error
                    && lyrics?.status
                    === "NOT_FOUND"
                    && (
                        <div className="lyrics-state">
                            暂未找到歌词
                        </div>
                    )}


                {!loading
                    && !error
                    && lyrics?.instrumental
                    && (
                        <div className="lyrics-state">
                            ♪ 纯音乐，请欣赏
                        </div>
                    )}


                {!loading
                    && !error
                    && !lyrics?.instrumental
                    && timedLines.length > 0
                    && (
                        <div className="lyrics-scroll">
                            {timedLines.map(
                                (
                                    line,
                                    index,
                                ) => (
                                    <button
                                        key={
                                            `${line.time}-${index}`
                                        }
                                        type="button"
                                        ref={(element) => {
                                            lineRefs
                                                .current[
                                                index
                                                ] =
                                                element;
                                        }}
                                        className={
                                            index
                                            === activeIndex
                                                ? "lyrics-line active"
                                                : "lyrics-line"
                                        }
                                        onClick={() =>
                                            seek(
                                                line.time,
                                            )
                                        }
                                    >
                                    <span className="lyrics-line-original">
                                        {line.text}
                                    </span>

                                        {line.translation
                                                .trim()
                                                .length > 0
                                            && (
                                                <span className="lyrics-line-translation">
                                            {line.translation}
                                        </span>
                                            )}
                                    </button>
                                ),
                            )}
                        </div>
                    )}


                {!loading
                    && !error
                    && !lyrics?.instrumental
                    && timedLines.length === 0
                    && plainLines.length > 0
                    && (
                        <div className="lyrics-scroll plain">
                            {plainLines.map(
                                (
                                    line,
                                    index,
                                ) => (
                                    <div
                                        key={index}
                                        className="lyrics-plain-line"
                                    >
                                    <span className="lyrics-line-original">
                                        {line.text}
                                    </span>

                                        {line.translation
                                                .trim()
                                                .length > 0
                                            && (
                                                <span className="lyrics-line-translation">
                                            {line.translation}
                                        </span>
                                            )}
                                    </div>
                                ),
                            )}
                        </div>
                    )}


                {!loading
                    && !error
                    && lyrics?.status
                    === "MATCHED"
                    && !lyrics.instrumental
                    && timedLines.length === 0
                    && plainLines.length === 0
                    && (
                        <div className="lyrics-state">
                            暂无可显示的歌词内容
                        </div>
                    )}
            </div>
        </section>
    );
}