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
                            percent,
                        );
                    },
            },
        );

    return response.data.data;
}
