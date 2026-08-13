package com.avemonica.avemusic.music.api.dto;

import java.io.Serializable;
import java.util.List;

public final class MusicManagementModels {

    private MusicManagementModels() {
    }

    public record Actor(
            String userId,
            String role
    ) implements Serializable {
    }

    public record SongItem(
            String id,
            String name,
            String artistName,
            List<String> artistIds,
            String albumId,
            String albumName,
            int durationSeconds,
            String style,
            String introduction,
            String coverUrl,
            String audioUrl,
            String auditStatus,
            String publishStatus,
            String createdAt
    ) implements Serializable {
    }

    public record AlbumItem(
            String id,
            String name,
            String artistName,
            List<String> artistIds,
            String style,
            String coverUrl,
            String releaseDate,
            String introduction,
            String auditStatus,
            String createdAt
    ) implements Serializable {
    }

    public record ArtistItem(
            String id,
            String name,
            List<String> translatedNames,
            String ownerUserId,
            String countryRegion,
            String style,
            String avatarUrl,
            String introduction,
            long followerCount,
            long songCount,
            long albumCount,
            String auditStatus,
            String publishStatus,
            String createdAt
    ) implements Serializable {
    }

    public record ArtistSearchItem(
            String id,
            String name,
            List<String> translatedNames,
            String avatarUrl,
            String countryRegion,
            String auditStatus
    ) implements Serializable {
    }

    public record AlbumSearchItem(
            String id,
            String name,
            String artistName,
            String coverUrl,
            String style
    ) implements Serializable {
    }

    public record CreateArtistRequest(
            String name,
            List<String> translatedNames,
            String countryRegion,
            String style,
            String introduction,
            String avatarUrl,
            Actor actor
    ) implements Serializable {
    }

    public record UpdateArtistRequest(
            String id,
            String name,
            List<String> translatedNames,
            String countryRegion,
            String style,
            String introduction,
            String avatarUrl,
            Actor actor
    ) implements Serializable {
    }

    public record ArtistStatusRequest(
            String id,
            boolean online,
            Actor actor
    ) implements Serializable {
    }

    public record ArtistDeleteRequest(
            String id,
            Actor actor
    ) implements Serializable {
    }

    public record BatchDeleteRequest(
            List<String> ids,
            Actor actor
    ) implements Serializable {

        public BatchDeleteRequest {
            ids = ids == null
                    ? List.of()
                    : List.copyOf(ids);
        }
    }

    public record CreateSongRequest(
            String name,
            String albumId,
            List<String> artistIds,
            int durationSeconds,
            String style,
            String introduction,
            String coverUrl,
            String audioUrl,
            Actor actor
    ) implements Serializable {

        public CreateSongRequest {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }

    /**
     * 编辑歌曲时：
     * - style 不从前端接收；
     * - coverUrl 不从前端接收；
     * - 二者由 Provider 根据 albumId 从 album_tb 重新计算。
     */
    public record UpdateSongRequest(
            String id,
            String name,
            String albumId,
            List<String> artistIds,
            int durationSeconds,
            String introduction,
            String audioUrl,
            Actor actor
    ) implements Serializable {

        public UpdateSongRequest {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }

    public record CreateAlbumSongRequest(
            String name,
            List<String> artistIds,
            int durationSeconds,
            String introduction,
            String audioUrl
    ) implements Serializable {

        public CreateAlbumSongRequest {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }

    public record CreateAlbumWithSongsRequest(
            String name,
            List<String> artistIds,
            String style,
            String coverUrl,
            String releaseDate,
            String introduction,
            List<CreateAlbumSongRequest> songs,
            Actor actor
    ) implements Serializable {

        public CreateAlbumWithSongsRequest {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);

            songs = songs == null
                    ? List.of()
                    : List.copyOf(songs);
        }
    }

    /**
     * 编辑专辑只修改专辑元数据。
     * 具体歌曲名称、音乐人和音频文件在音乐管理页单独编辑。
     *
     * 但专辑 style / coverUrl 修改后，
     * Provider 会同步到该专辑下的所有歌曲。
     */
    public record UpdateAlbumRequest(
            String id,
            String name,
            List<String> artistIds,
            String style,
            String coverUrl,
            String releaseDate,
            String introduction,
            Actor actor
    ) implements Serializable {

        public UpdateAlbumRequest {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }

    public record AlbumCreateResult(
            AlbumItem album,
            List<SongItem> songs
    ) implements Serializable {

        public AlbumCreateResult {
            songs = songs == null
                    ? List.of()
                    : List.copyOf(songs);
        }
    }

    public record ReviewRequest(
            String id,
            String action,
            String reason,
            Actor actor
    ) implements Serializable {
    }

    public record PageResult<T>(
            List<T> records,
            long total,
            int page,
            int size,
            int totalPages
    ) implements Serializable {

        public PageResult {
            records = records == null
                    ? List.of()
                    : List.copyOf(records);
        }
    }
}
