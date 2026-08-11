import {
    useEffect,
    useState,
    type ChangeEvent,
    type FormEvent,
} from "react";

import { createPortal } from "react-dom";

import {
    createPlaylist,
    updatePlaylist,
    type PlaylistSummary,
    type PlaylistVisibility,
} from "../../api/playlist";

import { uploadFile } from "../../api/file";
import { getApiError } from "../../auth/api/http";

import SquareImageCropDialog from "../common/SquareImageCropDialog";

import "../../styles/components/PlaylistFormDialog.css";

export default function PlaylistFormDialog({
    open,
    mode,
    playlist = null,
    onClose,
    onSaved,
}: {
    open: boolean;
    mode: "create" | "edit";
    playlist?: PlaylistSummary | null;
    onClose(): void;
    onSaved(
        playlist: PlaylistSummary,
    ): void;
}) {
    const [name, setName] =
        useState("");

    const [
        introduction,
        setIntroduction,
    ] = useState("");

    const [
        visibility,
        setVisibility,
    ] = useState<
        PlaylistVisibility
    >("PRIVATE");

    const [
        cropSourceFile,
        setCropSourceFile,
    ] = useState<File | null>(
        null,
    );

    const [
        croppedCoverFile,
        setCroppedCoverFile,
    ] = useState<File | null>(
        null,
    );

    const [
        localPreviewUrl,
        setLocalPreviewUrl,
    ] = useState<string | null>(
        null,
    );

    const [submitting, setSubmitting] =
        useState(false);

    const [error, setError] =
        useState("");

    useEffect(() => {
        if (!open) {
            return;
        }

        setName(
            playlist?.name
            ?? "",
        );

        setIntroduction(
            playlist?.introduction
            ?? "",
        );

        setVisibility(
            playlist?.visibility
            ?? "PRIVATE",
        );

        setCropSourceFile(null);
        setCroppedCoverFile(null);
        setLocalPreviewUrl(null);
        setSubmitting(false);
        setError("");
    }, [
        open,
        playlist,
    ]);

    useEffect(() => {
        if (
            croppedCoverFile
            === null
        ) {
            setLocalPreviewUrl(
                null,
            );
            return;
        }

        const url =
            URL.createObjectURL(
                croppedCoverFile,
            );

        setLocalPreviewUrl(url);

        return () => {
            URL.revokeObjectURL(
                url,
            );
        };
    }, [croppedCoverFile]);

    if (!open) {
        return null;
    }

    const previewUrl =
        localPreviewUrl
        ?? playlist?.coverUrl
        ?? null;

    function handleCoverSelect(
        event:
            ChangeEvent<HTMLInputElement>,
    ): void {
        const file =
            event.target.files?.[0]
            ?? null;

        event.target.value = "";

        if (file === null) {
            return;
        }

        if (
            !file.type.startsWith(
                "image/",
            )
        ) {
            setError(
                "请选择图片文件",
            );
            return;
        }

        if (
            file.size
            > 10
            * 1024
            * 1024
        ) {
            setError(
                "歌单封面不能超过10MB",
            );
            return;
        }

        setError("");
        setCropSourceFile(file);
    }

    async function handleSubmit(
        event:
            FormEvent<HTMLFormElement>,
    ): Promise<void> {
        event.preventDefault();

        const resolvedName =
            name.trim();

        if (
            resolvedName.length === 0
        ) {
            setError(
                "请输入歌单名称",
            );
            return;
        }

        setSubmitting(true);
        setError("");

        try {
            /*
             * 保存时只能使用用户真正上传的封面。
             *
             * playlist.coverUrl 可能只是“第一首歌曲自动封面”，
             * 不能把自动封面误写回 playlist_tb.cover_url。
             */
            let coverUrl =
                playlist?.customCoverUrl
                ?? null;

            if (
                croppedCoverFile
                !== null
            ) {
                const uploaded =
                    await uploadFile(
                        "playlist-cover",
                        croppedCoverFile,
                    );

                coverUrl =
                    uploaded.url;
            }

            const request = {
                name:
                    resolvedName,

                introduction:
                    introduction
                        .trim()
                    || null,

                coverUrl,

                visibility,
            };

            const saved =
                mode === "edit"
                && playlist !== null
                    ? await updatePlaylist(
                        playlist.id,
                        request,
                    )
                    : await createPlaylist(
                        request,
                    );

            onSaved(saved);
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
        <>
            <div className="playlist-form-layer">
                <button
                    type="button"
                    className="playlist-form-mask"
                    aria-label="关闭歌单编辑窗口"
                    onClick={
                        submitting
                            ? undefined
                            : onClose
                    }
                />

                <section
                    className="playlist-form-dialog"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="playlist-form-title"
                >
                    <header>
                        <div>
                            <h2 id="playlist-form-title">
                                {mode
                                === "edit"
                                    ? "编辑歌单"
                                    : "创建歌单"}
                            </h2>

                            <p>
                                歌单封面会裁剪为
                                1:1
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

                    <form
                        onSubmit={(event) => {
                            void handleSubmit(
                                event,
                            );
                        }}
                    >
                        <div className="playlist-cover-editor">
                            <div className="playlist-cover-preview">
                                {previewUrl ? (
                                    <img
                                        src={previewUrl}
                                        alt="歌单封面"
                                    />
                                ) : (
                                    <span>
                                        ♫
                                    </span>
                                )}
                            </div>

                            <div>
                                <strong>
                                    歌单封面
                                </strong>

                                <p>
                                    JPG / PNG，
                                    最大10MB
                                </p>

                                <label className="playlist-cover-select-button">
                                    选择封面

                                    <input
                                        type="file"
                                        accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                                        disabled={submitting}
                                        onChange={
                                            handleCoverSelect
                                        }
                                    />
                                </label>
                            </div>
                        </div>

                        <label className="playlist-form-field">
                            <span>
                                歌单名称<b>*</b>
                            </span>

                            <input
                                value={name}
                                maxLength={128}
                                disabled={submitting}
                                placeholder="请输入歌单名称"
                                onChange={(event) =>
                                    setName(
                                        event.target.value,
                                    )
                                }
                            />
                        </label>

                        <label className="playlist-form-field">
                            <span>
                                歌单简介
                            </span>

                            <textarea
                                value={introduction}
                                maxLength={1000}
                                rows={4}
                                disabled={submitting}
                                placeholder="介绍一下这个歌单，可选"
                                onChange={(event) =>
                                    setIntroduction(
                                        event.target.value,
                                    )
                                }
                            />
                        </label>

                        <fieldset className="playlist-visibility-field">
                            <legend>
                                歌单类型
                            </legend>

                            <label>
                                <input
                                    type="radio"
                                    name="visibility"
                                    checked={
                                        visibility
                                        === "PRIVATE"
                                    }
                                    disabled={submitting}
                                    onChange={() =>
                                        setVisibility(
                                            "PRIVATE",
                                        )
                                    }
                                />

                                私密
                            </label>

                            <label>
                                <input
                                    type="radio"
                                    name="visibility"
                                    checked={
                                        visibility
                                        === "PUBLIC"
                                    }
                                    disabled={submitting}
                                    onChange={() =>
                                        setVisibility(
                                            "PUBLIC",
                                        )
                                    }
                                />

                                公开
                            </label>
                        </fieldset>

                        {error && (
                            <div className="playlist-form-error">
                                {error}
                            </div>
                        )}

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
                                type="submit"
                                className="primary"
                                disabled={submitting}
                            >
                                {submitting
                                    ? "正在保存..."
                                    : mode
                                        === "edit"
                                        ? "保存修改"
                                        : "确认创建"}
                            </button>
                        </footer>
                    </form>
                </section>
            </div>

            <SquareImageCropDialog
                sourceFile={
                    cropSourceFile
                }
                title="裁剪歌单封面"
                onCancel={() =>
                    setCropSourceFile(
                        null,
                    )
                }
                onConfirm={(file) => {
                    setCroppedCoverFile(
                        file,
                    );

                    setCropSourceFile(
                        null,
                    );
                }}
            />
        </>,
        document.body,
    );
}
