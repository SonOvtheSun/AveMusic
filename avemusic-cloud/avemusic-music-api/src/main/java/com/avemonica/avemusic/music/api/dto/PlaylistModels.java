package com.avemonica.avemusic.music.api.dto;

import java.io.Serializable;
import java.util.List;

public final class PlaylistModels {

    private PlaylistModels() {
    }

    /**
     * coverUrl：
     *   页面最终显示封面。
     *   用户自定义封面优先，否则自动使用第一首歌曲封面。
     *
     * customCoverUrl：
     *   playlist_tb.cover_url 原始值。
     *   编辑歌单时必须用这个字段，不能把自动封面误写回数据库。
     *
     * ownerUserId：
     *   创建该歌单的用户ID。
     *
     * favoriteCount：
     *   当前歌单被多少用户收藏。
     */
    public record PlaylistSummary(
            String id,
            String name,
            String introduction,
            String coverUrl,
            String customCoverUrl,
            String ownerUserId,
            String visibility,
            long songCount,
            long favoriteCount,
            String createdAt
    ) implements Serializable {
    }

    public record PlaylistSongItem(
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

        public PlaylistSongItem {
            artistIds = artistIds == null
                    ? List.of()
                    : List.copyOf(artistIds);
        }
    }

    /**
     * favoritedByMe：
     * 当前登录用户是否已经收藏该歌单。
     *
     * 自己创建的歌单永远返回 false。
     */
    public record PlaylistDetail(
            PlaylistSummary playlist,
            List<PlaylistSongItem> songs,
            boolean favoritedByMe
    ) implements Serializable {

        public PlaylistDetail {
            songs = songs == null
                    ? List.of()
                    : List.copyOf(songs);
        }
    }

    public record CreatePlaylistRequest(
            String userId,
            String name,
            String introduction,
            String coverUrl,
            String visibility
    ) implements Serializable {
    }

    public record UpdatePlaylistRequest(
            String userId,
            String playlistId,
            String name,
            String introduction,
            String coverUrl,
            String visibility
    ) implements Serializable {
    }

    public record AddSongRequest(
            String userId,
            String playlistId,
            String songId
    ) implements Serializable {
    }

    public record RemoveSongRequest(
            String userId,
            String playlistId,
            String songId
    ) implements Serializable {
    }

    public record PlaylistPage(
            List<PlaylistSummary> records,
            long total,
            int page,
            int size,
            int totalPages
    ) implements Serializable {

        public PlaylistPage {
            records = records == null
                    ? List.of()
                    : List.copyOf(records);
        }
    }
}
