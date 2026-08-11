import { http } from "./http.ts";

export interface ApiResult<T> {
    code: string;
    message: string;
    data: T;
}

export interface User {
    userId: string;
    username: string;
    avatarUrl: string | null;
    authorities: string[];
}

export type SmsPurpose =
    | "REGISTER"
    | "LOGIN";

export interface PasswordLoginRequest {
    account: string;
    password: string;
}

export interface PhoneLoginRequest {
    phone: string;
    code: string;
}

export interface RegisterRequest {
    username: string;
    phone: string;
    password: string;
    code: string;
}

function unwrap<T>(
    result: ApiResult<T>,
): T {
    return result.data;
}

export async function sendSmsCode(
    phone: string,
    purpose: SmsPurpose,
): Promise<void> {
    await http.post<ApiResult<null>>(
        "/auth/sms/code",
        {
            phone,
            purpose,
        },
    );
}

export async function login(
    request: PasswordLoginRequest,
): Promise<User> {
    const response =
        await http.post<ApiResult<User>>(
            "/auth/login",
            request,
        );

    return unwrap(response.data);
}

export async function phoneLogin(
    request: PhoneLoginRequest,
): Promise<User> {
    const response =
        await http.post<ApiResult<User>>(
            "/auth/phone-login",
            request,
        );

    return unwrap(response.data);
}

export async function register(
    request: RegisterRequest,
): Promise<User> {
    const response =
        await http.post<ApiResult<User>>(
            "/auth/register",
            request,
        );

    return unwrap(response.data);
}

export async function getCurrentUser():
    Promise<User> {
    const response =
        await http.get<ApiResult<User>>(
            "/auth/me",
        );

    return unwrap(response.data);
}

export async function logout():
    Promise<void> {
    await http.post("/auth/logout");
}