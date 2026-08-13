import {
    useEffect,
    useRef,
    useState,
    type ChangeEvent,
    type FormEvent,
} from "react";

import {
    createSong,
    updateSong,
    searchAlbums,
    searchArtists,
    type AlbumSearchItem,
    type ArtistSearchItem,
    type SongManagementItem,
} from "../../api/management";

import { uploadFile } from "../../api/file";
import { getApiError } from "../../auth/api/http";
import ArtistCreateDrawer from "./ArtistCreateDrawer";

interface SongCreateDrawerProps {
    open: boolean;

    /**
     * null = 新增模式；
     * 非 null = 编辑模式。
     */
    song?: SongManagementItem | null;

    onClose(): void;
    onCreated(): Promise<void> | void;
}

interface SongFormState {
    name: string;
    introduction: string;
}

const initialForm: SongFormState = {
    name: "",
    introduction: "",
};

function readAudioDuration(
    file: File,
): Promise<number> {
    return new Promise(
        (resolve, reject) => {
            const url =
                URL.createObjectURL(file);

            const audio =
                document.createElement("audio");

            audio.preload = "metadata";

            function cleanup(): void {
                URL.revokeObjectURL(url);
                audio.removeAttribute("src");
                audio.load();
            }

            audio.onloadedmetadata = () => {
                const duration =
                    Math.round(audio.duration);

                cleanup();

                if (
                    !Number.isFinite(duration)
                    || duration <= 0
                    || duration > 86_400
                ) {
                    reject(
                        new Error(
                            "无法读取有效的音乐时长",
                        ),
                    );
                    return;
                }

                resolve(duration);
            };

            audio.onerror = () => {
                cleanup();

                reject(
                    new Error(
                        "无法读取音乐文件信息",
                    ),
                );
            };

            audio.src = url;
        },
    );
}

function formatDuration(
    seconds: number,
): string {
    const minutes =
        Math.floor(seconds / 60);

    const remainingSeconds =
        seconds % 60;

    return `${minutes}:${String(
        remainingSeconds,
    ).padStart(2, "0")}`;
}

function getAlbumStyle(
    album: AlbumSearchItem | null,
): string | null {
    if (album === null) {
        return null;
    }

    const value = album.style;

    return typeof value === "string"
    && value.trim().length > 0
        ? value.trim()
        : null;
}

function buildInitialArtists(
    artistIds: string[],
    artistName: string,
): ArtistSearchItem[] {
    const names = artistName
        .split(" / ")
        .map((item) => item.trim());

    return artistIds.map(
        (id, index) => ({
            id,
            name:
                names[index]
                ?? `音乐人 ${id}`,
            translatedNames: [],
            avatarUrl: null,
            countryRegion: null,

            /*
             * 管理列表没有逐个返回音乐人的审核状态。
             * 这里只负责编辑表单回显；
             * Provider 保存时仍会重新校验真实审核状态。
             */
            auditStatus: "APPROVED",
        }),
    );
}

function buildInitialAlbum(
    song: SongManagementItem,
): AlbumSearchItem | null {
    if (
        song.albumId === null
        || song.albumName === null
    ) {
        return null;
    }

    return {
        id: song.albumId,
        name: song.albumName,
        artistName: "",
        coverUrl: song.coverUrl,
        style: song.style,
    };
}

