import {
    useEffect,
    useState,
    type ReactNode,
} from "react";

import {
    Navigate,
    useNavigate,
} from "react-router-dom";

import {
    getFavoritePlaylists,
    getMyPlaylists,
    type PlaylistSummary,
} from "../../api/playlist";

import { getApiError } from "../../auth/api/http";
import Header from "../../components/Header";
import PlaylistFormDialog from "../../components/playlist/PlaylistFormDialog";
import { useAuth } from "../../context/useAuth";

import "../../styles/MyMusic/MyMusicPage.css";

export default function MyMusicPage() {
    const navigate =
        useNavigate();

    const {
        user,
        loading: authLoading,
    } = useAuth();

    const [
        createdPlaylists,
        setCreatedPlaylists,
    ] = useState<
        PlaylistSummary[]
    >([]);

    const [
        favoritePlaylists,
        setFavoritePlaylists,
    ] = useState<
        PlaylistSummary[]
    >([]);

    const [loading, setLoading] =
        useState(false);

    const [error, setError] =
        useState("");

    const [
        createDialogOpen,
        setCreateDialogOpen,
    ] = useState(false);

    async function loadAll():
            Promise<void> {
        const [
            created,
            favorites,
        ] = await Promise.all([
            getMyPlaylists(),
            getFavoritePlaylists(),
        ]);

        setCreatedPlaylists(
            created,
        );

        setFavoritePlaylists(
            favorites,
        );
    }

    useEffect(() => {
        if (
            authLoading
            || user === null
        ) {
            return;
        }

        let cancelled = false;

        setLoading(true);
        setError("");

        void Promise.all([
            getMyPlaylists(),
            getFavoritePlaylists(),
        ])
            .then(([
                created,
                favorites,
            ]) => {
                if (cancelled) {
                    return;
                }

                setCreatedPlaylists(
                    created,
                );

                setFavoritePlaylists(
                    favorites,
                );
            })
            .catch((requestError) => {
                if (!cancelled) {
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
        user,
    ]);

    /*
     * 歌曲加入歌单、歌单编辑、歌单收藏/取消收藏后，
     * 都重新读取两个模块。
     */
    useEffect(() => {
        if (
            authLoading
            || user === null
        ) {
            return;
        }

        function refresh() {
            void loadAll()
                .catch(() => {
                    /*
                     * 主操作已经成功，
                     * 页面刷新失败不回滚主操作。
                     */
                });
        }

        window.addEventListener(
            "avemusic:playlist-updated",
            refresh,
        );

        window.addEventListener(
            "avemusic:playlist-favorite-updated",
            refresh,
        );

        return () => {
            window.removeEventListener(
                "avemusic:playlist-updated",
                refresh,
            );

            window.removeEventListener(
                "avemusic:playlist-favorite-updated",
                refresh,
            );
        };
    }, [
        authLoading,
        user,
    ]);

    if (authLoading) {
        return (
            <div className="my-music-page-shell">
                <Header />

                <main className="my-music-page">
                    <div className="my-music-loading">
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

    return (
        <div className="my-music-page-shell">
            <Header />

            <main className="my-music-page">
                <section className="my-music-heading">
                    <div>
                        <h1>
                            我的音乐
                        </h1>

                        <p>
                            管理创建的歌单和收藏的公开歌单
                        </p>
                    </div>
                </section>

                {error && (
                    <div className="my-music-error">
                        {error}
                    </div>
                )}

                <PlaylistSection
                    title="创建的歌单"
                    playlists={
                        createdPlaylists
                    }
                    loading={loading}
                    emptyTitle="还没有创建歌单"
                    emptyDescription="创建一个歌单，然后从播放器收藏歌曲。"
                    action={
                        <button
                            type="button"
                            className="create-playlist-button"
                            onClick={() =>
                                setCreateDialogOpen(
                                    true,
                                )
                            }
                        >
                            + 创建歌单
                        </button>
                    }
                    onOpen={(playlist) =>
                        navigate(
                            `/playlists/${playlist.id}`,
                        )
                    }
                />

                <PlaylistSection
                    title="收藏的歌单"
                    playlists={
                        favoritePlaylists
                    }
                    loading={loading}
                    emptyTitle="还没有收藏歌单"
                    emptyDescription="打开其他用户的公开歌单，点击“收藏歌单”后会显示在这里。"
                    favoriteSection
                    onOpen={(playlist) =>
                        navigate(
                            `/playlists/${playlist.id}`,
                        )
                    }
                />
            </main>

            <PlaylistFormDialog
                open={createDialogOpen}
                mode="create"
                onClose={() =>
                    setCreateDialogOpen(
                        false,
                    )
                }
                onSaved={(playlist) => {
                    setCreatedPlaylists(
                        (current) => [
                            playlist,
                            ...current,
                        ],
                    );

                    setCreateDialogOpen(
                        false,
                    );
                }}
            />
        </div>
    );
}

function PlaylistSection({
    title,
    playlists,
    loading,
    emptyTitle,
    emptyDescription,
    action = null,
    favoriteSection = false,
    onOpen,
}: {
    title: string;
    playlists:
        PlaylistSummary[];
    loading: boolean;
    emptyTitle: string;
    emptyDescription: string;
    action?:
        ReactNode;
    favoriteSection?: boolean;
    onOpen(
        playlist:
            PlaylistSummary,
    ): void;
}) {
    return (
        <section className="my-playlists-section">
            <header>
                <div>
                    <h2>
                        {title}
                    </h2>

                    <span>
                        {playlists.length}
                        {" "}
                        个
                    </span>
                </div>

                {action}
            </header>

            {loading ? (
                <div className="my-music-loading">
                    正在加载歌单...
                </div>
            ) : playlists.length === 0 ? (
                <div className="my-music-empty compact">
                    <div className="my-music-empty-icon">
                        {favoriteSection
                            ? "♡"
                            : "♫"}
                    </div>

                    <strong>
                        {emptyTitle}
                    </strong>

                    <span>
                        {emptyDescription}
                    </span>
                </div>
            ) : (
                <div className="my-playlist-grid">
                    {playlists.map(
                        (playlist) => (
                            <article
                                key={
                                    playlist.id
                                }
                                className="my-playlist-card clickable"
                                role="link"
                                tabIndex={0}
                                onClick={() =>
                                    onOpen(
                                        playlist,
                                    )
                                }
                                onKeyDown={(event) => {
                                    if (
                                        event.key
                                        === "Enter"
                                        || event.key
                                        === " "
                                    ) {
                                        onOpen(
                                            playlist,
                                        );
                                    }
                                }}
                            >
                                <div className="my-playlist-cover">
                                    {playlist.coverUrl ? (
                                        <img
                                            src={
                                                playlist.coverUrl
                                            }
                                            alt={
                                                playlist.name
                                            }
                                        />
                                    ) : (
                                        <span>
                                            ♫
                                        </span>
                                    )}
                                </div>

                                <div className="my-playlist-info">
                                    <div className="my-playlist-title-row">
                                        <h3>
                                            {playlist.name}
                                        </h3>

                                        <span
                                            className={
                                                playlist.visibility
                                                === "PUBLIC"
                                                    ? "playlist-visibility public"
                                                    : "playlist-visibility private"
                                            }
                                        >
                                            {playlist.visibility
                                            === "PUBLIC"
                                                ? "公开"
                                                : "私密"}
                                        </span>
                                    </div>

                                    <p>
                                        {playlist.introduction
                                            || "暂无简介"}
                                    </p>

                                    <footer>
                                        <span>
                                            {playlist.songCount}
                                            {" "}
                                            首歌曲
                                            {" · "}
                                            {playlist.favoriteCount}
                                            {" "}
                                            人收藏
                                        </span>

                                        {favoriteSection ? (
                                            <span>
                                                创建者ID：
                                                {playlist.ownerUserId}
                                            </span>
                                        ) : (
                                            <span>
                                                {playlist.createdAt}
                                            </span>
                                        )}
                                    </footer>
                                </div>
                            </article>
                        ),
                    )}
                </div>
            )}
        </section>
    );
}
