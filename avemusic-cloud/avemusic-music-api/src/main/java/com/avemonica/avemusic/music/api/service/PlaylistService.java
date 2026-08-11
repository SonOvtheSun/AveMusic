package com.avemonica.avemusic.music.api.service;

import com.avemonica.avemusic.music.api.dto.PlaylistModels.AddSongRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.CreatePlaylistRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.PlaylistDetail;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.PlaylistSummary;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.RemoveSongRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.UpdatePlaylistRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.PlaylistPage;

import java.util.List;

public interface PlaylistService {

    /**
     * 当前用户自己创建的歌单。
     */
    List<PlaylistSummary> listMine(
            String userId
    );

    /**
     * 当前用户收藏的别人公开歌单。
     */
    List<PlaylistSummary> listFavorites(
            String userId
    );

    /**
     * 查看歌单详情：
     *
     * - 自己的 PUBLIC / PRIVATE 均可查看；
     * - 别人的仅 PUBLIC 可查看。
     */
    PlaylistDetail getDetail(
            String viewerUserId,
            String playlistId
    );

    PlaylistSummary createPlaylist(
            CreatePlaylistRequest request
    );

    PlaylistSummary updatePlaylist(
            UpdatePlaylistRequest request
    );

    PlaylistPage pagePopularPlaylists(
            int page
    );

    void addSong(
            AddSongRequest request
    );

    void removeSong(
            RemoveSongRequest request
    );

    /**
     * 收藏别人的公开歌单。
     */
    void favoritePlaylist(
            String userId,
            String playlistId
    );

    /**
     * 取消收藏。
     */
    void unfavoritePlaylist(
            String userId,
            String playlistId
    );
}
