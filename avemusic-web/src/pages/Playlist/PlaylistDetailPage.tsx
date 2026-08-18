import {
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    Navigate,
    useParams,
} from "react-router-dom";

import {
    favoritePlaylist,
    getPlaylistDetail,
    removeSongFromPlaylist,
    unfavoritePlaylist,
    type PlaylistDetail,
} from "../../api/playlist";

import type {
    SongCard,
} from "../../api/music";

import { getApiError } from "../../auth/api/http";

import Header
    from "../../components/Header";

import SongCollectionList,
{
    type CollectionSong,
} from "../../components/music/SongCollectionList";

import PlaylistFormDialog
    from "../../components/playlist/PlaylistFormDialog";

import { useAuth }
    from "../../context/useAuth";

import { usePlayer }
    from "../../player/usePlayer";

import "../../styles/Playlist/PlaylistDetailPage.css";

const DEFAULT_COVER =
    "https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?auto=format&fit=crop&w=600&q=80";

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

export default function PlaylistDetailPage() {
    const {
        playlistId,
    } = useParams<{
        playlistId: string;
    }>();

    const {
        user,
        loading: authLoading,
    } = useAuth();

    const {
        playQueue,
    } = usePlayer();

    const [detail, setDetail] =
        useState<
            PlaylistDetail | null
        >(null);

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState("");

    const [keyword, setKeyword] =
        useState("");

    const [
        editDialogOpen,
        setEditDialogOpen,
    ] = useState(false);

    const [
        favoriteSubmitting,
        setFavoriteSubmitting,
    ] = useState(false);

    async function reloadPlaylist():
            Promise<void> {
        if (!playlistId) {
            return;
        }

        const result =
            await getPlaylistDetail(
                playlistId,
            );

        setDetail(result);
    }

    useEffect(() => {
        if (
            authLoading
            || user === null
            || !playlistId
        ) {
            return;
        }

        let cancelled = false;

        setLoading(true);
        setError("");

        void getPlaylistDetail(
            playlistId,
        )
            .then((result) => {
                if (!cancelled) {
                    setDetail(result);
                }
            })
            .catch((requestError) => {
                if (!cancelled) {
                    setDetail(null);

                    setError(
                        getApiError(
                            requestError,
                        ).message,
                    );
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [
        authLoading,
        playlistId,
        user,
    ]);

    /*
     * 当前歌单内容、封面或歌曲数量变化后刷新详情。
     */
    useEffect(() => {
        if (!playlistId) {
            return;
        }

        function handlePlaylistUpdated(
            event: Event,
        ) {
            const customEvent =
                event as CustomEvent<{
                    playlistId?: string;
                }>;

            const changedPlaylistId =
                customEvent.detail
                    ?.playlistId;

            if (
                changedPlaylistId
                && changedPlaylistId
                    !== playlistId
            ) {
                return;
            }

            void reloadPlaylist()
                .catch(() => {
                    // 保留当前页面数据。
                });
        }

        window.addEventListener(
            "avemusic:playlist-updated",
            handlePlaylistUpdated,
        );

        return () => {
            window.removeEventListener(
                "avemusic:playlist-updated",
                handlePlaylistUpdated,
            );
        };
    }, [playlistId]);

    const collectionSongs =
        useMemo<
            CollectionSong[]
        >(
            () =>
                (detail?.songs ?? [])
                    .map((song) => ({
                        id: song.id,
                        name: song.name,

                        artistName:
                            song.artistName,

                        artistIds:
                            song.artistIds
                            ?? [],

                        albumName:
                            song.albumName,

                        coverUrl:
                            song.coverUrl,

                        audioUrl:
                            song.audioUrl,

                        durationSeconds:
                            song.durationSeconds,

                        playCount:
                            song.playCount,
                    })),
            [detail],
        );

    const playableSongs =
        useMemo(
            () =>
                collectionSongs
                    .filter(
                        (song) =>
                            Boolean(
                                song.audioUrl,
                            ),
                    )
                    .map(toSongCard),
            [collectionSongs],
        );

    if (authLoading) {
        return (
            <div className="playlist-detail-shell">
                <Header />

                <main className="playlist-detail-page">
                    <div className="playlist-detail-state">
                        正在读取登录状态...
                    </div>
                </main>
            </div>
        );
    }

    if (user === null) {
        return (
            <Navigate
                to="/auth"
                replace
            />
        );
    }

    if (loading) {
        return (
            <div className="playlist-detail-shell">
                <Header />

                <main className="playlist-detail-page">
                    <div className="playlist-detail-state">
                        正在加载歌单...
                    </div>
                </main>
            </div>
        );
    }

    if (
        error
        || detail === null
    ) {
        return (
            <div className="playlist-detail-shell">
                <Header />

                <main className="playlist-detail-page">
                    <div className="playlist-detail-state error">
                        {error
                            || "歌单不存在"}
                    </div>
                </main>
            </div>
        );
    }

    const playlist =
        detail.playlist;

    const isOwner =
        String(
            playlist.ownerUserId
            ?? "",
        )
        ===
        String(
            user.userId
            ?? "",
        );

    async function removeSong(
        song: CollectionSong,
    ): Promise<void> {
        if (!isOwner) {
            return;
        }

        if (
            !window.confirm(
                `确定将“${song.name}”从当前歌单中删除吗？`,
            )
        ) {
            return;
        }

        try {
            await removeSongFromPlaylist(
                playlist.id,
                song.id,
            );

            await reloadPlaylist();

            window.dispatchEvent(
                new CustomEvent(
                    "avemusic:playlist-updated",
                    {
                        detail: {
                            playlistId:
                                playlist.id,
                        },
                    },
                ),
            );
        } catch (requestError) {
            window.alert(
                getApiError(
                    requestError,
                ).message,
            );
        }
    }

    async function toggleFavorite():
        Promise<void> {
        if (
            isOwner
            || detail === null
        ) {
            return;
        }

        setFavoriteSubmitting(true);

        try {
            if (
                detail.favoritedByMe
            ) {
                await unfavoritePlaylist(
                    playlist.id,
                );

                setDetail((current) =>
                    current === null
                        ? current
                        : {
                            ...current,

                            favoritedByMe:
                                false,

                            playlist: {
                                ...current.playlist,

                                favoriteCount:
                                    Math.max(
                                        0,
                                        current.playlist
                                            .favoriteCount
                                        - 1,
                                    ),
                            },
                        },
                );
            } else {
                await favoritePlaylist(
                    playlist.id,
                );

                setDetail((current) =>
                    current === null
                        ? current
                        : {
                            ...current,

                            favoritedByMe:
                                true,

                            playlist: {
                                ...current.playlist,

                                favoriteCount:
                                    current.playlist
                                        .favoriteCount
                                    + 1,
                            },
                        },
                );
            }

            window.dispatchEvent(
                new CustomEvent(
                    "avemusic:playlist-favorite-updated",
                    {
                        detail: {
                            playlistId:
                                playlist.id,
                        },
                    },
                ),
            );
        } catch (requestError) {
            window.alert(
                getApiError(
                    requestError,
                ).message,
            );
        } finally {
            setFavoriteSubmitting(
                false,
            );
        }
    }

    return (
        <div className="playlist-detail-shell">
            <Header />

            <main className="playlist-detail-page">
                <section className="playlist-detail-hero">
                    <div className="playlist-detail-cover">
                        <img
                            src={
                                playlist.coverUrl
                                ?? DEFAULT_COVER
                            }
                            alt={
                                playlist.name
                            }
                        />

                        <span>
                            {playlist.songCount}
                        </span>
                    </div>

                    <div className="playlist-detail-info">
                        <div className="playlist-title-line">
                            <h1>
                                {playlist.name}
                            </h1>

                            {isOwner && (
                                <button
                                    type="button"
                                    className="playlist-edit-icon"
                                    aria-label="编辑歌单"
                                    title="编辑歌单"
                                    onClick={() =>
                                        setEditDialogOpen(
                                            true,
                                        )
                                    }
                                >
                                    ✎
                                </button>
                            )}
                        </div>

                        <div className="playlist-owner-row">
                            {isOwner
                            && user.avatarUrl ? (
                                <img
                                    src={
                                        user.avatarUrl
                                    }
                                    alt={
                                        user.username
                                    }
                                />
                            ) : (
                                <span className="playlist-owner-fallback">
                                    {isOwner
                                        ? user.username
                                            .slice(0, 1)
                                            .toUpperCase()
                                        : "U"}
                                </span>
                            )}

                            <strong>
                                {isOwner
                                    ? user.username
                                    : `用户 ${playlist.ownerUserId}`}
                            </strong>

                            <span>
                                {playlist.createdAt
                                    .slice(0, 10)}
                                创建
                            </span>

                            <span className="playlist-detail-visibility">
                                {playlist.visibility
                                === "PUBLIC"
                                    ? "公开"
                                    : "私密"}
                            </span>

                            <span className="playlist-detail-favorite-count">
                                {playlist.favoriteCount}
                                {" "}
                                人收藏
                            </span>
                        </div>

                        <div className="playlist-detail-actions">
                            <button
                                type="button"
                                className="playlist-play-all"
                                disabled={
                                    playableSongs
                                        .length
                                    === 0
                                }
                                onClick={() => {
                                    if (
                                        playableSongs
                                            .length
                                        > 0
                                    ) {
                                        /*
                                         * 播放列表 = 当前完整歌单。
                                         */
                                        playQueue(
                                            playableSongs,
                                            0,
                                        );
                                    }
                                }}
                            >
                                ▶ 播放全部
                            </button>

                            {isOwner ? (
                                <button
                                    type="button"
                                    className="playlist-edit-button"
                                    onClick={() =>
                                        setEditDialogOpen(
                                            true,
                                        )
                                    }
                                >
                                    编辑歌单
                                </button>
                            ) : (
                                <button
                                    type="button"
                                    className={
                                        detail.favoritedByMe
                                            ? "playlist-favorite-button active"
                                            : "playlist-favorite-button"
                                    }
                                    disabled={
                                        favoriteSubmitting
                                    }
                                    onClick={() => {
                                        void toggleFavorite();
                                    }}
                                >
                                    {favoriteSubmitting
                                        ? "处理中..."
                                        : detail.favoritedByMe
                                            ? "♥ 已收藏"
                                            : "♡ 收藏歌单"}
                                </button>
                            )}
                        </div>
                    </div>
                </section>

                <div className="playlist-content-layout">
                    <section className="playlist-song-section">
                        <header className="playlist-song-header">
                            <div className="playlist-song-tab">
                                歌曲

                                <sup>
                                    {detail.songs.length}
                                </sup>
                            </div>

                            <input
                                value={keyword}
                                placeholder="搜索歌单内歌曲"
                                onChange={(event) =>
                                    setKeyword(
                                        event.target.value,
                                    )
                                }
                            />
                        </header>

                        <SongCollectionList
                            songs={collectionSongs}
                            keyword={keyword}

                            onRemoveSong={
                                isOwner
                                    ? removeSong
                                    : undefined
                            }
                        />
                    </section>

                    <aside className="playlist-introduction-panel">
                        <h2>
                            简介
                        </h2>

                        <div className="playlist-introduction-content">
                            {playlist.introduction ? (
                                <p>
                                    {playlist.introduction}
                                </p>
                            ) : (
                                <span>
                    暂无歌单简介
                </span>
                            )}
                        </div>
                    </aside>
                </div>
            </main>

            {isOwner && (
                <PlaylistFormDialog
                    open={
                        editDialogOpen
                    }
                    mode="edit"
                    playlist={
                        playlist
                    }
                    onClose={() =>
                        setEditDialogOpen(
                            false,
                        )
                    }
                    onSaved={(updated) => {
                        setDetail((current) =>
                            current === null
                                ? current
                                : {
                                    ...current,

                                    playlist:
                                        updated,
                                },
                        );

                        setEditDialogOpen(
                            false,
                        );

                        window.dispatchEvent(
                            new CustomEvent(
                                "avemusic:playlist-updated",
                                {
                                    detail: {
                                        playlistId:
                                            playlist.id,
                                    },
                                },
                            ),
                        );
                    }}
                />
            )}
        </div>
    );
}
