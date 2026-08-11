import {
    useCallback,
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from "react";

import {
    AuthContext,
    type AuthContextValue,
    type CurrentUser,
    type PasswordLoginRequest,
    type PhoneLoginRequest,
    type RegisterRequest,
    type SmsPurpose,
} from "./AuthContext";

import {
    clearTokens,
    getAccessToken,
    getApiError,
    http,
    saveTokens,
} from "../auth/api/http";

interface ApiResult<T> {
    code: string;
    message: string;
    data: T;
}

interface TokenResponse {
    user: CurrentUser;
    tokenType: string;
    accessToken: string;
    refreshToken: string;
    accessExpiresIn: number;
    absoluteExpiresAt: number;
}

export function AuthProvider({
                                 children,
                             }: {
    children: ReactNode;
}) {
    const [user, setUser] =
        useState<CurrentUser | null>(null);

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState("");

    const refreshUser = useCallback(
        async (): Promise<void> => {
            if (getAccessToken() === null) {
                setUser(null);
                setError("");
                setLoading(false);
                return;
            }

            setLoading(true);
            setError("");

            try {
                const response =
                    await http.get<
                        ApiResult<CurrentUser>
                    >("/auth/me");

                setUser(response.data.data);
            } catch (requestError) {
                clearTokens();
                setUser(null);

                setError(
                    getApiError(
                        requestError,
                    ).message,
                );
            } finally {
                setLoading(false);
            }
        },
        [],
    );

    const passwordLogin = useCallback(
        async (
            request: PasswordLoginRequest,
        ): Promise<void> => {
            const response =
                await http.post<
                    ApiResult<TokenResponse>
                >(
                    "/auth/login",
                    request,
                );

            const result =
                response.data.data;

            saveTokens(result);
            setUser(result.user);
            setError("");
        },
        [],
    );

    const phoneLogin = useCallback(
        async (
            request: PhoneLoginRequest,
        ): Promise<void> => {
            const response =
                await http.post<
                    ApiResult<TokenResponse>
                >(
                    "/auth/phone-login",
                    request,
                );

            const result =
                response.data.data;

            saveTokens(result);
            setUser(result.user);
            setError("");
        },
        [],
    );

    const register = useCallback(
        async (
            request: RegisterRequest,
        ): Promise<void> => {
            const response =
                await http.post<
                    ApiResult<TokenResponse>
                >(
                    "/auth/register",
                    request,
                );

            const result =
                response.data.data;

            saveTokens(result);
            setUser(result.user);
            setError("");
        },
        [],
    );

    const sendSmsCode = useCallback(
        async (
            phone: string,
            purpose: SmsPurpose,
        ): Promise<void> => {
            await http.post<ApiResult<null>>(
                "/auth/sms/code",
                {
                    phone,
                    purpose,
                },
            );
        },
        [],
    );

    const logout = useCallback(
        async (): Promise<void> => {
            try {
                if (getAccessToken() !== null) {
                    await http.post<
                        ApiResult<null>
                    >("/auth/logout");
                }
            } finally {
                clearTokens();
                setUser(null);
                setError("");
            }
        },
        [],
    );

    useEffect(() => {
        void refreshUser();
    }, [refreshUser]);

    useEffect(() => {
        function handleAuthExpired() {
            clearTokens();
            setUser(null);
            setError("");
            setLoading(false);
        }

        window.addEventListener(
            "avemusic:auth-expired",
            handleAuthExpired,
        );

        return () => {
            window.removeEventListener(
                "avemusic:auth-expired",
                handleAuthExpired,
            );
        };
    }, []);

    const value =
        useMemo<AuthContextValue>(
            () => ({
                user,
                loading,
                error,
                passwordLogin,
                phoneLogin,
                register,
                sendSmsCode,
                refreshUser,
                logout,
            }),
            [
                user,
                loading,
                error,
                passwordLogin,
                phoneLogin,
                register,
                sendSmsCode,
                refreshUser,
                logout,
            ],
        );

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}
