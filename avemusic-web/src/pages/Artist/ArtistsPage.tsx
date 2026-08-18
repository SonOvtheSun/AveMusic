import {
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    useNavigate,
} from "react-router-dom";

import Header
    from "../../components/Header";

import {
    getArtistDirectory,
    type ArtistArea,
    type ArtistCategory,
    type ArtistDirectoryItem,
    type ArtistDirectoryResult,
    type ArtistInitial,
} from "../../api/music";

import {
    getApiError,
} from "../../auth/api/http";

import "../../styles/Artist/ArtistsPage.css";

const areaOptions: Array<{
    label: string;
    value: ArtistArea;
}> = [
    {
        label: "全部",
        value: "ALL",
    },
    {
        label: "华语",
        value: "CN",
    },
    {
        label: "欧美",
        value: "EU_US",
    },
    {
        label: "日本",
        value: "JP",
    },
    {
        label: "韩国",
        value: "KR",
    },
    {
        label: "其他",
        value: "OTHER",
    },
];

const categoryOptions: Array<{
    label: string;
    value: ArtistCategory;
}> = [
    {
        label: "全部",
        value: "ALL",
    },
    {
        label: "男歌手",
        value: "MALE",
    },
    {
        label: "女歌手",
        value: "FEMALE",
    },
    {
        label: "乐队组合",
        value: "BAND",
    },
];

const initialOptions: Array<{
    label: string;
    value: ArtistInitial;
}> = [
    {
        label: "热门",
        value: "HOT",
    },
    ..."ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        .split("")
        .map(
            (letter) => ({
                label: letter,
                value:
                    letter as ArtistInitial,
            }),
        ),
    {
        label: "#",
        value: "#",
    },
];

const EMPTY_RESULT: ArtistDirectoryResult =
    {
        records: [],
        total: 0,
        page: 1,
        pageSize: 10,
    };

export default function ArtistsPage() {

    const navigate =
        useNavigate();

    const [
        area,
        setArea,
    ] = useState<ArtistArea>(
        "ALL",
    );

    const [
        category,
        setCategory,
    ] = useState<ArtistCategory>(
        "ALL",
    );

    const [
        initial,
        setInitial,
    ] = useState<ArtistInitial>(
        "HOT",
    );

    const [
        page,
        setPage,
    ] = useState(1);

    const pageSize = 10;

    const [
        loading,
        setLoading,
    ] = useState(false);

    const [
        error,
        setError,
    ] = useState("");

    const [
        result,
        setResult,
    ] = useState<ArtistDirectoryResult>(
        EMPTY_RESULT,
    );

    useEffect(() => {

        let cancelled = false;

        setLoading(true);
        setError("");

        void getArtistDirectory({
            area,
            category,
            initial,
            page,
            pageSize,
        })
            .then((data) => {

                if (cancelled) {
                    return;
                }

                setResult(data);
            })
            .catch(
                (requestError) => {

                    if (cancelled) {
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

                if (!cancelled) {
                    setLoading(false);
                }
            });

        return () => {
            cancelled = true;
        };

    }, [
        area,
        category,
        initial,
        page,
    ]);

    const totalPages =
        useMemo(() => {

            if (
                result.pageSize <= 0
            ) {
                return 1;
            }

            return Math.max(
                1,
                Math.ceil(
                    result.total
                    / result.pageSize,
                ),
            );

        }, [
            result.total,
            result.pageSize,
        ]);

    function changeArea(
        next: ArtistArea,
    ): void {
        setArea(next);
        setPage(1);
    }

    function changeCategory(
        next: ArtistCategory,
    ): void {
        setCategory(next);
        setPage(1);
    }

    function changeInitial(
        next: ArtistInitial,
    ): void {
        setInitial(next);
        setPage(1);
    }

    function openArtist(
        artist: ArtistDirectoryItem,
    ): void {
        navigate(
            `/artists/${artist.id}`,
        );
    }

    function renderFilterRow<T extends string>(
        options: Array<{
            label: string;
            value: T;
        }>,
        current: T,
        onChange: (value: T) => void,
    ) {
        return (
            <div className="artists-filter-row">
                {options.map((item) => (
                    <button
                        key={item.value}
                        type="button"
                        className={
                            item.value
                            === current
                                ? "artists-filter-chip active"
                                : "artists-filter-chip"
                        }
                        onClick={() =>
                            onChange(
                                item.value,
                            )
                        }
                    >
                        {item.label}
                    </button>
                ))}
            </div>
        );
    }

    return (
        <div className="artists-page-shell">

            <Header />

            <main className="artists-page">

                <section className="artists-filters">

                    {renderFilterRow(
                        areaOptions,
                        area,
                        changeArea,
                    )}

                    {renderFilterRow(
                        categoryOptions,
                        category,
                        changeCategory,
                    )}

                    <div className="artists-initial-row">

                        {initialOptions.map(
                            (item) => (
                                <button
                                    key={
                                        item.value
                                    }
                                    type="button"
                                    className={
                                        item.value
                                        === initial
                                            ? "artists-initial-link active"
                                            : "artists-initial-link"
                                    }
                                    onClick={() =>
                                        changeInitial(
                                            item.value,
                                        )
                                    }
                                >
                                    {
                                        item.label
                                    }
                                </button>
                            ),
                        )}

                    </div>

                </section>

                {loading && (
                    <div className="artists-state">
                        正在加载歌手...
                    </div>
                )}

                {!loading
                    && error
                    && (
                        <div className="artists-state error">
                            {error}
                        </div>
                    )}

                {!loading
                    && !error
                    && result.records
                        .length === 0
                    && (
                        <div className="artists-state">
                            当前条件下暂无歌手
                        </div>
                    )}

                {!loading
                    && !error
                    && result.records
                        .length > 0
                    && (
                        <>
                            <section className="artists-grid">

                                {result.records.map(
                                    (artist) => (

                                        <article
                                            key={
                                                artist.id
                                            }
                                            className="artist-card"
                                            onClick={() =>
                                                openArtist(
                                                    artist,
                                                )
                                            }
                                        >
                                            <div className="artist-card-avatar">
                                                <img
                                                    src={
                                                        artist.avatarUrl
                                                        ?? "/default-avatar.png"
                                                    }
                                                    alt={
                                                        artist.name
                                                    }
                                                />
                                            </div>

                                            <div className="artist-card-name">
                                                {
                                                    artist.name
                                                }
                                            </div>

                                            <div className="artist-card-meta">
                                                单曲：
                                                {" "}
                                                {
                                                    artist.songCount
                                                }
                                            </div>
                                        </article>

                                    ),
                                )}

                            </section>

                            <div className="artists-pagination">

                                <button
                                    type="button"
                                    disabled={
                                        page <= 1
                                    }
                                    onClick={() =>
                                        setPage(
                                            (
                                                current,
                                            ) =>
                                                Math.max(
                                                    1,
                                                    current - 1,
                                                ),
                                        )
                                    }
                                >
                                    上一页
                                </button>

                                <span>
                                    第
                                    {" "}
                                    {page}
                                    {" "}
                                    /
                                    {" "}
                                    {totalPages}
                                    {" "}
                                    页
                                </span>

                                <button
                                    type="button"
                                    disabled={
                                        page
                                        >= totalPages
                                    }
                                    onClick={() =>
                                        setPage(
                                            (
                                                current,
                                            ) =>
                                                Math.min(
                                                    totalPages,
                                                    current + 1,
                                                ),
                                        )
                                    }
                                >
                                    下一页
                                </button>

                            </div>
                        </>
                    )}

            </main>

        </div>
    );
}