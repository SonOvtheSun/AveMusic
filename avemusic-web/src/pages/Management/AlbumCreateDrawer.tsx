import {
    useEffect,
    useRef,
    useState,
    type ChangeEvent,
    type FormEvent,
} from "react";

import { createPortal } from "react-dom";

import {
    createAlbumWithSongs,
    updateAlbum,
    searchArtists,
    type AlbumManagementItem,
    type ArtistSearchItem,
} from "../../api/management";

import { uploadFile } from "../../api/file";
import { getApiError } from "../../auth/api/http";
import "../../styles/Management/AlbumCreateDrawer.css";

interface AlbumCreateDrawerProps {
    open: boolean;

    /**
     * null = 新增专辑；
     * 非 null = 编辑专辑。
     */
    album?: AlbumManagementItem | null;

    onClose(): void;
    onCreated(): Promise<void> | void;
}

interface AlbumFormState {
    name: string;
    releaseDate: string;
    style: string;
    introduction: string;
}

interface AlbumSongDraft {
    key: string;
    name: string;
    artists: ArtistSearchItem[];
    durationSeconds: number | null;
    introduction: string;
    audioFile: File | null;
}

const initialAlbumForm: AlbumFormState = {
    name: "",
    releaseDate: "",
    style: "",
    introduction: "",
};

function createSongDraft(
    artists: ArtistSearchItem[] = [],
): AlbumSongDraft {
    return {
        key: createLocalKey(),
        name: "",
        artists: [...artists],
        durationSeconds: null,
        introduction: "",
        audioFile: null,
    };
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
            translatedName: null,
            avatarUrl: null,
            countryRegion: null,
            auditStatus: "APPROVED",
        }),
    );
}

