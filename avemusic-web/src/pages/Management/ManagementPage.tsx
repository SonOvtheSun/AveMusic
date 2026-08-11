import {
    useCallback,
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    Navigate,
    useNavigate,
} from "react-router-dom";

import Header from "../../components/Header";
import { useAuth } from "../../context/useAuth";
import type { UserRole } from "../../context/AuthContext";

import {
    getManagedAlbums,
    getManagedArtists,
    getManagedSongs,
    getManagedUsers,
    getAuditAlbums,
    getAuditArtists,
    getAuditSongs,
    deleteArtist,
    deleteAlbums,
    deleteArtists,
    deleteSongs,
    setArtistOnline,
    reviewAlbum,
    reviewArtist,
    reviewSong,
    type AlbumManagementItem,
    type ArtistManagementItem,
    type ReviewAction,
    type SongManagementItem,
    type UserManagementItem,
} from "../../api/management";

import { getApiError } from "../../auth/api/http";
import SongCreateDrawer from "./SongCreateDrawer";
import ArtistCreateDrawer from "./ArtistCreateDrawer";
import AlbumCreateDrawer from "./AlbumCreateDrawer";

import "../../styles/Management/ManagementPage.css";

type Section =
    | "MUSIC"
    | "ARTIST"
    | "USER"
    | "AUDIT";

type MusicTab =
    | "SONG"
    | "ALBUM";

type AuditTab =
    | "SONG"
    | "ALBUM"
    | "ARTIST";

type DeleteTarget =
    | "SONG"
    | "ALBUM"
    | "ARTIST";

const roleLabels: Record<UserRole, string> = {
    SUPER_ADMIN: "超级管理员",
    OPERATOR: "运维",
    REVIEWER: "审核员",
    ARTIST: "音乐人",
    USER: "普通用户",
};

function availableSections(
    role: UserRole,
): Section[] {
    switch (role) {
        case "SUPER_ADMIN":
            return [
                "MUSIC",
                "ARTIST",
                "USER",
                "AUDIT",
            ];

        case "OPERATOR":
            return [
                "MUSIC",
                "ARTIST",
                "USER",
            ];

        case "REVIEWER":
            return ["AUDIT"];

        default:
            return [];
    }
}

function auditLabel(
    status: string,
): string {
    switch (status) {
        case "APPROVED":
            return "已通过";
        case "REJECTED":
            return "已驳回";
        default:
            return "待审核";
    }
}

function statusClass(
    status: string,
): string {
    if (
        status === "APPROVED"
        || status === "ONLINE"
        || status === "ENABLED"
        || status === "VERIFIED"
    ) {
        return "success";
    }

    if (
        status === "PENDING"
        || status === "NONE"
    ) {
        return "pending";
    }

    return "danger";
}

function realNameLabel(
    status:
    UserManagementItem["realNameStatus"],
): string {
    switch (status) {
        case "VERIFIED":
            return "已实名";
        case "PENDING":
            return "待审核";
        case "REJECTED":
            return "已驳回";
        default:
            return "未实名";
    }
}