export default function SongCreateDrawer({
                                             open,
                                             song = null,
                                             onClose,
                                             onCreated,
                                         }: SongCreateDrawerProps) {
    const editing =
        song !== null;

    const [form, setForm] =
        useState<SongFormState>(
            initialForm,
        );

    const [
        selectedArtists,
        setSelectedArtists,
    ] = useState<ArtistSearchItem[]>([]);

    const [
        selectedAlbum,
        setSelectedAlbum,
    ] = useState<AlbumSearchItem | null>(
        null,
    );

    const [
        artistDrawerOpen,
        setArtistDrawerOpen,
    ] = useState(false);

    const [audioFile, setAudioFile] =
        useState<File | null>(null);

    const [
        durationSeconds,
        setDurationSeconds,
    ] = useState<number | null>(null);

    const [submitting, setSubmitting] =
        useState(false);

    const [uploadText, setUploadText] =
        useState("");

    const [error, setError] =
        useState("");

    const audioReadIdRef =
        useRef(0);

    /*
     * 每次打开抽屉时根据新增/编辑模式初始化。
     */
    useEffect(() => {
        if (!open) {
            return;
        }

        audioReadIdRef.current += 1;
        setAudioFile(null);
        setUploadText("");
        setError("");
        setArtistDrawerOpen(false);

        if (song === null) {
            setForm(initialForm);
            setSelectedArtists([]);
            setSelectedAlbum(null);
            setDurationSeconds(null);
            return;
        }

        setForm({
            name: song.name,
            introduction:
                song.introduction ?? "",
        });

        setSelectedArtists(
            buildInitialArtists(
                song.artistIds ?? [],
                song.artistName,
            ),
        );

        setSelectedAlbum(
            buildInitialAlbum(song),
        );

        setDurationSeconds(
            Number(song.durationSeconds) > 0
                ? Number(
                    song.durationSeconds,
                )
                : null,
        );
    }, [
        open,
        song,
    ]);

    useEffect(() => {
        if (!open) {
            return;
        }

        function handleEscape(
            event: KeyboardEvent,
        ) {
            if (
                event.key === "Escape"
                && !submitting
                && !artistDrawerOpen
            ) {
                onClose();
            }
        }

        window.addEventListener(
            "keydown",
            handleEscape,
        );

        return () => {
            window.removeEventListener(
                "keydown",
                handleEscape,
            );
        };
    }, [
        artistDrawerOpen,
        onClose,
        open,
        submitting,
    ]);

    function reset(): void {
        audioReadIdRef.current += 1;
        setForm(initialForm);
        setSelectedArtists([]);
        setSelectedAlbum(null);
        setArtistDrawerOpen(false);
        setAudioFile(null);
        setDurationSeconds(null);
        setUploadText("");
        setError("");
    }

    function close(): void {
        if (submitting) {
            return;
        }

        reset();
        onClose();
    }

    function updateField(
        field: keyof SongFormState,
        value: string,
    ): void {
        setForm((current) => ({
            ...current,
            [field]: value,
        }));
    }

    function handleArtistCreated(
        artist: ArtistSearchItem,
    ): void {
        setSelectedArtists(
            (current) => {
                if (
                    current.some(
                        (item) =>
                            item.id === artist.id,
                    )
                ) {
                    return current;
                }

                return [
                    ...current,
                    artist,
                ];
            },
        );
    }

    function handleAudioFileChange(
        file: File | null,
    ): void {
        setError("");

        const requestId =
            ++audioReadIdRef.current;

        if (file === null) {
            setAudioFile(null);

            /*
             * 编辑模式取消新文件时恢复旧时长。
             */
            setDurationSeconds(
                song?.durationSeconds
                ?? null,
            );
            return;
        }

        setAudioFile(file);
        setDurationSeconds(null);

        void readAudioDuration(file)
            .then((duration) => {
                if (
                    requestId
                    !== audioReadIdRef.current
                ) {
                    return;
                }

                setDurationSeconds(
                    duration,
                );
            })
            .catch(() => {
                if (
                    requestId
                    !== audioReadIdRef.current
                ) {
                    return;
                }

                setAudioFile(null);

                setDurationSeconds(
                    song?.durationSeconds
                    ?? null,
                );

                setError(
                    "无法读取该音乐文件的时长，请重新选择音频文件",
                );
            });
    }

    async function handleSubmit(
        event: FormEvent<HTMLFormElement>,
    ): Promise<void> {
        event.preventDefault();
        setError("");

        const name =
            form.name.trim();

        if (name.length === 0) {
            setError("请输入音乐名称");
            return;
        }

        if (
            selectedArtists.length === 0
        ) {
            setError(
                "至少选择一位音乐人",
            );
            return;
        }

        if (
            durationSeconds === null
            || durationSeconds <= 0
        ) {
            setError(
                "音乐时长尚未读取完成",
            );
            return;
        }

        if (
            !editing
            && audioFile === null
        ) {
            setError(
                "请选择需要上传的音乐文件",
            );
            return;
        }

        let audioUrl =
            song?.audioUrl ?? null;

        if (
            editing
            && audioFile === null
            && !audioUrl
        ) {
            setError(
                "原音乐文件不存在，请重新上传音乐文件",
            );
            return;
        }

        setSubmitting(true);

        try {
            if (audioFile !== null) {
                setUploadText(
                    editing
                        ? "正在上传新的音乐文件..."
                        : "正在上传音乐文件...",
                );

                const uploadedAudio =
                    await uploadFile(
                        "audio",
                        audioFile,
                        (percent) =>
                            setUploadText(
                                `正在上传音乐文件 ${percent}%`,
                            ),
                    );

                audioUrl =
                    uploadedAudio.url;
            }

            if (!audioUrl) {
                throw new Error(
                    "音乐文件地址不存在",
                );
            }

            setUploadText(
                editing
                    ? "正在保存音乐修改..."
                    : "正在保存音乐信息...",
            );

            const common = {
                name,

                albumId:
                    selectedAlbum?.id
                    ?? null,

                artistIds:
                    selectedArtists.map(
                        (artist) =>
                            artist.id,
                    ),

                durationSeconds,

                introduction:
                    form.introduction
                        .trim()
                    || null,

                audioUrl,
            };

            if (
                editing
                && song !== null
            ) {
                await updateSong(
                    song.id,
                    common,
                );
            } else {
                await createSong({
                    ...common,

                    /*
                     * 前端仅用于显示一致性；
                     * Provider 会再次从 album_tb
                     * 读取最终风格与封面。
                     */
                    style:
                        getAlbumStyle(
                            selectedAlbum,
                        ),

                    coverUrl:
                        selectedAlbum
                            ?.coverUrl
                        ?? null,
                });
            }

            await onCreated();
            reset();
            onClose();
        } catch (requestError) {
            setError(
                getApiError(
                    requestError,
                ).message,
            );
        } finally {
            setSubmitting(false);
            setUploadText("");
        }
    }

    if (!open) {
        return null;
    }

    const hasPendingArtist =
        selectedArtists.some(
            (artist) =>
                artist.auditStatus
                === "PENDING",
        );

    const selectedAlbumStyle =
        getAlbumStyle(
            selectedAlbum,
        );

    return (
        <div
            className="song-drawer-layer"
            role="presentation"
        >
            <button
                type="button"
                className="song-drawer-mask"
                aria-label={
                    editing
                        ? "关闭编辑音乐表单"
                        : "关闭新增音乐表单"
                }
                onClick={close}
            />

            <aside
                className="song-drawer"
                role="dialog"
                aria-modal="true"
                aria-labelledby="song-drawer-title"
            >
                <header className="song-drawer-header">
                    <div>
                        <h2 id="song-drawer-title">
                            {editing
                                ? "编辑音乐"
                                : "新增音乐"}
                        </h2>

                        <p>
                            {editing
                                ? "修改后将按当前账号角色重新进入审核流程"
                                : "输入关键词后再搜索音乐人和专辑"}
                        </p>
                    </div>

                    <button
                        type="button"
                        className="song-drawer-close"
                        disabled={submitting}
                        onClick={close}
                    >
                        ×
                    </button>
                </header>

                <form
                    className="song-form"
                    onSubmit={(event) => {
                        void handleSubmit(event);
                    }}
                >
                    <label className="song-form-field">
                        <span>
                            音乐名称
                            <b>*</b>
                        </span>

                        <input
                            value={form.name}
                            maxLength={128}
                            placeholder="请输入音乐名称"
                            disabled={submitting}
                            onChange={(
                                event: ChangeEvent<HTMLInputElement>,
                            ) =>
                                updateField(
                                    "name",
                                    event.target.value,
                                )
                            }
                        />
                    </label>

                    <ArtistSearchField
                        selected={selectedArtists}
                        disabled={submitting}
                        onChange={setSelectedArtists}
                        onCreate={() =>
                            setArtistDrawerOpen(true)
                        }
                    />

                    {hasPendingArtist && (
                        <div className="song-dependency-note">
                            已选择待审核音乐人，因此本次保存后音乐也会进入待审核状态；
                            音乐人审核通过后才能通过音乐审核。
                        </div>
                    )}

                    <AlbumSearchField
                        selected={selectedAlbum}
                        disabled={submitting}
                        onChange={setSelectedAlbum}
                    />

                    {selectedAlbum !== null && (
                        <div className="song-dependency-note">
                            歌曲封面和音乐风格由专辑自动继承
                            {selectedAlbumStyle
                                ? `：${selectedAlbumStyle}`
                                : ""}
                        </div>
                    )}

                    <label className="song-form-field">
                        <span>音乐简介</span>

                        <textarea
                            value={form.introduction}
                            maxLength={1000}
                            rows={4}
                            placeholder="请输入音乐简介"
                            disabled={submitting}
                            onChange={(
                                event: ChangeEvent<HTMLTextAreaElement>,
                            ) =>
                                updateField(
                                    "introduction",
                                    event.target.value,
                                )
                            }
                        />
                    </label>

                    <label className="song-form-field">
                        <span>
                            音乐文件
                            {!editing && <b>*</b>}
                        </span>

                        <input
                            type="file"
                            accept=".mp3,.wav,.flac,.m4a,.ogg,audio/*"
                            disabled={submitting}
                            onChange={(
                                event: ChangeEvent<HTMLInputElement>,
                            ) =>
                                handleAudioFileChange(
                                    event.target.files?.[0]
                                    ?? null,
                                )
                            }
                        />

                        <small>
                            {editing
                                ? "不重新选择文件则保留原音乐文件；选择新文件后会自动重新读取时长"
                                : "支持 mp3、wav、flac、m4a、ogg，最大300MB"}
                        </small>

                        {durationSeconds !== null && (
                            <small className="audio-duration-result">
                                当前时长：
                                {formatDuration(
                                    durationSeconds,
                                )}
                                （{durationSeconds} 秒）
                            </small>
                        )}
                    </label>

                    {uploadText.length > 0 && (
                        <div className="song-form-progress">
                            {uploadText}
                        </div>
                    )}

                    {error.length > 0 && (
                        <div className="song-form-error">
                            {error}
                        </div>
                    )}

                    <footer className="song-form-actions">
                        <button
                            type="button"
                            className="secondary"
                            disabled={submitting}
                            onClick={close}
                        >
                            取消
                        </button>

                        <button
                            type="submit"
                            className="primary"
                            disabled={submitting}
                        >
                            {submitting
                                ? "正在提交..."
                                : editing
                                    ? "保存修改"
                                    : "确认新增"}
                        </button>
                    </footer>
                </form>
            </aside>

            <ArtistCreateDrawer
                open={artistDrawerOpen}
                onClose={() =>
                    setArtistDrawerOpen(false)
                }
                onCreated={handleArtistCreated}
            />
        </div>
    );
}

