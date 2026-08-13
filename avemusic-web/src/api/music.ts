import { http } from "../auth/api/http";

export interface ApiResult<T> {
    code: string;
    message: string;
    data: T;
}

export interface SongLyrics {
    songId: string;

    status:
        | "MATCHED"
        | "NOT_FOUND";

    instrumental: boolean;

    synced: boolean;

    plainLyrics:
        string | null;

    syncedLyrics:
        string | null;

    /*
     * 与解析后的歌词行一一对应。
     */
    translatedLines:
        string[];

    source:
        string | null;
}

export async function getSongLyrics(
    songId: string,
): Promise<SongLyrics> {
    const response =
        await http.get<
            ApiResult<SongLyrics>
        >(
            `/music/songs/${songId}/lyrics`,
        );

    return response.data.data;
}

export async function translateSongLyrics(
    songId: string,
): Promise<SongLyrics> {
    const response =
        await http.post<
            ApiResult<SongLyrics>
        >(
            `/music/songs/${songId}/lyrics/translate`,
        );

    const result =
        response.data.data;

    return {
        ...result,

        translatedLines:
            result.translatedLines
            ?? [],
    };
}

export interface SongCard {
    id: string;
    name: string;
    artistName: string;

    /**
     * 与 artistName 按相同排序一一对应。
     * 首页用于将音乐人名称跳转到歌手详情页。
     */
    artistIds: string[];

    coverUrl: string | null;

    /**
     * 文件服务的公开音频地址。
     * 没有音频时为 null。
     */
    audioUrl: string | null;

    /**
     * 数据库存储的歌曲时长，单位秒。
     * 播放器加载 metadata 后会优先采用浏览器读取的实际时长。
     */
    durationSeconds: number;

    playCount: number;
}

export interface ArtistCard {
    id: string;
    name: string;
    translatedNames: string[];
    avatarUrl: string | null;
    countryRegion: string | null;
    followerCount: number;
}

export async function getHomeSongs(
    limit = 8,
): Promise<SongCard[]> {
    const response =
        await http.get<
            ApiResult<SongCard[]>
        >(
            "/music/songs/home",
            {
                params: {
                    limit,
                },
            },
        );

    return (
        response.data.data
        ?? []
    ).map((song) => ({
        ...song,

        artistIds:
            Array.isArray(song.artistIds)
                ? song.artistIds
                : [],

        audioUrl:
            song.audioUrl
            ?? null,

        durationSeconds:
            Number(
                song.durationSeconds
                ?? 0,
            ),

        playCount:
            Number(
                song.playCount
                ?? 0,
            ),
    }));
}

export async function getHomeArtists(
    limit = 6,
): Promise<ArtistCard[]> {
    const response =
        await http.get<
            ApiResult<ArtistCard[]>
        >(
            "/music/artists/home",
            {
                params: {
                    limit,
                },
            },
        );

    return (
        response.data.data
        ?? []
    ).map((artist) => ({
        ...artist,

        followerCount:
            Number(
                artist.followerCount
                ?? 0,
            ),
    }));
}

export interface ArtistDetailSong {
    id: string;
    name: string;
    artistName: string;
    artistIds: string[];
    albumId: string | null;
    albumName: string | null;
    coverUrl: string | null;
    audioUrl: string | null;
    durationSeconds: number;
    playCount: number;
}

export interface ArtistDetailAlbum {
    id: string;
    name: string;
    coverUrl: string | null;
    releaseDate: string | null;
    style: string | null;
}

export interface ArtistDetail {
    id: string;
    name: string;
    translatedNames: string[];
    ownerUserId: string | null;
    avatarUrl: string | null;
    countryRegion: string | null;
    style: string | null;
    introduction: string | null;
    followerCount: number;
    songCount: number;
    albumCount: number;
    songs: ArtistDetailSong[];
    albums: ArtistDetailAlbum[];
}

