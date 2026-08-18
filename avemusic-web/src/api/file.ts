import { http } from "../auth/api/http";

import axios from "axios";

interface ApiResult<T> {
    code: string;
    message: string;
    data: T;
}

interface UploadTicket {
    ticket: string;
    uploadUrl: string;
}

export type UploadCategory =
    | "avatar"
    | "album-cover"
    | "playlist-cover"
    | "audio";

export interface UploadedFile {
    category: UploadCategory;
    originalName: string;
    fileName: string;
    relativePath: string;
    url: string;
    size: number;
    contentType: string;
}

/**
 * 音频超过 5 MiB 时启用分片上传。
 *
 * 5 * 1024 * 1024 = 5,242,880 bytes。
 */
const AUDIO_CHUNK_SIZE =
    2 * 1024 * 1024;

const CHUNK_MAX_ATTEMPTS = 3;

const CHUNK_REQUEST_TIMEOUT =
    180_000;

const COMPLETE_REQUEST_TIMEOUT =
    300_000;


export async function uploadFile(
    category: UploadCategory,
    file: File,
    onProgress?: (
        percent: number,
    ) => void,
): Promise<UploadedFile> {
    const ticketResponse =
        await http.post<ApiResult<UploadTicket>>(
            "/files/upload-ticket",
            {
                category,
                size: file.size,
            },
        );

    const {
        ticket,
        uploadUrl,
    } = ticketResponse.data.data;

    /*
     * 只对大音频启用分片。
     *
     * 头像、封面以及 <= 5 MiB 的音频仍使用原来的一次上传，
     * 因此其它业务完全不受影响。
     */
    if (
        category === "audio"
        && file.size > AUDIO_CHUNK_SIZE
    ) {
        return uploadAudioByChunks(
            file,
            ticket,
            uploadUrl,
            onProgress,
        );
    }

    return uploadDirect(
        file,
        ticket,
        uploadUrl,
        onProgress,
    );
}


async function uploadDirect(
    file: File,
    ticket: string,
    uploadUrl: string,
    onProgress?: (
        percent: number,
    ) => void,
): Promise<UploadedFile> {
    const formData =
        new FormData();

    formData.append(
        "file",
        file,
    );

    const response =
        await axios.post<
            ApiResult<UploadedFile>
        >(
            uploadUrl,
            formData,
            {
                headers: {
                    "X-Upload-Ticket":
                    ticket,
                },

                timeout:
                COMPLETE_REQUEST_TIMEOUT,

                onUploadProgress:
                    (event) => {
                        if (!event.total) {
                            return;
                        }

                        const percent =
                            Math.round(
                                event.loaded
                                / event.total
                                * 100,
                            );

                        onProgress?.(
                            Math.min(
                                100,
                                percent,
                            ),
                        );
                    },
            },
        );

    return response.data.data;
}


async function uploadAudioByChunks(
    file: File,
    ticket: string,
    uploadUrl: string,
    onProgress?: (
        percent: number,
    ) => void,
): Promise<UploadedFile> {
    const chunkCount =
        Math.ceil(
            file.size
            / AUDIO_CHUNK_SIZE,
        );

    let maxReportedPercent = 0;

    const reportProgress = (
        percent: number,
    ): void => {
        const normalized =
            Math.max(
                maxReportedPercent,
                Math.min(
                    99,
                    Math.floor(percent),
                ),
            );

        if (
            normalized
            > maxReportedPercent
        ) {
            maxReportedPercent =
                normalized;

            onProgress?.(
                normalized,
            );
        }
    };

    for (
        let chunkIndex = 0;
        chunkIndex < chunkCount;
        chunkIndex += 1
    ) {
        const start =
            chunkIndex
            * AUDIO_CHUNK_SIZE;

        const end =
            Math.min(
                start
                + AUDIO_CHUNK_SIZE,
                file.size,
            );

        const chunk =
            file.slice(
                start,
                end,
            );

        await uploadOneChunkWithRetry(
            chunk,
            chunkIndex,
            chunkCount,
            file.size,
            start,
            ticket,
            uploadUrl,
            reportProgress,
        );

        reportProgress(
            end
            / file.size
            * 100,
        );
    }

    /*
     * 所有分片都收到后，通知 File Service：
     * 校验 -> 按序拼接 -> 转存正式文件。
     */
    const response =
        await axios.post<
            ApiResult<UploadedFile>
        >(
            `${uploadUrl}/complete`,
            {
                originalName:
                file.name,

                contentType:
                    file.type
                    || "application/octet-stream",

                chunkCount,
            },
            {
                headers: {
                    "X-Upload-Ticket":
                    ticket,

                    "Content-Type":
                        "application/json",
                },

                timeout:
                COMPLETE_REQUEST_TIMEOUT,
            },
        );

    onProgress?.(100);

    return response.data.data;
}


async function uploadOneChunkWithRetry(
    chunk: Blob,
    chunkIndex: number,
    chunkCount: number,
    totalFileSize: number,
    chunkStart: number,
    ticket: string,
    uploadUrl: string,
    reportProgress: (
        percent: number,
    ) => void,
): Promise<void> {
    let lastError: unknown = null;

    for (
        let attempt = 1;
        attempt <= CHUNK_MAX_ATTEMPTS;
        attempt += 1
    ) {
        try {
            await axios.post(
                `${uploadUrl}/chunk`,
                chunk,
                {
                    params: {
                        chunkIndex,
                        chunkCount,
                    },

                    headers: {
                        "X-Upload-Ticket":
                        ticket,

                        /*
                         * 分片直接作为原始字节流发送，
                         * 不使用 multipart/form-data。
                         */
                        "Content-Type":
                            "application/octet-stream",
                    },

                    timeout:
                    CHUNK_REQUEST_TIMEOUT,

                    onUploadProgress:
                        (event) => {
                            if (!event.total) {
                                return;
                            }

                            const ratio =
                                Math.min(
                                    1,
                                    event.loaded
                                    / event.total,
                                );

                            const uploadedBytes =
                                chunkStart
                                + chunk.size
                                * ratio;

                            reportProgress(
                                uploadedBytes
                                / totalFileSize
                                * 100,
                            );
                        },
                },
            );

            return;

        } catch (error) {
            lastError = error;

            if (
                attempt
                >= CHUNK_MAX_ATTEMPTS
                || !isRetryableUploadError(
                    error,
                )
            ) {
                break;
            }

            /*
             * 分片服务端使用 chunkIndex 对应独立临时文件，
             * 因此同一个分片可以安全重试。
             */
            await sleep(
                500 * attempt,
            );
        }
    }

    const detail =
        lastError instanceof Error
            ? lastError.message
            : "网络请求失败";

    throw new Error(
        `第 ${chunkIndex + 1}/${chunkCount} 个分片上传失败：${detail}`,
    );
}


function isRetryableUploadError(
    error: unknown,
): boolean {
    if (!axios.isAxiosError(error)) {
        return false;
    }

    const status =
        error.response?.status;

    /*
     * 没拿到 HTTP 状态通常意味着网络中断；
     * 408 / 429 / 5xx 也允许重试。
     *
     * 400 / 401 / 403 等业务或权限错误不要无意义地重传。
     */
    return status === undefined
        || status === 408
        || status === 429
        || status >= 500;
}


function sleep(
    milliseconds: number,
): Promise<void> {
    return new Promise(
        (resolve) => {
            window.setTimeout(
                resolve,
                milliseconds,
            );
        },
    );
}