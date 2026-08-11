package com.avemonica.avemusic.music.provider.service;

import com.avemonica.avemusic.music.api.dto.PlaylistModels.AddSongRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.CreatePlaylistRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.PlaylistDetail;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.PlaylistSongItem;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.PlaylistSummary;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.RemoveSongRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.UpdatePlaylistRequest;
import com.avemonica.avemusic.music.api.enums.MusicErrorCode;
import com.avemonica.avemusic.music.api.service.PlaylistService;
import com.avemonica.avemusic.music.provider.entity.PlaylistDO;
import com.avemonica.avemusic.music.provider.entity.PlaylistFavoriteDO;
import com.avemonica.avemusic.music.provider.entity.PlaylistSongDO;
import com.avemonica.avemusic.music.provider.entity.SongDO;
import com.avemonica.avemusic.music.provider.mapper.PlaylistFavoriteMapper;
import com.avemonica.avemusic.music.provider.mapper.PlaylistMapper;
import com.avemonica.avemusic.music.provider.mapper.PlaylistSongMapper;
import com.avemonica.avemusic.music.provider.mapper.SongMapper;
import com.avemonica.minirpc.core.exception.RpcBusinessException;
import com.avemonica.minirpc.spring.annotation.MiniRpcService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import com.avemonica.avemusic.music.api.dto
        .PlaylistModels.PlaylistPage;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@MiniRpcService(
        interfaceClass = PlaylistService.class,
        group = "music",
        version = "1.0.0"
)
public class PlaylistServiceImpl
        implements PlaylistService {

    private static final String PUBLIC =
            "PUBLIC";

    private static final String PRIVATE =
            "PRIVATE";

    private static final String APPROVED =
            "APPROVED";

    private static final int
            PUBLIC_PLAYLIST_PAGE_SIZE =
            20;

    private static final int
            MAX_PUBLIC_PLAYLIST_PAGES =
            20;

    private static final long
            MAX_PUBLIC_PLAYLIST_COUNT =
            (long)
                    PUBLIC_PLAYLIST_PAGE_SIZE
                    * MAX_PUBLIC_PLAYLIST_PAGES;

    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final PlaylistFavoriteMapper playlistFavoriteMapper;
    private final SongMapper songMapper;

    public PlaylistServiceImpl(
            PlaylistMapper playlistMapper,
            PlaylistSongMapper playlistSongMapper,
            PlaylistFavoriteMapper playlistFavoriteMapper,
            SongMapper songMapper
    ) {
        this.playlistMapper =
                playlistMapper;

        this.playlistSongMapper =
                playlistSongMapper;

        this.playlistFavoriteMapper =
                playlistFavoriteMapper;

        this.songMapper =
                songMapper;
    }

    @Override
    public List<PlaylistSummary> listMine(
            String userId
    ) {
        Long resolvedUserId =
                requiredId(userId);

        return playlistMapper
                .selectMine(
                        resolvedUserId
                )
                .stream()
                .map(
                        PlaylistServiceImpl
                                ::toSummary
                )
                .toList();
    }

    @Override
    public List<PlaylistSummary> listFavorites(
            String userId
    ) {
        Long resolvedUserId =
                requiredId(userId);

        return playlistMapper
                .selectFavorites(
                        resolvedUserId
                )
                .stream()
                .map(
                        PlaylistServiceImpl
                                ::toSummary
                )
                .toList();
    }

    @Override
    public PlaylistDetail getDetail(
            String viewerUserId,
            String playlistId
    ) {
        Long viewerId =
                requiredId(
                        viewerUserId
                );

        Long resolvedPlaylistId =
                requiredId(
                        playlistId
                );

        PlaylistDO playlist =
                playlistMapper.selectById(
                        resolvedPlaylistId
                );

        if (playlist == null) {
            throw invalid(
                    "歌单不存在"
            );
        }

        boolean owner =
                viewerId.equals(
                        playlist.getUserId()
                );

        /*
         * 自己的歌单：
         * PUBLIC / PRIVATE 均可查看。
         *
         * 别人的歌单：
         * 只有 PUBLIC 才允许访问。
         */
        if (
                !owner
                        && !PUBLIC.equals(
                        playlist.getVisibility()
                )
        ) {
            throw invalid(
                    "歌单不存在或无权访问"
            );
        }

        PlaylistSummary summary =
                findSummary(
                        playlist.getId()
                );

        List<PlaylistSongItem> songs =
                playlistSongMapper
                        .selectPlaylistSongs(
                                playlist.getId()
                        )
                        .stream()
                        .map(
                                PlaylistServiceImpl
                                        ::toSongItem
                        )
                        .toList();

        boolean favoritedByMe =
                !owner
                        && favoriteExists(
                        viewerId,
                        playlist.getId()
                );

        return new PlaylistDetail(
                summary,
                songs,
                favoritedByMe
        );
    }

    @Override
    @Transactional
    public PlaylistSummary createPlaylist(
            CreatePlaylistRequest request
    ) {
        if (request == null) {
            throw invalid(
                    "歌单信息不能为空"
            );
        }

        Long userId =
                requiredId(
                        request.userId()
                );

        String name =
                validateName(
                        request.name()
                );

        String introduction =
                validateIntroduction(
                        request.introduction()
                );

        String coverUrl =
                validateCoverUrl(
                        request.coverUrl()
                );

        String visibility =
                resolveVisibility(
                        request.visibility()
                );

        LocalDateTime now =
                LocalDateTime.now();

        PlaylistDO playlist =
                new PlaylistDO();

        playlist.setUserId(userId);
        playlist.setName(name);
        playlist.setIntroduction(
                introduction
        );
        playlist.setCoverUrl(
                coverUrl
        );
        playlist.setVisibility(
                visibility
        );
        playlist.setFavoriteCount(0L);
        playlist.setCreatedAt(now);
        playlist.setUpdatedAt(now);

        playlistMapper.insert(
                playlist
        );

        return findSummary(
                playlist.getId()
        );
    }

    @Override
    @Transactional
    public PlaylistSummary updatePlaylist(
            UpdatePlaylistRequest request
    ) {
        if (request == null) {
            throw invalid(
                    "歌单信息不能为空"
            );
        }

        Long userId =
                requiredId(
                        request.userId()
                );

        Long playlistId =
                requiredId(
                        request.playlistId()
                );

        PlaylistDO playlist =
                requireOwnedPlaylist(
                        userId,
                        playlistId
                );

        playlist.setName(
                validateName(
                        request.name()
                )
        );

        playlist.setIntroduction(
                validateIntroduction(
                        request.introduction()
                )
        );

        playlist.setCoverUrl(
                validateCoverUrl(
                        request.coverUrl()
                )
        );

        playlist.setVisibility(
                resolveVisibility(
                        request.visibility()
                )
        );

        playlist.setUpdatedAt(
                LocalDateTime.now()
        );

        playlistMapper.updateById(
                playlist
        );

        return findSummary(
                playlistId
        );
    }

    @Override
    @Transactional
    public void addSong(
            AddSongRequest request
    ) {
        if (request == null) {
            throw invalid(
                    "收藏参数不能为空"
            );
        }

        Long userId =
                requiredId(
                        request.userId()
                );

        Long playlistId =
                requiredId(
                        request.playlistId()
                );

        Long songId =
                requiredId(
                        request.songId()
                );

        requireOwnedPlaylist(
                userId,
                playlistId
        );

        SongDO song =
                songMapper.selectById(
                        songId
                );

        if (
                song == null
                        || song.getStatus() == null
                        || song.getStatus() != 1
                        || !APPROVED.equals(
                        song.getAuditStatus()
                )
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode.SONG_NOT_FOUND,
                    "歌曲不存在或当前不可收藏"
            );
        }

        Long exists =
                playlistSongMapper
                        .selectCount(
                                new LambdaQueryWrapper<
                                        PlaylistSongDO
                                        >()
                                        .eq(
                                                PlaylistSongDO
                                                        ::getPlaylistId,
                                                playlistId
                                        )
                                        .eq(
                                                PlaylistSongDO
                                                        ::getSongId,
                                                songId
                                        )
                        );

        if (
                exists != null
                        && exists > 0
        ) {
            throw invalid(
                    "歌曲已经收藏在该歌单中"
            );
        }

        PlaylistSongDO relation =
                new PlaylistSongDO();

        relation.setPlaylistId(
                playlistId
        );
        relation.setSongId(
                songId
        );
        relation.setCreatedAt(
                LocalDateTime.now()
        );

        try {
            playlistSongMapper.insert(
                    relation
            );
        } catch (
                DuplicateKeyException exception
        ) {
            throw invalid(
                    "歌曲已经收藏在该歌单中"
            );
        }
    }

    @Override
    public PlaylistPage
    pagePopularPlaylists(
            int page
    ) {
        int resolvedPage =
                Math.max(
                        1,
                        Math.min(
                                page,
                                MAX_PUBLIC_PLAYLIST_PAGES
                        )
                );

        int size =
                PUBLIC_PLAYLIST_PAGE_SIZE;

        long databaseTotal =
                playlistMapper
                        .countPublicPlaylists();

        /*
         * 页面只开放排行榜前400个。
         */
        long visibleTotal =
                Math.min(
                        databaseTotal,
                        MAX_PUBLIC_PLAYLIST_COUNT
                );

        int totalPages =
                visibleTotal == 0
                        ? 0
                        : (int) (
                        (
                                visibleTotal
                                + size
                                - 1
                        )
                        / size
                );

        int offset =
                (resolvedPage - 1)
                        * size;

        List<PlaylistSummary> records;

        if (
                offset >= visibleTotal
        ) {
            records =
                    List.of();
        } else {
            records =
                    playlistMapper
                            .selectPublicRanking(
                                    offset,
                                    size
                            )
                            .stream()
                            .map(
                                    PlaylistServiceImpl
                                            ::toSummary
                            )
                            .toList();
        }

        return new PlaylistPage(
                records,
                visibleTotal,
                resolvedPage,
                size,
                totalPages
        );
    }

    @Override
    @Transactional
    public void removeSong(
            RemoveSongRequest request
    ) {
        if (request == null) {
            throw invalid(
                    "删除参数不能为空"
            );
        }

        Long userId =
                requiredId(
                        request.userId()
                );

        Long playlistId =
                requiredId(
                        request.playlistId()
                );

        Long songId =
                requiredId(
                        request.songId()
                );

        requireOwnedPlaylist(
                userId,
                playlistId
        );

        playlistSongMapper.delete(
                new LambdaQueryWrapper<
                        PlaylistSongDO
                        >()
                        .eq(
                                PlaylistSongDO
                                        ::getPlaylistId,
                                playlistId
                        )
                        .eq(
                                PlaylistSongDO
                                        ::getSongId,
                                songId
                        )
        );
    }

    @Override
    @Transactional
    public void favoritePlaylist(
            String userId,
            String playlistId
    ) {
        Long resolvedUserId =
                requiredId(userId);

        Long resolvedPlaylistId =
                requiredId(playlistId);

        PlaylistDO playlist =
                playlistMapper.selectById(
                        resolvedPlaylistId
                );

        if (playlist == null) {
            throw invalid(
                    "歌单不存在"
            );
        }

        /*
         * 不能收藏自己的歌单。
         */
        if (
                resolvedUserId.equals(
                        playlist.getUserId()
                )
        ) {
            throw invalid(
                    "不能收藏自己创建的歌单"
            );
        }

        /*
         * 私密歌单不能被其他用户收藏。
         */
        if (
                !PUBLIC.equals(
                        playlist.getVisibility()
                )
        ) {
            throw invalid(
                    "私密歌单不可收藏"
            );
        }

        if (
                favoriteExists(
                        resolvedUserId,
                        resolvedPlaylistId
                )
        ) {
            throw invalid(
                    "已经收藏该歌单"
            );
        }

        PlaylistFavoriteDO favorite =
                new PlaylistFavoriteDO();

        favorite.setUserId(
                resolvedUserId
        );
        favorite.setPlaylistId(
                resolvedPlaylistId
        );
        favorite.setCreatedAt(
                LocalDateTime.now()
        );

        try {
            playlistFavoriteMapper.insert(
                    favorite
            );

            playlistMapper
                    .incrementFavoriteCount(
                            resolvedPlaylistId
                    );
        } catch (
                DuplicateKeyException exception
        ) {
            throw invalid(
                    "已经收藏该歌单"
            );
        }
    }

    @Override
    @Transactional
    public void unfavoritePlaylist(
            String userId,
            String playlistId
    ) {
        Long resolvedUserId =
                requiredId(userId);

        Long resolvedPlaylistId =
                requiredId(playlistId);

        PlaylistDO playlist =
                playlistMapper.selectById(
                        resolvedPlaylistId
                );

        if (playlist == null) {
            /*
             * DELETE 保持幂等。
             */
            return;
        }

        if (
                resolvedUserId.equals(
                        playlist.getUserId()
                )
        ) {
            throw invalid(
                    "不能收藏或取消收藏自己创建的歌单"
            );
        }

        int deleted =
                playlistFavoriteMapper.delete(
                        new LambdaQueryWrapper<
                                PlaylistFavoriteDO
                                >()
                                .eq(
                                        PlaylistFavoriteDO
                                                ::getUserId,
                                        resolvedUserId
                                )
                                .eq(
                                        PlaylistFavoriteDO
                                                ::getPlaylistId,
                                        resolvedPlaylistId
                                )
                );

        if (deleted > 0) {
            playlistMapper
                    .decrementFavoriteCount(
                            resolvedPlaylistId
                    );
        }
    }

    private boolean favoriteExists(
            Long userId,
            Long playlistId
    ) {
        Long count =
                playlistFavoriteMapper
                        .selectCount(
                                new LambdaQueryWrapper<
                                        PlaylistFavoriteDO
                                        >()
                                        .eq(
                                                PlaylistFavoriteDO
                                                        ::getUserId,
                                                userId
                                        )
                                        .eq(
                                                PlaylistFavoriteDO
                                                        ::getPlaylistId,
                                                playlistId
                                        )
                        );

        return count != null
                && count > 0;
    }

    private PlaylistDO requireOwnedPlaylist(
            Long userId,
            Long playlistId
    ) {
        PlaylistDO playlist =
                playlistMapper.selectById(
                        playlistId
                );

        if (
                playlist == null
                        || !userId.equals(
                        playlist.getUserId()
                )
        ) {
            throw invalid(
                    "歌单不存在或无权操作"
            );
        }

        return playlist;
    }

    private PlaylistSummary findSummary(
            Long playlistId
    ) {
        Map<String, Object> row =
                playlistMapper
                        .selectSummary(
                                playlistId
                        );

        if (
                row == null
                        || row.isEmpty()
        ) {
            throw invalid(
                    "歌单不存在"
            );
        }

        return toSummary(row);
    }

    private static PlaylistSummary toSummary(
            Map<String, Object> row
    ) {
        return new PlaylistSummary(
                text(
                        row,
                        "playlistId"
                ),
                text(
                        row,
                        "playlistName"
                ),
                nullableText(
                        row,
                        "introduction"
                ),
                nullableText(
                        row,
                        "coverUrl"
                ),
                nullableText(
                        row,
                        "customCoverUrl"
                ),
                text(
                        row,
                        "ownerUserId"
                ),
                text(
                        row,
                        "visibility"
                ),
                longValue(
                        row,
                        "songCount"
                ),
                longValue(
                        row,
                        "favoriteCount"
                ),
                text(
                        row,
                        "createdAt"
                )
        );
    }

    private static PlaylistSongItem toSongItem(
            Map<String, Object> row
    ) {
        return new PlaylistSongItem(
                text(
                        row,
                        "songId"
                ),
                text(
                        row,
                        "songName"
                ),
                text(
                        row,
                        "artistName"
                ),
                csvList(
                        row,
                        "artistIds"
                ),
                nullableText(
                        row,
                        "albumName"
                ),
                nullableText(
                        row,
                        "coverUrl"
                ),
                nullableText(
                        row,
                        "audioUrl"
                ),
                intValue(
                        row,
                        "durationSeconds"
                ),
                longValue(
                        row,
                        "playCount"
                )
        );
    }

    private static String validateName(
            String value
    ) {
        String name =
                requiredText(
                        value,
                        "歌单名称不能为空"
                );

        if (name.length() > 128) {
            throw invalid(
                    "歌单名称不能超过128个字符"
            );
        }

        return name;
    }

    private static String validateIntroduction(
            String value
    ) {
        String introduction =
                nullableText(value);

        if (
                introduction != null
                        && introduction.length() > 1000
        ) {
            throw invalid(
                    "歌单简介不能超过1000个字符"
            );
        }

        return introduction;
    }

    private static String validateCoverUrl(
            String value
    ) {
        String coverUrl =
                nullableText(value);

        if (
                coverUrl != null
                        && coverUrl.length() > 512
        ) {
            throw invalid(
                    "歌单封面URL过长"
            );
        }

        return coverUrl;
    }

    private static String resolveVisibility(
            String value
    ) {
        String resolved =
                value == null
                        ? PRIVATE
                        : value.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !PUBLIC.equals(resolved)
                        && !PRIVATE.equals(resolved)
        ) {
            throw invalid(
                    "歌单类型只能是PUBLIC或PRIVATE"
            );
        }

        return resolved;
    }

    private static Long requiredId(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            throw invalid(
                    "ID不能为空"
            );
        }

        try {
            long id =
                    Long.parseLong(
                            value.trim()
                    );

            if (id <= 0) {
                throw new NumberFormatException();
            }

            return id;
        } catch (
                NumberFormatException exception
        ) {
            throw invalid(
                    "ID格式不正确"
            );
        }
    }

    private static String requiredText(
            String value,
            String message
    ) {
        String resolved =
                nullableText(value);

        if (resolved == null) {
            throw invalid(message);
        }

        return resolved;
    }

    private static String nullableText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String resolved =
                value.trim();

        return resolved.isEmpty()
                ? null
                : resolved;
    }

    private static String nullableText(
            Map<String, Object> row,
            String key
    ) {
        Object value =
                row.get(key);

        if (value == null) {
            return null;
        }

        String text =
                value.toString();

        return text.isBlank()
                ? null
                : text;
    }

    private static String text(
            Map<String, Object> row,
            String key
    ) {
        Object value =
                row.get(key);

        return value == null
                ? ""
                : value.toString();
    }

    private static int intValue(
            Map<String, Object> row,
            String key
    ) {
        Object value =
                row.get(key);

        if (value == null) {
            return 0;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        return Integer.parseInt(
                value.toString()
        );
    }

    private static long longValue(
            Map<String, Object> row,
            String key
    ) {
        Object value =
                row.get(key);

        if (value == null) {
            return 0L;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(
                value.toString()
        );
    }

    private static List<String> csvList(
            Map<String, Object> row,
            String key
    ) {
        String value =
                nullableText(
                        row,
                        key
                );

        if (value == null) {
            return List.of();
        }

        return Arrays.stream(
                        value.split(",")
                )
                .map(String::trim)
                .filter(
                        item ->
                                !item.isBlank()
                )
                .toList();
    }

    private static RpcBusinessException invalid(
            String message
    ) {
        return new RpcBusinessException(
                MusicErrorCode.INVALID_PARAMETER,
                message
        );
    }
}