export async function getArtistDetail(
    id: string,
): Promise<ArtistDetail> {
    const response = await http.get<
        ApiResult<ArtistDetail>
    >(
        `/music/artists/detail/${id}`,
    );

    const result = response.data.data;

    return {
        ...result,
        followerCount:
            Number(result.followerCount ?? 0),
        songCount:
            Number(result.songCount ?? 0),
        albumCount:
            Number(result.albumCount ?? 0),
        songs:
            (result.songs ?? []).map((song) => ({
                ...song,

                artistIds:
                    Array.isArray(
                        song.artistIds,
                    )
                        ? song.artistIds
                        : [],

                durationSeconds:
                    Number(
                        song.durationSeconds
                        ?? 0,
                    ),

                playCount:
                    Number(
                        song.playCount
                        ?? 0,
                    ),
            })),
        albums:
            result.albums ?? [],
    };
}


/* ==================== C端专辑详情 ==================== */

export interface AlbumDetailSong {
    id: string;
    name: string;

    artistName: string;
    artistIds: string[];

    albumName: string | null;

    coverUrl: string | null;
    audioUrl: string | null;

    durationSeconds: number;
    playCount: number;
}

export interface AlbumDetail {
    id: string;
    name: string;

    coverUrl: string | null;

    artistName: string;
    artistIds: string[];

    /**
     * 当前专辑首位音乐人的头像，
     * 仅用于专辑页头部展示。
     */
    artistAvatarUrl: string | null;

    releaseDate: string | null;
    style: string | null;
    introduction: string | null;

    songs: AlbumDetailSong[];
}

export async function getAlbumDetail(
    id: string,
): Promise<AlbumDetail> {
    const response =
        await http.get<
            ApiResult<AlbumDetail>
        >(
            `/music/albums/detail/${id}`,
        );

    const result =
        response.data.data;

    return {
        ...result,

        artistIds:
            Array.isArray(
                result.artistIds,
            )
                ? result.artistIds
                : [],

        songs:
            (result.songs ?? [])
                .map((song) => ({
                    ...song,

                    artistIds:
                        Array.isArray(
                            song.artistIds,
                        )
                            ? song.artistIds
                            : [],

                    albumName:
                        song.albumName
                        ?? result.name,

                    coverUrl:
                        song.coverUrl
                        ?? result.coverUrl
                        ?? null,

                    audioUrl:
                        song.audioUrl
                        ?? null,

                    durationSeconds:
                        Number(
                            song.durationSeconds
                            ?? 0,
                        ),

                    playCount:
                        Number(
                            song.playCount
                            ?? 0,
                        ),
                })),
    };
}

/* ==================== 服务端播放会话 ==================== */

const PLAYBACK_CLIENT_STORAGE_KEY =
    "avemusic_playback_client_id";

export interface PlaySessionStartResult {
    sessionId: string;
    heartbeatIntervalSeconds: number;
}

export interface PlaySessionHeartbeatResult {
    counted: boolean;
    playCount: number | null;
}

function getPlaybackClientId(): string {
    const existing =
        window.localStorage.getItem(
            PLAYBACK_CLIENT_STORAGE_KEY,
        );

    if (existing) {
        return existing;
    }

    const created =
        typeof crypto.randomUUID
        === "function"
            ? crypto.randomUUID()
            : [
                Date.now().toString(36),
                Math.random()
                    .toString(36)
                    .slice(2),
                Math.random()
                    .toString(36)
                    .slice(2),
            ].join("-");

    window.localStorage.setItem(
        PLAYBACK_CLIENT_STORAGE_KEY,
        created,
    );

    return created;
}

function playbackHeaders() {
    return {
        "X-Playback-Client":
            getPlaybackClientId(),
    };
}

export async function startPlaySession(
    songId: string,
): Promise<PlaySessionStartResult> {
    const response = await http.post<
        ApiResult<PlaySessionStartResult>
    >(
        `/music/songs/${songId}/play-session`,
        null,
        {
            headers: playbackHeaders(),
        },
    );

    return response.data.data;
}

export async function heartbeatPlaySession(
    sessionId: string,
): Promise<PlaySessionHeartbeatResult> {
    const response = await http.post<
        ApiResult<PlaySessionHeartbeatResult>
    >(
        `/music/songs/play-session/${sessionId}/heartbeat`,
        null,
        {
            headers: playbackHeaders(),
        },
    );

    return response.data.data;
}

export async function finishPlaySession(
    sessionId: string,
): Promise<void> {
    await http.delete<ApiResult<null>>(
        `/music/songs/play-session/${sessionId}`,
        {
            headers: playbackHeaders(),
        },
    );
}
