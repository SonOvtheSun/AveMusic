import {
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    useNavigate,
    useSearchParams,
} from "react-router-dom";

import Header from "../../components/Header";

import {
    globalSearch,
    type SearchItem,
    type SearchResult,
    type SongCard,
} from "../../api/music";

import {
    getApiError,
} from "../../auth/api/http";

import {
    usePlayer,
} from "../../player/usePlayer";

import "../../styles/Search/SearchResultPage.css";

type SearchTab =
    | "ALL"
    | "SONG"
    | "ARTIST"
    | "ALBUM"
    | "PLAYLIST";

const EMPTY_RESULT: SearchResult = {
    keyword: "",
    expandedKeywords: [],
    songs: [],
    artists: [],
    albums: [],
    playlists: [],
};

const DEFAULT_COVER =
    "https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?auto=format&fit=crop&w=300&q=80";

const tabs: Array<{
    value: SearchTab;
    label: string;
}> = [
    {
        value: "ALL",
        label: "综合",
    },
    {
        value: "SONG",
        label: "单曲",
    },
    {
        value: "ARTIST",
        label: "歌手",
    },
    {
        value: "ALBUM",
        label: "专辑",
    },
    {
        value: "PLAYLIST",
        label: "歌单",
    },
];

function formatDuration(
    seconds: number,
): string {
    if (
        !Number.isFinite(seconds)
        || seconds <= 0
    ) {
        return "--:--";
    }

    const minute =
        Math.floor(seconds / 60);

    const second =
        Math.floor(seconds % 60);

    return `${minute}:${String(second).padStart(
        2,
        "0",
    )}`;
}

function formatCount(
    value: number,
): string {
    if (!Number.isFinite(value)) {
        return "0";
    }

    if (value >= 10_000) {
        return `${
            (value / 10_000)
                .toFixed(1)
                .replace(
                    /\.0$/,
                    "",
                )
        }万`;
    }

    return String(value);
}