export default function ManagementPage() {
    const navigate = useNavigate();
    const { user, loading } = useAuth();

    const [section, setSection] =
        useState<Section>("MUSIC");

    const [musicTab, setMusicTab] =
        useState<MusicTab>("SONG");

    const [auditTab, setAuditTab] =
        useState<AuditTab>("SONG");

    const [songs, setSongs] =
        useState<SongManagementItem[]>([]);

    const [albums, setAlbums] =
        useState<AlbumManagementItem[]>([]);

    const [artists, setArtists] =
        useState<ArtistManagementItem[]>([]);

    const [users, setUsers] =
        useState<UserManagementItem[]>([]);

    const [dataLoading, setDataLoading] =
        useState(false);

    const [dataError, setDataError] =
        useState("");

    const [drawerOpen, setDrawerOpen] =
        useState(false);

    const [albumDrawerOpen, setAlbumDrawerOpen] =
        useState(false);

    const [editingSong, setEditingSong] =
        useState<SongManagementItem | null>(null);

    const [editingAlbum, setEditingAlbum] =
        useState<AlbumManagementItem | null>(null);

    const [artistDrawerOpen, setArtistDrawerOpen] =
        useState(false);

    const [editingArtist, setEditingArtist] =
        useState<ArtistManagementItem | null>(null);

    const [selectedSongIds, setSelectedSongIds] =
        useState<string[]>([]);

    const [selectedAlbumIds, setSelectedAlbumIds] =
        useState<string[]>([]);

    const [selectedArtistIds, setSelectedArtistIds] =
        useState<string[]>([]);

    const MANAGEMENT_PAGE_SIZE =
        10;

    /*
     * searchInput：
     * 用户输入框里正在输入的内容。
     *
     * managementKeyword：
     * 真正提交给后端的查询条件。
     */
    const [
        searchInput,
        setSearchInput,
    ] = useState("");

    const [
        managementKeyword,
        setManagementKeyword,
    ] = useState("");

    const [
        songPage,
        setSongPage,
    ] = useState(1);

    const [
        albumPage,
        setAlbumPage,
    ] = useState(1);

    const [
        songTotal,
        setSongTotal,
    ] = useState(0);

    const [
        albumTotal,
        setAlbumTotal,
    ] = useState(0);

    const [
        songTotalPages,
        setSongTotalPages,
    ] = useState(0);

    const [
        albumTotalPages,
        setAlbumTotalPages,
    ] = useState(0);

    const allowed = useMemo(
        () => user
            ? availableSections(user.role)
            : [],
        [user],
    );

    useEffect(() => {
        if (
            allowed.length > 0
            && !allowed.includes(section)
        ) {
            setSection(allowed[0]);
        }
    }, [
        allowed,
        section,
    ]);

    function submitManagementSearch():
        void {
        /*
         * 搜索条件改变时必须回第一页。
         */
        setSongPage(1);
        setAlbumPage(1);

        setSelectedSongIds([]);
        setSelectedAlbumIds([]);

        setManagementKeyword(
            searchInput.trim(),
        );
    }

    function clearManagementSearch():
        void {
        setSearchInput("");

        setSongPage(1);
        setAlbumPage(1);

        setSelectedSongIds([]);
        setSelectedAlbumIds([]);

        setManagementKeyword("");
    }

    const loadCurrent =
        useCallback(
            async (): Promise<void> => {
                if (
                    user === null
                    || !allowed.includes(
                        section,
                    )
                ) {
                    return;
                }

                setDataLoading(true);
                setDataError("");

                try {
                    /*
                     * =====================
                     * 音乐 / 专辑管理
                     * =====================
                     */
                    if (
                        section === "MUSIC"
                    ) {
                        if (
                            musicTab === "SONG"
                        ) {
                            const result =
                                await getManagedSongs(
                                    songPage,
                                    MANAGEMENT_PAGE_SIZE,
                                    managementKeyword,
                                );

                            /*
                             * 删除最后一页最后一条数据后：
                             *
                             * 原 page=3
                             * 新 totalPages=2
                             *
                             * 自动退回第2页。
                             */
                            if (
                                result.totalPages
                                > 0
                                && songPage
                                > result.totalPages
                            ) {
                                setSongPage(
                                    result.totalPages,
                                );

                                return;
                            }

                            setSongs(
                                result.records,
                            );

                            setSongTotal(
                                result.total,
                            );

                            setSongTotalPages(
                                result.totalPages,
                            );
                        } else {
                            const result =
                                await getManagedAlbums(
                                    albumPage,
                                    MANAGEMENT_PAGE_SIZE,
                                    managementKeyword,
                                );

                            if (
                                result.totalPages
                                > 0
                                && albumPage
                                > result.totalPages
                            ) {
                                setAlbumPage(
                                    result.totalPages,
                                );

                                return;
                            }

                            setAlbums(
                                result.records,
                            );

                            setAlbumTotal(
                                result.total,
                            );

                            setAlbumTotalPages(
                                result.totalPages,
                            );
                        }

                        return;
                    }

                    /*
                     * =====================
                     * 音乐人管理
                     * =====================
                     */
                    if (
                        section === "ARTIST"
                    ) {
                        setArtists(
                            await getManagedArtists(),
                        );

                        return;
                    }

                    /*
                     * =====================
                     * 用户管理
                     * =====================
                     */
                    if (
                        section === "USER"
                    ) {
                        setUsers(
                            await getManagedUsers(),
                        );

                        return;
                    }

                    /*
                     * =====================
                     * 审核管理
                     *
                     * 当前阶段仍然使用旧接口。
                     * =====================
                     */
                    if (
                        auditTab === "SONG"
                    ) {
                        setSongs(
                            await getAuditSongs(),
                        );
                    } else if (
                        auditTab === "ALBUM"
                    ) {
                        setAlbums(
                            await getAuditAlbums(),
                        );
                    } else {
                        setArtists(
                            await getAuditArtists(),
                        );
                    }
                } catch (error) {
                    setDataError(
                        getApiError(
                            error,
                        ).message,
                    );
                } finally {
                    setDataLoading(
                        false,
                    );
                }
            },
            [
                user,
                allowed,
                section,

                musicTab,
                auditTab,

                songPage,
                albumPage,

                managementKeyword,
            ],
        );

    useEffect(() => {
        void loadCurrent();
    }, [loadCurrent]);

    async function handleReview(
        type: AuditTab,
        id: string,
        action: ReviewAction,
    ): Promise<void> {
        let reason: string | null = null;

        if (action === "REJECT") {
            reason = window.prompt(
                "请输入驳回原因",
            );

            if (
                reason === null
                || reason.trim().length === 0
            ) {
                return;
            }

            reason = reason.trim();
        }

        if (
            action === "REVOKE"
            && !window.confirm(
                "确定撤销该内容的审核吗？撤销后将重新进入待审核状态。",
            )
        ) {
            return;
        }

        setDataLoading(true);
        setDataError("");

        try {
            if (type === "SONG") {
                await reviewSong(
                    id,
                    action,
                    reason,
                );
            } else if (type === "ALBUM") {
                await reviewAlbum(
                    id,
                    action,
                    reason,
                );
            } else {
                await reviewArtist(
                    id,
                    action,
                    reason,
                );
            }

            await loadCurrent();
        } catch (error) {
            setDataError(
                getApiError(error).message,
            );
        } finally {
            setDataLoading(false);
        }
    }

    async function handleBatchDelete(
        target: DeleteTarget,
        ids: string[],
    ): Promise<void> {
        if (ids.length === 0) {
            return;
        }

        const label =
            target === "SONG"
                ? "音乐"
                : target === "ALBUM"
                    ? "专辑"
                    : "音乐人";

        const extra =
            target === "ARTIST"
                ? "\n音乐人仍绑定用户、歌曲或专辑时，该批次会整体拒绝删除。"
                : "";

        if (!window.confirm(
            `确定删除选中的 ${ids.length} 条${label}吗？${extra}`,
        )) {
            return;
        }

        setDataLoading(true);
        setDataError("");

        try {
            if (target === "SONG") {
                await deleteSongs(ids);
                setSelectedSongIds([]);
            } else if (target === "ALBUM") {
                await deleteAlbums(ids);
                setSelectedAlbumIds([]);
            } else {
                await deleteArtists(ids);
                setSelectedArtistIds([]);
            }

            await loadCurrent();
        } catch (error) {
            setDataError(
                getApiError(error).message,
            );
        } finally {
            setDataLoading(false);
        }
    }

    async function handleArtistStatus(
        artist: ArtistManagementItem,
    ): Promise<void> {
        const online =
            artist.publishStatus !== "ONLINE";

        if (
            online
            && artist.auditStatus !== "APPROVED"
        ) {
            window.alert(
                "只有审核通过的音乐人才能恢复上架",
            );
            return;
        }

        const actionText = online
            ? "恢复上架"
            : "下架";

        if (!window.confirm(
            `确定${actionText}“${artist.name}”吗？`,
        )) {
            return;
        }

        setDataLoading(true);
        setDataError("");

        try {
            await setArtistOnline(
                artist.id,
                online,
            );
            await loadCurrent();
        } catch (error) {
            setDataError(
                getApiError(error).message,
            );
        } finally {
            setDataLoading(false);
        }
    }

    async function handleArtistDelete(
        artist: ArtistManagementItem,
    ): Promise<void> {
        if (!window.confirm(
            `确定删除“${artist.name}”吗？\n只有未绑定用户、歌曲和专辑的音乐人才允许删除。`,
        )) {
            return;
        }

        setDataLoading(true);
        setDataError("");

        try {
            await deleteArtist(artist.id);
            setSelectedArtistIds((current) =>
                current.filter((id) => id !== artist.id),
            );
            await loadCurrent();
        } catch (error) {
            setDataError(
                getApiError(error).message,
            );
        } finally {
            setDataLoading(false);
        }
    }

    if (loading) {
        return (
            <div className="management-page">
                <Header />

                <main className="management-loading">
                    正在加载管理权限...
                </main>
            </div>
        );
    }

    if (
        user === null
        || user.role === "USER"
        || user.role === "ARTIST"
    ) {
        return (
            <Navigate
                to="/"
                replace
            />
        );
    }

    return (
        <div className="management-page">
            <Header />

            <main className="management-layout">
                <aside className="management-sidebar">
                    <div className="management-user">
                        <span>
                            {user.username
                                .slice(0, 1)
                                .toUpperCase()}
                        </span>

                        <div>
                            <strong>
                                {user.username}
                            </strong>

                            <small>
                                {roleLabels[user.role]}
                            </small>
                        </div>
                    </div>

                    <nav className="management-nav">
                        {allowed.includes("MUSIC") && (
                            <NavButton
                                active={
                                    section === "MUSIC"
                                }
                                title="音乐管理"
                                description="歌曲和专辑"
                                onClick={() =>
                                    setSection("MUSIC")
                                }
                            />
                        )}

                        {allowed.includes("ARTIST") && (
                            <NavButton
                                active={
                                    section === "ARTIST"
                                }
                                title="音乐人管理"
                                description="音乐人资料"
                                onClick={() =>
                                    setSection("ARTIST")
                                }
                            />
                        )}

                        {allowed.includes("USER") && (
                            <NavButton
                                active={
                                    section === "USER"
                                }
                                title="用户管理"
                                description="角色和实名状态"
                                onClick={() =>
                                    setSection("USER")
                                }
                            />
                        )}

                        {allowed.includes("AUDIT") && (
                            <NavButton
                                active={
                                    section === "AUDIT"
                                }
                                title="审核管理"
                                description="音乐、专辑、音乐人"
                                onClick={() =>
                                    setSection("AUDIT")
                                }
                            />
                        )}
                    </nav>

                    <button
                        type="button"
                        className="management-back"
                        onClick={() => navigate("/")}
                    >
                        返回首页
                    </button>
                </aside>

                <section className="management-content">
                    {section === "MUSIC" && (
                        <MusicSection
                            tab={musicTab}

                            songs={songs}
                            albums={albums}

                            loading={
                                dataLoading
                            }

                            selectedSongIds={
                                selectedSongIds
                            }

                            selectedAlbumIds={
                                selectedAlbumIds
                            }

                            onSelectedSongIdsChange={
                                setSelectedSongIds
                            }

                            onSelectedAlbumIdsChange={
                                setSelectedAlbumIds
                            }

                            searchInput={
                                searchInput
                            }

                            page={
                                musicTab === "SONG"
                                    ? songPage
                                    : albumPage
                            }

                            total={
                                musicTab === "SONG"
                                    ? songTotal
                                    : albumTotal
                            }

                            totalPages={
                                musicTab === "SONG"
                                    ? songTotalPages
                                    : albumTotalPages
                            }

                            onSearchInputChange={
                                setSearchInput
                            }

                            onSearch={
                                submitManagementSearch
                            }

                            onClearSearch={
                                clearManagementSearch
                            }

                            onPageChange={(page) => {
                                /*
                                 * 翻页后清除当前页选中状态。
                                 */
                                setSelectedSongIds([]);
                                setSelectedAlbumIds([]);

                                if (
                                    musicTab === "SONG"
                                ) {
                                    setSongPage(page);
                                } else {
                                    setAlbumPage(page);
                                }
                            }}

                            onTabChange={(nextTab) => {
                                setMusicTab(
                                    nextTab,
                                );

                                setSelectedSongIds([]);
                                setSelectedAlbumIds([]);

                                /*
                                 * 切换 tab 时对应页面从第一页开始。
                                 */
                                if (
                                    nextTab === "SONG"
                                ) {
                                    setSongPage(1);
                                } else {
                                    setAlbumPage(1);
                                }
                            }}

                            onAddSong={() => {
                                setEditingSong(null);
                                setDrawerOpen(true);
                            }}

                            onAddAlbum={() => {
                                setEditingAlbum(null);
                                setAlbumDrawerOpen(true);
                            }}

                            onEditSong={(song) => {
                                setEditingSong(song);
                                setDrawerOpen(true);
                            }}

                            onEditAlbum={(album) => {
                                setEditingAlbum(album);
                                setAlbumDrawerOpen(true);
                            }}

                            onDeleteSongs={(ids) => {
                                void handleBatchDelete(
                                    "SONG",
                                    ids,
                                );
                            }}

                            onDeleteAlbums={(ids) => {
                                void handleBatchDelete(
                                    "ALBUM",
                                    ids,
                                );
                            }}
                        />
                    )}

                    {section === "ARTIST" && (
                        <ArtistSection
                            artists={artists}
                            loading={dataLoading}
                            onAddArtist={() => {
                                setEditingArtist(null);
                                setArtistDrawerOpen(true);
                            }}
                            onEditArtist={(artist) => {
                                setEditingArtist(artist);
                                setArtistDrawerOpen(true);
                            }}
                            onRefresh={() => {
                                void loadCurrent();
                            }}
                            onToggleStatus={(artist) => {
                                void handleArtistStatus(artist);
                            }}
                            onDeleteArtist={(artist) => {
                                void handleArtistDelete(artist);
                            }}
                            selectedIds={selectedArtistIds}
                            onSelectedIdsChange={setSelectedArtistIds}
                            onBatchDelete={(ids) => {
                                void handleBatchDelete(
                                    "ARTIST",
                                    ids,
                                );
                            }}
                        />
                    )}

                    {section === "USER" && (
                        <UserSection
                            users={users}
                            loading={dataLoading}
                        />
                    )}

                    {section === "AUDIT" && (
                        <AuditSection
                            tab={auditTab}
                            songs={songs}
                            albums={albums}
                            artists={artists}
                            loading={dataLoading}
                            onTabChange={setAuditTab}
                            onReview={(type, id, action) => {
                                void handleReview(
                                    type,
                                    id,
                                    action,
                                );
                            }}
                        />
                    )}

                    {dataError.length > 0 && (
                        <div className="management-error">
                            <span>{dataError}</span>

                            <button
                                type="button"
                                onClick={() => {
                                    void loadCurrent();
                                }}
                            >
                                重新加载
                            </button>
                        </div>
                    )}
                </section>
            </main>

            <SongCreateDrawer
                open={drawerOpen}
                song={editingSong}
                onClose={() => {
                    setDrawerOpen(false);
                    setEditingSong(null);
                }}
                onCreated={loadCurrent}
            />

            <AlbumCreateDrawer
                open={albumDrawerOpen}
                album={editingAlbum}
                onClose={() => {
                    setAlbumDrawerOpen(false);
                    setEditingAlbum(null);
                }}
                onCreated={loadCurrent}
            />

            <ArtistCreateDrawer
                open={artistDrawerOpen}
                artist={editingArtist}
                onClose={() => {
                    setArtistDrawerOpen(false);
                    setEditingArtist(null);
                }}
                onSaved={loadCurrent}
            />
        </div>
    );
}

