package com.avemonica.avemusic.music.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 首页展示使用的轻量 RPC DTO。
 */
public final class MusicModels {

    private MusicModels() {
    }

    /**
     * 首页歌曲现在同时返回播放器所需的音频地址与时长。
     */
    public record SongCard(
            String id,
            String name,
            String artistName,
            List<String> artistIds,
            String coverUrl,
            String audioUrl,
            int durationSeconds,
            long playCount
    ) implements Serializable {

        public SongCard {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }

    public record ArtistCard(
            String id,
            String name,
            List<String> translatedNames,
            String avatarUrl,
            String countryRegion,
            long followerCount
    ) implements Serializable {
    }

    public record SearchItem(
            String type,
            String id,
            String name,
            String subtitle,
            String coverUrl,
            String audioUrl,
            int durationSeconds,
            long popularity,
            List<String> artistIds
    ) implements Serializable {

        public SearchItem {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }


    public record SearchResult(
            String keyword,
            List<String> expandedKeywords,
            List<SearchItem> songs,
            List<SearchItem> artists,
            List<SearchItem> albums,
            List<SearchItem> playlists
    ) implements Serializable {

        public SearchResult {
            expandedKeywords =
                    expandedKeywords == null
                            ? List.of()
                            : List.copyOf(
                            expandedKeywords
                    );

            songs = songs == null
                    ? List.of()
                    : List.copyOf(songs);

            artists = artists == null
                    ? List.of()
                    : List.copyOf(artists);

            albums = albums == null
                    ? List.of()
                    : List.copyOf(albums);

            playlists = playlists == null
                    ? List.of()
                    : List.copyOf(playlists);
        }
    }

    public record ArtistDirectoryItem(
            String id,
            String name,
            String avatarUrl,
            long songCount
    ) implements Serializable {
    }


    public record ArtistDirectoryResult(
            List<ArtistDirectoryItem> records,
            long total,
            int page,
            int pageSize
    ) implements Serializable {

        public ArtistDirectoryResult {

            records =
                    records == null
                            ? List.of()
                            : List.copyOf(
                            records
                    );
        }
    }
}