export default function SearchResultPage() {

    const navigate =
        useNavigate();

    const [
        searchParams,
    ] = useSearchParams();

    const {
        playQueue,
    } = usePlayer();

    const keyword =
        (
            searchParams.get(
                "keyword",
            )
            ?? ""
        ).trim();

    const [
        tab,
        setTab,
    ] = useState<SearchTab>(
        "ALL",
    );

    const [
        result,
        setResult,
    ] = useState<SearchResult>(
        EMPTY_RESULT,
    );

    const [
        loading,
        setLoading,
    ] = useState(false);

    const [
        error,
        setError,
    ] = useState("");

    /*
     * 搜索词改变后重新请求。
     */
    useEffect(() => {

        if (!keyword) {
            setResult(
                EMPTY_RESULT,
            );

            setError("");

            return;
        }

        const controller =
            new AbortController();

        setLoading(true);
        setError("");

        void globalSearch(
            keyword,
            controller.signal,
        )
            .then((data) => {

                if (
                    controller
                        .signal
                        .aborted
                ) {
                    return;
                }

                setResult(data);
            })
            .catch(
                (requestError) => {

                    if (
                        controller
                            .signal
                            .aborted
                    ) {
                        return;
                    }

                    setResult(
                        EMPTY_RESULT,
                    );

                    setError(
                        getApiError(
                            requestError,
                        ).message,
                    );
                },
            )
            .finally(() => {

                if (
                    !controller
                        .signal
                        .aborted
                ) {
                    setLoading(false);
                }
            });

        return () => {
            controller.abort();
        };

    }, [
        keyword,
    ]);

    /*
     * 切换新的搜索词时，
     * 默认回到综合页。
     */
    useEffect(() => {
        setTab("ALL");
    }, [
        keyword,
    ]);

    const totalCount =
        result.songs.length
        + result.artists.length
        + result.albums.length
        + result.playlists.length;

    /*
     * 综合页使用第一个歌手作为重点展示。
     *
     * 后端已经按照相关性排序，
     * 所以第一个就是当前最推荐结果。
     */
    const primaryArtist =
        result.artists[0]
        ?? null;

    const categoryItems =
        useMemo(() => {

            switch (tab) {

                case "SONG":
                    return result.songs;

                case "ARTIST":
                    return result.artists;

                case "ALBUM":
                    return result.albums;

                case "PLAYLIST":
                    return result.playlists;

                default:
                    return [];
            }

        }, [
            tab,
            result,
        ]);

    function playSong(
        item: SearchItem,
    ): void {

        if (!item.audioUrl) {
            return;
        }

        /*
         * 搜索页点击歌曲时，
         * 当前搜索结果中的所有可播放歌曲
         * 组成播放队列。
         */
        const queue =
            result.songs
                .filter(
                    (song) =>
                        Boolean(
                            song.audioUrl,
                        ),
                )
                .map<SongCard>(
                    (song) => ({
                        id:
                        song.id,

                        name:
                        song.name,

                        artistName:
                            song.subtitle
                            ?? "未知音乐人",

                        artistIds:
                            song.artistIds
                            ?? [],

                        coverUrl:
                        song.coverUrl,

                        audioUrl:
                        song.audioUrl,

                        durationSeconds:
                        song.durationSeconds,

                        playCount:
                        song.popularity,
                    }),
                );

        const index =
            queue.findIndex(
                (song) =>
                    song.id === item.id,
            );

        if (index < 0) {
            return;
        }

        playQueue(
            queue,
            index,
        );
    }

    function openItem(
        item: SearchItem,
    ): void {

        switch (item.type) {

            case "SONG":
                playSong(item);
                return;

            case "ARTIST":
                navigate(
                    `/artists/${item.id}`,
                );
                return;

            case "ALBUM":
                navigate(
                    `/albums/${item.id}`,
                );
                return;

            case "PLAYLIST":
                navigate(
                    `/playlists/${item.id}`,
                );
                return;
        }
    }

    function renderSong(
        song: SearchItem,
    ) {
        return (
            <article
                key={song.id}
                className="search-song-row"
            >
                <button
                    type="button"
                    className="search-song-cover"
                    onClick={() =>
                        playSong(song)
                    }
                >
                    <img
                        src={
                            song.coverUrl
                            ?? DEFAULT_COVER
                        }
                        alt={
                            song.name
                        }
                    />

                    <span className="search-song-play">
                        ▶
                    </span>
                </button>

                <div className="search-song-info">

                    <button
                        type="button"
                        className="search-song-name"
                        onClick={() =>
                            playSong(song)
                        }
                    >
                        {song.name}
                    </button>

                    <div className="search-song-meta">
                        {
                            song.subtitle
                            ?? "未知音乐人"
                        }
                    </div>

                </div>

                <span className="search-song-duration">
                    {
                        formatDuration(
                            song.durationSeconds,
                        )
                    }
                </span>
            </article>
        );
    }

    function renderCard(
        item: SearchItem,
    ) {
        return (
            <article
                key={
                    `${item.type}-${item.id}`
                }
                className="search-media-card"
                role="button"
                tabIndex={0}
                onClick={() =>
                    openItem(item)
                }
                onKeyDown={(event) => {

                    if (
                        event.key
                        === "Enter"
                        || event.key
                        === " "
                    ) {
                        openItem(item);
                    }
                }}
            >
                <div className="search-media-cover">

                    <img
                        src={
                            item.coverUrl
                            ?? DEFAULT_COVER
                        }
                        alt={
                            item.name
                        }
                    />

                    {item.type === "PLAYLIST"
                        && item.popularity > 0
                        && (
                            <span className="search-card-count">
                                ♡{" "}
                                {
                                    formatCount(
                                        item.popularity,
                                    )
                                }
                            </span>
                        )}

                </div>

                <h3>
                    {item.name}
                </h3>

                <p>
                    {
                        item.subtitle
                        ?? (
                            item.type
                            === "ALBUM"
                                ? "专辑"
                                : "歌单"
                        )
                    }
                </p>
            </article>
        );
    }

    function renderArtistCard(
        artist: SearchItem,
    ) {
        return (
            <article
                key={artist.id}
                className="search-artist-card"
                onClick={() =>
                    navigate(
                        `/artists/${artist.id}`,
                    )
                }
            >
                <img
                    src={
                        artist.coverUrl
                        ?? DEFAULT_COVER
                    }
                    alt={
                        artist.name
                    }
                />

                <div>
                    <strong>
                        {artist.name}
                    </strong>

                    <span>
                        {
                            artist.subtitle
                            ?? "音乐人"
                        }
                    </span>

                    {artist.popularity > 0 && (
                        <small>
                            粉丝：
                            {
                                formatCount(
                                    artist.popularity,
                                )
                            }
                        </small>
                    )}
                </div>
            </article>
        );
    }

    return (
        <div className="search-page-shell">

            <Header />

            <main className="search-page">

                {/* ================= 标题 ================= */}

                <header className="search-heading">

                    <h1>
                        {keyword || "搜索"}
                    </h1>

                    {result.expandedKeywords
                            .length > 0
                        && (
                            <div className="search-ai-hint">

                                <span>
                                    智能匹配
                                </span>

                                {
                                    result
                                        .expandedKeywords
                                        .map(
                                            (item) => (
                                                <button
                                                    type="button"
                                                    key={
                                                        item
                                                    }
                                                    onClick={() =>
                                                        navigate(
                                                            `/search?keyword=${
                                                                encodeURIComponent(
                                                                    item,
                                                                )
                                                            }`,
                                                        )
                                                    }
                                                >
                                                    {item}
                                                </button>
                                            ),
                                        )
                                }

                            </div>
                        )}

                </header>

                {/* ================= 页签 ================= */}

                <nav className="search-tabs">

                    {tabs.map(
                        (item) => (

                            <button
                                key={
                                    item.value
                                }
                                type="button"
                                className={
                                    item.value
                                    === tab
                                        ? "active"
                                        : ""
                                }
                                onClick={() =>
                                    setTab(
                                        item.value,
                                    )
                                }
                            >
                                {item.label}

                                {item.value
                                    === "SONG"
                                    && result.songs.length
                                    > 0
                                    && (
                                        <small>
                                            {
                                                result
                                                    .songs
                                                    .length
                                            }
                                        </small>
                                    )}

                            </button>

                        ),
                    )}

                </nav>

                {/* ================= 状态 ================= */}

                {loading && (
                    <div className="search-loading">

                        <span className="search-loading-circle" />

                        <p>
                            正在进行智能搜索...
                        </p>

                    </div>
                )}

                {!loading
                    && error
                    && (
                        <div className="search-empty error">
                            {error}
                        </div>
                    )}

                {!loading
                    && !error
                    && keyword
                    && totalCount === 0
                    && (
                        <div className="search-empty">

                            <strong>
                                没有找到
                                “{keyword}”
                                相关内容
                            </strong>

                            <span>
                                可以尝试其他歌曲名、
                                音乐人名称或别名
                            </span>

                        </div>
                    )}

                {/* ================= 综合 ================= */}

                {!loading
                    && !error
                    && totalCount > 0
                    && tab === "ALL"
                    && (
                        <div className="search-overview">

                            {/* 最相关音乐人 */}

                            {primaryArtist && (

                                <section className="search-primary-artist">

                                    <img
                                        src={
                                            primaryArtist
                                                .coverUrl
                                            ?? DEFAULT_COVER
                                        }
                                        alt={
                                            primaryArtist
                                                .name
                                        }
                                        onClick={() =>
                                            openItem(
                                                primaryArtist,
                                            )
                                        }
                                    />

                                    <div className="search-primary-artist-info">

                                        <span>
                                            歌手
                                        </span>

                                        <button
                                            type="button"
                                            onClick={() =>
                                                openItem(
                                                    primaryArtist,
                                                )
                                            }
                                        >
                                            {
                                                primaryArtist
                                                    .name
                                            }
                                        </button>

                                        <p>
                                            {
                                                primaryArtist
                                                    .subtitle
                                                ?? "音乐人"
                                            }
                                        </p>

                                        {primaryArtist
                                                .popularity
                                            > 0
                                            && (
                                                <small>
                                                    粉丝{" "}
                                                    {
                                                        formatCount(
                                                            primaryArtist
                                                                .popularity,
                                                        )
                                                    }
                                                </small>
                                            )}

                                    </div>

                                </section>
                            )}

                            {/* 单曲 */}

                            {result.songs.length
                                > 0
                                && (
                                    <section className="search-section">

                                        <div className="search-section-heading">

                                            <button
                                                type="button"
                                                onClick={() =>
                                                    setTab(
                                                        "SONG",
                                                    )
                                                }
                                            >
                                                单曲
                                                <span>
                                                    ›
                                                </span>
                                            </button>

                                            <button
                                                type="button"
                                                className="search-play-all"
                                                onClick={() => {

                                                    const first =
                                                        result
                                                            .songs[0];

                                                    if (first) {
                                                        playSong(
                                                            first,
                                                        );
                                                    }
                                                }}
                                            >
                                                ▶ 播放
                                            </button>

                                        </div>

                                        <div className="search-song-grid">

                                            {
                                                result
                                                    .songs
                                                    .slice(
                                                        0,
                                                        6,
                                                    )
                                                    .map(
                                                        renderSong,
                                                    )
                                            }

                                        </div>

                                    </section>
                                )}

                            {/* 专辑 */}

                            {result.albums.length
                                > 0
                                && (
                                    <section className="search-section">

                                        <div className="search-section-heading">

                                            <button
                                                type="button"
                                                onClick={() =>
                                                    setTab(
                                                        "ALBUM",
                                                    )
                                                }
                                            >
                                                专辑
                                                <span>
                                                    ›
                                                </span>
                                            </button>

                                        </div>

                                        <div className="search-card-grid">

                                            {
                                                result
                                                    .albums
                                                    .slice(
                                                        0,
                                                        6,
                                                    )
                                                    .map(
                                                        renderCard,
                                                    )
                                            }

                                        </div>

                                    </section>
                                )}

                            {/* 歌单 */}

                            {result.playlists.length
                                > 0
                                && (
                                    <section className="search-section">

                                        <div className="search-section-heading">

                                            <button
                                                type="button"
                                                onClick={() =>
                                                    setTab(
                                                        "PLAYLIST",
                                                    )
                                                }
                                            >
                                                歌单
                                                <span>
                                                    ›
                                                </span>
                                            </button>

                                        </div>

                                        <div className="search-card-grid">

                                            {
                                                result
                                                    .playlists
                                                    .slice(
                                                        0,
                                                        6,
                                                    )
                                                    .map(
                                                        renderCard,
                                                    )
                                            }

                                        </div>

                                    </section>
                                )}

                            {/* 其余歌手 */}

                            {result.artists.length
                                > 1
                                && (
                                    <section className="search-section">

                                        <div className="search-section-heading">

                                            <button
                                                type="button"
                                                onClick={() =>
                                                    setTab(
                                                        "ARTIST",
                                                    )
                                                }
                                            >
                                                更多歌手
                                                <span>
                                                    ›
                                                </span>
                                            </button>

                                        </div>

                                        <div className="search-artist-grid">

                                            {
                                                result
                                                    .artists
                                                    .slice(
                                                        1,
                                                        5,
                                                    )
                                                    .map(
                                                        renderArtistCard,
                                                    )
                                            }

                                        </div>

                                    </section>
                                )}

                        </div>
                    )}

                {/* ================= 单曲分类 ================= */}

                {!loading
                    && !error
                    && tab === "SONG"
                    && (
                        <section className="search-category-page">

                            <h2>
                                单曲
                            </h2>

                            <div className="search-song-grid full">

                                {
                                    categoryItems
                                        .map(
                                            renderSong,
                                        )
                                }

                            </div>

                        </section>
                    )}

                {/* ================= 歌手分类 ================= */}

                {!loading
                    && !error
                    && tab === "ARTIST"
                    && (
                        <section className="search-category-page">

                            <h2>
                                歌手
                            </h2>

                            <div className="search-artist-grid">

                                {
                                    categoryItems
                                        .map(
                                            renderArtistCard,
                                        )
                                }

                            </div>

                        </section>
                    )}

                {/* ================= 专辑 / 歌单 ================= */}

                {!loading
                    && !error
                    && (
                        tab === "ALBUM"
                        || tab === "PLAYLIST"
                    )
                    && (
                        <section className="search-category-page">

                            <h2>
                                {
                                    tab
                                    === "ALBUM"
                                        ? "专辑"
                                        : "歌单"
                                }
                            </h2>

                            <div className="search-card-grid large">

                                {
                                    categoryItems
                                        .map(
                                            renderCard,
                                        )
                                }

                            </div>

                        </section>
                    )}

            </main>

        </div>
    );
}