function NavButton({
                       active,
                       title,
                       description,
                       onClick,
                   }: {
    active: boolean;
    title: string;
    description: string;
    onClick(): void;
}) {
    return (
        <button
            type="button"
            className={
                active
                    ? "management-nav-item active"
                    : "management-nav-item"
            }
            onClick={onClick}
        >
            <strong>{title}</strong>
            <span>{description}</span>
        </button>
    );
}

function PageTitle({
                       title,
                       description,
                       action,
                       onAction,
                   }: {
    title: string;
    description: string;
    action?: string;
    onAction?(): void;
}) {
    return (
        <header className="management-title">
            <div>
                <h1>{title}</h1>
                <p>{description}</p>
            </div>

            {action && onAction && (
                <button
                    type="button"
                    className="management-primary"
                    onClick={onAction}
                >
                    {action}
                </button>
            )}
        </header>
    );
}

function Tabs<T extends string>({
                                    value,
                                    options,
                                    onChange,
                                }: {
    value: T;
    options: Array<{
        value: T;
        label: string;
    }>;
    onChange(value: T): void;
}) {
    return (
        <div className="management-tabs">
            {options.map((option) => (
                <button
                    key={option.value}
                    type="button"
                    className={
                        value === option.value
                            ? "management-tab active"
                            : "management-tab"
                    }
                    onClick={() =>
                        onChange(option.value)
                    }
                >
                    {option.label}
                </button>
            ))}
        </div>
    );
}

