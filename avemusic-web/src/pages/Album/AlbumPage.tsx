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
    getAlbumDetail,
    type AlbumDetail,
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

import "../../styles/Album/AlbumPage.css";

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

export default function AlbumPage() {
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

    const [album, setAlbum] =
        useState<
            AlbumDetail | null
        >(null);

    const [keyword, setKeyword] =
        useState("");

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState("");

    useEffect(() => {
        if (!id) {
            setError(
                "专辑ID不存在",
            );
            setLoading(false);
            return;
        }

        let cancelled = false;

        setLoading(true);
        setError("");

        void getAlbumDetail(id)
            .then((result) => {
                if (!cancelled) {
                    setAlbum(result);
                }
            })
            .catch((requestError) => {
                if (!cancelled) {
                    setAlbum(null);

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

    const collectionSongs =
        useMemo<CollectionSong[]>(
            () =>
                (album?.songs ?? [])
                    .map((song) => ({
                        id: song.id,
                        name: song.name,

                        artistName:
                        song.artistName,

                        artistIds:
                            song.artistIds
                            ?? [],

                        albumName:
                            song.albumName
                            ?? album?.name
                            ?? null,

                        coverUrl:
                        song.coverUrl,

                        audioUrl:
                        song.audioUrl,

                        durationSeconds:
                        song.durationSeconds,

                        playCount:
                        song.playCount,
                    })),
            [album],
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

    function renderAlbumArtists() {
        if (album === null) {
            return null;
        }

        const names =
            album.artistName
                .split(" / ")
                .map(
                    (item) =>
                        item.trim(),
                )
                .filter(Boolean);

        return names.map(
            (
                name,
                index,
            ) => {
                const artistId =
                    album.artistIds[
                        index
                        ];

                return (
                    <span
                        key={
                            artistId
                            ?? name
                        }
                        className="album-artist-name-group"
                    >
                        {index > 0 && (
                            <span>
                                {" / "}
                            </span>
                        )}

                        {artistId ? (
                            <button
                                type="button"
                                className="album-artist-link"
                                onClick={() =>
                                    navigate(
                                        `/artists/${artistId}`,
                                    )
                                }
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

    return (
        <div className="album-page-shell">
            <Header />

            <main className="album-page">
                {loading && (
                    <div className="album-page-state">
                        正在加载专辑...
                    </div>
                )}

                {!loading
                    && error
                    && (
                        <div className="album-page-state error">
                            <strong>
                                无法打开专辑
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
                    && album
                    && (
                        <>
                            <section className="album-hero">
                                <img
                                    src={
                                        album.coverUrl
                                        ?? DEFAULT_COVER
                                    }
                                    alt={
                                        album.name
                                    }
                                    className="album-hero-cover"
                                />

                                <div className="album-hero-info">
                                    <h1>
                                        {album.name}
                                    </h1>

                                    <div className="album-artist-row">
                                        {album.artistAvatarUrl ? (
                                            <img
                                                src={
                                                    album.artistAvatarUrl
                                                }
                                                alt=""
                                            />
                                        ) : (
                                            <span className="album-artist-avatar-fallback">
                                                {album.artistName
                                                    .slice(
                                                        0,
                                                        1,
                                                    )}
                                            </span>
                                        )}

                                        <div className="album-artist-names">
                                            {renderAlbumArtists()}
                                        </div>

                                        {album.releaseDate && (
                                            <span className="album-release-date">
                                                {album.releaseDate}
                                                发布
                                            </span>
                                        )}
                                    </div>

                                    <div className="album-meta-row">
                                        {album.style && (
                                            <span>
                                                {album.style}
                                            </span>
                                        )}

                                        <span>
                                            {album.songs.length}
                                            首歌曲
                                        </span>
                                    </div>

                                    <div className="album-actions">
                                        <button
                                            type="button"
                                            className="album-play-all"
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
                                                     * 当前专辑直接成为播放队列。
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
                                    </div>
                                </div>
                            </section>

                            <div className="album-content-layout">
                                <section className="album-song-section">
                                    <header className="album-song-header">
                                        <div className="album-song-tab">
                                            歌曲

                                            <sup>
                                                {album.songs.length}
                                            </sup>
                                        </div>

                                        <input
                                            value={keyword}
                                            placeholder="搜索专辑内歌曲"
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
                                    />
                                </section>

                                <aside className="album-introduction-panel">
                                    <h2>
                                        简介
                                    </h2>

                                    <div className="album-introduction-content">
                                        {album.introduction ? (
                                            <p>
                                                {album.introduction}
                                            </p>
                                        ) : (
                                            <span>
                                                暂无专辑简介
                                            </span>
                                        )}
                                    </div>
                                </aside>
                            </div>
                        </>
                    )}
            </main>
        </div>
    );
}
