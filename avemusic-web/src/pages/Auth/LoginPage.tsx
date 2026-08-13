import {
    useEffect,
    useState,
    type FormEvent,
} from "react";

import {
    useLocation,
    useNavigate,
} from "react-router-dom";

import { useAuth } from "../../context/useAuth";
import { getApiError } from "../../auth/api/http.ts";
import "../../styles/Auth/auth.css";

type AuthMode =
    | "PASSWORD"
    | "PHONE"
    | "REGISTER";

interface LocationState {
    from?: string;
}

export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();

    const {
        passwordLogin,
        phoneLogin,
        register,
        sendSmsCode,
    } = useAuth();

    const [mode, setMode] =
        useState<AuthMode>("PASSWORD");

    const [account, setAccount] =
        useState("");

    const [username, setUsername] =
        useState("");

    const [phone, setPhone] =
        useState("");

    const [password, setPassword] =
        useState("");

    const [confirmPassword, setConfirmPassword] =
        useState("");

    const [code, setCode] =
        useState("");

    const [countdown, setCountdown] =
        useState(0);

    const [submitting, setSubmitting] =
        useState(false);

    const [sendingCode, setSendingCode] =
        useState(false);

    const [message, setMessage] =
        useState("");

    const [error, setError] =
        useState("");

    useEffect(() => {
        if (countdown <= 0) {
            return;
        }

        const timer = window.setInterval(() => {
            setCountdown((value) =>
                Math.max(0, value - 1),
            );
        }, 1000);

        return () => {
            window.clearInterval(timer);
        };
    }, [countdown]);

    function switchMode(nextMode: AuthMode) {
        setMode(nextMode);
        setCode("");
        setPassword("");
        setConfirmPassword("");
        setMessage("");
        setError("");
    }

    function validatePhone(): boolean {
        if (!/^1[3-9]\d{9}$/.test(phone)) {
            setError("请输入正确的手机号");
            return false;
        }

        return true;
    }

    async function handleSendCode() {
        setError("");
        setMessage("");

        if (!validatePhone()) {
            return;
        }

        setSendingCode(true);

        try {
            await sendSmsCode(
                phone,
                mode === "REGISTER"
                    ? "REGISTER"
                    : "LOGIN",
            );

            setCountdown(60);
            setMessage(
                "验证码已发送，开发环境请查看 User Provider 控制台",
            );
        } catch (requestError) {
            setError(
                getApiError(requestError).message,
            );
        } finally {
            setSendingCode(false);
        }
    }

    async function handleSubmit(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        setError("");
        setMessage("");
        setSubmitting(true);

        try {
            if (mode === "PASSWORD") {
                if (!account.trim()) {
                    throw new Error("请输入用户名或手机号");
                }

                if (!password) {
                    throw new Error("请输入密码");
                }

                await passwordLogin({
                    account: account.trim(),
                    password,
                });
            }

            if (mode === "PHONE") {
                if (!validatePhone()) {
                    return;
                }

                if (!/^\d{6}$/.test(code)) {
                    throw new Error(
                        "请输入6位验证码",
                    );
                }

                await phoneLogin({
                    phone,
                    code,
                });
            }

            if (mode === "REGISTER") {
                if (!username.trim()) {
                    throw new Error("请输入用户名");
                }

                if (!validatePhone()) {
                    return;
                }

                if (password.length < 8) {
                    throw new Error(
                        "密码至少需要8位",
                    );
                }

                if (password !== confirmPassword) {
                    throw new Error(
                        "两次输入的密码不一致",
                    );
                }

                if (!/^\d{6}$/.test(code)) {
                    throw new Error(
                        "请输入6位验证码",
                    );
                }

                await register({
                    username: username.trim(),
                    phone,
                    password,
                    code,
                });
            }

            const state =
                location.state as
                    | LocationState
                    | null;

            navigate(
                state?.from ?? "/",
                {
                    replace: true,
                },
            );
        } catch (requestError) {
            if (requestError instanceof Error
                && !("response" in requestError)) {

                setError(requestError.message);
            } else {
                setError(
                    getApiError(
                        requestError,
                    ).message,
                );
            }
        } finally {
            setSubmitting(false);
        }
    }

    const needsPhone =
        mode === "PHONE"
        || mode === "REGISTER";

    return (
        <main className="auth-page">
            <section className="auth-brand">
                <div className="auth-brand-content">
                    <div className="auth-brand-logo">
                        <span className="auth-brand-logo-mark">A</span>
                        <strong>AveMusic</strong>
                    </div>

                    <span className="auth-brand-label">
                        MUSIC FOR EVERY MOMENT
                    </span>

                    <h1>
                        让每一次播放，
                        <br />
                        都成为记忆。
                    </h1>

                    <p>
                        收藏喜欢的音乐，创建自己的歌单，
                        随时继续上一次的旋律。
                    </p>

                    <div className="auth-wave">
                        <i />
                        <i />
                        <i />
                        <i />
                        <i />
                        <i />
                        <i />
                        <i />
                    </div>
                </div>
            </section>

            <section className="auth-panel">
                <button
                    type="button"
                    className="auth-back-home"
                    onClick={() => navigate("/")}
                >
                    ← 返回首页
                </button>

                <div className="auth-card">
                    <header className="auth-header">
                        <div className="auth-logo">
                            A
                        </div>

                        <div>
                            <h2>
                                {mode === "REGISTER"
                                    ? "创建 AveMusic 账号"
                                    : "欢迎回到 AveMusic"}
                            </h2>

                            <p>
                                {mode === "REGISTER"
                                    ? "注册后即可收藏音乐和创建歌单"
                                    : "登录后继续你的音乐旅程"}
                            </p>
                        </div>
                    </header>

                    <nav className="auth-tabs">
                        <button
                            type="button"
                            className={
                                mode === "PASSWORD"
                                    ? "active"
                                    : ""
                            }
                            onClick={() =>
                                switchMode("PASSWORD")
                            }
                        >
                            密码登录
                        </button>

                        <button
                            type="button"
                            className={
                                mode === "PHONE"
                                    ? "active"
                                    : ""
                            }
                            onClick={() =>
                                switchMode("PHONE")
                            }
                        >
                            手机登录
                        </button>

                        <button
                            type="button"
                            className={
                                mode === "REGISTER"
                                    ? "active"
                                    : ""
                            }
                            onClick={() =>
                                switchMode("REGISTER")
                            }
                        >
                            注册
                        </button>
                    </nav>

                    <form
                        className="auth-form"
                        onSubmit={handleSubmit}
                    >
                        {mode === "PASSWORD" && (
                            <>
                                <label>
                                    用户名或手机号
                                    <input
                                        value={account}
                                        onChange={(event) =>
                                            setAccount(
                                                event.target.value,
                                            )
                                        }
                                        placeholder="请输入用户名或手机号"
                                        autoComplete="username"
                                    />
                                </label>

                                <label>
                                    密码
                                    <input
                                        type="password"
                                        value={password}
                                        onChange={(event) =>
                                            setPassword(
                                                event.target.value,
                                            )
                                        }
                                        placeholder="请输入密码"
                                        autoComplete="current-password"
                                    />
                                </label>
                            </>
                        )}

                        {mode === "REGISTER" && (
                            <label>
                                用户名
                                <input
                                    value={username}
                                    onChange={(event) =>
                                        setUsername(
                                            event.target.value,
                                        )
                                    }
                                    placeholder="3到32位中文、字母或数字"
                                    autoComplete="username"
                                />
                            </label>
                        )}

                        {needsPhone && (
                            <>
                                <label>
                                    手机号
                                    <input
                                        value={phone}
                                        onChange={(event) =>
                                            setPhone(
                                                event.target.value
                                                    .replace(/\D/g, "")
                                                    .slice(0, 11),
                                            )
                                        }
                                        placeholder="请输入手机号"
                                        inputMode="numeric"
                                        autoComplete="tel"
                                    />
                                </label>

                                <label>
                                    验证码
                                    <div className="auth-code-row">
                                        <input
                                            value={code}
                                            onChange={(event) =>
                                                setCode(
                                                    event.target.value
                                                        .replace(/\D/g, "")
                                                        .slice(0, 6),
                                                )
                                            }
                                            placeholder="6位验证码"
                                            inputMode="numeric"
                                            autoComplete="one-time-code"
                                        />

                                        <button
                                            type="button"
                                            className="auth-code-button"
                                            disabled={
                                                countdown > 0
                                                || sendingCode
                                            }
                                            onClick={
                                                handleSendCode
                                            }
                                        >
                                            {sendingCode
                                                ? "发送中"
                                                : countdown > 0
                                                    ? `${countdown}s`
                                                    : "获取验证码"}
                                        </button>
                                    </div>
                                </label>
                            </>
                        )}

                        {mode === "REGISTER" && (
                            <>
                                <label>
                                    密码
                                    <input
                                        type="password"
                                        value={password}
                                        onChange={(event) =>
                                            setPassword(
                                                event.target.value,
                                            )
                                        }
                                        placeholder="至少8位"
                                        autoComplete="new-password"
                                    />
                                </label>

                                <label>
                                    确认密码
                                    <input
                                        type="password"
                                        value={confirmPassword}
                                        onChange={(event) =>
                                            setConfirmPassword(
                                                event.target.value,
                                            )
                                        }
                                        placeholder="再次输入密码"
                                        autoComplete="new-password"
                                    />
                                </label>
                            </>
                        )}

                        {message && (
                            <div className="auth-message">
                                {message}
                            </div>
                        )}

                        {error && (
                            <div className="auth-error">
                                {error}
                            </div>
                        )}

                        <button
                            type="submit"
                            className="auth-submit"
                            disabled={submitting}
                        >
                            {submitting
                                ? "处理中..."
                                : mode === "REGISTER"
                                    ? "注册并登录"
                                    : "登录"}
                        </button>
                    </form>

                    <footer className="auth-footer">
                        登录或注册即表示你同意
                        《用户协议》和《隐私政策》
                    </footer>
                </div>
            </section>
        </main>
    );
}