import {
    useEffect,
    useState,
    type FormEvent,
} from "react";

import { createPortal } from "react-dom";

import {
    replaceSongLyricsByFile,
    replaceSongLyricsBySource,
    type LyricsReplaceSource,
    type SongManagementItem,
} from "../../api/management";

import {
    getApiError,
} from "../../auth/api/http";

import "../../styles/Management/LyricsReplaceDialog.css";


type ReplaceMode =
    | "FILE"
    | "SOURCE";


interface LyricsReplaceDialogProps {
    open: boolean;

    song:
        SongManagementItem
        | null;

    onClose(): void;

    onReplaced():
        Promise<void>
        | void;
}


const MAX_LYRICS_FILE_SIZE =
    2 * 1024 * 1024;


function sourceTitle(
    source: LyricsReplaceSource,
): string {
    return source === "LRCLIB"
        ? "LRCLIB"
        : "网易云音乐";
}


export default function LyricsReplaceDialog({
    open,
    song,
    onClose,
    onReplaced,
}: LyricsReplaceDialogProps) {

    const [
        mode,
        setMode,
    ] = useState<ReplaceMode>(
        "FILE",
    );

    const [
        file,
        setFile,
    ] = useState<File | null>(
        null,
    );

    const [
        source,
        setSource,
    ] = useState<
        LyricsReplaceSource
    >(
        "LRCLIB",
    );

    const [
        submitting,
        setSubmitting,
    ] = useState(false);

    const [
        error,
        setError,
    ] = useState("");

    const [
        success,
        setSuccess,
    ] = useState("");


    useEffect(() => {
        if (!open) {
            return;
        }

        setMode("FILE");
        setFile(null);
        setSource("LRCLIB");
        setSubmitting(false);
        setError("");
        setSuccess("");

    }, [
        open,
        song?.id,
    ]);


    if (
        !open
        || song === null
    ) {
        return null;
    }


    function close(): void {
        if (submitting) {
            return;
        }

        onClose();
    }


    function chooseFile(
        selected:
            File
            | null,
    ): void {
        setError("");
        setSuccess("");

        if (selected === null) {
            setFile(null);
            return;
        }

        const lowerName =
            selected.name
                .toLowerCase();

        if (
            !lowerName.endsWith(
                ".lrc",
            )
            && !lowerName.endsWith(
                ".txt",
            )
        ) {
            setFile(null);

            setError(
                "仅支持 .lrc 或 .txt 歌词文件",
            );

            return;
        }

        if (
            selected.size
            > MAX_LYRICS_FILE_SIZE
        ) {
            setFile(null);

            setError(
                "歌词文件不能超过 2MB",
            );

            return;
        }

        setFile(selected);
    }


    async function submit(
        event:
        FormEvent<HTMLFormElement>,
    ): Promise<void> {

        event.preventDefault();

        if (song === null) {
            return;
        }

        setError("");
        setSuccess("");

        if (
            mode === "FILE"
            && file === null
        ) {
            setError(
                "请先选择需要上传的歌词文件",
            );

            return;
        }

        setSubmitting(true);

        try {
            if (mode === "FILE") {
                await replaceSongLyricsByFile(
                    song.id,
                    file!,
                );

                setSuccess(
                    `已使用人工歌词文件替换“${song.name}”的歌词`,
                );

                setFile(null);

            } else {
                await replaceSongLyricsBySource(
                    song.id,
                    source,
                );

                setSuccess(
                    `已从${sourceTitle(source)}重新匹配并替换歌词`,
                );
            }

            await onReplaced();

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
        <div
            className="lyrics-replace-layer"
            role="presentation"
        >
            <button
                type="button"
                className="lyrics-replace-mask"
                aria-label="关闭歌词管理窗口"
                disabled={submitting}
                onClick={close}
            />

            <section
                className="lyrics-replace-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="lyrics-replace-title"
            >
                <header className="lyrics-replace-header">
                    <div>
                        <span className="lyrics-replace-eyebrow">
                            歌词管理
                        </span>

                        <h2 id="lyrics-replace-title">
                            替换歌词
                        </h2>

                        <p>
                            {song.name}
                            <span>
                                {song.artistName}
                            </span>
                        </p>
                    </div>

                    <button
                        type="button"
                        className="lyrics-replace-close"
                        disabled={submitting}
                        onClick={close}
                    >
                        ×
                    </button>
                </header>


                <form
                    className="lyrics-replace-form"
                    onSubmit={(event) => {
                        void submit(event);
                    }}
                >
                    <div className="lyrics-replace-tabs">
                        <button
                            type="button"
                            className={
                                mode === "FILE"
                                    ? "active"
                                    : ""
                            }
                            disabled={submitting}
                            onClick={() => {
                                setMode(
                                    "FILE",
                                );

                                setError("");
                                setSuccess("");
                            }}
                        >
                            人工上传
                        </button>

                        <button
                            type="button"
                            className={
                                mode === "SOURCE"
                                    ? "active"
                                    : ""
                            }
                            disabled={submitting}
                            onClick={() => {
                                setMode(
                                    "SOURCE",
                                );

                                setError("");
                                setSuccess("");
                            }}
                        >
                            在线歌词源
                        </button>
                    </div>


                    {mode === "FILE" ? (
                        <section className="lyrics-replace-section">
                            <div className="lyrics-replace-section-title">
                                <strong>
                                    上传歌词文件
                                </strong>

                                <span>
                                    适用于已有校对版歌词或在线来源匹配错误的情况
                                </span>
                            </div>

                            <label className="lyrics-file-picker">
                                <input
                                    type="file"
                                    accept=".lrc,.txt,text/plain"
                                    disabled={submitting}
                                    onChange={(event) =>
                                        chooseFile(
                                            event.target
                                                .files?.[0]
                                            ?? null,
                                        )
                                    }
                                />

                                <span className="lyrics-file-icon">
                                    ↑
                                </span>

                                <strong>
                                    {file
                                        ? file.name
                                        : "选择歌词文件"}
                                </strong>

                                <small>
                                    支持 LRC 时间轴歌词和 TXT 纯文本歌词，最大 2MB
                                </small>
                            </label>

                            {file && (
                                <div className="lyrics-file-meta">
                                    <span>
                                        已选择
                                    </span>

                                    <strong>
                                        {file.name}
                                    </strong>

                                    <small>
                                        {Math.max(
                                            1,
                                            Math.round(
                                                file.size
                                                / 1024,
                                            ),
                                        )}
                                        KB
                                    </small>

                                    <button
                                        type="button"
                                        disabled={submitting}
                                        onClick={() =>
                                            setFile(
                                                null,
                                            )
                                        }
                                    >
                                        移除
                                    </button>
                                </div>
                            )}
                        </section>

                    ) : (
                        <section className="lyrics-replace-section">
                            <div className="lyrics-replace-section-title">
                                <strong>
                                    选择重新匹配的歌词源
                                </strong>

                                <span>
                                    只使用你指定的来源重新搜索，不自动切换到其他来源
                                </span>
                            </div>

                            <div className="lyrics-source-grid">
                                <label
                                    className={
                                        source === "LRCLIB"
                                            ? "lyrics-source-card active"
                                            : "lyrics-source-card"
                                    }
                                >
                                    <input
                                        type="radio"
                                        name="lyrics-source"
                                        value="LRCLIB"
                                        checked={
                                            source
                                            === "LRCLIB"
                                        }
                                        disabled={submitting}
                                        onChange={() =>
                                            setSource(
                                                "LRCLIB",
                                            )
                                        }
                                    />

                                    <div className="lyrics-source-badge">
                                        L
                                    </div>

                                    <div>
                                        <strong>
                                            LRCLIB
                                        </strong>

                                        <span>
                                            优先用于海外、日语及独立音乐，支持同步 LRC
                                        </span>
                                    </div>
                                </label>

                                <label
                                    className={
                                        source === "NETEASE"
                                            ? "lyrics-source-card active"
                                            : "lyrics-source-card"
                                    }
                                >
                                    <input
                                        type="radio"
                                        name="lyrics-source"
                                        value="NETEASE"
                                        checked={
                                            source
                                            === "NETEASE"
                                        }
                                        disabled={submitting}
                                        onChange={() =>
                                            setSource(
                                                "NETEASE",
                                            )
                                        }
                                    />

                                    <div className="lyrics-source-badge netease">
                                        N
                                    </div>

                                    <div>
                                        <strong>
                                            网易云音乐
                                        </strong>

                                        <span>
                                            通过 NeteaseCloudMusicApi 搜索并重新获取歌词
                                        </span>
                                    </div>
                                </label>
                            </div>
                        </section>
                    )}


                    <div className="lyrics-replace-warning">
                        <strong>
                            注意
                        </strong>

                        <span>
                            替换会覆盖当前歌曲歌词，并清空旧的 AI 翻译缓存；
                            下次打开歌词时会基于新歌词重新生成翻译。
                        </span>
                    </div>


                    {error && (
                        <div className="lyrics-replace-message error">
                            {error}
                        </div>
                    )}

                    {success && (
                        <div className="lyrics-replace-message success">
                            {success}
                        </div>
                    )}


                    <footer className="lyrics-replace-actions">
                        <button
                            type="button"
                            className="secondary"
                            disabled={submitting}
                            onClick={close}
                        >
                            关闭
                        </button>

                        <button
                            type="submit"
                            className="primary"
                            disabled={
                                submitting
                                || (
                                    mode === "FILE"
                                    && file === null
                                )
                            }
                        >
                            {submitting
                                ? mode === "FILE"
                                    ? "正在上传..."
                                    : "正在重新匹配..."
                                : mode === "FILE"
                                    ? "上传并替换"
                                    : "从该来源重新匹配"}
                        </button>
                    </footer>
                </form>
            </section>
        </div>,
        document.body,
    );
}
