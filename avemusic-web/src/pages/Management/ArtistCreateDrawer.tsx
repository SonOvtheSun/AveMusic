import {
    useEffect,
    useState,
    type ChangeEvent,
    type FormEvent,
} from "react";

import {
    createArtist,
    updateArtist,
    type ArtistManagementItem,
    type ArtistSearchItem,
} from "../../api/management";

import { uploadFile } from "../../api/file";
import { getApiError } from "../../auth/api/http";
import "../../styles/Management/ArtistAvatarCrop.css";
import "../../styles/Management/ArtistCreateDrawer.css";

interface ArtistCreateDrawerProps {
    open: boolean;
    artist?: ArtistManagementItem | null;
    onClose(): void;
    onCreated?(artist: ArtistSearchItem): void;
    onSaved?(): Promise<void> | void;
}

interface ArtistFormState {
    name: string;
    countryRegion: string;
    style: string;
    introduction: string;
}

const initialForm: ArtistFormState = {
    name: "",
    countryRegion: "",
    style: "",
    introduction: "",
};

export default function ArtistCreateDrawer({
                                               open,
                                               artist = null,
                                               onClose,
                                               onCreated,
                                               onSaved,
                                           }: ArtistCreateDrawerProps) {
    const [form, setForm] =
        useState<ArtistFormState>(
            initialForm,
        );

    const [avatarFile, setAvatarFile] =
        useState<File | null>(null);

    const [avatarPreviewUrl, setAvatarPreviewUrl] =
        useState("");

    const [cropSourceFile, setCropSourceFile] =
        useState<File | null>(null);

    const [cropSourceUrl, setCropSourceUrl] =
        useState("");

    const [cropX, setCropX] =
        useState(50);

    const [cropY, setCropY] =
        useState(50);

    const [cropping, setCropping] =
        useState(false);

    const [submitting, setSubmitting] =
        useState(false);

    const [progressText, setProgressText] =
        useState("");

    const [error, setError] =
        useState("");

    const [
        translatedNames,
        setTranslatedNames,
    ] = useState<string[]>([
        "",
    ]);



    useEffect(() => {
        if (!open) {
            return;
        }

        if (artist === null) {
            setForm(initialForm);
            setTranslatedNames([""]);
            setAvatarFile(null);
            setAvatarPreviewUrl("");
            setError("");
            return;
        }

        setForm({
            name: artist.name,
            countryRegion: artist.countryRegion ?? "",
            style: artist.style ?? "",
            introduction: artist.introduction ?? "",
        });

        setTranslatedNames(
            artist.translatedNames.length > 0
                ? [...artist.translatedNames]
                : [""],
        );

        setAvatarFile(null);
        setAvatarPreviewUrl(artist.avatarUrl ?? "");
        setError("");
    }, [artist, open]);

    useEffect(() => {
        return () => {
            if (avatarPreviewUrl) {
                URL.revokeObjectURL(
                    avatarPreviewUrl,
                );
            }
        };
    }, [avatarPreviewUrl]);

    useEffect(() => {
        return () => {
            if (cropSourceUrl) {
                URL.revokeObjectURL(
                    cropSourceUrl,
                );
            }
        };
    }, [cropSourceUrl]);

    function openAvatarCrop(
        file: File,
    ): void {
        if (!file.type.startsWith("image/")) {
            setError("请选择有效的图片文件");
            return;
        }

        setError("");

        setCropSourceFile(file);
        setCropSourceUrl(
            URL.createObjectURL(file),
        );

        setCropX(50);
        setCropY(50);
    }

    function cancelAvatarCrop(): void {
        setCropSourceFile(null);
        setCropSourceUrl("");
        setCropX(50);
        setCropY(50);
    }

    async function confirmAvatarCrop():
        Promise<void> {
        if (
            cropSourceFile === null
            || cropSourceUrl.length === 0
        ) {
            return;
        }

        setCropping(true);
        setError("");

        try {
            const croppedFile =
                await cropImageToSquare(
                    cropSourceFile,
                    cropSourceUrl,
                    cropX,
                    cropY,
                );

            setAvatarFile(croppedFile);

            setAvatarPreviewUrl(
                URL.createObjectURL(
                    croppedFile,
                ),
            );

            cancelAvatarCrop();
        } catch {
            setError("头像裁剪失败，请重新选择图片");
        } finally {
            setCropping(false);
        }
    }

    function updateField(
        field: keyof ArtistFormState,
        value: string,
    ): void {
        setForm((current) => ({
            ...current,
            [field]: value,
        }));
    }

    function reset(): void {
        setForm(initialForm);
        setTranslatedNames([""]);
        setAvatarFile(null);
        setAvatarPreviewUrl("");
        setCropSourceFile(null);
        setCropSourceUrl("");
        setCropX(50);
        setCropY(50);
        setProgressText("");
        setError("");
    }

    function close(): void {
        if (submitting) {
            return;
        }

        reset();
        onClose();
    }

    function addTranslatedName(): void {
        if (translatedNames.length >= 10) {
            setError("音乐人最多添加10个译名");
            return;
        }

        setTranslatedNames((current) => [
            ...current,
            "",
        ]);
    }

    function updateTranslatedName(
        index: number,
        value: string,
    ): void {
        setTranslatedNames((current) =>
            current.map((item, currentIndex) =>
                currentIndex === index
                    ? value
                    : item,
            ),
        );
    }

    function removeTranslatedName(
        index: number,
    ): void {
        setTranslatedNames((current) => {
            const next = current.filter(
                (_, currentIndex) =>
                    currentIndex !== index,
            );

            return next.length > 0
                ? next
                : [""];
        });
    }

    async function handleSubmit(
        event: FormEvent<HTMLFormElement>,
    ): Promise<void> {
        event.preventDefault();
        setError("");

        const name = form.name.trim();
        const countryRegion =
            form.countryRegion.trim();

        const resolvedTranslatedNames =
            translatedNames
                .map((item) =>
                    item.trim(),
                )
                .filter(
                    (
                        item,
                        index,
                        values,
                    ) =>
                        item.length > 0
                        && values.findIndex(
                            (value) =>
                                value.toLowerCase()
                                === item.toLowerCase(),
                        ) === index,
                );

        if (name.length === 0) {
            setError("请输入音乐人名称");
            return;
        }

        if (countryRegion.length === 0) {
            setError("请输入国家或地区");
            return;
        }

        setSubmitting(true);

        try {
            let avatarUrl: string | null =
                artist?.avatarUrl ?? null;

            if (avatarFile !== null) {
                setProgressText(
                    "正在上传音乐人头像...",
                );

                const uploaded = await uploadFile(
                    "avatar",
                    avatarFile,
                    (percent) =>
                        setProgressText(
                            `正在上传头像 ${percent}%`,
                        ),
                );

                avatarUrl = uploaded.url;
            }

            setProgressText(
                artist === null
                    ? "正在保存音乐人信息..."
                    : "正在保存音乐人修改...",
            );

            const request = {
                name,
                translatedNames:
                resolvedTranslatedNames,
                countryRegion,
                style:
                    form.style.trim()
                    || null,
                introduction:
                    form.introduction.trim()
                    || null,
                avatarUrl,
            };

            console.log(
                "[ArtistCreateDrawer] translatedNames =",
                translatedNames,
            );

            console.log(
                "[ArtistCreateDrawer] resolvedTranslatedNames =",
                resolvedTranslatedNames,
            );

            console.log(
                "[ArtistCreateDrawer] request =",
                request,
            );


            if (artist === null) {
                const created =
                    await createArtist(request);
                onCreated?.(created);
            } else {
                await updateArtist(
                    artist.id,
                    request,
                );
            }

            await onSaved?.();
            reset();
            onClose();
        } catch (requestError) {
            setError(
                getApiError(
                    requestError,
                ).message,
            );
        } finally {
            setSubmitting(false);
            setProgressText("");
        }
    }

    if (!open) {
        return null;
    }

    return (
        <div
            className="artist-drawer-layer"
            role="presentation"
        >
            <button
                type="button"
                className="artist-drawer-mask"
                aria-label="关闭新增音乐人表单"
                onClick={close}
            />

            <aside
                className="artist-drawer"
                role="dialog"
                aria-modal="true"
                aria-labelledby="artist-drawer-title"
            >
                <header className="song-drawer-header">
                    <div>
                        <h2 id="artist-drawer-title">
                            {artist === null
                                ? "新增音乐人"
                                : "编辑音乐人"}
                        </h2>

                        <p>
                            {artist === null
                                ? "运维新增后进入审核；超级管理员直接通过"
                                : "运维修改后重新进入审核；超级管理员直接生效"}
                        </p>
                    </div>

                    <button
                        type="button"
                        className="song-drawer-close"
                        disabled={submitting}
                        onClick={close}
                    >
                        ×
                    </button>
                </header>

                <form
                    className="song-form"
                    onSubmit={(event) => {
                        void handleSubmit(event);
                    }}
                >
                    <label className="song-form-field">
                        <span>
                            音乐人 / 乐队名称
                            <b>*</b>
                        </span>

                        <input
                            value={form.name}
                            maxLength={128}
                            placeholder="输入官方常用名称"
                            disabled={submitting}
                            onChange={(
                                event: ChangeEvent<HTMLInputElement>,
                            ) =>
                                updateField(
                                    "name",
                                    event.target.value,
                                )
                            }
                        />
                    </label>

                    <div className="song-form-field">
                        <div className="artist-alias-header">
                            <div>
                                <span>译名 / 别名</span>
                                <small>
                                    可填写英文名、中文译名、罗马音等；最多10个，
                                    用于音乐人搜索及歌词匹配。
                                </small>
                            </div>

                            <button
                                type="button"
                                className="artist-alias-add-button"
                                disabled={
                                    submitting
                                    || translatedNames.length >= 10
                                }
                                onClick={addTranslatedName}
                            >
                                <span aria-hidden="true">＋</span>
                                添加译名
                            </button>
                        </div>

                        <div className="artist-alias-list">
                            {translatedNames.map(
                                (translatedName, index) => (
                                    <div
                                        key={index}
                                        className="artist-alias-row"
                                    >
                                        <input
                                            value={translatedName}
                                            maxLength={128}
                                            placeholder={
                                                index === 0
                                                    ? "例如：Aimyon"
                                                    : "添加其他译名"
                                            }
                                            disabled={submitting}
                                            onChange={(
                                                event:
                                                ChangeEvent<HTMLInputElement>,
                                            ) =>
                                                updateTranslatedName(
                                                    index,
                                                    event.target.value,
                                                )
                                            }
                                        />

                                        <button
                                            type="button"
                                            className="artist-alias-delete-button"
                                            disabled={submitting}
                                            onClick={() =>
                                                removeTranslatedName(index)
                                            }
                                        >
                                            删除
                                        </button>
                                    </div>
                                ),
                            )}
                        </div>
                    </div>

                    <label className="song-form-field">
                        <span>
                            国家或地区
                            <b>*</b>
                        </span>

                        <input
                            value={form.countryRegion}
                            maxLength={64}
                            list="artist-region-options"
                            placeholder="例如：中国大陆、日本、美国"
                            disabled={submitting}
                            onChange={(
                                event: ChangeEvent<HTMLInputElement>,
                            ) =>
                                updateField(
                                    "countryRegion",
                                    event.target.value,
                                )
                            }
                        />

                        <datalist id="artist-region-options">
                            <option value="中国大陆" />
                            <option value="中国港澳台" />
                            <option value="日本" />
                            <option value="韩国" />
                            <option value="美国" />
                            <option value="英国" />
                            <option value="欧洲" />
                            <option value="其他海外地区" />
                        </datalist>
                    </label>

                    <label className="song-form-field">
                        <span>音乐风格</span>

                        <input
                            value={form.style}
                            maxLength={64}
                            placeholder="例如：流行 / 摇滚"
                            disabled={submitting}
                            onChange={(
                                event: ChangeEvent<HTMLInputElement>,
                            ) =>
                                updateField(
                                    "style",
                                    event.target.value,
                                )
                            }
                        />
                    </label>

                    <div className="song-form-field">
                        <span>官方头像</span>

                        {avatarPreviewUrl && (
                            <div className="avatar-cropped-preview">
                                <img
                                    src={avatarPreviewUrl}
                                    alt="裁剪后的头像预览"
                                />

                                <span>
                                    已裁剪为 1:1
                                </span>
                            </div>
                        )}

                        <label className="avatar-file-button">
                            {avatarFile
                                ? "重新选择头像"
                                : "选择头像"}

                            <input
                                type="file"
                                accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                                disabled={submitting}
                                onChange={(
                                    event: ChangeEvent<HTMLInputElement>,
                                ) => {
                                    const file =
                                        event.target.files?.[0];

                                    /*
                                     * 清空 input，保证连续选择
                                     * 同一个文件时仍会触发 change。
                                     */
                                    event.currentTarget.value = "";

                                    if (file) {
                                        openAvatarCrop(file);
                                    }
                                }}
                            />
                        </label>

                        <small>
                            可选。选择图片后必须先裁剪为 1:1，
                            确认后才会用于上传。
                        </small>
                    </div>

                    <label className="song-form-field">
                        <span>音乐人简介</span>

                        <textarea
                            value={form.introduction}
                            maxLength={1000}
                            rows={5}
                            placeholder="请输入音乐人或乐队简介"
                            disabled={submitting}
                            onChange={(
                                event: ChangeEvent<HTMLTextAreaElement>,
                            ) =>
                                updateField(
                                    "introduction",
                                    event.target.value,
                                )
                            }
                        />
                    </label>

                    <div className="artist-form-note">
                        名称首字母由后端根据音乐人名称自动生成，
                        前端不允许手动填写。
                    </div>

                    {progressText.length > 0 && (
                        <div className="song-form-progress">
                            {progressText}
                        </div>
                    )}

                    {error.length > 0 && (
                        <div className="song-form-error">
                            {error}
                        </div>
                    )}

                    <footer className="song-form-actions">
                        <button
                            type="button"
                            className="secondary"
                            disabled={submitting}
                            onClick={close}
                        >
                            取消
                        </button>

                        <button
                            type="submit"
                            className="primary"
                            disabled={submitting}
                        >
                            {submitting
                                ? "正在提交..."
                                : artist === null
                                    ? "确认新增"
                                    : "保存修改"}
                        </button>
                    </footer>
                </form>
            </aside>

            {cropSourceUrl && (
                <div
                    className="avatar-crop-layer"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="avatar-crop-title"
                >
                    <div className="avatar-crop-card">
                        <header className="avatar-crop-header">
                            <div>
                                <h3 id="avatar-crop-title">
                                    裁剪头像
                                </h3>

                                <p>
                                    最终头像固定为 1:1，
                                    输出尺寸 512 × 512
                                </p>
                            </div>
                        </header>

                        <div className="avatar-crop-preview">
                            <img
                                src={cropSourceUrl}
                                alt="待裁剪头像"
                                style={{
                                    objectPosition:
                                        `${cropX}% ${cropY}%`,
                                }}
                            />
                        </div>

                        <div className="avatar-crop-controls">
                            <label>
                                <span>水平位置</span>

                                <input
                                    type="range"
                                    min={0}
                                    max={100}
                                    value={cropX}
                                    disabled={cropping}
                                    onChange={(event) =>
                                        setCropX(
                                            Number(
                                                event.target.value,
                                            ),
                                        )
                                    }
                                />
                            </label>

                            <label>
                                <span>垂直位置</span>

                                <input
                                    type="range"
                                    min={0}
                                    max={100}
                                    value={cropY}
                                    disabled={cropping}
                                    onChange={(event) =>
                                        setCropY(
                                            Number(
                                                event.target.value,
                                            ),
                                        )
                                    }
                                />
                            </label>
                        </div>

                        <footer className="avatar-crop-actions">
                            <button
                                type="button"
                                className="secondary"
                                disabled={cropping}
                                onClick={cancelAvatarCrop}
                            >
                                取消
                            </button>

                            <button
                                type="button"
                                className="primary"
                                disabled={cropping}
                                onClick={() => {
                                    void confirmAvatarCrop();
                                }}
                            >
                                {cropping
                                    ? "正在裁剪..."
                                    : "确认裁剪"}
                            </button>
                        </footer>
                    </div>
                </div>
            )}
        </div>
    );
}

