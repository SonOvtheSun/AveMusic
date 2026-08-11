import { createContext } from "react";

export type UserRole =
    | "SUPER_ADMIN"
    | "OPERATOR"
    | "REVIEWER"
    | "ARTIST"
    | "USER";

export interface CurrentUser {
    userId: string;
    username: string;
    avatarUrl: string | null;
    role: UserRole;
    authorities: string[];
}

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

export type SmsPurpose = "REGISTER" | "LOGIN";

export interface AuthContextValue {
    user: CurrentUser | null;
    loading: boolean;
    error: string;
    passwordLogin(request: PasswordLoginRequest): Promise<void>;
    phoneLogin(request: PhoneLoginRequest): Promise<void>;
    register(request: RegisterRequest): Promise<void>;
    sendSmsCode(phone: string, purpose: SmsPurpose): Promise<void>;
    refreshUser(): Promise<void>;
    logout(): Promise<void>;
}

export const AuthContext =
    createContext<AuthContextValue | null>(null);
