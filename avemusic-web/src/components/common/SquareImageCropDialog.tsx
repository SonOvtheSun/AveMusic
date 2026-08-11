import {
    useEffect,
    useState,
} from "react";

import { createPortal } from "react-dom";

import "../../styles/components/SquareImageCropDialog.css";

interface SquareImageCropDialogProps {
    sourceFile: File | null;
    title: string;
    onCancel(): void;
    onConfirm(file: File): void;
}

export default function SquareImageCropDialog({
    sourceFile,
    title,
    onCancel,
    onConfirm,
}: SquareImageCropDialogProps) {
    const [sourceUrl, setSourceUrl] =
        useState<string | null>(null);

    const [cropX, setCropX] =
        useState(50);

    const [cropY, setCropY] =
        useState(50);

    const [cropping, setCropping] =
        useState(false);

    const [error, setError] =
        useState("");

    useEffect(() => {
        if (sourceFile === null) {
            setSourceUrl(null);
            return;
        }

        const url =
            URL.createObjectURL(
                sourceFile,
            );

        setSourceUrl(url);
        setCropX(50);
        setCropY(50);
        setError("");

        return () => {
            URL.revokeObjectURL(url);
        };
    }, [sourceFile]);

    if (
        sourceFile === null
        || sourceUrl === null
    ) {
        return null;
    }

    async function handleConfirm():
            Promise<void> {
        setCropping(true);
        setError("");

        try {
            const cropped =
                await cropImageToSquare(
                    sourceFile!,
                    sourceUrl!,
                    cropX,
                    cropY,
                );

            onConfirm(cropped);
        } catch (cropError) {
            setError(
                cropError instanceof Error
                    ? cropError.message
                    : "图片裁剪失败",
            );
        } finally {
            setCropping(false);
        }
    }

    return createPortal(
        <div
            className="square-crop-layer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="square-crop-title"
        >
            <div className="square-crop-card">
                <header>
                    <h3 id="square-crop-title">
                        {title}
                    </h3>

                    <p>
                        最终封面固定为 1:1，
                        输出尺寸 512 × 512
                    </p>
                </header>

                <div className="square-crop-preview">
                    <img
                        src={sourceUrl}
                        alt="待裁剪图片"
                        style={{
                            objectPosition:
                                `${cropX}% ${cropY}%`,
                        }}
                    />
                </div>

                <div className="square-crop-controls">
                    <label>
                        <span>
                            水平位置
                        </span>

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
                        <span>
                            垂直位置
                        </span>

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

                {error && (
                    <div className="square-crop-error">
                        {error}
                    </div>
                )}

                <footer>
                    <button
                        type="button"
                        className="secondary"
                        disabled={cropping}
                        onClick={onCancel}
                    >
                        取消
                    </button>

                    <button
                        type="button"
                        className="primary"
                        disabled={cropping}
                        onClick={() => {
                            void handleConfirm();
                        }}
                    >
                        {cropping
                            ? "正在裁剪..."
                            : "确认裁剪"}
                    </button>
                </footer>
            </div>
        </div>,
        document.body,
    );
}

async function cropImageToSquare(
    sourceFile: File,
    sourceUrl: string,
    focusX: number,
    focusY: number,
): Promise<File> {
    const image =
        await loadImage(
            sourceUrl,
        );

    const cropSize =
        Math.min(
            image.naturalWidth,
            image.naturalHeight,
        );

    const maxSourceX =
        image.naturalWidth
        - cropSize;

    const maxSourceY =
        image.naturalHeight
        - cropSize;

    const sourceX =
        maxSourceX
        * focusX
        / 100;

    const sourceY =
        maxSourceY
        * focusY
        / 100;

    const outputSize = 512;

    const canvas =
        document.createElement(
            "canvas",
        );

    canvas.width = outputSize;
    canvas.height = outputSize;

    const context =
        canvas.getContext("2d");

    if (context === null) {
        throw new Error(
            "浏览器不支持 Canvas",
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
        sourceFile.type
        === "image/png"
            ? "image/png"
            : "image/jpeg";

    const blob =
        await canvasToBlob(
            canvas,
            outputType,
        );

    const originalName =
        sourceFile.name.replace(
            /\.[^.]+$/,
            "",
        );

    const extension =
        outputType
        === "image/png"
            ? "png"
            : "jpg";

    return new File(
        [blob],
        `${originalName}-playlist-cover.${extension}`,
        {
            type: outputType,
            lastModified:
                Date.now(),
        },
    );
}

function loadImage(
    url: string,
): Promise<HTMLImageElement> {
    return new Promise(
        (resolve, reject) => {
            const image =
                new Image();

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
                                "歌单封面裁剪失败",
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
