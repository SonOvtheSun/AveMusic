package com.avemonica.avemusic.music.api.service;

import com.avemonica.avemusic.music.api.dto.ArtistPublicModels.ArtistDetail;
import com.avemonica.avemusic.music.api.dto.AlbumPublicModels.AlbumDetail;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumCreateResult;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.BatchDeleteRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumSearchItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistSearchItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistStatusRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistDeleteRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateAlbumWithSongsRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateArtistRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateSongRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateAlbumRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateArtistRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateSongRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ReviewRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.SongItem;
import com.avemonica.avemusic.music.api.dto.MusicModels.ArtistCard;
import com.avemonica.avemusic.music.api.dto.MusicModels.SongCard;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.PageResult;

import java.util.List;

public interface MusicService {

    List<SongCard> listHomeSongs(int limit);

    List<ArtistCard> listHomeArtists(int limit);

    PageResult<SongItem> pageManagedSongs(
            String keyword,
            int page,
            int size
    );

    PageResult<AlbumItem> pageManagedAlbums(
            String keyword,
            int page,
            int size
    );

    /**
     * C端公开音乐人详情。
     * 仅返回已审核且已上架音乐人，以及其已审核/已上架作品。
     */
    ArtistDetail getArtistDetail(String artistId);

    /**
     * C端公开专辑详情。
     * 只返回审核通过的专辑及其中已审核、已上架的歌曲。
     */
    AlbumDetail getAlbumDetail(
            String albumId
    );

    /**
     * playSession 创建时由 Gateway 调用。
     *
     * 只返回已审核、已上架、可公开播放歌曲的时长；
     * 不满足条件时按歌曲不存在处理。
     */
    int getPlayableSongDuration(
            String songId
    );

    /**
     * Redis playSession 在服务端确认达到有效播放阈值后调用。
     * Provider 使用数据库原子 +1，并返回最新播放量。
     */
    long incrementPlayCount(
            String songId
    );

    List<SongItem> listManagedSongs();

    List<AlbumItem> listManagedAlbums();

    List<ArtistItem> listManagedArtists();

    List<SongItem> listAuditSongs();

    List<AlbumItem> listAuditAlbums();

    List<ArtistItem> listAuditArtists();

    List<ArtistSearchItem> searchArtists(
            String keyword,
            int limit
    );

    List<AlbumSearchItem> searchAlbums(
            String keyword,
            int limit
    );

    ArtistSearchItem createArtist(
            CreateArtistRequest request
    );

    ArtistItem updateArtist(
            UpdateArtistRequest request
    );

    void setArtistOnline(
            ArtistStatusRequest request
    );

    void deleteArtist(
            ArtistDeleteRequest request
    );

    void deleteSongs(
            BatchDeleteRequest request
    );

    void deleteAlbums(
            BatchDeleteRequest request
    );

    void deleteArtists(
            BatchDeleteRequest request
    );

    SongItem createSong(
            CreateSongRequest request
    );

    SongItem updateSong(
            UpdateSongRequest request
    );

    AlbumCreateResult createAlbumWithSongs(
            CreateAlbumWithSongsRequest request
    );

    AlbumItem updateAlbum(
            UpdateAlbumRequest request
    );

    void reviewSong(
            ReviewRequest request
    );

    void reviewAlbum(
            ReviewRequest request
    );

    void reviewArtist(
            ReviewRequest request
    );
}
