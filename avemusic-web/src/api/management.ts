import { http } from "../auth/api/http";

interface ApiResult<T> {
    code: string;
    message: string;
    data: T;
}

export type AuditStatus =
    | "PENDING"
    | "APPROVED"
    | "REJECTED";

export type PublishStatus =
    | "ONLINE"
    | "OFFLINE";

export interface SongManagementItem {
    id: string;
    name: string;
    artistName: string;
    artistIds: string[];
    albumId: string | null;
    albumName: string | null;
    durationSeconds: number;
    style: string | null;
    introduction: string | null;
    coverUrl: string | null;
    audioUrl: string | null;
    auditStatus: AuditStatus;
    publishStatus: PublishStatus;
    createdAt: string;
}

export interface AlbumManagementItem {
    id: string;
    name: string;
    artistName: string;
    artistIds: string[];
    style: string | null;
    coverUrl: string | null;
    releaseDate: string | null;
    introduction: string | null;
    auditStatus: AuditStatus;
    createdAt: string;
}

export interface ArtistManagementItem {
    id: string;
    name: string;
    translatedNames: string[];
    ownerUserId: string | null;
    countryRegion: string | null;
    style: string | null;
    avatarUrl: string | null;
    introduction: string | null;
    followerCount: number;
    songCount: number;
    albumCount: number;
    auditStatus: AuditStatus;
    publishStatus: PublishStatus;
    createdAt: string;
}

export interface ArtistSearchItem {
    id: string;
    name: string;
    translatedNames: string[];
    avatarUrl: string | null;
    countryRegion: string | null;
    auditStatus: "APPROVED" | "PENDING";
}

export interface AlbumSearchItem {
    id: string;
    name: string;
    artistName: string;
    coverUrl: string | null;
    style: string | null;
}

export interface UserManagementItem {
    id: string;
    username: string;
    phoneMasked: string | null;
    role: string;
    realNameStatus:
        | "NONE"
        | "PENDING"
        | "VERIFIED"
        | "REJECTED";
    accountStatus:
        | "ENABLED"
        | "DISABLED";
    artistId: string | null;
    createdAt: string;
}

export interface CreateSongRequest {
    name: string;
    albumId: string | null;
    artistIds: string[];
    durationSeconds: number;
    style: string | null;
    introduction: string | null;
    coverUrl: string | null;
    audioUrl: string;
}

export interface UpdateSongRequest {
    name: string;
    albumId: string | null;
    artistIds: string[];
    durationSeconds: number;
    introduction: string | null;
    audioUrl: string;
}

export interface CreateArtistRequest {
    name: string;
    translatedNames: string[];
    countryRegion: string;
    style: string | null;
    introduction: string | null;
    avatarUrl: string | null;
}

export interface CreateAlbumSongRequest {
    name: string;
    artistIds: string[];
    durationSeconds: number;
    introduction: string | null;
    audioUrl: string;
}

export interface CreateAlbumWithSongsRequest {
    name: string;
    artistIds: string[];
    style: string;
    coverUrl: string;
    releaseDate: string | null;
    introduction: string | null;
    songs: CreateAlbumSongRequest[];
}

export interface UpdateAlbumRequest {
    name: string;
    artistIds: string[];
    style: string;
    coverUrl: string;
    releaseDate: string | null;
    introduction: string | null;
}

export interface PageResult<T> {
    records: T[];

    total: number;

    page: number;

    size: number;

    totalPages: number;
}

export interface AlbumCreateResult {
    album: AlbumManagementItem;
    songs: SongManagementItem[];
}

function normalizeSong(
    item: SongManagementItem,
): SongManagementItem {
    return {
        ...item,
        artistIds: item.artistIds ?? [],
        durationSeconds:
            Number(item.durationSeconds ?? 0),
        introduction:
            item.introduction ?? null,
    };
}

function normalizeAlbum(
    item: AlbumManagementItem,
): AlbumManagementItem {
    return {
        ...item,
        artistIds: item.artistIds ?? [],
        style: item.style ?? null,
        introduction:
            item.introduction ?? null,
    };
}

export async function getManagedSongs(
    page: number,
    size: number,
    keyword = "",
): Promise<
    PageResult<SongManagementItem>
> {
    const response =
        await http.get<
            ApiResult<
                PageResult<
                    SongManagementItem
                >
            >
        >(
            "/music/songs/manage",
            {
                params: {
                    page,
                    size,

                    keyword:
                        keyword.trim(),
                },
            },
        );

    return response.data.data;
}

export async function getManagedAlbums(
    page: number,
    size: number,
    keyword = "",
): Promise<
    PageResult<AlbumManagementItem>
> {
    const response =
        await http.get<
            ApiResult<
                PageResult<
                    AlbumManagementItem
                >
            >
        >(
            "/music/albums/manage",
            {
                params: {
                    page,
                    size,

                    keyword:
                        keyword.trim(),
                },
            },
        );

    return response.data.data;
}

export async function getManagedArtists():
    Promise<ArtistManagementItem[]> {
    const response = await http.get<
        ApiResult<ArtistManagementItem[]>
    >("/music/artists/manage");

    return (response.data.data ?? []).map((item) => ({
        ...item,
        followerCount:
            Number(item.followerCount ?? 0),
        songCount:
            Number(item.songCount ?? 0),
        albumCount:
            Number(item.albumCount ?? 0),
        publishStatus:
            item.publishStatus ?? "OFFLINE",
    }));
}

export async function getManagedUsers():
    Promise<UserManagementItem[]> {
    const response = await http.get<
        ApiResult<UserManagementItem[]>
    >("/users/manage");

    return response.data.data ?? [];
}

