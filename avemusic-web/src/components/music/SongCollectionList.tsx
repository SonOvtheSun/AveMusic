import {
    useEffect,
    useMemo,
    useState,
    type MouseEvent,
} from "react";

import {
    useNavigate,
} from "react-router-dom";

import type {
    SongCard,
} from "../../api/music";

import { useAuth } from "../../context/useAuth";
import { usePlayer } from "../../player/usePlayer";

import CollectToPlaylistDialog
    from "../player/CollectToPlaylistDialog";
import PlaybackIcon
    from "../player/PlaybackIcon";

import "../../styles/components/SongCollectionList.css";

export interface CollectionSong {
    id: string;
    name: string;

    artistName: string;
    artistIds: string[];

    albumName: string | null;

    coverUrl: string | null;
    audioUrl: string | null;

    durationSeconds: number;
    playCount: number;
}

interface ContextMenuState {
    song: CollectionSong;
    x: number;
    y: number;
}

interface SongCollectionListProps {
    songs: CollectionSong[];

    keyword?: string;

    /**
     * 只有歌单详情页传入。
     *
     * 专辑页 / 歌手页不传，
     * 右键菜单自然不会显示“从歌单中删除”。
     */
    onRemoveSong?(
        song: CollectionSong,
    ): Promise<void> | void;
}

function formatDuration(
    seconds: number,
): string {
    const total =
        Math.max(
            0,
            Math.floor(seconds),
        );

    const minutes =
        Math.floor(
            total / 60,
        );

    const remaining =
        total % 60;

    return `${String(minutes)
        .padStart(2, "0")}:${String(remaining)
        .padStart(2, "0")}`;
}

function toSongCard(
    song: CollectionSong,
): SongCard {
    return {
        id: song.id,
        name: song.name,

        artistName:
        song.artistName,

        artistIds:
        song.artistIds,

        coverUrl:
        song.coverUrl,

        audioUrl:
        song.audioUrl,

        durationSeconds:
        song.durationSeconds,

        playCount:
        song.playCount,
    };
}

function sameQueue(
    first: SongCard[],
    second: SongCard[],
): boolean {
    if (
        first.length
        !== second.length
    ) {
        return false;
    }

    return first.every(
        (song, index) =>
            song.id
            === second[index]?.id,
    );
}

