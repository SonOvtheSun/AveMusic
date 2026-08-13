import {
    useEffect,
    useMemo,
    useState,
    type CSSProperties,
} from "react";

import { useNavigate } from "react-router-dom";

import {
    getHomeArtists,
    getHomeSongs,
    type ArtistCard,
    type SongCard,
} from "../../api/music";

import { useAuth } from "../../context/useAuth";

import {
    getApiError,
} from "../../auth/api/http";

import Header from "../../components/Header";
import PlaybackIcon from "../../components/player/PlaybackIcon";
import { usePlayer } from "../../player/usePlayer";
import "../../styles/Home/HomePage.css";

type Banner = {
    id: number;
    title: string;
    subtitle: string;
    image: string;
    accent: string;
};

type BannerStyle =
    CSSProperties & {
    "--banner-accent": string;
};

const banners: Banner[] = [
    {
        id: 1,
        title: "今夜循环播放",
        subtitle:
            "精选流行与独立旋律，给你刚刚好的主页氛围。",
        image:
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=80",
        accent: "#b13cff",
    },
    {
        id: 2,
        title: "夏日推荐歌单",
        subtitle:
            "轻盈、浪漫、适合通勤和晚风的声音都在这里。",
        image:
            "https://images.unsplash.com/photo-1511379938547-c1f69419868d?auto=format&fit=crop&w=1200&q=80",
        accent: "#ff5f6d",
    },
    {
        id: 3,
        title: "发现新的音乐人",
        subtitle:
            "从热门新声到独立创作者，找到你的下一首单曲循环。",
        image:
            "https://images.unsplash.com/photo-1501612780327-45045538702b?auto=format&fit=crop&w=1200&q=80",
        accent: "#20c997",
    },
];

const DEFAULT_SONG_COVER =
    "https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?auto=format&fit=crop&w=500&q=80";

const DEFAULT_ARTIST_AVATAR =
    "https://images.unsplash.com/photo-1511367461989-f85a21fda167?auto=format&fit=crop&w=500&q=80";

function formatCount(value: number): string {
    if (value >= 10000) {
        return `${(value / 10000).toFixed(1)}万`;
    }

    return String(value);
}

function artistSubtitle(
    artist: ArtistCard,
): string {
    const values =
        (artist.translatedNames ?? [])
            .map((value) =>
                value.trim(),
            )
            .filter(
                (value) =>
                    value.length > 0,
            );

    return values.length === 0
        ? "音乐人"
        : values.join(" / ");
}