export async function getAuditSongs():
    Promise<SongManagementItem[]> {
    const response = await http.get<
        ApiResult<SongManagementItem[]>
    >("/music/songs/audit");

    return (response.data.data ?? [])
        .map(normalizeSong);
}

export async function getAuditAlbums():
    Promise<AlbumManagementItem[]> {
    const response = await http.get<
        ApiResult<AlbumManagementItem[]>
    >("/music/albums/audit");

    return (response.data.data ?? [])
        .map(normalizeAlbum);
}

export async function getAuditArtists():
    Promise<ArtistManagementItem[]> {
    const response = await http.get<
        ApiResult<ArtistManagementItem[]>
    >("/music/artists/audit");

    return (response.data.data ?? []).map((item) => ({
        ...item,
        followerCount:
            Number(item.followerCount ?? 0),
        songCount:
            Number(item.songCount ?? 0),
        albumCount:
            Number(item.albumCount ?? 0),
        publishStatus:
            item.publishStatus ?? "OFFLINE",
    }));
}

export async function searchArtists(
    keyword: string,
): Promise<ArtistSearchItem[]> {
    const response = await http.get<
        ApiResult<ArtistSearchItem[]>
    >(
        "/music/artists/search",
        {
            params: {
                keyword,
                limit: 10,
            },
        },
    );

    return response.data.data ?? [];
}

export async function searchAlbums(
    keyword: string,
): Promise<AlbumSearchItem[]> {
    const response = await http.get<
        ApiResult<AlbumSearchItem[]>
    >(
        "/music/albums/search",
        {
            params: {
                keyword,
                limit: 10,
            },
        },
    );

    return (response.data.data ?? []).map(
        (item) => ({
            ...item,
            style: item.style ?? null,
        }),
    );
}

export async function createArtist(
    request: CreateArtistRequest,
): Promise<ArtistSearchItem> {
    const response = await http.post<
        ApiResult<ArtistSearchItem>
    >(
        "/music/artists",
        request,
    );

    return response.data.data;
}

export async function updateArtist(
    id: string,
    request: CreateArtistRequest,
): Promise<ArtistManagementItem> {
    const response = await http.put<
        ApiResult<ArtistManagementItem>
    >(
        `/music/artists/${id}`,
        request,
    );

    return response.data.data;
}

export async function createSong(
    request: CreateSongRequest,
): Promise<SongManagementItem> {
    const response = await http.post<
        ApiResult<SongManagementItem>
    >(
        "/music/songs",
        request,
    );

    return normalizeSong(
        response.data.data,
    );
}

export async function updateSong(
    id: string,
    request: UpdateSongRequest,
): Promise<SongManagementItem> {
    const response = await http.put<
        ApiResult<SongManagementItem>
    >(
        `/music/songs/${id}`,
        request,
    );

    return normalizeSong(
        response.data.data,
    );
}

export async function createAlbumWithSongs(
    request: CreateAlbumWithSongsRequest,
): Promise<AlbumCreateResult> {
    const response = await http.post<
        ApiResult<AlbumCreateResult>
    >(
        "/music/albums",
        request,
    );

    return response.data.data;
}

export async function updateAlbum(
    id: string,
    request: UpdateAlbumRequest,
): Promise<AlbumManagementItem> {
    const response = await http.put<
        ApiResult<AlbumManagementItem>
    >(
        `/music/albums/${id}`,
        request,
    );

    return normalizeAlbum(
        response.data.data,
    );
}

export async function setArtistOnline(
    id: string,
    online: boolean,
): Promise<void> {
    await http.put<ApiResult<null>>(
        `/music/artists/${id}/status`,
        { online },
    );
}

export async function deleteArtist(
    id: string,
): Promise<void> {
    await http.delete<ApiResult<null>>(
        `/music/artists/${id}`,
    );
}

interface BatchDeleteRequest {
    ids: string[];
}

async function batchDelete(
    path: string,
    ids: string[],
): Promise<void> {
    await http.delete<ApiResult<null>>(
        path,
        {
            data: {
                ids,
            } satisfies BatchDeleteRequest,
        },
    );
}

export async function deleteSongs(
    ids: string[],
): Promise<void> {
    await batchDelete(
        "/music/songs/batch",
        ids,
    );
}

export async function deleteAlbums(
    ids: string[],
): Promise<void> {
    await batchDelete(
        "/music/albums/batch",
        ids,
    );
}

export async function deleteArtists(
    ids: string[],
): Promise<void> {
    await batchDelete(
        "/music/artists/batch",
        ids,
    );
}

export type ReviewAction =
    | "APPROVE"
    | "REJECT"
    | "REVOKE";

interface ReviewRequest {
    action: ReviewAction;
    reason: string | null;
}

async function review(
    path: string,
    action: ReviewAction,
    reason: string | null,
): Promise<void> {
    await http.put<ApiResult<null>>(
        path,
        {
            action,
            reason,
        } satisfies ReviewRequest,
    );
}

export async function reviewSong(
    id: string,
    action: ReviewAction,
    reason: string | null,
): Promise<void> {
    await review(
        `/music/songs/${id}/audit`,
        action,
        reason,
    );
}

export async function reviewAlbum(
    id: string,
    action: ReviewAction,
    reason: string | null,
): Promise<void> {
    await review(
        `/music/albums/${id}/audit`,
        action,
        reason,
    );
}

export async function reviewArtist(
    id: string,
    action: ReviewAction,
    reason: string | null,
): Promise<void> {
    await review(
        `/music/artists/${id}/audit`,
        action,
        reason,
    );
}
