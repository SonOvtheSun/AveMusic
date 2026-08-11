package com.avemonica.avemusic.music.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * C端公开专辑详情 DTO。
 *
 * 与后台管理 DTO 分离，避免把审核状态、创建人等管理字段
 * 暴露给公开页面。
 */
public final class AlbumPublicModels {

    private AlbumPublicModels() {
    }

    public record AlbumDetail(
            String id,
            String name,
            String coverUrl,
            String artistName,
            List<String> artistIds,
            String artistAvatarUrl,
            String releaseDate,
            String style,
            String introduction,
            List<AlbumSongItem> songs
    ) implements Serializable {

        public AlbumDetail {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);

            songs = songs == null
                    ? List.of()
                    : List.copyOf(songs);
        }
    }

    public record AlbumSongItem(
            String id,
            String name,
            String artistName,
            List<String> artistIds,
            String albumName,
            String coverUrl,
            String audioUrl,
            int durationSeconds,
            long playCount
    ) implements Serializable {

        public AlbumSongItem {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }
}
