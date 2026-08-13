package com.avemonica.avemusic.music.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * C端音乐人详情页 DTO。
 */
public final class ArtistPublicModels {

    private ArtistPublicModels() {
    }

    public record ArtistDetail(
            String id,
            String name,
            List<String> translatedNames,
            String ownerUserId,
            String avatarUrl,
            String countryRegion,
            String style,
            String introduction,
            long followerCount,
            long songCount,
            long albumCount,
            List<ArtistSongItem> songs,
            List<ArtistAlbumItem> albums
    ) implements Serializable {

        public ArtistDetail {
            songs = songs == null
                    ? List.of()
                    : List.copyOf(songs);

            albums = albums == null
                    ? List.of()
                    : List.copyOf(albums);
        }
    }

    public record ArtistSongItem(
            String id,
            String name,
            String artistName,
            List<String> artistIds,
            String albumId,
            String albumName,
            String coverUrl,
            String audioUrl,
            int durationSeconds,
            long playCount
    ) implements Serializable {

        public ArtistSongItem {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }

    public record ArtistAlbumItem(
            String id,
            String name,
            String coverUrl,
            String releaseDate,
            String style
    ) implements Serializable {
    }
}
