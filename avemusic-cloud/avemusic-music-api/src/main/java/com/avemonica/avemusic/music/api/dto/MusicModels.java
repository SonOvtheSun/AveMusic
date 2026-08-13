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
}
