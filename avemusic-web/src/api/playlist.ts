import { http } from "../auth/api/http";

interface ApiResult<T> {
    code: string;
    message: string;
    data: T;
}

export type PlaylistVisibility =
    | "PUBLIC"
    | "PRIVATE";

export interface PlaylistSummary {
    id: string;
    name: string;
    introduction: string | null;

    /**
     * 页面最终展示封面：
     * 自定义封面 or 第一首歌曲封面。
     */
    coverUrl: string | null;

    /**
     * playlist_tb.cover_url 原始值。
     */
    customCoverUrl: string | null;

    /**
     * 创建该歌单的用户ID。
     */
    ownerUserId: string;

    visibility: PlaylistVisibility;

    songCount: number;

    /**
     * 被多少用户收藏。
     */
    favoriteCount: number;

    createdAt: string;
}

export interface PlaylistSongItem {
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

export interface PlaylistDetail {
    playlist: PlaylistSummary;
    songs: PlaylistSongItem[];

    /**
     * 当前登录用户是否已收藏该歌单。
     * 自己创建的歌单恒为 false。
     */
    favoritedByMe: boolean;
}

export interface SavePlaylistRequest {
    name: string;
    introduction: string | null;
    coverUrl: string | null;
    visibility: PlaylistVisibility;
}

export interface PlaylistPage {
    records: PlaylistSummary[];

    total: number;

    page: number;

    size: number;

    totalPages: number;
}

function normalizePlaylist(
    item: PlaylistSummary,
): PlaylistSummary {
    return {
        ...item,

        introduction:
            item.introduction
            ?? null,

        coverUrl:
            item.coverUrl
            ?? null,

        customCoverUrl:
            item.customCoverUrl
            ?? null,

        ownerUserId:
            String(
                item.ownerUserId
                ?? "",
            ),

        visibility:
            item.visibility
            === "PUBLIC"
                ? "PUBLIC"
                : "PRIVATE",

        songCount:
            Number(
                item.songCount
                ?? 0,
            ),

        favoriteCount:
            Number(
                item.favoriteCount
                ?? 0,
            ),
    };
}

function normalizeSong(
    item: PlaylistSongItem,
): PlaylistSongItem {
    return {
        ...item,

        artistIds:
            item.artistIds
            ?? [],

        albumName:
            item.albumName
            ?? null,

        coverUrl:
            item.coverUrl
            ?? null,

        audioUrl:
            item.audioUrl
            ?? null,

        durationSeconds:
            Number(
                item.durationSeconds
                ?? 0,
            ),

        playCount:
            Number(
                item.playCount
                ?? 0,
            ),
    };
}

export async function getMyPlaylists():
        Promise<PlaylistSummary[]> {
    const response =
        await http.get<
            ApiResult<PlaylistSummary[]>
        >(
            "/playlists/mine",
        );

    return (
        response.data.data
        ?? []
    ).map(normalizePlaylist);
}

export async function getFavoritePlaylists():
        Promise<PlaylistSummary[]> {
    const response =
        await http.get<
            ApiResult<PlaylistSummary[]>
        >(
            "/playlists/favorites",
        );

    return (
        response.data.data
        ?? []
    ).map(normalizePlaylist);
}

export async function getPlaylistDetail(
        playlistId: string,
): Promise<PlaylistDetail> {
    const response =
        await http.get<
            ApiResult<PlaylistDetail>
        >(
            `/playlists/${playlistId}`,
        );

    return {
        playlist:
            normalizePlaylist(
                response.data.data.playlist,
            ),

        songs:
            (
                response.data.data.songs
                ?? []
            ).map(normalizeSong),

        favoritedByMe:
            Boolean(
                response.data.data
                    .favoritedByMe,
            ),
    };
}

export async function createPlaylist(
        request: SavePlaylistRequest,
): Promise<PlaylistSummary> {
    const response =
        await http.post<
            ApiResult<PlaylistSummary>
        >(
            "/playlists",
            request,
        );

    return normalizePlaylist(
        response.data.data,
    );
}

export async function updatePlaylist(
        playlistId: string,
        request: SavePlaylistRequest,
): Promise<PlaylistSummary> {
    const response =
        await http.put<
            ApiResult<PlaylistSummary>
        >(
            `/playlists/${playlistId}`,
            request,
        );

    return normalizePlaylist(
        response.data.data,
    );
}

export async function addSongToPlaylist(
        playlistId: string,
        songId: string,
): Promise<void> {
    await http.post<
        ApiResult<null>
    >(
        `/playlists/${playlistId}/songs/${songId}`,
    );
}

export async function removeSongFromPlaylist(
        playlistId: string,
        songId: string,
): Promise<void> {
    await http.delete<
        ApiResult<null>
    >(
        `/playlists/${playlistId}/songs/${songId}`,
    );
}

export async function favoritePlaylist(
        playlistId: string,
): Promise<void> {
    await http.post<
        ApiResult<null>
    >(
        `/playlists/${playlistId}/favorite`,
    );
}

export async function unfavoritePlaylist(
        playlistId: string,
): Promise<void> {
    await http.delete<
        ApiResult<null>
    >(
        `/playlists/${playlistId}/favorite`,
    );
}

export async function getPopularPlaylists(
    page: number,
): Promise<PlaylistPage> {
    const response =
        await http.get<
            ApiResult<PlaylistPage>
        >(
            "/playlists/ranking",
            {
                params: {
                    page,
                },
            },
        );

    const data =
        response.data.data;

    return {
        records:
            (
                data.records
                ?? []
            ).map(
                normalizePlaylist,
            ),

        total:
            Number(
                data.total
                ?? 0,
            ),

        page:
            Number(
                data.page
                ?? page,
            ),

        size:
            Number(
                data.size
                ?? 20,
            ),

        totalPages:
            Math.min(
                Number(
                    data.totalPages
                    ?? 0,
                ),
                20,
            ),
    };
}