function MusicSection({
                          tab,
                          songs,
                          albums,
                          loading,

                          selectedSongIds,
                          selectedAlbumIds,

                          searchInput,

                          page,
                          total,
                          totalPages,

                          onSelectedSongIdsChange,
                          onSelectedAlbumIdsChange,

                          onSearchInputChange,
                          onSearch,
                          onClearSearch,
                          onPageChange,

                          onTabChange,

                          onAddSong,
                          onAddAlbum,

                          onEditSong,
                          onEditAlbum,

                          onDeleteSongs,
                          onDeleteAlbums,
                      }: {
    tab: MusicTab;

    songs:
        SongManagementItem[];

    albums:
        AlbumManagementItem[];

    loading: boolean;

    selectedSongIds:
        string[];

    selectedAlbumIds:
        string[];

    searchInput: string;

    page: number;
    total: number;
    totalPages: number;

    onSelectedSongIdsChange(
        ids: string[],
    ): void;

    onSelectedAlbumIdsChange(
        ids: string[],
    ): void;

    onSearchInputChange(
        value: string,
    ): void;

    onSearch(): void;

    onClearSearch(): void;

    onPageChange(
        page: number,
    ): void;

    onTabChange(
        tab: MusicTab,
    ): void;

    onAddSong(): void;
    onAddAlbum(): void;

    onEditSong(
        song:
        SongManagementItem,
    ): void;

    onEditAlbum(
        album:
        AlbumManagementItem,
    ): void;

    onDeleteSongs(
        ids: string[],
    ): void;

    onDeleteAlbums(
        ids: string[],
    ): void;
}) {
    const selectedIds =
        tab === "SONG"
            ? selectedSongIds
            : selectedAlbumIds;

    return (
        <>
            <PageTitle
                title="音乐管理"
                description="管理平台中的音乐和专辑"
                action={
                    tab === "SONG"
                        ? "新增音乐"
                        : "新增专辑"
                }
                onAction={
                    tab === "SONG"
                        ? onAddSong
                        : onAddAlbum
                }
            />

            <Tabs
                value={tab}
                options={[
                    {
                        value: "SONG",
                        label: "音乐管理",
                    },
                    {
                        value: "ALBUM",
                        label: "专辑管理",
                    },
                ]}
                onChange={
                    onTabChange
                }
            />

            <form
                className="management-search-bar"
                onSubmit={(event) => {
                    event.preventDefault();

                    onSearch();
                }}
            >
                <div className="management-search-box">
                    <input
                        value={
                            searchInput
                        }
                        placeholder={
                            tab === "SONG"
                                ? "搜索音乐 / 音乐人 / 专辑"
                                : "搜索专辑 / 音乐 / 音乐人"
                        }
                        onChange={(event) =>
                            onSearchInputChange(
                                event.target.value,
                            )
                        }
                    />

                    {searchInput && (
                        <button
                            type="button"
                            className="management-search-clear"
                            aria-label="清空搜索"
                            onClick={
                                onClearSearch
                            }
                        >
                            ×
                        </button>
                    )}
                </div>

                <button
                    type="submit"
                    className="management-secondary"
                    disabled={loading}
                >
                    搜索
                </button>

                <span className="management-search-total">
        共
                    {" "}
                    <strong>
            {total}
        </strong>
                    {" "}
                    条
    </span>
            </form>

            <BatchDeleteBar
                selectedCount={
                    selectedIds.length
                }
                itemLabel={
                    tab === "SONG"
                        ? "音乐"
                        : "专辑"
                }
                loading={loading}
                onDelete={() => {
                    if (
                        tab === "SONG"
                    ) {
                        onDeleteSongs(
                            selectedSongIds,
                        );
                    } else {
                        onDeleteAlbums(
                            selectedAlbumIds,
                        );
                    }
                }}
            />

            {loading ? (
                <LoadingBlock />
            ) : tab === "SONG" ? (
                <SongTable
                    rows={songs}
                    selectable
                    selectedIds={
                        selectedSongIds
                    }
                    onSelectedIdsChange={
                        onSelectedSongIdsChange
                    }
                    onEdit={
                        onEditSong
                    }
                    onDelete={(id) =>
                        onDeleteSongs(
                            [id],
                        )
                    }
                />
            ) : (
                <AlbumTable
                    rows={albums}
                    selectable
                    selectedIds={
                        selectedAlbumIds
                    }
                    onSelectedIdsChange={
                        onSelectedAlbumIdsChange
                    }
                    onEdit={
                        onEditAlbum
                    }
                    onDelete={(id) =>
                        onDeleteAlbums(
                            [id],
                        )
                    }
                />
            )}

            <Pagination
                page={page}
                total={total}
                totalPages={
                    totalPages
                }
                loading={loading}
                onChange={
                    onPageChange
                }
            />
        </>
    );
}