function ArtistSearchField({
                               selected,
                               disabled,
                               onChange,
                               onCreate,
                           }: {
    selected: ArtistSearchItem[];
    disabled: boolean;
    onChange(items: ArtistSearchItem[]): void;
    onCreate(): void;
}) {
    const [keyword, setKeyword] =
        useState("");

    const [results, setResults] =
        useState<ArtistSearchItem[]>([]);

    const [loading, setLoading] =
        useState(false);

    const [searchError, setSearchError] =
        useState("");

    const requestIdRef = useRef(0);

    useEffect(() => {
        const normalized = keyword.trim();

        if (normalized.length === 0) {
            requestIdRef.current += 1;
            setResults([]);
            setLoading(false);
            setSearchError("");
            return;
        }

        const requestId =
            ++requestIdRef.current;

        const timer = window.setTimeout(
            () => {
                setLoading(true);
                setSearchError("");

                void searchArtists(normalized)
                    .then((items) => {
                        if (
                            requestId
                            !== requestIdRef.current
                        ) {
                            return;
                        }

                        setResults(items);
                    })
                    .catch((error) => {
                        if (
                            requestId
                            !== requestIdRef.current
                        ) {
                            return;
                        }

                        setResults([]);
                        setSearchError(
                            getApiError(error).message,
                        );
                    })
                    .finally(() => {
                        if (
                            requestId
                            === requestIdRef.current
                        ) {
                            setLoading(false);
                        }
                    });
            },
            250,
        );

        return () => {
            window.clearTimeout(timer);
        };
    }, [keyword]);

    function addArtist(
        artist: ArtistSearchItem,
    ): void {
        if (
            selected.some(
                (item) => item.id === artist.id,
            )
        ) {
            setKeyword("");
            setResults([]);
            return;
        }

        onChange([
            ...selected,
            artist,
        ]);

        setKeyword("");
        setResults([]);
    }

    function removeArtist(
        id: string,
    ): void {
        onChange(
            selected.filter(
                (artist) => artist.id !== id,
            ),
        );
    }

    const showDropdown =
        keyword.trim().length > 0;

    return (
        <div className="song-form-field">
            <span>
                音乐人 / 乐队（多选）
                <b>*</b>
            </span>

            {selected.length > 0 && (
                <div className="entity-selected-list">
                    {selected.map((artist) => (
                        <span
                            key={artist.id}
                            className="entity-selected-chip"
                        >
                            {artist.avatarUrl ? (
                                <img
                                    src={artist.avatarUrl}
                                    alt=""
                                />
                            ) : (
                                <i>
                                    {artist.name.slice(0, 1)}
                                </i>
                            )}

                            <span>{artist.name}</span>

                            {artist.auditStatus === "PENDING" && (
                                <em>待审核</em>
                            )}

                            <button
                                type="button"
                                aria-label={`移除${artist.name}`}
                                disabled={disabled}
                                onClick={() =>
                                    removeArtist(artist.id)
                                }
                            >
                                ×
                            </button>
                        </span>
                    ))}
                </div>
            )}

            <div className="entity-search-wrap">
                <input
                    value={keyword}
                    placeholder="输入音乐人姓名后搜索"
                    autoComplete="off"
                    disabled={disabled}
                    onChange={(event) =>
                        setKeyword(
                            event.target.value,
                        )
                    }
                />

                {showDropdown && (
                    <div className="entity-search-dropdown">
                        {loading ? (
                            <div className="entity-search-state">
                                正在搜索...
                            </div>
                        ) : searchError ? (
                            <div className="entity-search-state error">
                                {searchError}
                            </div>
                        ) : results.length === 0 ? (
                            <div className="entity-search-state">
                                未找到匹配的音乐人
                            </div>
                        ) : (
                            results.map((artist) => (
                                <button
                                    key={artist.id}
                                    type="button"
                                    className="entity-search-option"
                                    onClick={() =>
                                        addArtist(artist)
                                    }
                                >
                                    {artist.avatarUrl ? (
                                        <img
                                            src={artist.avatarUrl}
                                            alt=""
                                        />
                                    ) : (
                                        <span className="entity-option-avatar">
                                            {artist.name.slice(0, 1)}
                                        </span>
                                    )}

                                    <span className="entity-option-main">
                                        <strong>
                                            {artist.name}
                                        </strong>

                                        <small>
                                            {[
                                                    artist.translatedNames
                                                        .length > 0
                                                        ? artist.translatedNames
                                                            .join(" / ")
                                                        : null,
                                                    artist.countryRegion,
                                                ]
                                                    .filter(Boolean)
                                                    .join(" · ")
                                                || "音乐人"}
                                        </small>
                                    </span>

                                    <span
                                        className={
                                            artist.auditStatus === "PENDING"
                                                ? "entity-audit-badge pending"
                                                : "entity-audit-badge approved"
                                        }
                                    >
                                        {artist.auditStatus === "PENDING"
                                            ? "待审核"
                                            : "已通过"}
                                    </span>
                                </button>
                            ))
                        )}
                    </div>
                )}
            </div>

            <div className="entity-create-hint">
                未找到音乐人？
                <button
                    type="button"
                    disabled={disabled}
                    onClick={onCreate}
                >
                    + 新增音乐人
                </button>
            </div>
        </div>
    );
}

