package com.avemonica.avemusic.music.provider.service;

import com.avemonica.avemusic.common.security.UserRole;
import com.avemonica.avemusic.music.api.dto.AlbumPublicModels.AlbumDetail;
import com.avemonica.avemusic.music.api.dto.AlbumPublicModels.AlbumSongItem;
import com.avemonica.avemusic.music.api.dto.ArtistPublicModels.ArtistAlbumItem;
import com.avemonica.avemusic.music.api.dto.ArtistPublicModels.ArtistDetail;
import com.avemonica.avemusic.music.api.dto.ArtistPublicModels.ArtistSongItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.Actor;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumCreateResult;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumSearchItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.BatchDeleteRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistSearchItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistStatusRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistDeleteRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateAlbumSongRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateAlbumWithSongsRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateArtistRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateSongRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateArtistRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateSongRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateAlbumRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ReviewRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.SongItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.PageResult;
import com.avemonica.avemusic.music.api.dto.MusicModels;
import com.avemonica.avemusic.music.api.dto.MusicModels.ArtistDirectoryItem;
import com.avemonica.avemusic.music.api.dto.MusicModels.ArtistDirectoryResult;
import com.avemonica.avemusic.music.api.dto.MusicModels.SearchItem;
import com.avemonica.avemusic.music.api.dto.MusicModels.SearchResult;
import com.avemonica.avemusic.music.api.dto.MusicModels.ArtistCard;
import com.avemonica.avemusic.music.api.dto.MusicModels.SongCard;
import com.avemonica.avemusic.music.provider.util.ArtistAreaUtil;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.github.houbb.opencc4j.util.ZhJpConverterUtil;
import com.avemonica.avemusic.music.api.dto.MusicModels.SearchResult;
import com.avemonica.avemusic.music.api.enums.MusicErrorCode;
import com.avemonica.avemusic.music.api.service.MusicService;
import com.avemonica.avemusic.music.provider.client.OllamaClient;
import com.avemonica.avemusic.music.provider.entity.AlbumDO;
import com.avemonica.avemusic.music.provider.entity.ArtistDO;
import com.avemonica.avemusic.music.provider.entity.SongArtistDO;
import com.avemonica.avemusic.music.provider.entity.SongDO;
import com.avemonica.avemusic.music.provider.mapper.*;
import com.avemonica.avemusic.music.provider.util.ArtistInitialUtil;
import com.avemonica.minirpc.core.exception.RpcBusinessException;
import com.avemonica.minirpc.spring.annotation.MiniRpcService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@MiniRpcService(
        interfaceClass = MusicService.class,
        group = "music",
        version = "1.0.0"
)
public class MusicServiceImpl
        implements MusicService {

    private static final String PENDING =
            "PENDING";

    private static final String APPROVED =
            "APPROVED";

    private static final String REJECTED =
            "REJECTED";

    private static final int DEFAULT_SONG_LIMIT =
            8;

    private static final int MAX_SONG_LIMIT =
            20;

    private static final int DEFAULT_ARTIST_LIMIT =
            6;

    private static final int MAX_ARTIST_LIMIT =
            20;

    private static final Set<String>
            ARTIST_AREAS =
            Set.of(
                    "ALL",
                    "CN",
                    "EU_US",
                    "JP",
                    "KR",
                    "OTHER"
            );

    private static final Set<String>
            ARTIST_CATEGORIES =
            Set.of(
                    "ALL",
                    "MALE",
                    "FEMALE",
                    "BAND"
            );

    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final SongArtistMapper songArtistMapper;
    private final PlaylistMapper playlistMapper;
    private final OllamaClient ollamaClient;

    public MusicServiceImpl(
            SongMapper songMapper,
            AlbumMapper albumMapper,
            ArtistMapper artistMapper,
            SongArtistMapper songArtistMapper,
            PlaylistMapper playlistMapper,
            OllamaClient ollamaClient
    ) {
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.songArtistMapper = songArtistMapper;
        this.playlistMapper = playlistMapper;
        this.ollamaClient = ollamaClient;
    }

    @Override
    public List<SongCard> listHomeSongs(
            int limit
    ) {
        int resolvedLimit = resolveLimit(
                limit,
                DEFAULT_SONG_LIMIT,
                MAX_SONG_LIMIT
        );

        return songMapper
                .selectHomeSongs(
                        resolvedLimit
                )
                .stream()
                .map(
                        MusicServiceImpl
                                ::toSongCard
                )
                .toList();
    }

    @Override
    public PageResult<AlbumItem>
    pageManagedAlbums(
            String keyword,
            int page,
            int size
    ) {
        int resolvedPage =
                resolvePage(page);

        int resolvedSize =
                resolvePageSize(size);

        String normalized =
                nullableText(keyword);

        int offset =
                (resolvedPage - 1)
                        * resolvedSize;

        long total =
                albumMapper
                        .countManagementAlbums(
                                normalized
                        );

        List<AlbumItem> records =
                total == 0
                        ? List.of()
                        : albumMapper
                        .selectManagementAlbumPage(
                                normalized,
                                offset,
                                resolvedSize
                        )
                        .stream()
                        .map(
                                MusicServiceImpl
                                ::toAlbumItem
                        )
                        .toList();

        return new PageResult<>(
                records,
                total,
                resolvedPage,
                resolvedSize,
                totalPages(
                        total,
                        resolvedSize
                )
        );
    }

    @Override
    public PageResult<SongItem>
    pageManagedSongs(
            String keyword,
            int page,
            int size
    ) {
        int resolvedPage =
                resolvePage(page);

        int resolvedSize =
                resolvePageSize(size);

        String normalized =
                nullableText(keyword);

        int offset =
                (resolvedPage - 1)
                        * resolvedSize;

        long total =
                songMapper
                        .countManagementSongs(
                                normalized
                        );

        List<SongItem> records =
                total == 0
                        ? List.of()
                        : songMapper
                        .selectManagementSongPage(
                                normalized,
                                offset,
                                resolvedSize
                        )
                        .stream()
                        .map(
                                MusicServiceImpl
                                ::toSongItem
                        )
                        .toList();

        return new PageResult<>(
                records,
                total,
                resolvedPage,
                resolvedSize,
                totalPages(
                        total,
                        resolvedSize
                )
        );
    }

    @Override
    public SearchResult search(
            String keyword,
            int limit
    ) {
        String normalized =
                requiredText(
                        keyword,
                        "搜索内容不能为空"
                );

        if (
                normalized.length()
                        > 64
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .INVALID_PARAMETER,
                    "搜索内容不能超过64个字符"
            );
        }

        int resolvedLimit =
                resolveLimit(
                        limit,
                        5,
                        8
                );

        List<String> expanded =
                ollamaClient
                        .expandSearchKeywords(
                                normalized
                        );

        LinkedHashMap<
                String,
                String
                > unique =
                new LinkedHashMap<>();

        /*
         * 原始搜索词：
         * 同时加入原文 + 简体 + 繁体。
         */
        addSearchKeywordWithCjkVariants(
                unique,
                normalized
        );

        /*
         * AI 扩展出来的每一个搜索词，
         * 同样补齐简繁体。
         */
        for (
                String value
                : expanded
        ) {
            addSearchKeywordWithCjkVariants(
                    unique,
                    normalized
            );
        }

        List<String> keywords =
                List.copyOf(
                        unique.values()
                );

        System.out.println(
                "[Search] keyword="
                        + normalized
                        + ", keywords="
                        + keywords
        );

        return new SearchResult(
                normalized,

                expanded,

                songMapper
                        .searchPublic(
                                keywords,
                                normalized,
                                resolvedLimit
                        )
                        .stream()
                        .map(
                                MusicServiceImpl
                                        ::toSongGlobalSearchItem
                        )
                        .toList(),

                artistMapper
                        .searchPublic(
                                keywords,
                                normalized,
                                resolvedLimit
                        )
                        .stream()
                        .map(
                                MusicServiceImpl
                                        ::toArtistGlobalSearchItem
                        )
                        .toList(),

                albumMapper
                        .searchPublic(
                                keywords,
                                normalized,
                                resolvedLimit
                        )
                        .stream()
                        .map(
                                MusicServiceImpl
                                        ::toAlbumGlobalSearchItem
                        )
                        .toList(),

                playlistMapper
                        .searchPublic(
                                keywords,
                                normalized,
                                resolvedLimit
                        )
                        .stream()
                        .map(
                                MusicServiceImpl
                                        ::toPlaylistGlobalSearchItem
                        )
                        .toList()
        );
    }

    @Override
    public int getPlayableSongDuration(
            String songId
    ) {
        Long id =
                requiredId(songId);

        Integer duration =
                songMapper
                        .selectPlayableDurationSeconds(
                                id
                        );

        if (
                duration == null
                        || duration <= 0
        ) {
            throw business(
                    MusicErrorCode.SONG_NOT_FOUND
            );
        }

        return duration;
    }

    @Override
    @Transactional
    public long incrementPlayCount(
            String songId
    ) {
        Long id =
                requiredId(songId);

        int updated =
                songMapper.incrementPlayCount(
                        id
                );

        if (updated == 0) {
            throw business(
                    MusicErrorCode.SONG_NOT_FOUND
            );
        }

        Long playCount =
                songMapper.selectPlayCount(
                        id
                );

        return playCount == null
                ? 0L
                : playCount;
    }

    @Override
    public List<ArtistCard> listHomeArtists(
            int limit
    ) {
        int resolvedLimit = resolveLimit(
                limit,
                DEFAULT_ARTIST_LIMIT,
                MAX_ARTIST_LIMIT
        );

        return artistMapper
                .selectHomeArtists(
                        resolvedLimit
                )
                .stream()
                .map(
                        MusicServiceImpl
                                ::toArtistCard
                )
                .toList();
    }

    @Override
    public ArtistDetail getArtistDetail(
            String artistId
    ) {
        Long id = requiredId(artistId);

        Map<String, Object> detail =
                artistMapper
                        .selectPublicArtistDetail(id);

        if (detail == null
                || detail.isEmpty()) {
            throw business(
                    MusicErrorCode.ARTIST_NOT_FOUND
            );
        }

        List<ArtistSongItem> songs =
                artistMapper
                        .selectPublicArtistSongs(id)
                        .stream()
                        .map(
                                MusicServiceImpl
                                        ::toArtistSongItem
                        )
                        .toList();

        List<ArtistAlbumItem> albums =
                artistMapper
                        .selectPublicArtistAlbums(id)
                        .stream()
                        .map(
                                MusicServiceImpl
                                        ::toArtistAlbumItem
                        )
                        .toList();

        return new ArtistDetail(
                text(detail, "artistId"),
                text(detail, "artistName"),
                jsonStringList(
                        detail,
                        "translatedNames"
                ),
                nullableText(
                        detail,
                        "ownerUserId"
                ),
                nullableText(
                        detail,
                        "avatarUrl"
                ),
                nullableText(
                        detail,
                        "countryRegion"
                ),
                nullableText(detail, "style"),
                nullableText(
                        detail,
                        "introduction"
                ),
                longValue(
                        detail,
                        "followerCount"
                ),
                longValue(detail, "songCount"),
                longValue(detail, "albumCount"),
                songs,
                albums
        );
    }

    @Override
    public AlbumDetail getAlbumDetail(
            String albumId
    ) {
        Long id =
                requiredId(albumId);

        Map<String, Object> detail =
                albumMapper
                        .selectPublicAlbumDetail(
                                id
                        );

        if (
                detail == null
                        || detail.isEmpty()
        ) {
            throw business(
                    MusicErrorCode.ALBUM_NOT_FOUND
            );
        }

        List<AlbumSongItem> songs =
                albumMapper
                        .selectPublicAlbumSongs(
                                id
                        )
                        .stream()
                        .map(
                                MusicServiceImpl
                                        ::toAlbumSongItem
                        )
                        .toList();

        return new AlbumDetail(
                text(
                        detail,
                        "albumId"
                ),
                text(
                        detail,
                        "albumName"
                ),
                nullableText(
                        detail,
                        "coverUrl"
                ),
                text(
                        detail,
                        "artistName"
                ),
                csvList(
                        detail,
                        "artistIds"
                ),
                nullableText(
                        detail,
                        "artistAvatarUrl"
                ),
                nullableText(
                        detail,
                        "releaseDate"
                ),
                nullableText(
                        detail,
                        "style"
                ),
                nullableText(
                        detail,
                        "introduction"
                ),
                songs
        );
    }

    @Override
    public List<SongItem> listManagedSongs() {
        return songMapper
                .selectManagementSongs(false)
                .stream()
                .map(
                        MusicServiceImpl
                                ::toSongItem
                )
                .toList();
    }

    @Override
    public List<AlbumItem> listManagedAlbums() {
        return albumMapper
                .selectManagementAlbums(false)
                .stream()
                .map(
                        MusicServiceImpl
                                ::toAlbumItem
                )
                .toList();
    }

    @Override
    public List<ArtistItem> listManagedArtists() {
        return artistMapper
                .selectManagementArtists(false)
                .stream()
                .map(
                        MusicServiceImpl
                                ::toArtistItem
                )
                .toList();
    }

    @Override
    public List<SongItem> listAuditSongs() {
        return songMapper
                .selectManagementSongs(false)
                .stream()
                .map(
                        MusicServiceImpl
                                ::toSongItem
                )
                .toList();
    }

    @Override
    public List<AlbumItem> listAuditAlbums() {
        return albumMapper
                .selectManagementAlbums(false)
                .stream()
                .map(
                        MusicServiceImpl
                                ::toAlbumItem
                )
                .toList();
    }

    @Override
    public List<ArtistItem> listAuditArtists() {
        return artistMapper
                .selectManagementArtists(false)
                .stream()
                .map(
                        MusicServiceImpl
                                ::toArtistItem
                )
                .toList();
    }

    @Override
    public List<ArtistSearchItem> searchArtists(
            String keyword,
            int limit
    ) {
        String normalized = requiredText(
                keyword,
                "音乐人搜索关键词不能为空"
        );

        int resolvedLimit = resolveLimit(
                limit,
                10,
                20
        );

        return artistMapper
                .searchForSong(
                        normalized,
                        resolvedLimit
                )
                .stream()
                .map(
                        MusicServiceImpl
                                ::toArtistSearchItem
                )
                .toList();
    }

    @Override
    public List<AlbumSearchItem> searchAlbums(
            String keyword,
            int limit
    ) {
        String normalized = requiredText(
                keyword,
                "专辑搜索关键词不能为空"
        );

        int resolvedLimit = resolveLimit(
                limit,
                10,
                20
        );

        return albumMapper
                .searchForSong(
                        normalized,
                        resolvedLimit
                )
                .stream()
                .map(
                        MusicServiceImpl
                                ::toAlbumSearchItem
                )
                .toList();
    }

    @Override
    @Transactional
    public ArtistSearchItem createArtist(
            CreateArtistRequest request
    ) {
        if (request == null) {
            throw business(
                    MusicErrorCode.INVALID_PARAMETER
            );
        }

        ResolvedActor actor =
                resolveActor(request.actor());

        if (actor.role() != UserRole.SUPER_ADMIN
                && actor.role()
                != UserRole.OPERATOR) {
            throw business(
                    MusicErrorCode.PERMISSION_DENIED
            );
        }

        String name = requiredText(
                request.name(),
                "音乐人名称不能为空"
        );

        String countryRegion = requiredText(
                request.countryRegion(),
                "国家或地区不能为空"
        );

        boolean exists = artistMapper.exists(
                new LambdaQueryWrapper<ArtistDO>()
                        .eq(
                                ArtistDO::getName,
                                name
                        )
        );

        if (exists) {
            throw business(
                    MusicErrorCode.ARTIST_NAME_EXISTS
            );
        }

        boolean directApprove =
                actor.role()
                        == UserRole.SUPER_ADMIN;

        ArtistDO artist = new ArtistDO();
        artist.setName(name);
        artist.setTranslatedNames(
                normalizeTranslatedNames(
                        request.translatedNames()
                )
        );
        artist.setCountryRegion(
                countryRegion
        );
        artist.setAreaCode(
                ArtistAreaUtil.resolve(
                        countryRegion
                )
        );
        artist.setStyle(
                nullableText(request.style())
        );
        artist.setIntroduction(
                nullableText(
                        request.introduction()
                )
        );
        artist.setAvatarUrl(
                nullableText(
                        request.avatarUrl()
                )
        );
        artist.setNameInitial(
                ArtistInitialUtil.resolve(name)
        );
        artist.setFollowerCount(0L);
        artist.setAuditStatus(
                directApprove
                        ? APPROVED
                        : PENDING
        );
        artist.setStatus(
                directApprove ? 1 : 0
        );
        artist.setCreatedBy(
                actor.userId()
        );

        if (directApprove) {
            artist.setReviewedBy(
                    actor.userId()
            );
            artist.setReviewedAt(
                    LocalDateTime.now()
            );
        }

        try {
            artistMapper.insert(artist);
        } catch (DuplicateKeyException exception) {
            throw business(
                    MusicErrorCode.ARTIST_NAME_EXISTS
            );
        }

        return new ArtistSearchItem(
                String.valueOf(
                        artist.getId()
                ),

                artist.getName(),

                artist.getTranslatedNames(),

                artist.getAvatarUrl(),

                artist.getCountryRegion(),

                artist.getAuditStatus()
        );
    }

    @Override
    @Transactional
    public ArtistItem updateArtist(
            UpdateArtistRequest request
    ) {
        if (request == null) {
            throw business(MusicErrorCode.INVALID_PARAMETER);
        }

        ResolvedActor actor =
                resolveManageActor(request.actor());
        Long artistId = requiredId(request.id());
        ArtistDO artist = artistMapper.selectById(artistId);

        if (artist == null) {
            throw business(MusicErrorCode.ARTIST_NOT_FOUND);
        }

        String name = requiredText(
                request.name(),
                "音乐人名称不能为空"
        );
        String countryRegion = requiredText(
                request.countryRegion(),
                "国家或地区不能为空"
        );

        boolean duplicated = artistMapper.exists(
                new LambdaQueryWrapper<ArtistDO>()
                        .eq(ArtistDO::getName, name)
                        .ne(ArtistDO::getId, artistId)
        );

        if (duplicated) {
            throw business(MusicErrorCode.ARTIST_NAME_EXISTS);
        }

        artist.setName(name);
        artist.setTranslatedNames(
                normalizeTranslatedNames(
                        request.translatedNames()
                )
        );
        artist.setCountryRegion(
                countryRegion
        );

        artist.setAreaCode(
                ArtistAreaUtil.resolve(
                        countryRegion
                )
        );

        artist.setArtistType(
                normalizeArtistType(
                        request.artistType()
                )
        );

        artist.setStyle(
                nullableText(
                        request.style()
                )
        );

        artist.setIntroduction(
                nullableText(
                        request.introduction()
                )
        );

        artist.setAvatarUrl(
                nullableText(
                        request.avatarUrl()
                )
        );

        artist.setNameInitial(
                ArtistInitialUtil.resolve(
                        name
                )
        );

        boolean directApprove =
                actor.role() == UserRole.SUPER_ADMIN;

        artist.setAuditStatus(
                directApprove ? APPROVED : PENDING
        );
        artist.setStatus(directApprove ? 1 : 0);
        artist.setRejectReason(null);
        artist.setReviewedBy(
                directApprove ? actor.userId() : null
        );
        artist.setReviewedAt(
                directApprove ? LocalDateTime.now() : null
        );

        artistMapper.updateById(artist);
        return findArtistItem(artistId);
    }

    @Override
    public ArtistDirectoryResult listArtists(
            String area,
            String category,
            String initial,
            int page,
            int pageSize
    ) {
        String resolvedArea =
                normalizeArtistArea(
                        area
                );

        String resolvedCategory =
                normalizeArtistCategory(
                        category
                );

        String resolvedInitial =
                normalizeArtistInitial(
                        initial
                );

        int resolvedPage =
                Math.max(
                        page,
                        1
                );

        int resolvedPageSize =
                Math.max(
                        1,
                        Math.min(
                                pageSize,
                                50
                        )
                );

        int offset =
                (resolvedPage - 1)
                        * resolvedPageSize;

        long total =
                artistMapper
                        .countPublicArtistDirectory(
                                resolvedArea,
                                resolvedCategory,
                                resolvedInitial
                        );

        if (total <= 0) {

            return new ArtistDirectoryResult(
                    List.of(),
                    0,
                    resolvedPage,
                    resolvedPageSize
            );
        }

        List<ArtistDirectoryItem>
                records =
                artistMapper
                        .selectPublicArtistDirectory(
                                resolvedArea,
                                resolvedCategory,
                                resolvedInitial,
                                offset,
                                resolvedPageSize
                        )
                        .stream()
                        .map(
                                MusicServiceImpl
                                        ::toArtistDirectoryItem
                        )
                        .toList();

        return new ArtistDirectoryResult(
                records,
                total,
                resolvedPage,
                resolvedPageSize
        );
    }

    @Override
    @Transactional
    public void setArtistOnline(
            ArtistStatusRequest request
    ) {
        if (request == null) {
            throw business(MusicErrorCode.INVALID_PARAMETER);
        }

        resolveManageActor(request.actor());
        Long artistId = requiredId(request.id());
        ArtistDO artist = artistMapper.selectById(artistId);

        if (artist == null) {
            throw business(MusicErrorCode.ARTIST_NOT_FOUND);
        }

        if (request.online()
                && !APPROVED.equals(artist.getAuditStatus())) {
            throw business(MusicErrorCode.ARTIST_NOT_APPROVED);
        }

        artist.setStatus(request.online() ? 1 : 0);
        artistMapper.updateById(artist);
    }

    @Override
    @Transactional
    public void deleteArtist(
            ArtistDeleteRequest request
    ) {
        if (request == null) {
            throw business(MusicErrorCode.INVALID_PARAMETER);
        }

        deleteArtists(
                new BatchDeleteRequest(
                        List.of(request.id()),
                        request.actor()
                )
        );
    }

    @Override
    @Transactional
    public void deleteSongs(
            BatchDeleteRequest request
    ) {
        List<Long> songIds = resolveBatchIds(request);

        for (Long songId : songIds) {
            if (songMapper.selectById(songId) == null) {
                throw business(MusicErrorCode.SONG_NOT_FOUND);
            }
        }

        songArtistMapper.delete(
                new LambdaQueryWrapper<SongArtistDO>()
                        .in(
                                SongArtistDO::getSongId,
                                songIds
                        )
        );

        songMapper.delete(
                new LambdaQueryWrapper<SongDO>()
                        .in(
                                SongDO::getId,
                                songIds
                        )
        );
    }

    @Override
    @Transactional
    public void deleteAlbums(
            BatchDeleteRequest request
    ) {
        List<Long> albumIds = resolveBatchIds(request);

        for (Long albumId : albumIds) {
            if (albumMapper.selectById(albumId) == null) {
                throw business(MusicErrorCode.ALBUM_NOT_FOUND);
            }
        }

        /*
         * 删除专辑不删除歌曲。
         * 先把歌曲 album_id 置空，再删除专辑与音乐人的关联。
         */
        albumMapper.clearSongAlbumIds(albumIds);
        albumMapper.deleteArtistAlbumRelations(albumIds);

        albumMapper.delete(
                new LambdaQueryWrapper<AlbumDO>()
                        .in(
                                AlbumDO::getId,
                                albumIds
                        )
        );
    }

    @Override
    @Transactional
    public void deleteArtists(
            BatchDeleteRequest request
    ) {
        List<Long> artistIds = resolveBatchIds(request);

        /*
         * 先完整校验，再统一删除。
         * 任意一个音乐人仍被引用，则整个批次不删除，避免半成功。
         */
        for (Long artistId : artistIds) {
            ArtistDO artist =
                    artistMapper.selectById(artistId);

            if (artist == null) {
                throw business(
                        MusicErrorCode.ARTIST_NOT_FOUND
                );
            }

            if (artist.getOwnerUserId() != null
                    || artistMapper.countSongsByArtist(artistId) > 0
                    || artistMapper.countAlbumsByArtist(artistId) > 0) {
                throw business(
                        MusicErrorCode.ARTIST_IN_USE
                );
            }
        }

        artistMapper.delete(
                new LambdaQueryWrapper<ArtistDO>()
                        .in(
                                ArtistDO::getId,
                                artistIds
                        )
        );
    }

    @Override
    @Transactional
    public SongItem createSong(
            CreateSongRequest request
    ) {
        if (request == null) {
            throw business(
                    MusicErrorCode.INVALID_PARAMETER
            );
        }

        ResolvedActor actor =
                resolveManageActor(
                        request.actor()
                );

        String name = requiredText(
                request.name(),
                "音乐名称不能为空"
        );

        String audioUrl = requiredText(
                request.audioUrl(),
                "音乐文件地址不能为空"
        );

        if (request.durationSeconds() <= 0
                || request.durationSeconds()
                > 86_400) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "音乐时长必须是1到86400之间的整数"
            );
        }

        List<Long> artistIds =
                parseDistinctIds(
                        request.artistIds(),
                        "至少选择一位音乐人"
                );

        boolean hasPendingArtist =
                validateCreatableArtists(
                        artistIds
                );

        Long albumId = nullableId(
                request.albumId()
        );

        AlbumDO selectedAlbum = null;

        if (albumId != null) {
            selectedAlbum =
                    albumMapper.selectById(
                            albumId
                    );

            if (selectedAlbum == null
                    || !APPROVED.equals(
                    selectedAlbum
                            .getAuditStatus()
            )) {
                throw business(
                        MusicErrorCode
                                .ALBUM_NOT_FOUND
                );
            }
        }

        boolean directApprove =
                actor.role()
                        == UserRole.SUPER_ADMIN
                        && !hasPendingArtist;

        SongDO song = new SongDO();
        song.setAlbumId(albumId);
        song.setName(name);
        song.setDurationSeconds(
                request.durationSeconds()
        );

        /*
         * 歌曲风格与封面统一由专辑决定。
         * 无专辑时均为空。
         */
        song.setStyle(
                selectedAlbum == null
                        ? null
                        : selectedAlbum.getStyle()
        );

        song.setCoverUrl(
                selectedAlbum == null
                        ? null
                        : selectedAlbum.getCoverUrl()
        );

        song.setIntroduction(
                nullableText(
                        request.introduction()
                )
        );
        song.setAudioUrl(audioUrl);
        song.setPlayCount(0L);
        song.setStatus(
                directApprove ? 1 : 0
        );
        song.setAuditStatus(
                directApprove
                        ? APPROVED
                        : PENDING
        );
        song.setCreatedBy(actor.userId());
        song.setRejectReason(null);

        if (directApprove) {
            song.setReviewedBy(
                    actor.userId()
            );
            song.setReviewedAt(
                    LocalDateTime.now()
            );
        }

        songMapper.insert(song);

        replaceSongArtists(
                song.getId(),
                artistIds
        );

        return findSongItem(
                song.getId()
        );
    }

    @Override
    @Transactional
    public SongItem updateSong(
            UpdateSongRequest request
    ) {
        if (request == null) {
            throw business(
                    MusicErrorCode.INVALID_PARAMETER
            );
        }

        ResolvedActor actor =
                resolveManageActor(
                        request.actor()
                );

        Long songId =
                requiredId(request.id());

        SongDO song =
                songMapper.selectById(
                        songId
                );

        if (song == null) {
            throw business(
                    MusicErrorCode.SONG_NOT_FOUND
            );
        }

        String name = requiredText(
                request.name(),
                "音乐名称不能为空"
        );

        String audioUrl = requiredText(
                request.audioUrl(),
                "音乐文件地址不能为空"
        );

        if (request.durationSeconds() <= 0
                || request.durationSeconds()
                > 86_400) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "音乐时长必须是1到86400之间的整数"
            );
        }

        List<Long> artistIds =
                parseDistinctIds(
                        request.artistIds(),
                        "至少选择一位音乐人"
                );

        boolean hasPendingArtist =
                validateCreatableArtists(
                        artistIds
                );

        Long albumId =
                nullableId(
                        request.albumId()
                );

        AlbumDO selectedAlbum = null;

        if (albumId != null) {
            selectedAlbum =
                    albumMapper.selectById(
                            albumId
                    );

            if (selectedAlbum == null
                    || !APPROVED.equals(
                    selectedAlbum
                            .getAuditStatus()
            )) {
                throw business(
                        MusicErrorCode
                                .ALBUM_NOT_FOUND
                );
            }
        }

        boolean directApprove =
                actor.role()
                        == UserRole.SUPER_ADMIN
                        && !hasPendingArtist;

        song.setName(name);
        song.setAlbumId(albumId);
        song.setDurationSeconds(
                request.durationSeconds()
        );
        song.setIntroduction(
                nullableText(
                        request.introduction()
                )
        );
        song.setAudioUrl(audioUrl);

        /*
         * 编辑歌曲同样禁止前端直接决定封面和风格。
         */
        song.setStyle(
                selectedAlbum == null
                        ? null
                        : selectedAlbum.getStyle()
        );
        song.setCoverUrl(
                selectedAlbum == null
                        ? null
                        : selectedAlbum.getCoverUrl()
        );

        song.setAuditStatus(
                directApprove
                        ? APPROVED
                        : PENDING
        );
        song.setStatus(
                directApprove ? 1 : 0
        );
        song.setRejectReason(null);
        song.setReviewedBy(
                directApprove
                        ? actor.userId()
                        : null
        );
        song.setReviewedAt(
                directApprove
                        ? LocalDateTime.now()
                        : null
        );

        songMapper.updateById(song);

        replaceSongArtists(
                songId,
                artistIds
        );

        return findSongItem(songId);
    }

    @Override
    @Transactional
    public AlbumCreateResult createAlbumWithSongs(
            CreateAlbumWithSongsRequest request
    ) {
        if (request == null) {
            throw business(
                    MusicErrorCode.INVALID_PARAMETER
            );
        }

        ResolvedActor actor =
                resolveManageActor(
                        request.actor()
                );

        String albumName = requiredText(
                request.name(),
                "专辑名称不能为空"
        );

        String albumStyle = requiredText(
                request.style(),
                "专辑音乐风格不能为空"
        );

        String coverUrl = requiredText(
                request.coverUrl(),
                "专辑封面不能为空"
        );

        List<Long> albumArtistIds =
                parseDistinctIds(
                        request.artistIds(),
                        "专辑至少选择一位音乐人"
                );

        if (request.songs() == null
                || request.songs().isEmpty()) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "专辑至少需要包含一首音乐"
            );
        }

        if (request.songs().size() > 50) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "单张专辑最多一次新增50首音乐"
            );
        }

        boolean hasPendingDependency =
                validateCreatableArtists(
                        albumArtistIds
                );

        List<List<Long>> songArtistIds =
                new ArrayList<>(
                        request.songs().size()
                );

        for (CreateAlbumSongRequest songRequest
                : request.songs()) {

            if (songRequest == null) {
                throw new RpcBusinessException(
                        MusicErrorCode.INVALID_PARAMETER,
                        "专辑中的音乐信息不能为空"
                );
            }

            requiredText(
                    songRequest.name(),
                    "音乐名称不能为空"
            );

            requiredText(
                    songRequest.audioUrl(),
                    "音乐文件地址不能为空"
            );

            if (songRequest.durationSeconds() <= 0
                    || songRequest.durationSeconds()
                    > 86_400) {
                throw new RpcBusinessException(
                        MusicErrorCode.INVALID_PARAMETER,
                        "音乐时长必须是1到86400之间的整数"
                );
            }

            List<Long> resolvedArtistIds =
                    parseDistinctIds(
                            songRequest.artistIds(),
                            "每首音乐至少选择一位音乐人"
                    );

            if (validateCreatableArtists(
                    resolvedArtistIds
            )) {
                hasPendingDependency = true;
            }

            songArtistIds.add(
                    resolvedArtistIds
            );
        }

        LocalDate releaseDate =
                parseReleaseDate(
                        request.releaseDate()
                );

        boolean directApprove =
                actor.role()
                        == UserRole.SUPER_ADMIN
                        && !hasPendingDependency;

        LocalDateTime now =
                LocalDateTime.now();

        AlbumDO album = new AlbumDO();
        album.setName(albumName);
        album.setStyle(albumStyle);
        album.setCoverUrl(coverUrl);
        album.setReleaseDate(releaseDate);
        album.setFavoriteCount(0L);
        album.setIntroduction(
                nullableText(
                        request.introduction()
                )
        );
        album.setAuditStatus(
                directApprove
                        ? APPROVED
                        : PENDING
        );
        album.setCreatedBy(actor.userId());
        album.setRejectReason(null);

        if (directApprove) {
            album.setReviewedBy(
                    actor.userId()
            );
            album.setReviewedAt(now);
        }

        albumMapper.insert(album);

        replaceAlbumArtists(
                album.getId(),
                albumArtistIds
        );

        List<Long> createdSongIds =
                new ArrayList<>(
                        request.songs().size()
                );

        for (int index = 0;
             index < request.songs().size();
             index++) {

            CreateAlbumSongRequest songRequest =
                    request.songs().get(index);

            SongDO song = new SongDO();
            song.setAlbumId(album.getId());
            song.setName(
                    requiredText(
                            songRequest.name(),
                            "音乐名称不能为空"
                    )
            );
            song.setDurationSeconds(
                    songRequest.durationSeconds()
            );

            /*
             * 专辑中的所有歌曲统一继承专辑风格与封面。
             */
            song.setStyle(albumStyle);
            song.setCoverUrl(coverUrl);

            song.setIntroduction(
                    nullableText(
                            songRequest.introduction()
                    )
            );
            song.setAudioUrl(
                    requiredText(
                            songRequest.audioUrl(),
                            "音乐文件地址不能为空"
                    )
            );
            song.setPlayCount(0L);
            song.setStatus(
                    directApprove ? 1 : 0
            );
            song.setAuditStatus(
                    directApprove
                            ? APPROVED
                            : PENDING
            );
            song.setCreatedBy(
                    actor.userId()
            );
            song.setRejectReason(null);

            if (directApprove) {
                song.setReviewedBy(
                        actor.userId()
                );
                song.setReviewedAt(now);
            }

            songMapper.insert(song);

            replaceSongArtists(
                    song.getId(),
                    songArtistIds.get(index)
            );

            createdSongIds.add(
                    song.getId()
            );
        }

        return new AlbumCreateResult(
                findAlbumItem(
                        album.getId()
                ),
                createdSongIds.stream()
                        .map(this::findSongItem)
                        .toList()
        );
    }

    @Override
    @Transactional
    public AlbumItem updateAlbum(
            UpdateAlbumRequest request
    ) {
        if (request == null) {
            throw business(
                    MusicErrorCode.INVALID_PARAMETER
            );
        }

        ResolvedActor actor =
                resolveManageActor(
                        request.actor()
                );

        Long albumId =
                requiredId(request.id());

        AlbumDO album =
                albumMapper.selectById(
                        albumId
                );

        if (album == null) {
            throw business(
                    MusicErrorCode.ALBUM_NOT_FOUND
            );
        }

        String albumName = requiredText(
                request.name(),
                "专辑名称不能为空"
        );

        String albumStyle = requiredText(
                request.style(),
                "专辑音乐风格不能为空"
        );

        String coverUrl = requiredText(
                request.coverUrl(),
                "专辑封面不能为空"
        );

        List<Long> albumArtistIds =
                parseDistinctIds(
                        request.artistIds(),
                        "专辑至少选择一位音乐人"
                );

        boolean hasPendingAlbumArtist =
                validateCreatableArtists(
                        albumArtistIds
                );

        boolean albumDirectApprove =
                actor.role()
                        == UserRole.SUPER_ADMIN
                        && !hasPendingAlbumArtist;

        LocalDateTime now =
                LocalDateTime.now();

        album.setName(albumName);
        album.setStyle(albumStyle);
        album.setCoverUrl(coverUrl);
        album.setReleaseDate(
                parseReleaseDate(
                        request.releaseDate()
                )
        );
        album.setIntroduction(
                nullableText(
                        request.introduction()
                )
        );

        album.setAuditStatus(
                albumDirectApprove
                        ? APPROVED
                        : PENDING
        );
        album.setRejectReason(null);
        album.setReviewedBy(
                albumDirectApprove
                        ? actor.userId()
                        : null
        );
        album.setReviewedAt(
                albumDirectApprove
                        ? now
                        : null
        );

        albumMapper.updateById(album);

        replaceAlbumArtists(
                albumId,
                albumArtistIds
        );

        /*
         * 专辑封面与风格是歌曲继承字段。
         * 因此修改专辑时必须同步专辑中的全部歌曲。
         *
         * OPERATOR 修改：
         *   专辑与歌曲全部重新进入 PENDING，歌曲下架。
         *
         * SUPER_ADMIN 修改：
         *   专辑可直接通过；
         *   只有歌曲自身的音乐人也全部 APPROVED 时，
         *   该歌曲才能直接保持/恢复 APPROVED。
         */
        List<SongDO> albumSongs =
                songMapper.selectList(
                        new LambdaQueryWrapper<SongDO>()
                                .eq(
                                        SongDO::getAlbumId,
                                        albumId
                                )
                );

        for (SongDO song : albumSongs) {
            song.setStyle(albumStyle);
            song.setCoverUrl(coverUrl);

            boolean songDirectApprove =
                    albumDirectApprove
                            && songArtistsApproved(
                            song.getId()
                    );

            song.setAuditStatus(
                    songDirectApprove
                            ? APPROVED
                            : PENDING
            );
            song.setStatus(
                    songDirectApprove ? 1 : 0
            );
            song.setRejectReason(null);
            song.setReviewedBy(
                    songDirectApprove
                            ? actor.userId()
                            : null
            );
            song.setReviewedAt(
                    songDirectApprove
                            ? now
                            : null
            );

            songMapper.updateById(song);
        }

        return findAlbumItem(
                albumId
        );
    }

    @Override
    @Transactional
    public void reviewSong(
            ReviewRequest request
    ) {
        ResolvedReview review =
                resolveReview(request);

        SongDO song = songMapper.selectById(
                review.id()
        );

        if (song == null) {
            throw business(
                    MusicErrorCode.SONG_NOT_FOUND
            );
        }

        ensureReviewAllowed(
                song.getAuditStatus(),
                review.action()
        );

        if (review.action()
                == ReviewAction.APPROVE) {
            ensureSongDependenciesApproved(song);
        }

        song.setAuditStatus(
                nextAuditStatus(
                        review.action()
                )
        );

        song.setStatus(
                review.action()
                        == ReviewAction.APPROVE
                        ? 1
                        : 0
        );

        song.setReviewedBy(
                review.actor().userId()
        );

        song.setReviewedAt(
                LocalDateTime.now()
        );

        song.setRejectReason(
                review.action()
                        == ReviewAction.REJECT
                        ? review.reason()
                        : null
        );

        songMapper.updateById(song);
    }

    @Override
    @Transactional
    public void reviewAlbum(
            ReviewRequest request
    ) {
        ResolvedReview review =
                resolveReview(request);

        AlbumDO album =
                albumMapper.selectById(
                        review.id()
                );

        if (album == null) {
            throw business(
                    MusicErrorCode.ALBUM_NOT_FOUND
            );
        }

        ensureReviewAllowed(
                album.getAuditStatus(),
                review.action()
        );

        album.setAuditStatus(
                nextAuditStatus(
                        review.action()
                )
        );

        album.setReviewedBy(
                review.actor().userId()
        );

        album.setReviewedAt(
                LocalDateTime.now()
        );

        album.setRejectReason(
                review.action()
                        == ReviewAction.REJECT
                        ? review.reason()
                        : null
        );

        albumMapper.updateById(album);
    }

    @Override
    @Transactional
    public void reviewArtist(
            ReviewRequest request
    ) {
        ResolvedReview review =
                resolveReview(request);

        ArtistDO artist =
                artistMapper.selectById(
                        review.id()
                );

        if (artist == null) {
            throw business(
                    MusicErrorCode.ARTIST_NOT_FOUND
            );
        }

        ensureReviewAllowed(
                artist.getAuditStatus(),
                review.action()
        );

        artist.setAuditStatus(
                nextAuditStatus(
                        review.action()
                )
        );

        if (review.action() == ReviewAction.APPROVE) {
            artist.setStatus(1);
        } else if (review.action() == ReviewAction.REVOKE) {
            artist.setStatus(0);
        }

        artist.setReviewedBy(
                review.actor().userId()
        );

        artist.setReviewedAt(
                LocalDateTime.now()
        );

        artist.setRejectReason(
                review.action()
                        == ReviewAction.REJECT
                        ? review.reason()
                        : null
        );

        artistMapper.updateById(artist);
    }

    private void replaceSongArtists(
            Long songId,
            List<Long> artistIds
    ) {
        songArtistMapper.delete(
                new LambdaQueryWrapper<SongArtistDO>()
                        .eq(
                                SongArtistDO::getSongId,
                                songId
                        )
        );

        for (Long artistId : artistIds) {
            SongArtistDO relation =
                    new SongArtistDO();

            relation.setSongId(songId);
            relation.setArtistId(artistId);

            songArtistMapper.insert(
                    relation
            );
        }
    }

    private static String normalizeArtistType(
            String value
    ) {
        String result =
                requiredText(
                        value,
                        "请选择音乐人类型"
                )
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !"MALE".equals(result)
                        && !"FEMALE".equals(result)
                        && !"BAND".equals(result)
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "音乐人类型只能是MALE、FEMALE或BAND"
            );
        }

        return result;
    }

    private void replaceAlbumArtists(
            Long albumId,
            List<Long> artistIds
    ) {
        albumMapper.deleteArtistAlbumRelations(
                List.of(albumId)
        );

        for (Long artistId : artistIds) {
            albumMapper.insertArtistAlbumRelation(
                    artistId,
                    albumId
            );
        }
    }

    private boolean songArtistsApproved(
            Long songId
    ) {
        List<Long> artistIds =
                songArtistMapper
                        .selectArtistIdsBySongId(
                                songId
                        );

        if (artistIds.isEmpty()) {
            return false;
        }

        for (Long artistId : artistIds) {
            ArtistDO artist =
                    artistMapper.selectById(
                            artistId
                    );

            if (artist == null
                    || !APPROVED.equals(
                    artist.getAuditStatus()
            )) {
                return false;
            }
        }

        return true;
    }

    private List<Long> resolveBatchIds(
            BatchDeleteRequest request
    ) {
        if (request == null) {
            throw business(MusicErrorCode.INVALID_PARAMETER);
        }

        resolveManageActor(request.actor());

        List<Long> ids = parseDistinctIds(
                request.ids(),
                "至少选择一条需要删除的数据"
        );

        if (ids.size() > 100) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "单次最多批量删除100条数据"
            );
        }

        return ids;
    }

    private ResolvedActor resolveManageActor(
            Actor actor
    ) {
        ResolvedActor resolved = resolveActor(actor);

        if (resolved.role() != UserRole.SUPER_ADMIN
                && resolved.role() != UserRole.OPERATOR) {
            throw business(MusicErrorCode.PERMISSION_DENIED);
        }

        return resolved;
    }

    private ArtistItem findArtistItem(Long artistId) {
        String id = String.valueOf(artistId);

        return listManagedArtists()
                .stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() ->
                        business(MusicErrorCode.ARTIST_NOT_FOUND)
                );
    }

    private void ensureSongDependenciesApproved(
            SongDO song
    ) {
        if (song.getAlbumId() != null) {
            AlbumDO album =
                    albumMapper.selectById(
                            song.getAlbumId()
                    );

            if (album == null
                    || !APPROVED.equals(
                    album.getAuditStatus()
            )) {
                throw business(
                        MusicErrorCode
                                .DEPENDENCY_NOT_APPROVED
                );
            }
        }

        List<Long> artistIds =
                songArtistMapper
                        .selectArtistIdsBySongId(
                                song.getId()
                        );

        if (artistIds.isEmpty()) {
            throw business(
                    MusicErrorCode
                            .DEPENDENCY_NOT_APPROVED
            );
        }

        for (Long artistId : artistIds) {
            ArtistDO artist =
                    artistMapper.selectById(
                            artistId
                    );

            if (artist == null
                    || !APPROVED.equals(
                    artist.getAuditStatus()
            )) {
                throw business(
                        MusicErrorCode
                                .DEPENDENCY_NOT_APPROVED
                );
            }
        }
    }

    private ResolvedReview resolveReview(
            ReviewRequest request
    ) {
        if (request == null) {
            throw business(
                    MusicErrorCode.INVALID_PARAMETER
            );
        }

        ResolvedActor actor =
                resolveActor(request.actor());

        if (actor.role() != UserRole.SUPER_ADMIN
                && actor.role()
                != UserRole.REVIEWER) {
            throw business(
                    MusicErrorCode.PERMISSION_DENIED
            );
        }

        final ReviewAction action;

        try {
            action = ReviewAction.valueOf(
                    requiredText(
                            request.action(),
                            "审核动作不能为空"
                    ).toUpperCase(
                            Locale.ROOT
                    )
            );
        } catch (IllegalArgumentException exception) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "审核动作只能是APPROVE、REJECT或REVOKE"
            );
        }

        String reason = nullableText(
                request.reason()
        );

        if (action == ReviewAction.REJECT
                && reason == null) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "驳回时必须填写原因"
            );
        }

        return new ResolvedReview(
                requiredId(request.id()),
                action,
                reason,
                actor
        );
    }

    private static void ensureReviewAllowed(
            String currentStatus,
            ReviewAction action
    ) {
        boolean allowed = switch (action) {
            case APPROVE, REJECT ->
                    PENDING.equals(
                            currentStatus
                    );

            case REVOKE ->
                    APPROVED.equals(
                            currentStatus
                    );
        };

        if (!allowed) {
            throw business(
                    MusicErrorCode
                            .INVALID_REVIEW_STATE
            );
        }
    }

    private static String nextAuditStatus(
            ReviewAction action
    ) {
        return switch (action) {
            case APPROVE -> APPROVED;
            case REJECT -> REJECTED;
            case REVOKE -> PENDING;
        };
    }

    /**
     * 加入搜索词及常见 CJK 字形变体。
     *
     * 例如：
     *
     * 音樂
     * ->
     * 音樂
     * 音乐
     * 音楽
     *
     *
     * 音楽
     * ->
     * 音楽
     * 音樂
     * 音乐
     *
     *
     * 龍
     * ->
     * 龍
     * 龙
     * 竜
     *
     *
     * 竜
     * ->
     * 竜
     * 龍
     * 龙
     *
     * 注意：
     * 这些只是搜索候选词，
     * 绝不会替换用户原始搜索词。
     */
    private static void
    addSearchKeywordWithCjkVariants(
            Map<String, String> values,
            String value
    ) {
        if (
                values == null
                        || value == null
        ) {
            return;
        }

        String normalized =
                value.trim();

        if (
                normalized.isEmpty()
                        || normalized.length() > 64
        ) {
            return;
        }

        /*
         * ① 原始形式永远保留。
         */
        addSearchKeyword(
                values,
                normalized
        );

        try {

            /*
             * ② 中文简体。
             *
             * 音樂 -> 音乐
             * 音楽 -> 有机会转成对应中文形式
             */
            addSearchKeyword(
                    values,

                    ZhConverterUtil
                            .toSimple(
                                    normalized
                            )
            );

            /*
             * ③ 中文标准繁体。
             *
             * 音乐 -> 音樂
             */
            addSearchKeyword(
                    values,

                    ZhConverterUtil
                            .toTraditional(
                                    normalized
                            )
            );

            /*
             * ④ 日文新字体。
             *
             * opencc4j 的这个方法名虽然叫
             * toTraditional，
             * 但 ZhJpConverterUtil 的语义是：
             *
             * 中文简体
             *   ↓
             * 标准繁体
             *   ↓
             * 日文新字体
             *
             * 例如：
             *
             * 音乐 -> 音楽
             * 龙   -> 竜
             */
            addSearchKeyword(
                    values,

                    ZhJpConverterUtil
                            .toTraditional(
                                    normalized
                            )
            );

            /*
             * ⑤ 日文新字体 -> 中文简体。
             *
             * 例如：
             *
             * 音楽 -> 音乐
             * 竜   -> 龙
             */
            addSearchKeyword(
                    values,

                    ZhJpConverterUtil
                            .toSimple(
                                    normalized
                            )
            );

        } catch (Exception exception) {

            /*
             * 字形转换只能增强搜索，
             * 绝不能因为转换失败导致搜索失败。
             */
            System.err.println(
                    "[Search-CJK] 字形扩展失败："
                            + normalized
                            + ", error="
                            + exception.getMessage()
            );
        }
    }

    private boolean validateCreatableArtists(
            List<Long> artistIds
    ) {
        boolean hasPending = false;

        for (Long artistId : artistIds) {
            ArtistDO artist =
                    artistMapper.selectById(
                            artistId
                    );

            if (artist == null
                    || (!APPROVED.equals(
                    artist.getAuditStatus()
            ) && !PENDING.equals(
                    artist.getAuditStatus()
            ))) {
                throw business(
                        MusicErrorCode
                                .ARTIST_NOT_FOUND
                );
            }

            if (PENDING.equals(
                    artist.getAuditStatus()
            )) {
                hasPending = true;
            }
        }

        return hasPending;
    }

    private static LocalDate parseReleaseDate(
            String value
    ) {
        String normalized = nullableText(value);

        if (normalized == null) {
            return null;
        }

        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "专辑发行日期格式必须为yyyy-MM-dd"
            );
        }
    }

    private AlbumItem findAlbumItem(
            Long albumId
    ) {
        Map<String, Object> row =
                albumMapper
                        .selectManagementAlbumById(
                                albumId
                        );

        if (
                row == null
                        || row.isEmpty()
        ) {
            throw business(
                    MusicErrorCode
                            .ALBUM_NOT_FOUND
            );
        }

        return toAlbumItem(row);
    }

    private static String normalizeArtistArea(
            String value
    ) {
        String result =
                value == null
                        ? "ALL"
                        : value
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !ARTIST_AREAS
                        .contains(
                                result
                        )
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .INVALID_PARAMETER,
                    "非法歌手地区分类"
            );
        }

        return result;
    }


    private static String normalizeArtistCategory(
            String value
    ) {
        String result =
                value == null
                        ? "ALL"
                        : value
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !ARTIST_CATEGORIES
                        .contains(
                                result
                        )
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .INVALID_PARAMETER,
                    "非法歌手类型"
            );
        }

        return result;
    }


    private static String normalizeArtistInitial(
            String value
    ) {
        String result =
                value == null
                        ? "HOT"
                        : value
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if ("HOT".equals(result)) {
            return result;
        }

        if ("#".equals(result)) {
            return result;
        }

        if (
                result.length() == 1
                        && result.charAt(0)
                        >= 'A'
                        && result.charAt(0)
                        <= 'Z'
        ) {
            return result;
        }

        throw new RpcBusinessException(
                MusicErrorCode
                        .INVALID_PARAMETER,
                "非法歌手首字母"
        );
    }

    private static ArtistDirectoryItem
    toArtistDirectoryItem(
            Map<String, Object> row
    ) {
        return new ArtistDirectoryItem(
                text(
                        row,
                        "artistId"
                ),

                text(
                        row,
                        "artistName"
                ),

                nullableText(
                        row,
                        "avatarUrl"
                ),

                longValue(
                        row,
                        "songCount"
                )
        );
    }

    private static void addSearchKeyword(
            Map<String, String> values,
            String value
    ) {
        if (
                values == null
                        || value == null
        ) {
            return;
        }

        String normalized =
                value.trim();

        if (
                normalized.isEmpty()
                        || normalized.length() > 64
        ) {
            return;
        }

        /*
         * 英文大小写视为同一个关键词。
         *
         * 例如：
         *
         * MyGO
         * mygo
         * MYGO
         *
         * 最终只保留第一次出现的一个。
         */
        String key =
                normalized
                        .toLowerCase(
                                Locale.ROOT
                        );

        values.putIfAbsent(
                key,
                normalized
        );
    }

    private SongItem findSongItem(
            Long songId
    ) {
        Map<String, Object> row =
                songMapper
                        .selectManagementSongById(
                                songId
                        );

        if (
                row == null
                        || row.isEmpty()
        ) {
            throw business(
                    MusicErrorCode
                            .SONG_NOT_FOUND
            );
        }

        return toSongItem(row);
    }

    private static ResolvedActor resolveActor(
            Actor actor
    ) {
        if (actor == null) {
            throw business(
                    MusicErrorCode.PERMISSION_DENIED
            );
        }

        Long userId = requiredId(
                actor.userId()
        );

        try {
            return new ResolvedActor(
                    userId,
                    UserRole.valueOf(
                            requiredText(
                                    actor.role(),
                                    "角色不能为空"
                            )
                    )
            );
        } catch (IllegalArgumentException exception) {
            throw business(
                    MusicErrorCode.PERMISSION_DENIED
            );
        }
    }

    private static List<Long> parseDistinctIds(
            List<String> values,
            String emptyMessage
    ) {
        if (values == null
                || values.isEmpty()) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    emptyMessage
            );
        }

        LinkedHashSet<Long> result =
                new LinkedHashSet<>();

        for (String value : values) {
            result.add(requiredId(value));
        }

        return List.copyOf(result);
    }

    private static Long nullableId(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return null;
        }

        return requiredId(value);
    }

    private static Long requiredId(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            throw business(
                    MusicErrorCode.INVALID_PARAMETER
            );
        }

        try {
            long result = Long.parseLong(
                    value.trim()
            );

            if (result <= 0) {
                throw new NumberFormatException();
            }

            return result;
        } catch (NumberFormatException exception) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "ID格式不正确"
            );
        }
    }

    private static String requiredText(
            String value,
            String message
    ) {
        String result = nullableText(value);

        if (result == null) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    message
            );
        }

        return result;
    }

    private static String nullableText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String result = value.trim();

        return result.isEmpty()
                ? null
                : result;
    }

    private static int resolveLimit(
            int value,
            int defaultValue,
            int maxValue
    ) {
        if (value <= 0) {
            return defaultValue;
        }

        return Math.min(value, maxValue);
    }

    private static AlbumSongItem toAlbumSongItem(
            Map<String, Object> row
    ) {
        return new AlbumSongItem(
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

    private static ArtistSongItem toArtistSongItem(
            Map<String, Object> row
    ) {
        return new ArtistSongItem(
                text(row, "songId"),
                text(row, "songName"),
                text(row, "artistName"),
                csvList(
                        row,
                        "artistIds"
                ),
                nullableText(row, "albumId"),
                nullableText(row, "albumName"),
                nullableText(row, "coverUrl"),
                nullableText(row, "audioUrl"),
                intValue(
                        row,
                        "durationSeconds"
                ),
                longValue(row, "playCount")
        );
    }

    private static ArtistAlbumItem toArtistAlbumItem(
            Map<String, Object> row
    ) {
        return new ArtistAlbumItem(
                text(row, "albumId"),
                text(row, "albumName"),
                nullableText(row, "coverUrl"),
                nullableText(
                        row,
                        "releaseDate"
                ),
                nullableText(row, "style")
        );
    }

    private static SongCard toSongCard(
            Map<String, Object> row
    ) {
        return new SongCard(
                text(row, "songId"),
                text(row, "songName"),
                text(row, "artistName"),
                csvList(row, "artistIds"),
                nullableText(row, "coverUrl"),
                nullableText(row, "audioUrl"),
                intValue(
                        row,
                        "durationSeconds"
                ),
                longValue(row, "playCount")
        );
    }

    private static ArtistCard toArtistCard(
            Map<String, Object> row
    ) {
        return new ArtistCard(
                text(row, "artistId"),
                text(row, "artistName"),
                jsonStringList(
                        row,
                        "translatedNames"
                ),
                nullableText(row, "avatarUrl"),
                nullableText(
                        row,
                        "countryRegion"
                ),
                longValue(
                        row,
                        "followerCount"
                )
        );
    }

    private static List<String>
    normalizeTranslatedNames(
            List<String> values
    ) {
        if (
                values == null
                        || values.isEmpty()
        ) {
            return List.of();
        }

        LinkedHashMap<
                String,
                String
                > unique =
                new LinkedHashMap<>();

        for (String value : values) {
            if (value == null) {
                continue;
            }

            String normalized =
                    value.trim();

            if (normalized.isEmpty()) {
                continue;
            }

            if (normalized.length() > 128) {
                throw new RpcBusinessException(
                        MusicErrorCode
                                .INVALID_PARAMETER,
                        "单个音乐人译名不能超过128个字符"
                );
            }

            unique.putIfAbsent(
                    normalized.toLowerCase(
                            Locale.ROOT
                    ),
                    normalized
            );
        }

        if (unique.size() > 10) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .INVALID_PARAMETER,
                    "音乐人最多添加10个译名"
            );
        }

        return List.copyOf(
                unique.values()
        );
    }

    private static SongItem toSongItem(
            Map<String, Object> row
    ) {
        return new SongItem(
                text(row, "songId"),
                text(row, "songName"),
                text(row, "artistName"),
                csvList(row, "artistIds"),
                nullableText(row, "albumId"),
                nullableText(row, "albumName"),
                intValue(
                        row,
                        "durationSeconds"
                ),
                nullableText(row, "style"),
                nullableText(row, "introduction"),
                nullableText(row, "coverUrl"),
                nullableText(row, "audioUrl"),
                text(row, "auditStatus"),
                text(row, "publishStatus"),
                text(row, "createdAt")
        );
    }

    private static AlbumItem toAlbumItem(
            Map<String, Object> row
    ) {
        return new AlbumItem(
                text(row, "albumId"),
                text(row, "albumName"),
                text(row, "artistName"),
                csvList(row, "artistIds"),
                nullableText(row, "style"),
                nullableText(row, "coverUrl"),
                nullableText(row, "releaseDate"),
                nullableText(row, "introduction"),
                text(row, "auditStatus"),
                text(row, "createdAt")
        );
    }

    private static ArtistItem toArtistItem(
            Map<String, Object> row
    ) {
        return new ArtistItem(
                text(row, "artistId"),
                text(row, "artistName"),
                jsonStringList(row, "translatedNames"),
                nullableText(row, "ownerUserId"),
                nullableText(row, "countryRegion"),
                nullableText(row, "artistType"),
                nullableText(row, "style"),
                nullableText(row, "avatarUrl"),
                nullableText(row, "introduction"),
                longValue(row, "followerCount"),
                longValue(row, "songCount"),
                longValue(row, "albumCount"),
                text(row, "auditStatus"),
                text(row, "publishStatus"),
                text(row, "createdAt")
        );
    }

    private static ArtistSearchItem toArtistSearchItem(
            Map<String, Object> row
    ) {
        return new ArtistSearchItem(
                text(row, "artistId"),
                text(row, "artistName"),
                jsonStringList(
                        row,
                        "translatedNames"
                ),
                nullableText(row, "avatarUrl"),
                nullableText(
                        row,
                        "countryRegion"
                ),
                text(row, "auditStatus")
        );
    }

    private static AlbumSearchItem toAlbumSearchItem(
            Map<String, Object> row
    ) {
        return new AlbumSearchItem(
                text(row, "albumId"),
                text(row, "albumName"),
                text(row, "artistName"),
                nullableText(row, "coverUrl"),
                nullableText(row, "style")
        );
    }

    private static List<String> csvList(
            Map<String, Object> row,
            String key
    ) {
        String value = nullableText(row, key);

        if (value == null || value.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(
                        value.split(",")
                )
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String text(
            Map<String, Object> row,
            String key
    ) {
        String value = nullableText(
                row,
                key
        );

        return value == null ? "" : value;
    }

    private static String nullableText(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        return value == null
                ? null
                : value.toString();
    }

    private static long longValue(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        if (value instanceof Number number) {
            return number.longValue();
        }

        return value == null
                ? 0L
                : Long.parseLong(
                value.toString()
        );
    }

    private static int intValue(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        if (value instanceof Number number) {
            return number.intValue();
        }

        return value == null
                ? 0
                : Integer.parseInt(
                value.toString()
        );
    }

    private static RpcBusinessException business(
            MusicErrorCode errorCode
    ) {
        return new RpcBusinessException(
                errorCode
        );
    }

    private static SearchItem
    toSongGlobalSearchItem(
            Map<String, Object> row
    ) {
        return toGlobalSearchItem(
                row,
                "SONG"
        );
    }


    private static SearchItem
    toArtistGlobalSearchItem(
            Map<String, Object> row
    ) {
        return toGlobalSearchItem(
                row,
                "ARTIST"
        );
    }


    private static SearchItem
    toAlbumGlobalSearchItem(
            Map<String, Object> row
    ) {
        return toGlobalSearchItem(
                row,
                "ALBUM"
        );
    }


    private static SearchItem
    toPlaylistGlobalSearchItem(
            Map<String, Object> row
    ) {
        return toGlobalSearchItem(
                row,
                "PLAYLIST"
        );
    }


    /**
     * 四种搜索结果使用完全相同的 SQL 字段别名，
     * 所以这里可以共用一个转换方法。
     */
    private static SearchItem
    toGlobalSearchItem(
            Map<String, Object> row,
            String type
    ) {
        return new SearchItem(
                type,

                text(
                        row,
                        "id"
                ),

                text(
                        row,
                        "name"
                ),

                nullableText(
                        row,
                        "subtitle"
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
                        "popularity"
                ),

                csvList(
                        row,
                        "artistIds"
                )
        );
    }

    private record ResolvedActor(
            Long userId,
            UserRole role
    ) {
    }

    private enum ReviewAction {
        APPROVE,
        REJECT,
        REVOKE
    }



    private record ResolvedReview(
            Long id,
            ReviewAction action,
            String reason,
            ResolvedActor actor
    ) {
    }

    private static int resolvePage(
            int page
    ) {
        return Math.max(
                page,
                1
        );
    }

    private static int resolvePageSize(
            int size
    ) {
        if (size <= 0) {
            return 10;
        }

        return Math.min(
                size,
                50
        );
    }

    private static int totalPages(
            long total,
            int size
    ) {
        if (total <= 0) {
            return 0;
        }

        return (int) (
                (total + size - 1)
                        / size
        );
    }

    private static final ObjectMapper
            JSON_MAPPER =
            new ObjectMapper();

    private static List<String>
    jsonStringList(
            Map<String, Object> row,
            String key
    ) {
        Object value =
                row.get(key);

        if (value == null) {
            return List.of();
        }

        String json =
                value.toString()
                        .trim();

        if (
                json.isEmpty()
                        || "null".equals(json)
        ) {
            return List.of();
        }

        try {
            return JSON_MAPPER.readValue(
                    json,
                    new TypeReference<List<String>>() {
                    }
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "音乐人译名JSON格式错误",
                    exception
            );
        }
    }
}