async function cropImageToSquare(
    sourceFile: File,
    sourceUrl: string,
    focusX: number,
    focusY: number,
): Promise<File> {
    const image = await loadImage(
        sourceUrl,
    );

    /*
     * 取原图能够容纳的最大正方形区域。
     *
     * 横图：
     *   高度作为正方形边长，focusX 控制左右位置。
     *
     * 竖图：
     *   宽度作为正方形边长，focusY 控制上下位置。
     */
    const cropSize = Math.min(
        image.naturalWidth,
        image.naturalHeight,
    );

    const maxSourceX =
        image.naturalWidth - cropSize;

    const maxSourceY =
        image.naturalHeight - cropSize;

    const sourceX =
        maxSourceX * focusX / 100;

    const sourceY =
        maxSourceY * focusY / 100;

    const outputSize = 512;

    const canvas =
        document.createElement("canvas");

    canvas.width = outputSize;
    canvas.height = outputSize;

    const context =
        canvas.getContext("2d");

    if (context === null) {
        throw new Error(
            "浏览器不支持Canvas",
        );
    }

    context.drawImage(
        image,
        sourceX,
        sourceY,
        cropSize,
        cropSize,
        0,
        0,
        outputSize,
        outputSize,
    );

    const outputType =
        sourceFile.type === "image/png"
            ? "image/png"
            : "image/jpeg";

    const blob = await canvasToBlob(
        canvas,
        outputType,
    );

    const originalName =
        sourceFile.name.replace(
            /\.[^.]+$/,
            "",
        );

    const extension =
        outputType === "image/png"
            ? "png"
            : "jpg";

    return new File(
        [blob],
        `${originalName}-avatar.${extension}`,
        {
            type: outputType,
            lastModified: Date.now(),
        },
    );
}

function loadImage(
    url: string,
): Promise<HTMLImageElement> {
    return new Promise(
        (resolve, reject) => {
            const image = new Image();

            image.onload = () =>
                resolve(image);

            image.onerror = () =>
                reject(
                    new Error(
                        "图片读取失败",
                    ),
                );

            image.src = url;
        },
    );
}

function canvasToBlob(
    canvas: HTMLCanvasElement,
    type: string,
): Promise<Blob> {
    return new Promise(
        (resolve, reject) => {
            canvas.toBlob(
                (blob) => {
                    if (blob === null) {
                        reject(
                            new Error(
                                "头像裁剪失败",
                            ),
                        );
                        return;
                    }

                    resolve(blob);
                },
                type,
                0.92,
            );
        },
    );
}
