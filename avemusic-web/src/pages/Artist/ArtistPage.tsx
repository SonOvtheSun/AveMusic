import {
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    useNavigate,
    useParams,
} from "react-router-dom";

import {
    getArtistDetail,
    type ArtistDetail,
} from "../../api/music";

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

import { usePlayer } from "../../player/usePlayer";

import "../../styles/Artist/ArtistPage.css";

const DEFAULT_ARTIST_AVATAR =
    "https://images.unsplash.com/photo-1511367461989-f85a21fda167?auto=format&fit=crop&w=500&q=80";

const DEFAULT_ALBUM_COVER =
    "https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?auto=format&fit=crop&w=500&q=80";

type ArtistTab =
    | "SONGS"
    | "ALBUMS"
    | "ABOUT";

function formatCount(
    value: number,
): string {
    if (value >= 10000) {
        return `${(value / 10000)
            .toFixed(1)}万`;
    }

    return String(value);
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

export default function ArtistPage() {
    const {
        id,
    } = useParams<{
        id: string;
    }>();

    const navigate =
        useNavigate();

    const {
        playQueue,
    } = usePlayer();

    const [artist, setArtist] =
        useState<
            ArtistDetail | null
        >(null);

    const [tab, setTab] =
        useState<
            ArtistTab
        >("SONGS");

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState("");

    useEffect(() => {
        if (!id) {
            setError(
                "音乐人ID不存在",
            );

            setLoading(false);
            return;
        }

        let cancelled = false;

        setLoading(true);
        setError("");

        void getArtistDetail(id)
            .then((result) => {
                if (!cancelled) {
                    setArtist(
                        result,
                    );
                }
            })
            .catch((requestError) => {
                if (!cancelled) {
                    setArtist(null);

                    setError(
                        getApiError(
                            requestError,
                        ).message,
                    );
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(
                        false,
                    );
                }
            });

        return () => {
            cancelled = true;
        };
    }, [id]);

    useEffect(() => {
        function handlePlayCounted(
            event: Event,
        ): void {
            const customEvent =
                event as CustomEvent<{
                    songId: string;
                    playCount: number;
                }>;

            const detail =
                customEvent.detail;

            if (!detail) {
                return;
            }

            setArtist((current) => {
                if (current === null) {
                    return current;
                }

                return {
                    ...current,

                    songs:
                        current.songs.map(
                            (song) =>
                                song.id
                                === detail.songId
                                    ? {
                                        ...song,

                                        playCount:
                                        detail.playCount,
                                    }
                                    : song,
                        ),
                };
            });
        }

        window.addEventListener(
            "avemusic:play-counted",
            handlePlayCounted,
        );

        return () => {
            window.removeEventListener(
                "avemusic:play-counted",
                handlePlayCounted,
            );
        };
    }, []);

    const collectionSongs =
        useMemo<CollectionSong[]>(
            () =>
                (artist?.songs ?? [])
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
            [artist],
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
                    .map(
                        toSongCard,
                    ),
            [
                collectionSongs,
            ],
        );

    function playAll(): void {
        if (
            playableSongs.length
            === 0
        ) {
            return;
        }

        playQueue(
            playableSongs,
            0,
        );
    }

    return (
        <div className="artist-page-shell">
            <Header />

            <main className="artist-page">
                {loading && (
                    <div className="artist-page-state">
                        正在加载音乐人资料...
                    </div>
                )}

                {!loading
                    && error
                    && (
                        <div className="artist-page-state error">
                            <strong>
                                无法打开音乐人页面
                            </strong>

                            <span>
                                {error}
                            </span>

                            <button
                                type="button"
                                onClick={() =>
                                    navigate("/")
                                }
                            >
                                返回首页
                            </button>
                        </div>
                    )}

                {!loading
                    && !error
                    && artist
                    && (
                        <>
                            <section className="artist-hero">
                                <img
                                    src={
                                        artist.avatarUrl
                                        ?? DEFAULT_ARTIST_AVATAR
                                    }
                                    alt={
                                        artist.name
                                    }
                                    className="artist-hero-avatar"
                                />

                                <div className="artist-hero-content">
                                    <div className="artist-hero-title">
                                        <div>
                                            <h1>
                                                {artist.name}
                                            </h1>

                                            {artist.translatedName && (
                                                <p>
                                                    {artist.translatedName}
                                                </p>
                                            )}
                                        </div>
                                    </div>

                                    <div className="artist-hero-meta">
                                        {artist.countryRegion && (
                                            <span>
                                                {artist.countryRegion}
                                            </span>
                                        )}

                                        {artist.style && (
                                            <span>
                                                {artist.style}
                                            </span>
                                        )}
                                    </div>

                                    <div className="artist-hero-stats">
                                        <span>
                                            <strong>
                                                {formatCount(
                                                    artist.followerCount,
                                                )}
                                            </strong>

                                            关注
                                        </span>

                                        <span>
                                            <strong>
                                                {artist.songCount}
                                            </strong>

                                            首作品
                                        </span>

                                        <span>
                                            <strong>
                                                {artist.albumCount}
                                            </strong>

                                            张专辑
                                        </span>
                                    </div>

                                    <div className="artist-hero-actions">
                                        <button
                                            type="button"
                                            className="artist-play-all"
                                            disabled={
                                                playableSongs
                                                    .length
                                                === 0
                                            }
                                            onClick={
                                                playAll
                                            }
                                        >
                                            ▶ 播放全部
                                        </button>

                                        {artist.ownerUserId && (
                                            <button
                                                type="button"
                                                className="artist-profile-button"
                                                onClick={() =>
                                                    navigate(
                                                        `/users/${artist.ownerUserId}`,
                                                    )
                                                }
                                            >
                                                个人主页
                                            </button>
                                        )}
                                    </div>
                                </div>
                            </section>

                            <section className="artist-content-card">
                                <nav className="artist-tabs">
                                    <button
                                        type="button"
                                        className={
                                            tab
                                            === "SONGS"
                                                ? "active"
                                                : ""
                                        }
                                        onClick={() =>
                                            setTab(
                                                "SONGS",
                                            )
                                        }
                                    >
                                        热门作品
                                    </button>

                                    <button
                                        type="button"
                                        className={
                                            tab
                                            === "ALBUMS"
                                                ? "active"
                                                : ""
                                        }
                                        onClick={() =>
                                            setTab(
                                                "ALBUMS",
                                            )
                                        }
                                    >
                                        所有专辑
                                    </button>

                                    <button
                                        type="button"
                                        className={
                                            tab
                                            === "ABOUT"
                                                ? "active"
                                                : ""
                                        }
                                        onClick={() =>
                                            setTab(
                                                "ABOUT",
                                            )
                                        }
                                    >
                                        艺人介绍
                                    </button>
                                </nav>

                                {tab === "SONGS" && (
                                    <div className="artist-collection-list">
                                        <SongCollectionList
                                            songs={
                                                collectionSongs
                                            }
                                        />
                                    </div>
                                )}

                                {tab === "ALBUMS" && (
                                    <div className="artist-album-grid">
                                        {artist.albums.length
                                        === 0 ? (
                                            <div className="artist-empty">
                                                暂无已上架专辑
                                            </div>
                                        ) : (
                                            artist.albums.map(
                                                (album) => (
                                                    <article
                                                        key={
                                                            album.id
                                                        }
                                                        className="artist-album-card clickable"
                                                        role="link"
                                                        tabIndex={
                                                            0
                                                        }
                                                        onClick={() =>
                                                            navigate(
                                                                `/albums/${album.id}`,
                                                            )
                                                        }
                                                        onKeyDown={(
                                                            event,
                                                        ) => {
                                                            if (
                                                                event.key
                                                                === "Enter"
                                                                || event.key
                                                                === " "
                                                            ) {
                                                                navigate(
                                                                    `/albums/${album.id}`,
                                                                );
                                                            }
                                                        }}
                                                    >
                                                        <img
                                                            src={
                                                                album.coverUrl
                                                                ?? DEFAULT_ALBUM_COVER
                                                            }
                                                            alt={
                                                                album.name
                                                            }
                                                        />

                                                        <strong>
                                                            {album.name}
                                                        </strong>

                                                        <span>
                                                            {[
                                                                    album.releaseDate,
                                                                    album.style,
                                                                ]
                                                                    .filter(
                                                                        Boolean,
                                                                    )
                                                                    .join(
                                                                        " · ",
                                                                    )
                                                                || "专辑"}
                                                        </span>
                                                    </article>
                                                ),
                                            )
                                        )}
                                    </div>
                                )}

                                {tab === "ABOUT" && (
                                    <div className="artist-about">
                                        <h2>
                                            {artist.name}
                                            {" "}
                                            简介
                                        </h2>

                                        <p>
                                            {artist.introduction
                                                || "该音乐人暂未填写简介。"}
                                        </p>
                                    </div>
                                )}
                            </section>
                        </>
                    )}
            </main>
        </div>
    );
}