export default function SongCollectionList({
                                               songs,
                                               keyword = "",
                                               onRemoveSong,
                                           }: SongCollectionListProps) {
    const navigate =
        useNavigate();

    const {
        user,
    } = useAuth();

    const {
        queue,
        playQueue,
        enqueueNext,
        currentSong,
        isPlaying,
        togglePlay,
    } = usePlayer();

    const [
        contextMenu,
        setContextMenu,
    ] = useState<
        ContextMenuState | null
    >(null);

    const [
        collectSong,
        setCollectSong,
    ] = useState<
        CollectionSong | null
    >(null);

    /*
     * 播放队列一定来自页面的完整歌曲集合，
     * 而不是搜索过滤后的结果。
     */
    const queueSongs =
        useMemo(
            () =>
                songs
                    .filter(
                        (song) =>
                            Boolean(
                                song.audioUrl,
                            ),
                    )
                    .map(
                        toSongCard,
                    ),
            [songs],
        );

    const visibleSongs =
        useMemo(() => {
            const normalized =
                keyword
                    .trim()
                    .toLowerCase();

            if (
                normalized.length
                === 0
            ) {
                return songs;
            }

            return songs.filter(
                (song) =>
                    song.name
                        .toLowerCase()
                        .includes(
                            normalized,
                        )
                    || song.artistName
                        .toLowerCase()
                        .includes(
                            normalized,
                        )
                    || (
                        song.albumName
                            ?.toLowerCase()
                            .includes(
                                normalized,
                            )
                        ?? false
                    ),
            );
        }, [
            keyword,
            songs,
        ]);

    useEffect(() => {
        if (
            contextMenu
            === null
        ) {
            return;
        }

        function closeMenu() {
            setContextMenu(
                null,
            );
        }

        function handleKeyDown(
            event: KeyboardEvent,
        ) {
            if (
                event.key
                === "Escape"
            ) {
                closeMenu();
            }
        }

        window.addEventListener(
            "click",
            closeMenu,
        );

        window.addEventListener(
            "scroll",
            closeMenu,
            true,
        );

        window.addEventListener(
            "keydown",
            handleKeyDown,
        );

        return () => {
            window.removeEventListener(
                "click",
                closeMenu,
            );

            window.removeEventListener(
                "scroll",
                closeMenu,
                true,
            );

            window.removeEventListener(
                "keydown",
                handleKeyDown,
            );
        };
    }, [
        contextMenu,
    ]);

    function playSong(
        song: CollectionSong,
    ): void {
        if (!song.audioUrl) {
            return;
        }

        const targetIndex =
            queueSongs.findIndex(
                (item) =>
                    item.id
                    === song.id,
            );

        if (
            targetIndex < 0
        ) {
            return;
        }

        /*
         * 当前歌曲 + 当前页面队列：
         * 只做播放 / 暂停切换，不重新从头播放。
         */
        if (
            currentSong?.id
            === song.id
            && sameQueue(
                queue,
                queueSongs,
            )
        ) {
            togglePlay();
            return;
        }

        /*
         * 当前歌曲虽然相同，但来源队列不同：
         * 重新以本页面集合建立队列。
         */
        playQueue(
            queueSongs,
            targetIndex,
        );
    }

    function handleContextMenu(
        event:
        MouseEvent<HTMLDivElement>,
        song: CollectionSong,
    ): void {
        event.preventDefault();

        const menuWidth =
            196;

        const menuHeight =
            onRemoveSong
                ? 230
                : 190;

        const x =
            Math.max(
                8,
                Math.min(
                    event.clientX,
                    window.innerWidth
                    - menuWidth
                    - 10,
                ),
            );

        const y =
            Math.max(
                8,
                Math.min(
                    event.clientY,
                    window.innerHeight
                    - menuHeight
                    - 10,
                ),
            );

        setContextMenu({
            song,
            x,
            y,
        });
    }

    function downloadSong(
        song: CollectionSong,
    ): void {
        if (!song.audioUrl) {
            return;
        }

        const separator =
            song.audioUrl
                .includes("?")
                ? "&"
                : "?";

        const anchor =
            document.createElement(
                "a",
            );

        anchor.href =
            `${song.audioUrl}${separator}download=1`;

        anchor.target =
            "_blank";

        anchor.rel =
            "noopener";

        document.body
            .appendChild(
                anchor,
            );

        anchor.click();
        anchor.remove();
    }

    function renderArtistLinks(
        song: CollectionSong,
    ) {
        const names =
            song.artistName
                .split(" / ")
                .map(
                    (item) =>
                        item.trim(),
                )
                .filter(Boolean);

        if (
            names.length === 0
        ) {
            return song.artistName;
        }

        return names.map(
            (
                name,
                index,
            ) => {
                const artistId =
                    song.artistIds[
                        index
                        ];

                return (
                    <span
                        key={
                            artistId
                            ?? `${song.id}-${name}`
                        }
                        className="collection-artist-link-group"
                    >
                        {index > 0 && (
                            <span
                                className="collection-artist-separator"
                                aria-hidden="true"
                            >
                                {" / "}
                            </span>
                        )}

                        {artistId ? (
                            <button
                                type="button"
                                className="collection-artist-link"
                                onDoubleClick={(
                                    event,
                                ) =>
                                    event
                                        .stopPropagation()
                                }
                                onClick={(
                                    event,
                                ) => {
                                    event
                                        .stopPropagation();

                                    navigate(
                                        `/artists/${artistId}`,
                                    );
                                }}
                            >
                                {name}
                            </button>
                        ) : (
                            <span>
                                {name}
                            </span>
                        )}
                    </span>
                );
            },
        );
    }

    const contextSongPlaying =
        contextMenu !== null
        && currentSong?.id
        === contextMenu.song.id
        && isPlaying;

    return (
        <>
            <div className="collection-song-table-header">
                <span>#</span>
                <span>标题</span>
                <span>专辑</span>
                <span>时长</span>
            </div>

            {visibleSongs.length === 0 ? (
                <div className="collection-song-empty">
                    {songs.length === 0
                        ? "暂无歌曲"
                        : "没有匹配的歌曲"}
                </div>
            ) : (
                <div className="collection-song-list">
                    {visibleSongs.map(
                        (
                            song,
                            index,
                        ) => {
                            const active =
                                currentSong
                                    ?.id
                                === song.id;

                            return (
                                <div
                                    key={
                                        song.id
                                    }
                                    className={
                                        active
                                            ? "collection-song-row active"
                                            : "collection-song-row"
                                    }
                                    title="双击播放，右键查看更多操作"
                                    onDoubleClick={() =>
                                        playSong(
                                            song,
                                        )
                                    }
                                    onContextMenu={(
                                        event,
                                    ) =>
                                        handleContextMenu(
                                            event,
                                            song,
                                        )
                                    }
                                >
                                    <span className="collection-song-index">
                                        {String(
                                            index + 1,
                                        ).padStart(
                                            2,
                                            "0",
                                        )}
                                    </span>

                                    <div className="collection-song-title-cell">
                                        <button
                                            type="button"
                                            className="collection-song-play"
                                            disabled={
                                                !song.audioUrl
                                            }
                                            aria-label={
                                                active
                                                && isPlaying
                                                    ? "暂停"
                                                    : "播放"
                                            }
                                            title={
                                                active
                                                && isPlaying
                                                    ? "暂停"
                                                    : "播放"
                                            }
                                            onDoubleClick={(
                                                event,
                                            ) =>
                                                event
                                                    .stopPropagation()
                                            }
                                            onClick={(
                                                event,
                                            ) => {
                                                event
                                                    .stopPropagation();

                                                playSong(
                                                    song,
                                                );
                                            }}
                                        >
                                            <PlaybackIcon
                                                type={
                                                    active
                                                    && isPlaying
                                                        ? "pause"
                                                        : "play"
                                                }
                                                size={
                                                    12
                                                }
                                            />
                                        </button>

                                        <div
                                            className="collection-song-cover"
                                        >
                                            {song.coverUrl ? (
                                                <img
                                                    src={
                                                        song.coverUrl
                                                    }
                                                    alt=""
                                                />
                                            ) : (
                                                <span>
                                                    ♫
                                                </span>
                                            )}
                                        </div>

                                        <div className="collection-song-main">
                                            <strong
                                                title={
                                                    song.name
                                                }
                                            >
                                                {song.name}
                                            </strong>

                                            <small>
                                                {renderArtistLinks(
                                                    song,
                                                )}
                                            </small>
                                        </div>
                                    </div>

                                    <span
                                        className="collection-song-album"
                                        title={
                                            song.albumName
                                            ?? "无所属专辑"
                                        }
                                    >
                                        {song.albumName
                                            ?? "无所属专辑"}
                                    </span>

                                    <span className="collection-song-duration">
                                        {formatDuration(
                                            song.durationSeconds,
                                        )}
                                    </span>
                                </div>
                            );
                        },
                    )}
                </div>
            )}

            {contextMenu && (
                <div
                    className="collection-context-menu"
                    style={{
                        left:
                        contextMenu.x,
                        top:
                        contextMenu.y,
                    }}
                    onClick={(
                        event,
                    ) =>
                        event
                            .stopPropagation()
                    }
                >
                    <button
                        type="button"
                        disabled={
                            !contextMenu
                                .song.audioUrl
                        }
                        onClick={() => {
                            playSong(
                                contextMenu
                                    .song,
                            );

                            setContextMenu(
                                null,
                            );
                        }}
                    >
                        <PlaybackIcon
                            type={
                                contextSongPlaying
                                    ? "pause"
                                    : "play"
                            }
                            size={15}
                        />

                        <span>
                            {contextSongPlaying
                                ? "暂停"
                                : "播放"}
                        </span>
                    </button>

                    <button
                        type="button"
                        disabled={
                            !contextMenu
                                .song.audioUrl
                        }
                        onClick={() => {
                            enqueueNext(
                                toSongCard(
                                    contextMenu
                                        .song,
                                ),
                            );

                            setContextMenu(
                                null,
                            );
                        }}
                    >
                        <span className="collection-context-symbol">
                            ⏭
                        </span>

                        <span>
                            下一首播放
                        </span>
                    </button>

                    <button
                        type="button"
                        onClick={() => {
                            if (
                                user
                                === null
                            ) {
                                setContextMenu(
                                    null,
                                );

                                navigate(
                                    "/auth",
                                );

                                return;
                            }

                            setCollectSong(
                                contextMenu
                                    .song,
                            );

                            setContextMenu(
                                null,
                            );
                        }}
                    >
                        <span className="collection-context-symbol">
                            ♡
                        </span>

                        <span>
                            收藏
                        </span>
                    </button>

                    <button
                        type="button"
                        disabled={
                            !contextMenu
                                .song.audioUrl
                        }
                        onClick={() => {
                            downloadSong(
                                contextMenu
                                    .song,
                            );

                            setContextMenu(
                                null,
                            );
                        }}
                    >
                        <span className="collection-context-symbol">
                            ↓
                        </span>

                        <span>
                            下载
                        </span>
                    </button>

                    {onRemoveSong && (
                        <>
                            <div className="collection-context-divider" />

                            <button
                                type="button"
                                className="danger"
                                onClick={() => {
                                    const song =
                                        contextMenu
                                            .song;

                                    setContextMenu(
                                        null,
                                    );

                                    void onRemoveSong(
                                        song,
                                    );
                                }}
                            >
                                <span className="collection-context-symbol">
                                    🗑
                                </span>

                                <span>
                                    从歌单中删除
                                </span>
                            </button>
                        </>
                    )}
                </div>
            )}

            <CollectToPlaylistDialog
                open={
                    collectSong
                    !== null
                }
                songId={
                    collectSong
                        ?.id
                    ?? ""
                }
                songName={
                    collectSong
                        ?.name
                    ?? ""
                }
                onClose={() =>
                    setCollectSong(
                        null,
                    )
                }
            />
        </>
    );
}