function ArtistSection({
                           artists,
                           loading,
                           onAddArtist,
                           onEditArtist,
                           onRefresh,
                           onToggleStatus,
                           onDeleteArtist,
                           selectedIds,
                           onSelectedIdsChange,
                           onBatchDelete,
                       }: {
    artists: ArtistManagementItem[];
    loading: boolean;
    onAddArtist(): void;
    onEditArtist(artist: ArtistManagementItem): void;
    onRefresh(): void;
    onToggleStatus(artist: ArtistManagementItem): void;
    onDeleteArtist(artist: ArtistManagementItem): void;
    selectedIds: string[];
    onSelectedIdsChange(ids: string[]): void;
    onBatchDelete(ids: string[]): void;
}) {
    const [keyword, setKeyword] =
        useState("");

    const [appliedKeyword, setAppliedKeyword] =
        useState("");

    const filteredArtists = useMemo(() => {
        const normalized =
            appliedKeyword.trim().toLowerCase();

        if (normalized.length === 0) {
            return artists;
        }

        return artists.filter((artist) =>
            [
                artist.name,
                artist.translatedName,
                artist.countryRegion,
                artist.style,
            ]
                .filter((value): value is string =>
                    Boolean(value),
                )
                .some((value) =>
                    value.toLowerCase()
                        .includes(normalized),
                ),
        );
    }, [appliedKeyword, artists]);

    function applySearch(): void {
        setAppliedKeyword(keyword.trim());
    }

    const visibleIds = filteredArtists.map(
        (artist) => artist.id,
    );

    const allVisibleSelected =
        visibleIds.length > 0
        && visibleIds.every((id) =>
            selectedIds.includes(id),
        );

    return (
        <>
            <PageTitle
                title="音乐人管理"
                description="管理音乐人资料、审核状态和上架状态"
                action="新增音乐人"
                onAction={onAddArtist}
            />

            <div className="artist-management-toolbar">
                <div className="artist-management-search">
                    <input
                        value={keyword}
                        placeholder="输入名称、地区或风格进行搜索"
                        onChange={(event) =>
                            setKeyword(event.target.value)
                        }
                        onKeyDown={(event) => {
                            if (event.key === "Enter") {
                                applySearch();
                            }
                        }}
                    />

                    <button
                        type="button"
                        className="management-secondary"
                        onClick={applySearch}
                    >
                        搜索
                    </button>

                    {appliedKeyword && (
                        <button
                            type="button"
                            className="management-link-button"
                            onClick={() => {
                                setKeyword("");
                                setAppliedKeyword("");
                            }}
                        >
                            清除
                        </button>
                    )}
                </div>

                <div className="artist-management-toolbar-actions">
                    <button
                        type="button"
                        className="management-danger-button"
                        disabled={
                            loading
                            || selectedIds.length === 0
                        }
                        onClick={() =>
                            onBatchDelete(selectedIds)
                        }
                    >
                        批量删除
                        {selectedIds.length > 0
                            ? `（${selectedIds.length}）`
                            : ""}
                    </button>

                    <button
                        type="button"
                        className="management-secondary"
                        disabled={loading}
                        onClick={onRefresh}
                    >
                        {loading
                            ? "正在刷新..."
                            : "刷新列表"}
                    </button>
                </div>
            </div>

            {loading ? (
                <LoadingBlock />
            ) : filteredArtists.length === 0 ? (
                <EmptyBlock />
            ) : (
                <div className="management-table-box">
                    <table className="management-table artist-management-table">
                        <thead>
                        <tr>
                            <th className="management-select-column">
                                <input
                                    type="checkbox"
                                    aria-label="选择当前所有音乐人"
                                    checked={allVisibleSelected}
                                    onChange={(event) => {
                                        if (event.target.checked) {
                                            onSelectedIdsChange(
                                                Array.from(
                                                    new Set([
                                                        ...selectedIds,
                                                        ...visibleIds,
                                                    ]),
                                                ),
                                            );
                                        } else {
                                            onSelectedIdsChange(
                                                selectedIds.filter(
                                                    (id) =>
                                                        !visibleIds.includes(id),
                                                ),
                                            );
                                        }
                                    }}
                                />
                            </th>
                            <th>头像</th>
                            <th>名称</th>
                            <th>国家/地区</th>
                            <th>风格</th>
                            <th>关注数</th>
                            <th>歌曲数</th>
                            <th>专辑数</th>
                            <th>审核状态</th>
                            <th>发布状态</th>
                            <th>操作</th>
                        </tr>
                        </thead>

                        <tbody>
                        {filteredArtists.map((artist) => (
                            <tr key={artist.id}>
                                <td className="management-select-column">
                                    <input
                                        type="checkbox"
                                        aria-label={`选择${artist.name}`}
                                        checked={
                                            selectedIds.includes(
                                                artist.id,
                                            )
                                        }
                                        onChange={() =>
                                            onSelectedIdsChange(
                                                toggleSelectedId(
                                                    selectedIds,
                                                    artist.id,
                                                ),
                                            )
                                        }
                                    />
                                </td>

                                <td>
                                    {artist.avatarUrl ? (
                                        <img
                                            src={artist.avatarUrl}
                                            alt={artist.name}
                                            className="artist-table-avatar"
                                        />
                                    ) : (
                                        <span className="artist-table-avatar fallback">
                                            {artist.name.slice(0, 1)}
                                        </span>
                                    )}
                                </td>

                                <td>
                                    <div className="management-entity-cell">
                                        <strong>{artist.name}</strong>
                                        {artist.translatedName && (
                                            <small>
                                                {artist.translatedName}
                                            </small>
                                        )}
                                        <small>ID：{artist.id}</small>
                                    </div>
                                </td>

                                <td>{artist.countryRegion ?? "-"}</td>

                                <td>
                                    {artist.style ? (
                                        <span className="artist-style-tag">
                                            {artist.style}
                                        </span>
                                    ) : "-"}
                                </td>

                                <td>
                                    {Number(
                                        artist.followerCount ?? 0,
                                    ).toLocaleString()}
                                </td>
                                <td>{artist.songCount ?? 0}</td>
                                <td>{artist.albumCount ?? 0}</td>

                                <td>
                                    <Status
                                        value={artist.auditStatus}
                                        label={auditLabel(artist.auditStatus)}
                                    />
                                </td>

                                <td>
                                    <Status
                                        value={artist.publishStatus}
                                        label={
                                            artist.publishStatus === "ONLINE"
                                                ? "已上架"
                                                : "已下架"
                                        }
                                    />
                                </td>

                                <td>
                                    <div className="artist-row-actions">
                                        <button
                                            type="button"
                                            className="edit"
                                            onClick={() => onEditArtist(artist)}
                                        >
                                            编辑
                                        </button>

                                        <button
                                            type="button"
                                            className={
                                                artist.publishStatus === "ONLINE"
                                                    ? "warning"
                                                    : "success"
                                            }
                                            onClick={() => onToggleStatus(artist)}
                                        >
                                            {artist.publishStatus === "ONLINE"
                                                ? "下架"
                                                : "恢复"}
                                        </button>

                                        <button
                                            type="button"
                                            className="danger"
                                            onClick={() => onDeleteArtist(artist)}
                                        >
                                            删除
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            <div className="artist-management-summary">
                共 {artists.length} 位音乐人
                {appliedKeyword
                    ? `，当前匹配 ${filteredArtists.length} 位`
                    : ""}
                {selectedIds.length > 0
                    ? `，已选择 ${selectedIds.length} 位`
                    : ""}
            </div>
        </>
    );
}

function UserSection({
                         users,
                         loading,
                     }: {
    users: UserManagementItem[];
    loading: boolean;
}) {
    return (
        <>
            <PageTitle
                title="用户管理"
                description="查看用户角色、实名和账号状态"
            />

            {loading ? (
                <LoadingBlock />
            ) : users.length === 0 ? (
                <EmptyBlock />
            ) : (
                <div className="management-table-box">
                    <table className="management-table">
                        <thead>
                        <tr>
                            <th>用户ID</th>
                            <th>用户名</th>
                            <th>手机号</th>
                            <th>角色</th>
                            <th>实名状态</th>
                            <th>账号状态</th>
                            <th>音乐人ID</th>
                        </tr>
                        </thead>

                        <tbody>
                        {users.map((item) => (
                            <tr key={item.id}>
                                <td>{item.id}</td>
                                <td>
                                    {item.username}
                                </td>
                                <td>
                                    {item.phoneMasked
                                        ?? "-"}
                                </td>
                                <td>{item.role}</td>
                                <td>
                                    <Status
                                        value={
                                            item.realNameStatus
                                        }
                                        label={
                                            realNameLabel(
                                                item.realNameStatus,
                                            )
                                        }
                                    />
                                </td>
                                <td>
                                    <Status
                                        value={
                                            item.accountStatus
                                        }
                                        label={
                                            item.accountStatus
                                            === "ENABLED"
                                                ? "正常"
                                                : "禁用"
                                        }
                                    />
                                </td>
                                <td>
                                    {item.artistId
                                        ?? "-"}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </>
    );
}

function AuditSection({
                          tab,
                          songs,
                          albums,
                          artists,
                          loading,
                          onTabChange,
                          onReview,
                      }: {
    tab: AuditTab;
    songs: SongManagementItem[];
    albums: AlbumManagementItem[];
    artists: ArtistManagementItem[];
    loading: boolean;
    onTabChange(tab: AuditTab): void;
    onReview(
        type: AuditTab,
        id: string,
        action: ReviewAction,
    ): void;
}) {
    return (
        <>
            <PageTitle
                title="审核管理"
                description="分类审核音乐、专辑和音乐人"
            />

            <Tabs
                value={tab}
                options={[
                    {
                        value: "SONG",
                        label: "音乐审核",
                    },
                    {
                        value: "ALBUM",
                        label: "专辑审核",
                    },
                    {
                        value: "ARTIST",
                        label: "音乐人审核",
                    },
                ]}
                onChange={onTabChange}
            />

            {loading ? (
                <LoadingBlock />
            ) : tab === "SONG" ? (
                <SongTable
                    rows={songs}
                    audit
                    onReview={(id, action) =>
                        onReview(
                            "SONG",
                            id,
                            action,
                        )
                    }
                />
            ) : tab === "ALBUM" ? (
                <AlbumTable
                    rows={albums}
                    audit
                    onReview={(id, action) =>
                        onReview(
                            "ALBUM",
                            id,
                            action,
                        )
                    }
                />
            ) : artists.length === 0 ? (
                <EmptyBlock />
            ) : (
                <div className="management-card-grid">
                    {artists.map((artist) => (
                        <article
                            key={artist.id}
                            className="management-artist-card"
                        >
                            <span className="management-artist-avatar">
                                {artist.name.slice(0, 1)}
                            </span>

                            <div className="management-artist-info">
                                <h3>{artist.name}</h3>

                                <p>
                                    申请用户：
                                    {artist.ownerUserId
                                        ?? "未绑定"}
                                </p>

                                <p>
                                    地区：
                                    {artist.countryRegion
                                        ?? "未填写"}
                                </p>
                            </div>

                            <Status
                                value={
                                    artist.auditStatus
                                }
                                label={auditLabel(
                                    artist.auditStatus,
                                )}
                            />

                            <AuditActions
                                id={artist.id}
                                status={
                                    artist.auditStatus
                                }
                                onReview={(id, action) =>
                                    onReview(
                                        "ARTIST",
                                        id,
                                        action,
                                    )
                                }
                                card
                            />
                        </article>
                    ))}
                </div>
            )}
        </>
    );
}

function SongTable({
                       rows,
                       audit = false,
                       selectable = false,
                       selectedIds = [],
                       onSelectedIdsChange,
                       onEdit,
                       onDelete,
                       onReview,
                   }: {
    rows: SongManagementItem[];
    audit?: boolean;
    selectable?: boolean;
    selectedIds?: string[];
    onSelectedIdsChange?(ids: string[]): void;
    onEdit?(song: SongManagementItem): void;
    onDelete?(id: string): void;
    onReview?(
        id: string,
        action: ReviewAction,
    ): void;
}) {
    if (rows.length === 0) {
        return <EmptyBlock />;
    }

    const allSelected =
        selectable
        && rows.length > 0
        && rows.every((item) =>
            selectedIds.includes(item.id),
        );

    return (
        <div className="management-table-box">
            <table className="management-table music-management-table">
                <thead>
                <tr>
                    {selectable && (
                        <th className="management-select-column">
                            <input
                                type="checkbox"
                                aria-label="选择全部音乐"
                                checked={allSelected}
                                onChange={(event) => {
                                    if (!onSelectedIdsChange) {
                                        return;
                                    }

                                    onSelectedIdsChange(
                                        event.target.checked
                                            ? rows.map((item) => item.id)
                                            : [],
                                    );
                                }}
                            />
                        </th>
                    )}
                    <th>音乐名</th>
                    <th>音乐人</th>
                    <th>专辑</th>
                    <th>时长</th>
                    <th>风格</th>
                    <th>审核状态</th>
                    <th>发布状态</th>
                    {(audit || onEdit || onDelete) && <th>操作</th>}
                </tr>
                </thead>

                <tbody>
                {rows.map((item) => (
                    <tr key={item.id}>
                        {selectable && (
                            <td className="management-select-column">
                                <input
                                    type="checkbox"
                                    aria-label={`选择${item.name}`}
                                    checked={selectedIds.includes(item.id)}
                                    onChange={() => {
                                        onSelectedIdsChange?.(
                                            toggleSelectedId(
                                                selectedIds,
                                                item.id,
                                            ),
                                        );
                                    }}
                                />
                            </td>
                        )}

                        <td>
                            <EntityCell
                                name={item.name}
                                idText={item.id}
                            />
                        </td>

                        <td>
                            <EntityCell
                                name={item.artistName}
                                idText={
                                    item.artistIds.length > 0
                                        ? item.artistIds.join(" / ")
                                        : null
                                }
                            />
                        </td>

                        <td>
                            {item.albumName ? (
                                <EntityCell
                                    name={item.albumName}
                                    idText={item.albumId}
                                />
                            ) : (
                                <span className="management-muted">
                                    无专辑
                                </span>
                            )}
                        </td>

                        <td>
                            {item.durationSeconds}秒
                        </td>
                        <td>{item.style ?? "-"}</td>
                        <td>
                            <Status
                                value={item.auditStatus}
                                label={auditLabel(
                                    item.auditStatus,
                                )}
                            />
                        </td>
                        <td>
                            <Status
                                value={item.publishStatus}
                                label={
                                    item.publishStatus === "ONLINE"
                                        ? "已上架"
                                        : "未上架"
                                }
                            />
                        </td>

                        {audit && onReview ? (
                            <td>
                                <AuditActions
                                    id={item.id}
                                    status={item.auditStatus}
                                    onReview={onReview}
                                />
                            </td>
                        ) : (onEdit || onDelete) ? (
                            <td>
                                <div className="management-table-actions">
                                    {onEdit && (
                                        <button
                                            type="button"
                                            className="management-table-edit"
                                            onClick={() =>
                                                onEdit(item)
                                            }
                                        >
                                            编辑
                                        </button>
                                    )}

                                    {onDelete && (
                                        <button
                                            type="button"
                                            className="management-table-danger"
                                            onClick={() =>
                                                onDelete(item.id)
                                            }
                                        >
                                            删除
                                        </button>
                                    )}
                                </div>
                            </td>
                        ) : null}
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}

function AlbumTable({
                        rows,
                        audit = false,
                        selectable = false,
                        selectedIds = [],
                        onSelectedIdsChange,
                        onEdit,
                        onDelete,
                        onReview,
                    }: {
    rows: AlbumManagementItem[];
    audit?: boolean;
    selectable?: boolean;
    selectedIds?: string[];
    onSelectedIdsChange?(ids: string[]): void;
    onEdit?(album: AlbumManagementItem): void;
    onDelete?(id: string): void;
    onReview?(
        id: string,
        action: ReviewAction,
    ): void;
}) {
    if (rows.length === 0) {
        return <EmptyBlock />;
    }

    const allSelected =
        selectable
        && rows.length > 0
        && rows.every((item) =>
            selectedIds.includes(item.id),
        );

    return (
        <div className="management-table-box">
            <table className="management-table album-management-table">
                <thead>
                <tr>
                    {selectable && (
                        <th className="management-select-column">
                            <input
                                type="checkbox"
                                aria-label="选择全部专辑"
                                checked={allSelected}
                                onChange={(event) => {
                                    if (!onSelectedIdsChange) {
                                        return;
                                    }

                                    onSelectedIdsChange(
                                        event.target.checked
                                            ? rows.map((item) => item.id)
                                            : [],
                                    );
                                }}
                            />
                        </th>
                    )}
                    <th>专辑名</th>
                    <th>音乐人</th>
                    <th>风格</th>
                    <th>发行日期</th>
                    <th>审核状态</th>
                    {(audit || onEdit || onDelete) && <th>操作</th>}
                </tr>
                </thead>

                <tbody>
                {rows.map((item) => (
                    <tr key={item.id}>
                        {selectable && (
                            <td className="management-select-column">
                                <input
                                    type="checkbox"
                                    aria-label={`选择${item.name}`}
                                    checked={selectedIds.includes(item.id)}
                                    onChange={() => {
                                        onSelectedIdsChange?.(
                                            toggleSelectedId(
                                                selectedIds,
                                                item.id,
                                            ),
                                        );
                                    }}
                                />
                            </td>
                        )}

                        <td>
                            <EntityCell
                                name={item.name}
                                idText={item.id}
                            />
                        </td>
                        <td>
                            <EntityCell
                                name={item.artistName}
                                idText={
                                    item.artistIds.length > 0
                                        ? item.artistIds.join(" / ")
                                        : null
                                }
                            />
                        </td>
                        <td>{item.style ?? "-"}</td>
                        <td>{item.releaseDate ?? "-"}</td>
                        <td>
                            <Status
                                value={item.auditStatus}
                                label={auditLabel(
                                    item.auditStatus,
                                )}
                            />
                        </td>

                        {audit && onReview ? (
                            <td>
                                <AuditActions
                                    id={item.id}
                                    status={item.auditStatus}
                                    onReview={onReview}
                                />
                            </td>
                        ) : (onEdit || onDelete) ? (
                            <td>
                                <div className="management-table-actions">
                                    {onEdit && (
                                        <button
                                            type="button"
                                            className="management-table-edit"
                                            onClick={() =>
                                                onEdit(item)
                                            }
                                        >
                                            编辑
                                        </button>
                                    )}

                                    {onDelete && (
                                        <button
                                            type="button"
                                            className="management-table-danger"
                                            onClick={() =>
                                                onDelete(item.id)
                                            }
                                        >
                                            删除
                                        </button>
                                    )}
                                </div>
                            </td>
                        ) : null}
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}

function Pagination({
                        page,
                        total,
                        totalPages,
                        loading,
                        onChange,
                    }: {
    page: number;
    total: number;
    totalPages: number;
    loading: boolean;

    onChange(
        page: number,
    ): void;
}) {
    /*
     * 没数据时不显示分页按钮。
     */
    if (total === 0) {
        return null;
    }

    return (
        <div className="management-pagination">
            <span className="management-pagination-total">
                共
                {" "}
                <strong>
                    {total}
                </strong>
                {" "}
                条
            </span>

            <div className="management-pagination-controls">
                <button
                    type="button"
                    disabled={
                        loading
                        || page <= 1
                    }
                    onClick={() =>
                        onChange(
                            page - 1,
                        )
                    }
                >
                    上一页
                </button>

                <span>
                    第
                    {" "}
                    <strong>
                        {page}
                    </strong>
                    {" "}
                    /
                    {" "}
                    {Math.max(
                        totalPages,
                        1,
                    )}
                    {" "}
                    页
                </span>

                <button
                    type="button"
                    disabled={
                        loading
                        || page
                        >= totalPages
                    }
                    onClick={() =>
                        onChange(
                            page + 1,
                        )
                    }
                >
                    下一页
                </button>
            </div>
        </div>
    );
}

function BatchDeleteBar({
                            selectedCount,
                            itemLabel,
                            loading,
                            onDelete,
                        }: {
    selectedCount: number;
    itemLabel: string;
    loading: boolean;
    onDelete(): void;
}) {
    return (
        <div className="management-batch-bar">
            <span>
                已选择
                <strong>{selectedCount}</strong>
                条{itemLabel}
            </span>

            <button
                type="button"
                className="management-danger-button"
                disabled={
                    loading
                    || selectedCount === 0
                }
                onClick={onDelete}
            >
                批量删除
            </button>
        </div>
    );
}

function EntityCell({
                        name,
                        idText,
                    }: {
    name: string;
    idText: string | null;
}) {
    return (
        <div className="management-entity-cell">
            <strong>{name}</strong>
            {idText && (
                <small>ID：{idText}</small>
            )}
        </div>
    );
}

function toggleSelectedId(
    selectedIds: string[],
    id: string,
): string[] {
    if (selectedIds.includes(id)) {
        return selectedIds.filter(
            (selectedId) => selectedId !== id,
        );
    }

    return [
        ...selectedIds,
        id,
    ];
}

function AuditActions({
                          id,
                          status,
                          onReview,
                          card = false,
                      }: {
    id: string;
    status: string;
    onReview(
        id: string,
        action: ReviewAction,
    ): void;
    card?: boolean;
}) {
    if (status === "REJECTED") {
        return (
            <span className="management-action-note">
                已驳回，等待内容修改后重新提交
            </span>
        );
    }

    return (
        <div
            className={
                card
                    ? "management-actions audit"
                    : "management-row-actions"
            }
        >
            {status === "PENDING" && (
                <>
                    <button
                        type="button"
                        className="success"
                        onClick={() =>
                            onReview(
                                id,
                                "APPROVE",
                            )
                        }
                    >
                        通过
                    </button>

                    <button
                        type="button"
                        className="danger"
                        onClick={() =>
                            onReview(
                                id,
                                "REJECT",
                            )
                        }
                    >
                        驳回
                    </button>
                </>
            )}

            {status === "APPROVED" && (
                <button
                    type="button"
                    className="danger"
                    onClick={() =>
                        onReview(
                            id,
                            "REVOKE",
                        )
                    }
                >
                    撤销审核
                </button>
            )}
        </div>
    );
}

function Status({
                    value,
                    label,
                }: {
    value: string;
    label: string;
}) {
    return (
        <span
            className={
                `management-status ${
                    statusClass(value)
                }`
            }
        >
            {label}
        </span>
    );
}

function LoadingBlock() {
    return (
        <div className="management-state">
            正在加载数据...
        </div>
    );
}

function EmptyBlock() {
    return (
        <div className="management-state">
            暂无数据
        </div>
    );
}
