import {
    useEffect,
    useMemo,
    useState,
} from "react";

import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";

import {
    addSongToPlaylist,
    getMyPlaylists,
    type PlaylistSummary,
} from "../../api/playlist";

import { getApiError } from "../../auth/api/http";

import "../../styles/components/CollectToPlaylistDialog.css";

export default function CollectToPlaylistDialog({
    open,
    songId,
    songName,
    onClose,
}: {
    open: boolean;
    songId: string;
    songName: string;
    onClose(): void;
}) {
    const navigate = useNavigate();

    const [playlists, setPlaylists] =
        useState<PlaylistSummary[]>([]);

    const [keyword, setKeyword] =
        useState("");

    const [
        selectedPlaylistId,
        setSelectedPlaylistId,
    ] = useState<string | null>(
        null,
    );

    const [loading, setLoading] =
        useState(false);

    const [submitting, setSubmitting] =
        useState(false);

    const [error, setError] =
        useState("");

    const [success, setSuccess] =
        useState("");

    useEffect(() => {
        if (!open) {
            return;
        }

        let cancelled = false;

        setKeyword("");
        setSelectedPlaylistId(null);
        setError("");
        setSuccess("");
        setLoading(true);

        void getMyPlaylists()
            .then((items) => {
                if (!cancelled) {
                    setPlaylists(items);
                }
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
    }, [open]);

    const visiblePlaylists =
        useMemo(() => {
            const normalized =
                keyword
                    .trim()
                    .toLowerCase();

            if (
                normalized.length === 0
            ) {
                return playlists;
            }

            return playlists.filter(
                (playlist) =>
                    playlist.name
                        .toLowerCase()
                        .includes(
                            normalized,
                        )
                    || (
                        playlist.introduction
                        ?.toLowerCase()
                        .includes(
                            normalized,
                        )
                        ?? false
                    ),
            );
        }, [
            keyword,
            playlists,
        ]);

    if (!open) {
        return null;
    }

    async function handleConfirm():
            Promise<void> {
        if (
            selectedPlaylistId
            === null
        ) {
            setError(
                "请选择一个歌单",
            );
            return;
        }

        setSubmitting(true);
        setError("");
        setSuccess("");

        try {
            await addSongToPlaylist(
                selectedPlaylistId,
                songId,
            );

            setSuccess(
                "收藏成功",
            );

            window.dispatchEvent(
                new CustomEvent(
                    "avemusic:playlist-updated",
                    {
                        detail: {
                            playlistId:
                                selectedPlaylistId,
                        },
                    },
                ),
            );

            window.setTimeout(
                () => {
                    onClose();
                },
                550,
            );
        } catch (requestError) {
            setError(
                getApiError(
                    requestError,
                ).message,
            );
        } finally {
            setSubmitting(false);
        }
    }

    return createPortal(
        <div className="collect-playlist-layer">
            <button
                type="button"
                className="collect-playlist-mask"
                aria-label="关闭收藏窗口"
                onClick={
                    submitting
                        ? undefined
                        : onClose
                }
            />

            <section
                className="collect-playlist-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="collect-playlist-title"
            >
                <header>
                    <div>
                        <h2 id="collect-playlist-title">
                            收藏到歌单
                        </h2>

                        <p title={songName}>
                            {songName}
                        </p>
                    </div>

                    <button
                        type="button"
                        disabled={submitting}
                        onClick={onClose}
                    >
                        ×
                    </button>
                </header>

                <div className="collect-playlist-body">
                    <input
                        className="collect-playlist-search"
                        value={keyword}
                        placeholder="搜索我的歌单"
                        disabled={submitting}
                        onChange={(event) =>
                            setKeyword(
                                event.target.value,
                            )
                        }
                    />

                    {loading ? (
                        <div className="collect-playlist-state">
                            正在加载歌单...
                        </div>
                    ) : playlists.length === 0 ? (
                        <div className="collect-playlist-empty">
                            <strong>
                                还没有创建歌单
                            </strong>

                            <span>
                                请先在“我的音乐”页面创建一个歌单。
                            </span>

                            <button
                                type="button"
                                onClick={() => {
                                    onClose();

                                    navigate(
                                        "/my-music",
                                    );
                                }}
                            >
                                去创建歌单
                            </button>
                        </div>
                    ) : visiblePlaylists.length === 0 ? (
                        <div className="collect-playlist-state">
                            没有匹配的歌单
                        </div>
                    ) : (
                        <div className="collect-playlist-list">
                            {visiblePlaylists.map(
                                (playlist) => (
                                    <label
                                        key={
                                            playlist.id
                                        }
                                        className={
                                            selectedPlaylistId
                                            === playlist.id
                                                ? "collect-playlist-item active"
                                                : "collect-playlist-item"
                                        }
                                    >
                                        <input
                                            type="radio"
                                            name="targetPlaylist"
                                            value={
                                                playlist.id
                                            }
                                            checked={
                                                selectedPlaylistId
                                                === playlist.id
                                            }
                                            disabled={
                                                submitting
                                            }
                                            onChange={() =>
                                                setSelectedPlaylistId(
                                                    playlist.id,
                                                )
                                            }
                                        />

                                        <span className="collect-playlist-cover">
                                            ♫
                                        </span>

                                        <span className="collect-playlist-info">
                                            <strong>
                                                {playlist.name}
                                            </strong>

                                            <small>
                                                {playlist.songCount}
                                                {" "}
                                                首 ·
                                                {" "}
                                                {playlist.visibility
                                                === "PUBLIC"
                                                    ? "公开"
                                                    : "私密"}
                                            </small>
                                        </span>
                                    </label>
                                ),
                            )}
                        </div>
                    )}

                    {error && (
                        <div className="collect-playlist-error">
                            {error}
                        </div>
                    )}

                    {success && (
                        <div className="collect-playlist-success">
                            {success}
                        </div>
                    )}
                </div>

                <footer>
                    <button
                        type="button"
                        className="secondary"
                        disabled={submitting}
                        onClick={onClose}
                    >
                        取消
                    </button>

                    <button
                        type="button"
                        className="primary"
                        disabled={
                            submitting
                            || selectedPlaylistId
                                === null
                            || playlists.length
                                === 0
                        }
                        onClick={() => {
                            void handleConfirm();
                        }}
                    >
                        {submitting
                            ? "正在收藏..."
                            : "确认收藏"}
                    </button>
                </footer>
            </section>
        </div>,
        document.body,
    );
}