function AlbumSearchField({
                              selected,
                              disabled,
                              onChange,
                          }: {
    selected: AlbumSearchItem | null;
    disabled: boolean;
    onChange(item: AlbumSearchItem | null): void;
}) {
    const [keyword, setKeyword] =
        useState("");

    const [results, setResults] =
        useState<AlbumSearchItem[]>([]);

    const [loading, setLoading] =
        useState(false);

    const [searchError, setSearchError] =
        useState("");

    const requestIdRef = useRef(0);

    useEffect(() => {
        const normalized = keyword.trim();

        if (normalized.length === 0) {
            requestIdRef.current += 1;
            setResults([]);
            setLoading(false);
            setSearchError("");
            return;
        }

        const requestId =
            ++requestIdRef.current;

        const timer = window.setTimeout(
            () => {
                setLoading(true);
                setSearchError("");

                void searchAlbums(normalized)
                    .then((items) => {
                        if (
                            requestId
                            === requestIdRef.current
                        ) {
                            setResults(items);
                        }
                    })
                    .catch((error) => {
                        if (
                            requestId
                            === requestIdRef.current
                        ) {
                            setResults([]);
                            setSearchError(
                                getApiError(error).message,
                            );
                        }
                    })
                    .finally(() => {
                        if (
                            requestId
                            === requestIdRef.current
                        ) {
                            setLoading(false);
                        }
                    });
            },
            250,
        );

        return () => {
            window.clearTimeout(timer);
        };
    }, [keyword]);

    const showDropdown =
        selected === null
        && keyword.trim().length > 0;

    return (
        <div className="song-form-field">
            <span>所属专辑</span>

            {selected !== null ? (
                <div className="album-selected-card">
                    {selected.coverUrl ? (
                        <img
                            src={selected.coverUrl}
                            alt=""
                        />
                    ) : (
                        <span className="entity-option-avatar">
                            专
                        </span>
                    )}

                    <span className="entity-option-main">
                        <strong>{selected.name}</strong>
                        <small>{selected.artistName}</small>
                    </span>

                    <button
                        type="button"
                        disabled={disabled}
                        onClick={() => onChange(null)}
                    >
                        ×
                    </button>
                </div>
            ) : (
                <div className="entity-search-wrap">
                    <input
                        value={keyword}
                        placeholder="输入专辑名称后搜索；留空表示无所属专辑"
                        autoComplete="off"
                        disabled={disabled}
                        onChange={(event) =>
                            setKeyword(
                                event.target.value,
                            )
                        }
                    />

                    {showDropdown && (
                        <div className="entity-search-dropdown">
                            {loading ? (
                                <div className="entity-search-state">
                                    正在搜索...
                                </div>
                            ) : searchError ? (
                                <div className="entity-search-state error">
                                    {searchError}
                                </div>
                            ) : results.length === 0 ? (
                                <div className="entity-search-state">
                                    未找到匹配的已审核专辑
                                </div>
                            ) : (
                                results.map((album) => (
                                    <button
                                        key={album.id}
                                        type="button"
                                        className="entity-search-option"
                                        onClick={() => {
                                            onChange(album);
                                            setKeyword("");
                                            setResults([]);
                                        }}
                                    >
                                        {album.coverUrl ? (
                                            <img
                                                src={album.coverUrl}
                                                alt=""
                                            />
                                        ) : (
                                            <span className="entity-option-avatar">
                                                专
                                            </span>
                                        )}

                                        <span className="entity-option-main">
                                            <strong>
                                                {album.name}
                                            </strong>
                                            <small>
                                                {album.artistName}
                                            </small>
                                        </span>
                                    </button>
                                ))
                            )}
                        </div>
                    )}
                </div>
            )}

            <small>
                未输入关键词时不会加载或展示任何专辑选项
            </small>
        </div>
    );
}
