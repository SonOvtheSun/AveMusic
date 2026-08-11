import {
    useEffect,
    useState,
} from "react";

import {
    useNavigate,
} from "react-router-dom";

import {
    getPopularPlaylists,
    type PlaylistSummary,
} from "../../api/playlist.ts";

import Header
    from "../../components/Header.tsx";

import {
    getApiError,
} from "../../auth/api/http.ts";

import {
    useAuth,
} from "../../context/useAuth.ts";

import "../../styles/Playlist/PlaylistRankingPage.css";

export default function
    PlaylistRankingPage() {
    const navigate =
        useNavigate();

    const { user } =
        useAuth();

    const [page, setPage] =
        useState(1);

    const [
        playlists,
        setPlaylists,
    ] = useState<
        PlaylistSummary[]
    >([]);

    const [
        totalPages,
        setTotalPages,
    ] = useState(0);

    const [total, setTotal] =
        useState(0);

    const [loading, setLoading] =
        useState(false);

    const [error, setError] =
        useState("");

    useEffect(() => {
        let cancelled = false;

        setLoading(true);
        setError("");

        void getPopularPlaylists(
            page,
        )
            .then((result) => {
                if (cancelled) {
                    return;
                }

                setPlaylists(
                    result.records,
                );

                setTotal(
                    result.total,
                );

                setTotalPages(
                    Math.min(
                        result.totalPages,
                        20,
                    ),
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
    }, [page]);

    function openPlaylist(
        playlist:
        PlaylistSummary,
    ): void {
        /*
         * 当前歌单详情与收藏状态
         * 仍依赖登录用户。
         */
        if (user === null) {
            navigate("/auth");
            return;
        }

        navigate(
            `/discover/playlists/${playlist.id}`,
        );
    }

    return (
        <div className="playlist-ranking-shell">
            <Header />

            <main className="playlist-ranking-page">
                <header className="playlist-ranking-title">
                    <div>
                        <h1>
                            热门歌单
                        </h1>

                        <p>
                            按收藏数量排序
                        </p>
                    </div>

                    <span>
                        Top {total}
                    </span>
                </header>

                {error && (
                    <div className="playlist-ranking-error">
                        {error}
                    </div>
                )}

                {loading ? (
                    <div className="playlist-ranking-state">
                        正在加载歌单...
                    </div>
                ) : (
                    <div className="playlist-ranking-grid">
                        {playlists.map(
                            (
                                playlist,
                                index,
                            ) => {
                                const rank =
                                    (
                                        page - 1
                                    )
                                    * 20
                                    + index
                                    + 1;

                                return (
                                    <article
                                        key={
                                            playlist.id
                                        }
                                        className="playlist-ranking-card"
                                        onClick={() =>
                                            openPlaylist(
                                                playlist,
                                            )
                                        }
                                    >
                                        <div className="playlist-ranking-cover">
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

                                            <strong>
                                                #{rank}
                                            </strong>
                                        </div>

                                        <h3>
                                            {playlist.name}
                                        </h3>

                                        <div className="playlist-ranking-meta">
                                            <span>
                                                {playlist.songCount}
                                                {" "}
                                                首歌曲
                                            </span>

                                            <span>
                                                ♥
                                                {" "}
                                                {playlist.favoriteCount}
                                            </span>
                                        </div>
                                    </article>
                                );
                            },
                        )}
                    </div>
                )}

                {totalPages > 1 && (
                    <div className="playlist-ranking-pagination">
                        <button
                            type="button"
                            disabled={
                                page <= 1
                            }
                            onClick={() =>
                                setPage(
                                    page - 1,
                                )
                            }
                        >
                            上一页
                        </button>

                        {Array.from(
                            {
                                length:
                                totalPages,
                            },
                            (_, index) =>
                                index + 1,
                        ).map(
                            (item) => (
                                <button
                                    key={
                                        item
                                    }
                                    type="button"
                                    className={
                                        item
                                        === page
                                            ? "active"
                                            : ""
                                    }
                                    onClick={() =>
                                        setPage(
                                            item,
                                        )
                                    }
                                >
                                    {item}
                                </button>
                            ),
                        )}

                        <button
                            type="button"
                            disabled={
                                page
                                >= totalPages
                            }
                            onClick={() =>
                                setPage(
                                    page + 1,
                                )
                            }
                        >
                            下一页
                        </button>
                    </div>
                )}
            </main>
        </div>
    );
}