export default function HomePage() {
    const navigate = useNavigate();

    const {
        user,
        loading: authLoading,
        logout,
    } = useAuth();

    const {
        playQueue,
        currentSong,
        isPlaying,
        togglePlay,
    } = usePlayer();

    const [bannerIndex, setBannerIndex] =
        useState(0);

    const [songs, setSongs] =
        useState<SongCard[]>([]);

    const [artists, setArtists] =
        useState<ArtistCard[]>([]);

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState("");

    const currentBanner = useMemo(
        () => banners[bannerIndex],
        [bannerIndex],
    );

    useEffect(() => {
        const timer = window.setInterval(
            () => {
                setBannerIndex(
                    (current) =>
                        (current + 1)
                        % banners.length,
                );
            },
            4500,
        );

        return () => {
            window.clearInterval(timer);
        };
    }, []);

    useEffect(() => {
        async function loadHomeData() {
            setLoading(true);
            setError("");

            try {
                const [
                    songData,
                    artistData,
                ] = await Promise.all([
                    getHomeSongs(8),
                    getHomeArtists(6),
                ]);

                setSongs(songData);
                setArtists(artistData);
            } catch (requestError) {
                setError(
                    getApiError(
                        requestError,
                    ).message,
                );
            } finally {
                setLoading(false);
            }
        }

        void loadHomeData();
    }, []);

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

            setSongs((current) =>
                current.map((song) =>
                    song.id === detail.songId
                        ? {
                            ...song,
                            playCount:
                            detail.playCount,
                        }
                        : song,
                ),
            );
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

    function switchBanner(step: number) {
        setBannerIndex((current) => {
            const next = current + step;

            if (next < 0) {
                return banners.length - 1;
            }

            if (next >= banners.length) {
                return 0;
            }

            return next;
        });
    }

    async function handleLogout() {
        await logout();
        navigate("/");
    }

    function playHomeSong(
        song: SongCard,
    ): void {
        if (!song.audioUrl) {
            return;
        }

        /*
         * 首页当前歌曲再次点击时直接切换暂停/继续，
         * 不从头重新播放。
         */
        if (
            currentSong?.id
            === song.id
        ) {
            togglePlay();
            return;
        }

        const index =
            songs.findIndex(
                (item) =>
                    item.id === song.id,
            );

        if (index >= 0) {
            playQueue(
                songs,
                index,
            );
        }
    }

    function renderArtistLinks(
        song: SongCard,
    ) {
        const names =
            song.artistName
                .split(" / ")
                .map((name) =>
                    name.trim(),
                )
                .filter(Boolean);

        if (
            names.length === 0
            || song.artistIds.length === 0
        ) {
            return song.artistName;
        }

        return names.map(
            (name, index) => {
                const artistId =
                    song.artistIds[index];

                return (
                    <span
                        key={
                            artistId
                            ?? `${song.id}-${name}`
                        }
                        className="song-artist-link-group"
                    >
                        {index > 0 && (
                            <span
                                className="song-artist-separator"
                                aria-hidden="true"
                            >
                                {" / "}
                            </span>
                        )}

                        {artistId ? (
                            <button
                                type="button"
                                className="song-artist-link"
                                onClick={() =>
                                    navigate(
                                        `/artists/${artistId}`,
                                    )
                                }
                            >
                                {name}
                            </button>
                        ) : (
                            <span>{name}</span>
                        )}
                    </span>
                );
            },
        );
    }

    const firstPlayableIndex =
        songs.findIndex(
            (song) =>
                Boolean(song.audioUrl),
        );

    const bannerStyle: BannerStyle = {
        "--banner-accent":
        currentBanner.accent,

        backgroundImage:
            `linear-gradient(
                90deg,
                rgba(18, 18, 18, 0.75),
                rgba(18, 18, 18, 0.22)
            ),
            url(${currentBanner.image})`,
    };

    return (
        <div className="home-page">
            <Header />

            <main className="home-main">
                <section
                    className="home-banner"
                    style={bannerStyle}
                >
                    <button
                        type="button"
                        className="banner-arrow left"
                        onClick={() =>
                            switchBanner(-1)
                        }
                    >
                        ‹
                    </button>

                    <div className="banner-content">
                        <span className="banner-badge">
                            官方推荐
                        </span>

                        <h1>
                            {currentBanner.title}
                        </h1>

                        <p>
                            {currentBanner.subtitle}
                        </p>

                        <div className="banner-actions">
                            <button
                                type="button"
                                className="banner-primary"
                                disabled={
                                    firstPlayableIndex < 0
                                }
                                onClick={() => {
                                    if (
                                        firstPlayableIndex
                                        >= 0
                                    ) {
                                        playHomeSong(
                                            songs[
                                                firstPlayableIndex
                                                ],
                                        );
                                    }
                                }}
                            >
                                立即播放
                            </button>

                            <button
                                type="button"
                                className="banner-secondary"
                            >
                                查看详情
                            </button>
                        </div>

                        <div className="banner-dots">
                            {banners.map(
                                (banner, index) => (
                                    <button
                                        key={banner.id}
                                        type="button"
                                        className={
                                            index
                                            === bannerIndex
                                                ? "banner-dot active"
                                                : "banner-dot"
                                        }
                                        onClick={() =>
                                            setBannerIndex(
                                                index,
                                            )
                                        }
                                    />
                                ),
                            )}
                        </div>
                    </div>

                    <button
                        type="button"
                        className="banner-arrow right"
                        onClick={() =>
                            switchBanner(1)
                        }
                    >
                        ›
                    </button>
                </section>

                <div className="home-content">
                    <div className="home-primary">
                        {loading && (
                            <div className="home-state">
                                正在加载首页音乐...
                            </div>
                        )}

                        {!loading && error && (
                            <div className="home-state error">
                                {error}
                            </div>
                        )}

                        {!loading && !error && (
                            <>
                                <section className="home-section">
                                    <div className="section-header">
                                        <div className="section-heading">
                                            <span className="section-dot" />
                                            <h2>热门歌曲</h2>
                                        </div>

                                        <button
                                            type="button"
                                            className="section-more"
                                        >
                                            更多 &gt;
                                        </button>
                                    </div>

                                    <div className="song-grid">
                                        {songs.map(
                                            (song) => {
                                                const active =
                                                    currentSong
                                                        ?.id
                                                    === song.id;

                                                return (
                                                    <article
                                                        key={song.id}
                                                        className={
                                                            active
                                                                ? "song-card playing"
                                                                : "song-card"
                                                        }
                                                    >
                                                        <div className="song-cover-box">
                                                            <img
                                                                src={
                                                                    song.coverUrl
                                                                    ?? DEFAULT_SONG_COVER
                                                                }
                                                                alt={song.name}
                                                                className="song-cover"
                                                            />

                                                            <button
                                                                type="button"
                                                                className="song-cover-mask"
                                                                disabled={
                                                                    !song.audioUrl
                                                                }
                                                                aria-label={
                                                                    !song.audioUrl
                                                                        ? "暂无音频"
                                                                        : active
                                                                        && isPlaying
                                                                            ? "暂停"
                                                                            : "播放"
                                                                }
                                                                title={
                                                                    !song.audioUrl
                                                                        ? "暂无音频"
                                                                        : active
                                                                        && isPlaying
                                                                            ? "暂停"
                                                                            : "播放"
                                                                }
                                                                onClick={() =>
                                                                    playHomeSong(
                                                                        song,
                                                                    )
                                                                }
                                                            >
                                                                {!song.audioUrl ? (
                                                                    <span>
                                                                        暂无音频
                                                                    </span>
                                                                ) : (
                                                                    <>
                                                                        <PlaybackIcon
                                                                            type={
                                                                                active
                                                                                && isPlaying
                                                                                    ? "pause"
                                                                                    : "play"
                                                                            }
                                                                            size={16}
                                                                        />

                                                                        <span>
                                                                            {active
                                                                            && isPlaying
                                                                                ? "暂停"
                                                                                : "播放"}
                                                                        </span>
                                                                    </>
                                                                )}
                                                            </button>
                                                        </div>

                                                        <h3 className="song-title">
                                                            <button
                                                                type="button"
                                                                className="song-title-link"
                                                                disabled={
                                                                    !song.audioUrl
                                                                }
                                                                onClick={() =>
                                                                    playHomeSong(
                                                                        song,
                                                                    )
                                                                }
                                                            >
                                                                {song.name}
                                                            </button>
                                                        </h3>

                                                        <div className="song-artist">
                                                            {renderArtistLinks(
                                                                song,
                                                            )}
                                                        </div>

                                                        <p className="song-count">
                                                            播放{" "}
                                                            {formatCount(
                                                                song.playCount,
                                                            )}
                                                        </p>
                                                    </article>
                                                );
                                            },
                                        )}
                                    </div>
                                </section>

                                <section className="home-section">
                                    <div className="section-header">
                                        <div className="section-heading">
                                            <span className="section-dot" />
                                            <h2>推荐音乐人</h2>
                                        </div>

                                        <button
                                            type="button"
                                            className="section-more"
                                        >
                                            更多 &gt;
                                        </button>
                                    </div>

                                    <div className="artist-grid">
                                        {artists.map((artist) => (
                                            <article
                                                key={artist.id}
                                                className="artist-card clickable"
                                                role="link"
                                                tabIndex={0}
                                                onClick={() =>
                                                    navigate(
                                                        `/artists/${artist.id}`,
                                                    )
                                                }
                                                onKeyDown={(event) => {
                                                    if (
                                                        event.key === "Enter"
                                                        || event.key === " "
                                                    ) {
                                                        navigate(
                                                            `/artists/${artist.id}`,
                                                        );
                                                    }
                                                }}
                                            >
                                                <img
                                                    src={
                                                        artist.avatarUrl
                                                        ?? DEFAULT_ARTIST_AVATAR
                                                    }
                                                    alt={artist.name}
                                                    className="artist-avatar"
                                                />

                                                <div>
                                                    <h3 className="artist-name">
                                                        {artist.name}
                                                    </h3>

                                                    <p className="artist-tag">
                                                        {artistSubtitle(
                                                            artist,
                                                        )}
                                                    </p>

                                                    <p className="artist-followers">
                                                        {formatCount(
                                                            artist.followerCount,
                                                        )}{" "}
                                                        人关注
                                                    </p>
                                                </div>
                                            </article>
                                        ))}
                                    </div>
                                </section>
                            </>
                        )}
                    </div>

                    <aside className="home-sidebar">
                        {authLoading && (
                            <section className="login-card">
                                <div className="user-card-loading">
                                    正在读取登录状态...
                                </div>
                            </section>
                        )}

                        {!authLoading && user === null && (
                            <section className="login-card">
                                <div className="login-card-cover" />

                                <div className="login-card-body">
                                    <h3>登录 AveMusic</h3>

                                    <p>
                                        登录后同步收藏、歌单和播放记录，
                                        发现更适合你的音乐。
                                    </p>

                                    <button
                                        type="button"
                                        className="login-card-button"
                                        onClick={() =>
                                            navigate("/auth")
                                        }
                                    >
                                        用户登录
                                    </button>
                                </div>
                            </section>
                        )}

                        {!authLoading && user !== null && (
                            <section className="user-card">
                                <div className="user-card-profile">
                                    {user.avatarUrl ? (
                                        <img
                                            src={user.avatarUrl}
                                            alt={user.username}
                                            className="user-card-avatar"
                                        />
                                    ) : (
                                        <div className="user-card-avatar fallback">
                                            {user.username
                                                .slice(0, 1)
                                                .toUpperCase()}
                                        </div>
                                    )}

                                    <div className="user-card-name">
                                        <h3>
                                            {user.username}
                                        </h3>

                                        <span>
                                            已登录
                                        </span>
                                    </div>
                                </div>

                                <div className="user-card-details">
                                    <span>用户 ID</span>
                                    <strong>
                                        {user.userId}
                                    </strong>
                                </div>

                                <div className="user-card-actions">
                                    <button
                                        type="button"
                                        onClick={() =>
                                            navigate(
                                                "/my-music",
                                            )
                                        }
                                    >
                                        我的音乐
                                    </button>

                                    <button
                                        type="button"
                                        className="danger"
                                        onClick={() => {
                                            void handleLogout();
                                        }}
                                    >
                                        退出登录
                                    </button>
                                </div>
                            </section>
                        )}

                        <section className="side-panel">
                            <h3 className="side-panel-title">
                                今日推荐
                            </h3>

                            <ul className="side-song-list">
                                {songs
                                    .slice(0, 5)
                                    .map((song) => (
                                        <li
                                            key={song.id}
                                            className="side-song-item"
                                        >
                                            <button
                                                type="button"
                                                className="side-song-title-link"
                                                disabled={
                                                    !song.audioUrl
                                                }
                                                onClick={() =>
                                                    playHomeSong(
                                                        song,
                                                    )
                                                }
                                            >
                                                {song.name}
                                            </button>

                                            <small className="side-song-artist-links">
                                                {renderArtistLinks(
                                                    song,
                                                )}
                                            </small>
                                        </li>
                                    ))}
                            </ul>
                        </section>

                        <section className="side-panel">
                            <h3 className="side-panel-title">
                                热门歌手
                            </h3>

                            <ul className="side-artist-list">
                                {artists
                                    .slice(0, 4)
                                    .map((artist) => (
                                        <li
                                            key={artist.id}
                                            className="side-artist-item clickable"
                                            role="link"
                                            tabIndex={0}
                                            onClick={() =>
                                                navigate(
                                                    `/artists/${artist.id}`,
                                                )
                                            }
                                            onKeyDown={(event) => {
                                                if (
                                                    event.key === "Enter"
                                                    || event.key === " "
                                                ) {
                                                    navigate(
                                                        `/artists/${artist.id}`,
                                                    );
                                                }
                                            }}
                                        >
                                            <img
                                                src={
                                                    artist.avatarUrl
                                                    ?? DEFAULT_ARTIST_AVATAR
                                                }
                                                alt={artist.name}
                                            />

                                            <div>
                                                <span>
                                                    {artist.name}
                                                </span>

                                                <small>
                                                    {artistSubtitle(
                                                        artist,
                                                    )}
                                                </small>
                                            </div>
                                        </li>
                                    ))}
                            </ul>
                        </section>
                    </aside>
                </div>
            </main>
        </div>
    );
}
