import {
    useEffect,
    useState,
    type FormEvent,
} from "react";

import {
    useLocation,
    useNavigate,
} from "react-router-dom";

import { useAuth } from "../context/useAuth";

import "../styles/components/Header.css";

interface NavItem {
    label: string;
    path?: string;
}

const navItems: NavItem[] = [
    {
        label: "发现音乐",
        path: "/",
    },
    {
        label: "我的音乐",
        path: "/my-music",
    },
];

function canAccessManagement(
    role: string | undefined,
): boolean {
    return role === "SUPER_ADMIN"
        || role === "OPERATOR"
        || role === "REVIEWER";
}

export default function Header() {
    const navigate = useNavigate();
    const location = useLocation();

    const {
        user,
        loading,
        logout,
    } = useAuth();

    const pathname =
        location.pathname;

    const [searchKeyword, setSearchKeyword] =
        useState("");


    const activeNav =
        pathname.startsWith(
            "/my-music",
        )
        || pathname.startsWith(
            "/playlists/",
        )
            ? "我的音乐"

            : pathname.startsWith(
                "/artists/",
            )
                ? "歌手"

                : pathname.startsWith(
                    "/management",
                )
                    ? "管理中心"

                    : "发现音乐";

    const showSubNav =
        activeNav === "发现音乐";

    useEffect(() => {

        if (
            !location.pathname
                .startsWith(
                    "/search",
                )
        ) {
            return;
        }

        const params =
            new URLSearchParams(
                location.search,
            );

        setSearchKeyword(
            params.get("keyword")
            ?? "",
        );

    }, [
        location.pathname,
        location.search,
    ]);

    function handleSearchSubmit(
        event:
        FormEvent<HTMLFormElement>,
    ): void {

        event.preventDefault();

        const keyword =
            searchKeyword.trim();

        if (keyword.length === 0) {
            return;
        }

        navigate(
            `/search?keyword=${
                encodeURIComponent(
                    keyword,
                )
            }`,
        );
    }

    function handleNavClick(
        item: NavItem,
    ): void {
        if (item.path !== undefined) {
            navigate(item.path);
        }
    }

    async function handleLogout() {
        await logout();
        navigate("/");
    }

    return (
        <header className="site-header">
            <div className="header-main">
                <div className="header-inner">
                    <button
                        type="button"
                        className="header-brand"
                        onClick={() => navigate("/")}
                    >
                        <span className="header-logo">
                            A
                        </span>

                        <span className="header-brand-name">
                            AveMusic
                        </span>
                    </button>

                    <nav className="header-nav">
                        {navItems.map((item) => (
                            <button
                                key={item.label}
                                type="button"
                                className={
                                    item.label
                                    === activeNav
                                        ? "header-nav-item active"
                                        : "header-nav-item"
                                }
                                onClick={() =>
                                    handleNavClick(item)
                                }
                            >
                                {item.label}
                            </button>
                        ))}

                        {!loading
                            && canAccessManagement(
                                user?.role,
                            )
                            && (
                                <button
                                    type="button"
                                    className={
                                        activeNav === "管理中心"
                                            ? "header-nav-item management active"
                                            : "header-nav-item management"
                                    }
                                    onClick={() =>
                                        navigate(
                                            "/management",
                                        )
                                    }
                                >
                                    管理中心
                                </button>
                            )}
                    </nav>

                    <div className="header-actions">
                        <form
                            className="header-search-form"
                            onSubmit={
                                handleSearchSubmit
                            }
                        >
                            <input
                                className="header-search"
                                value={
                                    searchKeyword
                                }
                                maxLength={64}
                                autoComplete="off"
                                placeholder={
                                    "搜索歌曲 / 歌手 / 专辑 / 歌单"
                                }
                                aria-label="全局搜索"
                                onChange={(event) =>
                                    setSearchKeyword(
                                        event.target.value,
                                    )
                                }
                            />

                            <button
                                type="submit"
                                className="header-search-button"
                                disabled={
                                    searchKeyword
                                        .trim()
                                        .length === 0
                                }
                            >
                                搜索
                            </button>
                        </form>

                        {loading && (
                            <div
                                className="header-auth-loading"
                                aria-label="正在读取用户信息"
                            />
                        )}

                        {!loading && user === null && (
                            <button
                                type="button"
                                className="header-login-button"
                                onClick={() =>
                                    navigate("/auth")
                                }
                            >
                                登录
                            </button>
                        )}

                        {!loading && user !== null && (
                            <details className="header-user">
                                <summary className="header-user-summary">
                                    {user.avatarUrl ? (
                                        <img
                                            src={user.avatarUrl}
                                            alt={user.username}
                                            className="header-user-avatar"
                                        />
                                    ) : (
                                        <span className="header-user-avatar fallback">
                                            {user.username
                                                .slice(0, 1)
                                                .toUpperCase()}
                                        </span>
                                    )}

                                    <span className="header-username">
                                        {user.username}
                                    </span>

                                    <span className="header-user-arrow">
                                        ▾
                                    </span>
                                </summary>

                                <div className="header-user-menu">
                                    <div className="header-user-info">
                                        <strong>
                                            {user.username}
                                        </strong>

                                        <span>
                                            {user.role}
                                        </span>
                                    </div>

                                    {canAccessManagement(
                                        user.role,
                                    ) && (
                                        <button
                                            type="button"
                                            className="header-menu-button"
                                            onClick={() =>
                                                navigate(
                                                    "/management",
                                                )
                                            }
                                        >
                                            管理中心
                                        </button>
                                    )}

                                    <button
                                        type="button"
                                        className="header-menu-button danger"
                                        onClick={() => {
                                            void handleLogout();
                                        }}
                                    >
                                        退出登录
                                    </button>
                                </div>
                            </details>
                        )}
                    </div>
                </div>
            </div>

            {showSubNav && (
                <div className="header-sub-nav">
                    <button
                        type="button"
                        className={
                            pathname === "/"
                                ? "header-sub-item active"
                                : "header-sub-item"
                        }
                        onClick={() =>
                            navigate("/")
                        }
                    >
                        推荐
                    </button>

                    <button
                        type="button"
                        className={
                            pathname.startsWith(
                                "/discover/playlists",
                            )
                                ? "header-sub-item active"
                                : "header-sub-item"
                        }
                        onClick={() =>
                            navigate(
                                "/discover/playlists",
                            )
                        }
                    >
                        歌单
                    </button>

                    <button
                        type="button"
                        className={
                        pathname.startsWith(
                            "/artists"
                        )
                            ? "header-sub-item active"
                            : "header-sub-item"
                    }
                        onClick={() =>
                            navigate("/artists"
                            )}
                    >
                        歌手
                    </button>
                </div>
            )}
        </header>
    );
}
