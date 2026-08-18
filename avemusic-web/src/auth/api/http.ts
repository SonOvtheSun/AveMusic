import axios, {
    AxiosError,
    type InternalAxiosRequestConfig,
} from "axios";

interface ApiResult<T> {
    code: string;
    message: string;
    data: T;
}

interface TokenResponse {
    tokenType: string;
    accessToken: string;
    refreshToken: string;
    accessExpiresIn: number;
    absoluteExpiresAt: number;
}

interface RetryRequestConfig
    extends InternalAxiosRequestConfig {
    _retry?: boolean;
}

const API_BASE_URL =
    "/api";

const ACCESS_TOKEN_KEY =
    "avemusic.access-token";

const REFRESH_TOKEN_KEY =
    "avemusic.refresh-token";

/*
 * 不写死 Content-Type。
 *
 * 普通对象由 Axios 自动发送 JSON；
 * FormData 由浏览器自动生成 multipart boundary。
 */
export const http =
    axios.create({
        baseURL:
        API_BASE_URL,

        timeout:
            8000,

        headers: {
            "Content-Type":
                "application/json",
        },
    });

const refreshHttp =
    axios.create({
        baseURL:
        API_BASE_URL,

        timeout:
            8000,

        headers: {
            "Content-Type":
                "application/json",
        },
    });

export function getAccessToken():
    string | null {
    return localStorage.getItem(
        ACCESS_TOKEN_KEY,
    );
}

export function getRefreshToken():
    string | null {
    return localStorage.getItem(
        REFRESH_TOKEN_KEY,
    );
}

export function saveTokens(
    token: TokenResponse,
): void {
    localStorage.setItem(
        ACCESS_TOKEN_KEY,
        token.accessToken,
    );

    localStorage.setItem(
        REFRESH_TOKEN_KEY,
        token.refreshToken,
    );
}

export function clearTokens(): void {
    localStorage.removeItem(
        ACCESS_TOKEN_KEY,
    );

    localStorage.removeItem(
        REFRESH_TOKEN_KEY,
    );
}

http.interceptors.request.use(
    (config) => {
        const accessToken =
            getAccessToken();

        if (accessToken !== null) {
            config.headers.Authorization =
                `Bearer ${accessToken}`;
        }

        return config;
    },
);

let refreshTask:
    Promise<TokenResponse> | null = null;

async function refreshAccessToken():
    Promise<TokenResponse> {
    if (refreshTask !== null) {
        return refreshTask;
    }

    const refreshToken =
        getRefreshToken();

    if (refreshToken === null) {
        throw new Error(
            "Refresh Token 不存在",
        );
    }

    refreshTask = refreshHttp
        .post<ApiResult<TokenResponse>>(
            "/auth/refresh",
            {
                refreshToken,
            },
        )
        .then((response) => {
            const token =
                response.data.data;

            saveTokens(token);
            return token;
        })
        .finally(() => {
            refreshTask = null;
        });

    return refreshTask;
}

function isPublicAuthRequest(
    url: string | undefined,
): boolean {
    if (url === undefined) {
        return false;
    }

    return [
        "/auth/login",
        "/auth/register",
        "/auth/phone-login",
        "/auth/sms/code",
        "/auth/refresh",
    ].some((path) =>
        url.includes(path),
    );
}

http.interceptors.response.use(
    (response) => response,

    async (error: AxiosError) => {
        const originalRequest =
            error.config as
                | RetryRequestConfig
                | undefined;

        const shouldRefresh =
            error.response?.status === 401
            && originalRequest !== undefined
            && originalRequest._retry !== true
            && !isPublicAuthRequest(
                originalRequest.url,
            );

        if (!shouldRefresh) {
            throw error;
        }

        originalRequest._retry = true;

        try {
            const token =
                await refreshAccessToken();

            originalRequest.headers.Authorization =
                `Bearer ${token.accessToken}`;

            return http(originalRequest);
        } catch (refreshError) {
            clearTokens();

            window.dispatchEvent(
                new Event(
                    "avemusic:auth-expired",
                ),
            );

            throw refreshError;
        }
    },
);

export function getApiError(
    error: unknown,
): {
    code: string;
    message: string;
} {
    if (axios.isAxiosError<
        ApiResult<unknown>
    >(error)) {
        return {
            code:
                error.response?.data?.code
                ?? "NETWORK_ERROR",

            message:
                error.response?.data?.message
                ?? "网络请求失败，请稍后重试",
        };
    }

    if (error instanceof Error) {
        return {
            code: "CLIENT_ERROR",
            message: error.message,
        };
    }

    return {
        code: "UNKNOWN_ERROR",
        message: "发生未知错误",
    };
}