function readAudioDuration(
    file: File,
): Promise<number> {
    return new Promise(
        (resolve, reject) => {
            const url =
                URL.createObjectURL(file);

            const audio =
                document.createElement(
                    "audio",
                );

            audio.preload = "metadata";

            function cleanup(): void {
                URL.revokeObjectURL(url);
                audio.removeAttribute("src");
                audio.load();
            }

            audio.onloadedmetadata = () => {
                const duration =
                    Math.round(
                        audio.duration,
                    );

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

    const remaining =
        seconds % 60;

    return `${minutes}:${String(
        remaining,
    ).padStart(2, "0")}`;
}

export default function AlbumCreateDrawer({
                                              open,
                                              album = null,
                                              onClose,
                                              onCreated,
                                          }: AlbumCreateDrawerProps) {
    const editing =
        album !== null;

    const [form, setForm] =
        useState<AlbumFormState>(
            initialAlbumForm,
        );

    const [albumArtists, setAlbumArtists] =
        useState<ArtistSearchItem[]>([]);

    const [coverFile, setCoverFile] =
        useState<File | null>(null);

    const [songs, setSongs] =
        useState<AlbumSongDraft[]>([
            createSongDraft(),
        ]);

    const [submitting, setSubmitting] =
        useState(false);

    const [progressText, setProgressText] =
        useState("");

    const [error, setError] =
        useState("");

    useEffect(() => {
        if (!open) {
            return;
        }

        setCoverFile(null);
        setProgressText("");
        setError("");

        if (album === null) {
            setForm(initialAlbumForm);
            setAlbumArtists([]);
            setSongs([
                createSongDraft(),
            ]);
            return;
        }

        setForm({
            name: album.name,
            releaseDate:
                album.releaseDate ?? "",
            style:
                album.style ?? "",
            introduction:
                album.introduction ?? "",
        });

        setAlbumArtists(
            buildInitialArtists(
                album.artistIds ?? [],
                album.artistName,
            ),
        );

        /*
         * 编辑专辑只修改专辑元数据；
         * 专辑中的歌曲通过“音乐管理”逐首编辑。
         */
        setSongs([
            createSongDraft(),
        ]);
    }, [
        album,
        open,
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
        onClose,
        open,
        submitting,
    ]);

    function reset(): void {
        setForm(initialAlbumForm);
        setAlbumArtists([]);
        setCoverFile(null);
        setSongs([
            createSongDraft(),
        ]);
        setProgressText("");
        setError("");
    }

    function close(): void {
        if (submitting) {
            return;
        }

        reset();
        onClose();
    }

    function updateAlbumField(
        field: keyof AlbumFormState,
        value: string,
    ): void {
        setForm((current) => ({
            ...current,
            [field]: value,
        }));
    }

    function updateSong(
        key: string,
        updater: (
            current: AlbumSongDraft,
        ) => AlbumSongDraft,
    ): void {
        setSongs((current) =>
            current.map((song) =>
                song.key === key
                    ? updater(song)
                    : song,
            ),
        );
    }

    function addSong(): void {
        if (songs.length >= 50) {
            setError(
                "单张专辑最多一次新增50首音乐",
            );
            return;
        }

        setSongs((current) => [
            ...current,

            /*
             * 新增歌曲时不自动选中专辑音乐人。
             * 专辑音乐人只作为搜索框下方的快捷候选，
             * 由用户点击后才加入当前歌曲。
             */
            createSongDraft(),
        ]);
    }

    function removeSong(
        key: string,
    ): void {
        if (songs.length <= 1) {
            setError(
                "专辑至少需要保留一首音乐",
            );
            return;
        }

        setSongs((current) =>
            current.filter(
                (song) =>
                    song.key !== key,
            ),
        );
    }

    function handleSongAudioChange(
        key: string,
        file: File | null,
    ): void {
        setError("");

        if (file === null) {
            updateSong(
                key,
                (current) => ({
                    ...current,
                    audioFile: null,
                    durationSeconds: null,
                }),
            );
            return;
        }

        updateSong(
            key,
            (current) => ({
                ...current,
                audioFile: file,
                durationSeconds: null,
            }),
        );

        void readAudioDuration(file)
            .then((durationSeconds) => {
                updateSong(
                    key,
                    (current) => ({
                        ...current,
                        audioFile: file,
                        durationSeconds,
                    }),
                );
            })
            .catch(() => {
                updateSong(
                    key,
                    (current) => ({
                        ...current,
                        audioFile: null,
                        durationSeconds: null,
                    }),
                );

                setError(
                    "无法读取音乐文件时长，请重新选择",
                );
            });
    }

    async function handleSubmit(
        event: FormEvent<HTMLFormElement>,
    ): Promise<void> {
        event.preventDefault();
        setError("");

        const albumName =
            form.name.trim();

        const albumStyle =
            form.style.trim();

        if (albumName.length === 0) {
            setError("请输入专辑名称");
            return;
        }

        if (albumStyle.length === 0) {
            setError(
                "请输入专辑音乐风格",
            );
            return;
        }

        if (albumArtists.length === 0) {
            setError(
                "专辑至少选择一位音乐人",
            );
            return;
        }

        /*
         * 编辑模式允许保留原封面；
         * 新增模式必须上传封面。
         */
        if (
            !editing
            && coverFile === null
        ) {
            setError("请选择专辑封面");
            return;
        }

        if (
            editing
            && coverFile === null
            && !album?.coverUrl
        ) {
            setError(
                "当前专辑没有封面，请重新上传封面",
            );
            return;
        }

        if (!editing) {
            for (
                let index = 0;
                index < songs.length;
                index++
            ) {
                const songItem =
                    songs[index];

                const displayIndex =
                    index + 1;

                if (
                    songItem.name
                        .trim().length === 0
                ) {
                    setError(
                        `第${displayIndex}首音乐未填写名称`,
                    );
                    return;
                }

                if (
                    songItem.artists.length === 0
                ) {
                    setError(
                        `第${displayIndex}首音乐至少选择一位音乐人`,
                    );
                    return;
                }

                if (
                    songItem.audioFile === null
                    || songItem.durationSeconds === null
                ) {
                    setError(
                        `第${displayIndex}首音乐还没有有效的音乐文件`,
                    );
                    return;
                }
            }
        }

        setSubmitting(true);

        try {
            let coverUrl =
                album?.coverUrl ?? null;

            if (coverFile !== null) {
                setProgressText(
                    editing
                        ? "正在上传新的专辑封面..."
                        : "正在上传专辑封面...",
                );

                const uploadedCover =
                    await uploadFile(
                        "album-cover",
                        coverFile,
                    );

                coverUrl =
                    uploadedCover.url;
            }

            if (!coverUrl) {
                throw new Error(
                    "专辑封面地址不存在",
                );
            }

            const common = {
                name: albumName,

                artistIds:
                    albumArtists.map(
                        (artist) =>
                            artist.id,
                    ),

                style: albumStyle,

                coverUrl,

                releaseDate:
                    form.releaseDate
                    || null,

                introduction:
                    form.introduction
                        .trim()
                    || null,
            };

            if (
                editing
                && album !== null
            ) {
                setProgressText(
                    "正在保存专辑修改...",
                );

                await updateAlbum(
                    album.id,
                    common,
                );
            } else {
                const uploadedSongs = [];

                for (
                    let index = 0;
                    index < songs.length;
                    index++
                ) {
                    const songItem =
                        songs[index];

                    setProgressText(
                        `正在上传第${index + 1}/${songs.length}首音乐...`,
                    );

                    const uploadedAudio =
                        await uploadFile(
                            "audio",
                            songItem.audioFile!,
                        );

                    uploadedSongs.push({
                        name:
                            songItem.name.trim(),

                        artistIds:
                            songItem.artists.map(
                                (artist) =>
                                    artist.id,
                            ),

                        durationSeconds:
                            songItem.durationSeconds!,

                        introduction:
                            songItem.introduction
                                .trim()
                            || null,

                        audioUrl:
                        uploadedAudio.url,
                    });
                }

                setProgressText(
                    `正在一次性提交专辑和${songs.length}首音乐...`,
                );

                await createAlbumWithSongs({
                    ...common,
                    songs: uploadedSongs,
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
            setProgressText("");
        }
    }

    if (!open) {
        return null;
    }

    return createPortal(
        <div
            className="song-drawer-layer album-drawer-layer"
            role="presentation"
        >
            <button
                type="button"
                className="song-drawer-mask"
                aria-label={
                    editing
                        ? "关闭编辑专辑表单"
                        : "关闭新增专辑表单"
                }
                onClick={close}
            />

            <aside
                className="song-drawer album-create-drawer"
                role="dialog"
                aria-modal="true"
                aria-labelledby="album-drawer-title"
            >
                <header className="song-drawer-header">
                    <div>
                        <h2 id="album-drawer-title">
                            {editing
                                ? "编辑专辑"
                                : "新增专辑"}
                        </h2>

                        <p>
                            {editing
                                ? "修改封面或风格时，专辑内歌曲会同步继承并重新进入审核流程"
                                : "专辑和其中的音乐会一次性提交审核"}
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
                    className="song-form album-create-form"
                    onSubmit={(event) => {
                        void handleSubmit(event);
                    }}
                >
                    <section className="album-form-section">
                        <h3>专辑信息</h3>

                        <label className="song-form-field">
                            <span>
                                专辑名称<b>*</b>
                            </span>

                            <input
                                value={form.name}
                                maxLength={128}
                                disabled={submitting}
                                placeholder="请输入专辑名称"
                                onChange={(event) =>
                                    updateAlbumField(
                                        "name",
                                        event.target.value,
                                    )
                                }
                            />
                        </label>

                        <label className="song-form-field">
                            <span>
                                音乐风格<b>*</b>
                            </span>

                            <input
                                value={form.style}
                                maxLength={64}
                                disabled={submitting}
                                placeholder="例如 流行"
                                onChange={(event) =>
                                    updateAlbumField(
                                        "style",
                                        event.target.value,
                                    )
                                }
                            />

                            <small>
                                专辑内全部音乐会自动继承该音乐风格
                            </small>
                        </label>

                        <div className="song-form-field">
                            <span>
                                专辑音乐人 / 乐队<b>*</b>
                            </span>

                            <ArtistMultiSearch
                                selected={albumArtists}
                                disabled={submitting}
                                onChange={setAlbumArtists}
                            />

                            <small>
                                搜索结果包含已通过和待审核音乐人
                            </small>
                        </div>

                        <div className="song-form-row">
                            <label className="song-form-field">
                                <span>
                                    发行日期
                                </span>

                                <input
                                    type="date"
                                    value={form.releaseDate}
                                    disabled={submitting}
                                    onChange={(event) =>
                                        updateAlbumField(
                                            "releaseDate",
                                            event.target.value,
                                        )
                                    }
                                />
                            </label>

                            <label className="song-form-field">
                                <span>
                                    专辑封面
                                    {!editing && <b>*</b>}
                                </span>

                                <input
                                    type="file"
                                    accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                                    disabled={submitting}
                                    onChange={(
                                        event: ChangeEvent<HTMLInputElement>,
                                    ) =>
                                        setCoverFile(
                                            event.target
                                                .files?.[0]
                                            ?? null,
                                        )
                                    }
                                />

                                {editing && (
                                    <small>
                                        不重新选择则保留原专辑封面
                                    </small>
                                )}
                            </label>
                        </div>

                        {editing
                            && album?.coverUrl
                            && (
                                <div className="album-current-cover">
                                    <img
                                        src={album.coverUrl}
                                        alt={album.name}
                                    />
                                    <span>当前专辑封面</span>
                                </div>
                            )}

                        <label className="song-form-field">
                            <span>
                                专辑简介
                            </span>

                            <textarea
                                rows={3}
                                maxLength={1000}
                                value={form.introduction}
                                disabled={submitting}
                                placeholder="请输入专辑简介"
                                onChange={(event) =>
                                    updateAlbumField(
                                        "introduction",
                                        event.target.value,
                                    )
                                }
                            />
                        </label>
                    </section>

                    {!editing && (
                        <section className="album-form-section">
                            <div className="album-song-section-title">
                                <div>
                                    <h3>
                                        专辑音乐
                                    </h3>

                                    <p>
                                        当前共 {songs.length} 首；歌曲自动使用专辑封面和音乐风格
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    className="album-add-song-button"
                                    disabled={submitting}
                                    onClick={addSong}
                                >
                                    + 添加音乐
                                </button>
                            </div>

                            <div className="album-song-list">
                                {songs.map(
                                    (songItem, index) => (
                                        <article
                                            key={songItem.key}
                                            className="album-song-card"
                                        >
                                            <header>
                                                <strong>
                                                    第 {index + 1} 首音乐
                                                </strong>

                                                <button
                                                    type="button"
                                                    disabled={
                                                        submitting
                                                        || songs.length <= 1
                                                    }
                                                    onClick={() =>
                                                        removeSong(
                                                            songItem.key,
                                                        )
                                                    }
                                                >
                                                    删除
                                                </button>
                                            </header>

                                            <label className="song-form-field">
                                                <span>
                                                    音乐名称<b>*</b>
                                                </span>

                                                <input
                                                    value={songItem.name}
                                                    maxLength={128}
                                                    disabled={submitting}
                                                    placeholder="请输入音乐名称"
                                                    onChange={(event) =>
                                                        updateSong(
                                                            songItem.key,
                                                            (current) => ({
                                                                ...current,
                                                                name:
                                                                event.target.value,
                                                            }),
                                                        )
                                                    }
                                                />
                                            </label>

                                            <div className="song-form-field">
                                                <span>
                                                    音乐人 / 乐队<b>*</b>
                                                </span>

                                                <ArtistMultiSearch
                                                    selected={songItem.artists}
                                                    suggestions={albumArtists}
                                                    disabled={submitting}
                                                    onChange={(artists) =>
                                                        updateSong(
                                                            songItem.key,
                                                            (current) => ({
                                                                ...current,
                                                                artists,
                                                            }),
                                                        )
                                                    }
                                                />
                                            </div>

                                            <label className="song-form-field">
                                                <span>
                                                    音乐文件<b>*</b>
                                                </span>

                                                <input
                                                    type="file"
                                                    accept=".mp3,.wav,.flac,.m4a,.ogg,audio/*"
                                                    disabled={submitting}
                                                    onChange={(
                                                        event:
                                                        ChangeEvent<HTMLInputElement>,
                                                    ) =>
                                                        handleSongAudioChange(
                                                            songItem.key,
                                                            event.target
                                                                .files?.[0]
                                                            ?? null,
                                                        )
                                                    }
                                                />

                                                {songItem.durationSeconds
                                                    !== null
                                                    && (
                                                        <small className="audio-duration-result">
                                                            已自动读取时长：
                                                            {formatDuration(
                                                                songItem.durationSeconds,
                                                            )}
                                                            （
                                                            {songItem.durationSeconds}
                                                            秒）
                                                        </small>
                                                    )}
                                            </label>

                                            <label className="song-form-field">
                                                <span>音乐简介</span>

                                                <textarea
                                                    rows={2}
                                                    maxLength={1000}
                                                    value={
                                                        songItem.introduction
                                                    }
                                                    disabled={submitting}
                                                    placeholder="可选"
                                                    onChange={(event) =>
                                                        updateSong(
                                                            songItem.key,
                                                            (current) => ({
                                                                ...current,
                                                                introduction:
                                                                event.target.value,
                                                            }),
                                                        )
                                                    }
                                                />
                                            </label>
                                        </article>
                                    ),
                                )}
                            </div>
                        </section>
                    )}

                    {editing && (
                        <div className="song-dependency-note">
                            专辑内已有歌曲不在这里逐首编辑。
                            如需修改歌曲名称、音乐人或音频文件，请在“音乐管理”标签中编辑对应歌曲。
                        </div>
                    )}

                    {progressText && (
                        <div className="song-form-progress">
                            {progressText}
                        </div>
                    )}

                    {error && (
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
                                    : `提交专辑及${songs.length}首音乐`}
                        </button>
                    </footer>
                </form>
            </aside>
        </div>,
        document.body,
    );
}

function ArtistMultiSearch({
                               selected,
                               suggestions = [],
                               disabled,
                               onChange,
                           }: {
    selected: ArtistSearchItem[];

    /**
     * 输入框下方展示的快捷候选。
     *
     * 在“专辑中的歌曲”场景里传 albumArtists，
     * 只作为候选按钮，不会自动选中。
     */
    suggestions?: ArtistSearchItem[];

    disabled: boolean;
    onChange(items: ArtistSearchItem[]): void;
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

    const quickSuggestions =
        suggestions.filter(
            (suggestion) =>
                !selected.some(
                    (selectedArtist) =>
                        selectedArtist.id
                        === suggestion.id,
                ),
        );

    return (
        <div className="album-artist-picker">
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

                            <span>
                                {artist.name}
                            </span>

                            {artist.auditStatus === "PENDING" && (
                                <em>待审核</em>
                            )}

                            <button
                                type="button"
                                disabled={disabled}
                                onClick={() =>
                                    removeArtist(
                                        artist.id,
                                    )
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

                {quickSuggestions.length > 0 && (
                    <div className="album-artist-quick-pick">
                        <span className="album-artist-quick-label">
                            专辑音乐人快捷选择
                        </span>

                        <div className="album-artist-quick-list">
                            {quickSuggestions.map(
                                (artist) => (
                                    <button
                                        key={artist.id}
                                        type="button"
                                        className="album-artist-quick-button"
                                        disabled={disabled}
                                        onClick={() =>
                                            addArtist(
                                                artist,
                                            )
                                        }
                                    >
                                        <span
                                            className="album-artist-quick-plus"
                                            aria-hidden="true"
                                        >
                                            +
                                        </span>

                                        {artist.avatarUrl ? (
                                            <img
                                                src={artist.avatarUrl}
                                                alt=""
                                            />
                                        ) : (
                                            <i>
                                                {artist.name
                                                    .slice(0, 1)}
                                            </i>
                                        )}

                                        <span>
                                            {artist.name}
                                        </span>

                                        {artist.auditStatus
                                            === "PENDING"
                                            && (
                                                <em>
                                                    待审核
                                                </em>
                                            )}
                                    </button>
                                ),
                            )}
                        </div>
                    </div>
                )}

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
                                                    artist.translatedName,
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
        </div>
    );
}

function createLocalKey(): string {
    if (
        typeof crypto !== "undefined"
        && "randomUUID" in crypto
    ) {
        return crypto.randomUUID();
    }

    return `${Date.now()}-${Math.random()}`;
}