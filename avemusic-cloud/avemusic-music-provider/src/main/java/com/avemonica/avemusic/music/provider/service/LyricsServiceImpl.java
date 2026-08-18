package com.avemonica.avemusic.music.provider.service;

import com.avemonica.avemusic.music.api.dto
        .LyricsModels.LyricsResult;
import com.avemonica.avemusic.music.api.enums.MusicErrorCode;
import com.avemonica.avemusic.music.api.service.LyricsService;
import com.avemonica.avemusic.music.provider.client.LrclibClient;
import com.avemonica.avemusic.music.provider.client
        .LrclibClient.LrclibRecord;
import com.avemonica.avemusic.music.provider.client.NeteaseCloudMusicClient;
import com.avemonica.avemusic.music.provider.client.NeteaseCloudMusicClient.NeteaseLyrics;
import com.avemonica.avemusic.music.provider.client.NeteaseCloudMusicClient.NeteaseSong;
import com.avemonica.avemusic.music.provider.service
        .LyricsSourceResolver.LyricsTarget;
import com.avemonica.avemusic.music.provider.service
        .LyricsSourceResolver.ResolvedLyrics;
import com.avemonica.avemusic.music.provider.entity.ArtistDO;
import com.avemonica.avemusic.music.provider.entity.SongLyricsDO;
import com.avemonica.avemusic.music.provider.mapper.ArtistMapper;
import com.avemonica.avemusic.music.provider.mapper.SongArtistMapper;
import com.avemonica.avemusic.music.provider.mapper.SongLyricsMapper;
import com.avemonica.avemusic.music.provider.mapper.SongMapper;
import com.avemonica.minirpc.core.exception.RpcBusinessException;
import com.avemonica.minirpc.spring.annotation.MiniRpcService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import com.avemonica.avemusic.music.provider.client.OllamaClient;
import com.avemonica.avemusic.music.provider.client
        .OllamaClient.LyricsMatchCandidate;
import com.avemonica.avemusic.music.provider.client
        .OllamaClient.LyricsMatchTarget;
import com.avemonica.avemusic.music.provider.client
        .OllamaClient.MatchDecision;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@MiniRpcService(
        interfaceClass =
                LyricsService.class,
        group = "music",
        version = "1.0.0"
)
public class LyricsServiceImpl
        implements LyricsService {

    private final SongMapper songMapper;

    private final SongLyricsMapper
            songLyricsMapper;

    private final LrclibClient
            lrclibClient;

    private final LyricsSourceResolver
            lyricsSourceResolver;

    private final OllamaClient
            ollamaClient;

    private final ArtistMapper
            artistMapper;

    private final SongArtistMapper
            songArtistMapper;

    private static final ObjectMapper
            JSON_MAPPER =
            new ObjectMapper();

    private final NeteaseCloudMusicClient
            neteaseClient;

    private static final String
            PROVIDER_MANUAL =
            "MANUAL";

    private static final String
            PROVIDER_LRCLIB =
            "LRCLIB";

    private static final String
            PROVIDER_NETEASE =
            "NETEASE";

    private static final Pattern
            LRC_TIME_PATTERN =
            Pattern.compile(
                    "\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?]"
            );

    public LyricsServiceImpl(
            SongMapper songMapper,
            SongLyricsMapper songLyricsMapper,
            ArtistMapper artistMapper,
            SongArtistMapper songArtistMapper,
            LrclibClient lrclibClient,
            NeteaseCloudMusicClient neteaseClient,
            OllamaClient ollamaClient,
            LyricsSourceResolver lyricsSourceResolver
    ) {
        this.songMapper =
                songMapper;

        this.songLyricsMapper =
                songLyricsMapper;

        this.artistMapper =
                artistMapper;

        this.songArtistMapper =
                songArtistMapper;

        this.lrclibClient =
                lrclibClient;

        this.neteaseClient =
                neteaseClient;

        this.ollamaClient =
                ollamaClient;

        this.lyricsSourceResolver =
                lyricsSourceResolver;
    }

    @Override
    public LyricsResult replaceManualLyrics(
            String songId,
            String fileName,
            String content
    ) {
        Long resolvedSongId =
                requiredId(
                        songId
                );

        /*
         * 管理端允许修改未上架歌曲，
         * 所以这里只判断歌曲真实存在。
         */
        if (
                songMapper.selectById(
                        resolvedSongId
                ) == null
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .SONG_NOT_FOUND
            );
        }

        String normalizedFileName =
                fileName == null
                        ? ""
                        : fileName
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        boolean lrc =
                normalizedFileName
                        .endsWith(
                                ".lrc"
                        );

        boolean txt =
                normalizedFileName
                        .endsWith(
                                ".txt"
                        );

        if (!lrc && !txt) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .INVALID_PARAMETER,
                    "仅支持 .lrc 或 .txt 歌词文件"
            );
        }

        String normalizedContent =
                normalizeUploadedLyrics(
                        content
                );

        if (normalizedContent.isBlank()) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .INVALID_PARAMETER,
                    "歌词文件内容不能为空"
            );
        }


        String plainLyrics;

        String syncedLyrics;


        if (lrc) {

            /*
             * LRC 至少必须存在一个时间标签，
             * 防止用户把普通文本错误命名成 .lrc。
             */
            if (
                    !containsLrcTimestamp(
                            normalizedContent
                    )
            ) {
                throw new RpcBusinessException(
                        MusicErrorCode
                                .INVALID_PARAMETER,
                        "LRC歌词中未检测到时间标签"
                );
            }

            syncedLyrics =
                    normalizedContent;

            plainLyrics =
                    stripLrcTimestamps(
                            normalizedContent
                    );

        } else {

            /*
             * TXT 不具备时间轴。
             */
            plainLyrics =
                    normalizedContent;

            syncedLyrics =
                    null;
        }


        return replaceStoredLyrics(
                resolvedSongId,

                PROVIDER_MANUAL,

                null,

                false,

                plainLyrics,

                syncedLyrics
        );
    }

    @Override
    public LyricsResult translateLyrics(
            String songId
    ) {
        Long resolvedSongId =
                requiredId(
                        songId
                );

        SongLyricsDO entity =
                findCached(
                        resolvedSongId
                );

        /*
         * 必须已经先获取到 LRCLIB 歌词。
         */
        if (
                entity == null
                        || !"MATCHED".equals(
                        entity.getStatus()
                )
        ) {
            return notFound(
                    songId
            );
        }

        /*
         * 已经翻译过：
         * 直接返回数据库缓存。
         */
        if (
                entity.getTranslationJson()
                        != null
                        && !entity
                        .getTranslationJson()
                        .isBlank()
        ) {
            return toResult(
                    entity
            );
        }

        List<String> sourceLines =
                extractTranslationSource(
                        entity
                );

        if (sourceLines.isEmpty()) {
            return toResult(
                    entity
            );
        }

        System.out.println(
                "[Lyrics-AI] 开始翻译歌词，songId="
                        + songId
                        + ", lines="
                        + sourceLines.size()
        );

        Optional<List<String>>
                translated =
                ollamaClient
                        .translateLyrics(
                                sourceLines
                        );

        if (
                translated.isEmpty()
                        || translated.get()
                        .size()
                        != sourceLines.size()
        ) {
            /*
             * AI失败不能影响原歌词显示。
             */
            System.err.println(
                    "[Lyrics-AI] 翻译失败，保留原歌词"
            );

            return toResult(
                    entity
            );
        }

        try {
            entity.setTranslationJson(
                    JSON_MAPPER
                            .writeValueAsString(
                                    translated.get()
                            )
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "歌词翻译序列化失败",
                    exception
            );
        }

        entity.setUpdatedAt(
                LocalDateTime.now()
        );

        songLyricsMapper.updateById(
                entity
        );

        System.out.println(
                "[Lyrics-AI] 歌词翻译完成，songId="
                        + songId
        );

        return toResult(
                entity
        );
    }

    @Override
    @Transactional
    public LyricsResult replaceLyricsFromSource(
            String songId,
            String source
    ) {
        Long resolvedSongId =
                requiredId(songId);

        String normalizedSource =
                source == null
                        ? ""
                        : source
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !"NETEASE".equals(
                        normalizedSource
                )
                        && !"LRCLIB".equals(
                        normalizedSource
                )
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "歌词源只能是 NETEASE 或 LRCLIB"
            );
        }

        LyricsTarget target =
                buildLyricsTarget(
                        resolvedSongId
                );

        ResolvedLyrics resolved =
                lyricsSourceResolver
                        .resolveFromSource(
                                target,
                                normalizedSource
                        )
                        .orElseThrow(
                                () ->
                                        new RpcBusinessException(
                                                MusicErrorCode.INVALID_PARAMETER,
                                                "指定歌词源未匹配到可用歌词："
                                                        + normalizedSource
                                        )
                        );

        /*
         * 先成功获取新歌词，走到这里以后才修改数据库。
         */
        LocalDateTime now =
                LocalDateTime.now();

        SongLyricsDO entity =
                findCached(
                        resolvedSongId
                );

        boolean insert =
                entity == null;

        if (insert) {
            entity =
                    new SongLyricsDO();

            entity.setSongId(
                    resolvedSongId
            );

            entity.setCreatedAt(now);
        }

        applyResolvedLyrics(
                entity,
                resolved,
                now
        );

        if (insert) {
            songLyricsMapper.insert(
                    entity
            );
        } else {
            updateReplacedLyrics(
                    entity
            );
        }

        return toResult(
                entity
        );
    }

    @Override
    public LyricsResult getLyrics(
            String songId
    ) {
        Long resolvedSongId =
                requiredId(songId);

        /*
         * 1. 本地缓存永远优先。
         */
        SongLyricsDO cached =
                findCached(
                        resolvedSongId
                );

        if (cached != null) {
            return toResult(
                    cached
            );
        }

        /*
         * 2. 读取歌曲元数据 + 音乐人所有别名。
         */
        LyricsTarget target =
                buildLyricsTarget(
                        resolvedSongId
                );

        /*
         * 3. 在线自动匹配：
         *    NETEASE -> LRCLIB。
         */
        Optional<ResolvedLyrics> resolved =
                lyricsSourceResolver.resolve(
                        target
                );

        if (resolved.isEmpty()) {
            return notFound(
                    songId
            );
        }

        /*
         * 4. 只有成功拿到完整歌词以后才落库。
         */
        return persistFirstResolvedLyrics(
                resolvedSongId,
                resolved.get()
        );
    }

    private LyricsTarget buildLyricsTarget(
            Long songId
    ) {
        Map<String, Object> meta =
                songMapper
                        .selectLyricsMeta(
                                songId
                        );

        if (
                meta == null
                        || meta.isEmpty()
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode.SONG_NOT_FOUND,
                    "歌曲不存在或当前不可播放"
            );
        }

        String songName =
                text(
                        meta,
                        "songName"
                );

        if (songName.isBlank()) {
            throw new RpcBusinessException(
                    MusicErrorCode.INVALID_PARAMETER,
                    "歌曲名称为空，无法匹配歌词"
            );
        }

        String fallbackArtistName =
                text(
                        meta,
                        "artistName"
                );

        String albumName =
                text(
                        meta,
                        "albumName"
                );

        int durationSeconds =
                intValue(
                        meta,
                        "durationSeconds"
                );

        List<String> artistNames =
                resolveArtistNameCandidates(
                        songId,
                        fallbackArtistName
                );

        return new LyricsTarget(
                songName,
                artistNames,
                albumName,
                durationSeconds
        );
    }


    private LyricsResult persistFirstResolvedLyrics(
            Long songId,
            ResolvedLyrics resolved
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        SongLyricsDO entity =
                new SongLyricsDO();

        entity.setSongId(
                songId
        );

        applyResolvedLyrics(
                entity,
                resolved,
                now
        );

        entity.setCreatedAt(now);

        try {
            songLyricsMapper.insert(
                    entity
            );

        } catch (DuplicateKeyException exception) {
            /*
             * 两个并发首查只允许一个 insert 成功。
             */
            SongLyricsDO existing =
                    findCached(
                            songId
                    );

            if (existing != null) {
                return toResult(
                        existing
                );
            }

            throw exception;
        }

        return toResult(
                entity
        );
    }


    private static void applyResolvedLyrics(
            SongLyricsDO entity,
            ResolvedLyrics resolved,
            LocalDateTime now
    ) {
        entity.setProvider(
                resolved.provider()
        );

        entity.setProviderLyricId(
                resolved.providerLyricId()
        );

        entity.setInstrumental(
                resolved.instrumental()
        );

        entity.setPlainLyrics(
                resolved.plainLyrics()
        );

        entity.setSyncedLyrics(
                resolved.syncedLyrics()
        );

        /*
         * 新歌词必须让旧 AI 翻译失效。
         */
        entity.setTranslationJson(
                null
        );

        entity.setStatus(
                "MATCHED"
        );

        entity.setMatchedAt(now);
        entity.setUpdatedAt(now);
    }

    private Optional<LrclibRecord>
    findFromLrclib(
            String songName,
            List<String> artistNames,
            String albumName,
            int durationSeconds
    ) {
        /*
         * 1. 精确查询。
         */
        for (
                int index = 0;
                index < artistNames.size();
                index++
        ) {
            String artistName =
                    artistNames.get(
                            index
                    );

            if (index > 0) {
                sleepQuietly(
                        250
                );
            }

            Optional<LrclibRecord>
                    exact =
                    lrclibClient.getExact(
                            songName,
                            artistName,
                            albumName,
                            durationSeconds
                    );

            if (exact.isPresent()) {
                return exact;
            }
        }


        /*
         * 2. 模糊搜索。
         */
        List<LrclibRecord> searched =
                searchCandidates(
                        songName,
                        artistNames
                );

        if (searched.isEmpty()) {
            return Optional.empty();
        }


        /*
         * 3. Java 本地评分。
         */
        List<ScoredLyricsCandidate>
                scored =
                scoreCandidates(
                        songName,
                        artistNames,
                        albumName,
                        durationSeconds,
                        searched
                );

        if (scored.isEmpty()) {
            return Optional.empty();
        }


        /*
         * 4. 高置信直接采用。
         */
        if (canAutoAccept(scored)) {
            return Optional.of(
                    scored.get(0)
                            .record()
            );
        }


        /*
         * 5. 交给 Qwen 消歧。
         */
        return selectWithQwen(
                songName,
                artistNames,
                albumName,
                durationSeconds,
                scored
        );
    }

    private LyricsResult fallbackToNetease(
            String songId,
            Long resolvedSongId,
            String songName,
            List<String> artistCandidates,
            String albumName,
            int durationSeconds
    ) {
        System.out.println(
                "[Lyrics] LRCLIB未找到歌词，"
                        + "开始尝试 NeteaseCloudMusicApi"
        );

        Optional<NeteaseLyrics> neteaseLyrics =
                findFromNetease(
                        songName,
                        artistCandidates,
                        albumName,
                        durationSeconds
                );

        if (neteaseLyrics.isPresent()) {

            System.out.println(
                    "[Lyrics] Netease歌词匹配成功，"
                            + "songId="
                            + neteaseLyrics
                            .get()
                            .songId()
            );

            return saveNeteaseLyrics(
                    resolvedSongId,
                    neteaseLyrics.get()
            );
        }

        System.out.println(
                "[Lyrics] 所有歌词来源均未匹配成功"
        );

        return notFound(
                songId
        );
    }

    private static String
    stripLrcTimestamps(
            String syncedLyrics
    ) {
        if (
                syncedLyrics == null
                        || syncedLyrics.isBlank()
        ) {
            return "";
        }

        Pattern timestampPattern =
                Pattern.compile(
                        "\\[\\d{1,3}:"
                                + "\\d{2}"
                                + "(?:\\.\\d{1,3})?]"
                );

        Pattern metadataPattern =
                Pattern.compile(
                        "^\\[(ar|ti|al|by|offset|re|ve):.*]$",
                        Pattern.CASE_INSENSITIVE
                );

        return Arrays
                .stream(
                        syncedLyrics
                                .split("\\R")
                )
                .map(
                        String::trim
                )
                .filter(
                        line ->
                                !metadataPattern
                                        .matcher(line)
                                        .matches()
                )
                .map(
                        line ->
                                timestampPattern
                                        .matcher(line)
                                        .replaceAll("")
                                        .trim()
                )
                .filter(
                        line ->
                                !line.isBlank()
                )
                .collect(
                        java.util.stream
                                .Collectors
                                .joining("\n")
                );
    }

    private LyricsResult saveNeteaseLyrics(
            Long songId,
            NeteaseLyrics lyric
    ) {
        String syncedLyrics =
                lyric.syncedLyrics();

        /*
         * 根据 LRC 同时生成普通歌词。
         */
        String plainLyrics =
                stripLrcTimestamps(
                        syncedLyrics
                );

        LocalDateTime now =
                LocalDateTime.now();

        SongLyricsDO entity =
                new SongLyricsDO();

        entity.setSongId(
                songId
        );

        entity.setProvider(
                "NETEASE"
        );

        entity.setProviderLyricId(
                Long.toString(
                        lyric.songId()
                )
        );

        entity.setInstrumental(
                false
        );

        entity.setPlainLyrics(
                plainLyrics
        );

        entity.setSyncedLyrics(
                syncedLyrics
        );

        entity.setTranslationJson(
                null
        );

        entity.setStatus(
                "MATCHED"
        );

        entity.setMatchedAt(
                now
        );

        entity.setCreatedAt(
                now
        );

        entity.setUpdatedAt(
                now
        );

        try {
            songLyricsMapper.insert(
                    entity
            );

        } catch (
                DuplicateKeyException exception
        ) {
            SongLyricsDO existing =
                    findCached(
                            songId
                    );

            if (existing != null) {
                return toResult(
                        existing
                );
            }

            throw exception;
        }

        return toResult(
                entity
        );
    }

    private Optional<LrclibRecord>
    selectWithQwen(
            String songName,
            List<String> artistNames,
            String albumName,
            int durationSeconds,
            List<ScoredLyricsCandidate> candidates
    ) {
        LyricsMatchTarget target =
                new LyricsMatchTarget(
                        songName,
                        artistNames,
                        albumName,
                        durationSeconds
                );

        List<LyricsMatchCandidate>
                aiCandidates =
                candidates
                        .stream()
                        .map(
                                candidate -> {
                                    LrclibRecord record =
                                            candidate.record();

                                    return new LyricsMatchCandidate(
                                            record.id(),
                                            record.trackName(),
                                            record.artistName(),
                                            record.albumName(),
                                            record.duration(),
                                            candidate.score()
                                    );
                                }
                        )
                        .toList();

        Optional<MatchDecision> result =
                ollamaClient
                        .selectLyricsCandidate(
                                target,
                                aiCandidates
                        );

        if (result.isEmpty()) {
            return Optional.empty();
        }

        MatchDecision decision =
                result.get();

        System.out.println(
                "[Lyrics-AI] decision="
                        + "matched="
                        + decision.matched()
                        + ", selectedId="
                        + decision.selectedId()
                        + ", confidence="
                        + decision.confidence()
        );

        /*
         * 模型自己也认为没有匹配项。
         */
        if (!decision.matched()) {
            return Optional.empty();
        }

        /*
         * 这是 AveMusic 自己的安全阈值。
         * Qwen 低于 0.75 的结果不要直接写数据库。
         */
        if (decision.confidence() < 0.75) {
            System.out.println(
                    "[Lyrics-AI] confidence过低，拒绝采用"
            );

            return Optional.empty();
        }

        /*
         * 即便 OllamaClient 已经检查过一次，
         * Service 再校验一次 selectedId。
         *
         * AI 永远不能直接决定任意 LRCLIB ID。
         */
        return candidates
                .stream()
                .map(
                        ScoredLyricsCandidate::record
                )
                .filter(
                        record ->
                                record.id() != null
                                        && record.id()
                                        .longValue()
                                        == decision
                                        .selectedId()
                )
                .findFirst();
    }

    private LyricsResult saveMatchedLyrics(
            Long songId,
            LrclibRecord lyric
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        SongLyricsDO entity =
                new SongLyricsDO();

        entity.setSongId(
                songId
        );

        entity.setProvider(
                "LRCLIB"
        );

        entity.setProviderLyricId(
                lyric.id() == null
                        ? null
                        : lyric.id()
                        .toString()
        );

        entity.setInstrumental(
                lyric.instrumental()
        );

        entity.setPlainLyrics(
                lyric.plainLyrics()
        );

        entity.setSyncedLyrics(
                lyric.syncedLyrics()
        );

        entity.setStatus(
                "MATCHED"
        );

        entity.setMatchedAt(
                now
        );

        entity.setCreatedAt(
                now
        );

        entity.setUpdatedAt(
                now
        );

        try {
            songLyricsMapper.insert(
                    entity
            );

        } catch (
                DuplicateKeyException exception
        ) {
            /*
             * 并发情况下，
             * 同一首歌曲可能同时第一次查询歌词。
             */
            SongLyricsDO existing =
                    findCached(
                            songId
                    );

            if (existing != null) {
                return toResult(
                        existing
                );
            }

            throw exception;
        }

        return toResult(
                entity
        );
    }

    private LyricsResult replaceStoredLyrics(
            Long songId,
            String provider,
            String providerLyricId,
            boolean instrumental,
            String plainLyrics,
            String syncedLyrics
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        SongLyricsDO entity =
                findCached(
                        songId
                );

        boolean create =
                entity == null;

        if (create) {
            entity =
                    new SongLyricsDO();

            entity.setSongId(
                    songId
            );

            entity.setCreatedAt(
                    now
            );
        }

        entity.setProvider(
                provider
        );

        entity.setProviderLyricId(
                providerLyricId
        );

        entity.setInstrumental(
                instrumental
        );

        entity.setPlainLyrics(
                plainLyrics
        );

        entity.setSyncedLyrics(
                syncedLyrics
        );

        /*
         * 歌词内容发生变化后，
         * 原 AI 翻译必须作废。
         */
        entity.setTranslationJson(
                null
        );

        entity.setStatus(
                "MATCHED"
        );

        entity.setMatchedAt(
                now
        );

        entity.setUpdatedAt(
                now
        );

        if (create) {
            songLyricsMapper.insert(
                    entity
            );
        } else {
            updateReplacedLyrics(
                    entity
            );
        }

        return toResult(
                entity
        );
    }

    /**
     * 管理端替换歌词时使用显式 SET。
     *
     * 不能只依赖 updateById(entity)：
     * MyBatis-Plus 默认情况下可能忽略值为 null 的字段，
     * 从而导致 translation_json 仍保留旧 AI 翻译。
     *
     * 同理，人工 TXT 歌词的 synced_lyrics=null、
     * MANUAL 的 provider_lyric_id=null 也必须真正写入数据库。
     */
    private void updateReplacedLyrics(
            SongLyricsDO entity
    ) {
        int updated =
                songLyricsMapper.update(
                        null,
                        new LambdaUpdateWrapper<
                                SongLyricsDO
                                >()
                                .eq(
                                        SongLyricsDO
                                                ::getSongId,
                                        entity.getSongId()
                                )
                                .set(
                                        SongLyricsDO
                                                ::getProvider,
                                        entity.getProvider()
                                )
                                .set(
                                        SongLyricsDO
                                                ::getProviderLyricId,
                                        entity.getProviderLyricId()
                                )
                                .set(
                                        SongLyricsDO
                                                ::getInstrumental,
                                        entity.getInstrumental()
                                )
                                .set(
                                        SongLyricsDO
                                                ::getPlainLyrics,
                                        entity.getPlainLyrics()
                                )
                                .set(
                                        SongLyricsDO
                                                ::getSyncedLyrics,
                                        entity.getSyncedLyrics()
                                )
                                /*
                                 * 关键：
                                 * 无论旧翻译是什么，
                                 * 替换原歌词后都强制写成 SQL NULL。
                                 */
                                .set(
                                        SongLyricsDO
                                                ::getTranslationJson,
                                        null
                                )
                                .set(
                                        SongLyricsDO
                                                ::getStatus,
                                        entity.getStatus()
                                )
                                .set(
                                        SongLyricsDO
                                                ::getMatchedAt,
                                        entity.getMatchedAt()
                                )
                                .set(
                                        SongLyricsDO
                                                ::getUpdatedAt,
                                        entity.getUpdatedAt()
                                )
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "歌词替换更新失败，songId="
                            + entity.getSongId()
            );
        }
    }

    private List<LrclibRecord>
    searchCandidates(
            String songName,
            List<String> artistNames
    ) {
        Map<Long, LrclibRecord> unique =
                new LinkedHashMap<>();

        for (
                int index = 0;
                index < artistNames.size();
                index++
        ) {
            String artistName =
                    artistNames.get(
                            index
                    );

            /*
             * 不连续高频请求 LRCLIB。
             */
            if (index > 0) {
                sleepQuietly(
                        250
                );
            }

            System.out.println(
                    "[Lyrics] LRCLIB模糊搜索："
                            + "song="
                            + songName
                            + ", artist="
                            + artistName
            );

            List<LrclibRecord> records =
                    lrclibClient.search(
                            songName,
                            artistName
                    );

            for (
                    LrclibRecord record
                    : records
            ) {
                if (
                        record == null
                                || record.id() == null
                ) {
                    continue;
                }

                unique.putIfAbsent(
                        record.id(),
                        record
                );
            }
        }

        return List.copyOf(
                unique.values()
        );
    }

    private SongLyricsDO findCached(
            Long songId
    ) {
        return songLyricsMapper
                .selectOne(
                        new LambdaQueryWrapper<
                                SongLyricsDO
                                >()
                                .eq(
                                        SongLyricsDO
                                                ::getSongId,
                                        songId
                                )
                                .last(
                                        "LIMIT 1"
                                )
                );
    }

    private static LyricsResult toResult(
            SongLyricsDO entity
    ) {
        String syncedLyrics =
                entity.getSyncedLyrics();

        return new LyricsResult(
                entity.getSongId()
                        .toString(),

                entity.getStatus(),

                Boolean.TRUE.equals(
                        entity.getInstrumental()
                ),

                syncedLyrics != null
                        && !syncedLyrics
                        .isBlank(),

                entity.getPlainLyrics(),

                syncedLyrics,

                parseTranslationLines(
                        entity.getTranslationJson()
                ),

                entity.getProvider()
        );
    }

    private static List<String>
    parseTranslationLines(
            String json
    ) {
        if (
                json == null
                        || json.isBlank()
        ) {
            return List.of();
        }

        try {
            List<String> result =
                    JSON_MAPPER.readValue(
                            json,
                            new TypeReference<
                                    List<String>
                                    >() {
                            }
                    );

            return result == null
                    ? List.of()
                    : result;

        } catch (Exception exception) {
            System.err.println(
                    "[Lyrics] translation_json解析失败"
            );

            return List.of();
        }
    }

    private static LyricsResult notFound(
            String songId
    ) {
        return new LyricsResult(
                songId,
                "NOT_FOUND",
                false,
                false,
                null,
                null,
                List.of(),
                "LRCLIB"
        );
    }

    private static Long requiredId(
            String value
    ) {
        try {
            long id =
                    Long.parseLong(value);

            if (id <= 0) {
                throw new NumberFormatException();
            }

            return id;

        } catch (
                NumberFormatException exception
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .INVALID_PARAMETER,
                    "歌曲ID格式错误"
            );
        }
    }

    private static String text(
            Map<String, Object> row,
            String key
    ) {
        Object value =
                row.get(key);

        return value == null
                ? ""
                : value.toString()
                .trim();
    }

    private static void sleepQuietly(
            long millis
    ) {
        try {
            Thread.sleep(millis);

        } catch (
                InterruptedException exception
        ) {
            Thread.currentThread()
                    .interrupt();

            throw new IllegalStateException(
                    "歌词匹配任务被中断",
                    exception
            );
        }
    }

    private List<String>
    resolveArtistNameCandidates(
            Long songId,
            String fallbackArtistName
    ) {
        LinkedHashMap<
                String,
                String
                > result =
                new LinkedHashMap<>();

        /*
         * 先尝试当前歌曲正常显示的音乐人名称。
         */
        addArtistCandidate(
                result,
                fallbackArtistName
        );

        List<Long> artistIds =
                songArtistMapper
                        .selectArtistIdsBySongId(
                                songId
                        );

        for (Long artistId : artistIds) {
            ArtistDO artist =
                    artistMapper
                            .selectById(
                                    artistId
                            );

            if (artist == null) {
                continue;
            }

            /*
             * 原名。
             */
            addArtistCandidate(
                    result,
                    artist.getName()
            );

            /*
             * 所有译名。
             */
            List<String> names =
                    artist.getTranslatedNames();

            if (names == null) {
                continue;
            }

            for (String name : names) {
                addArtistCandidate(
                        result,
                        name
                );
            }
        }

        return List.copyOf(
                result.values()
        );
    }

    private static void
    addArtistCandidate(
            Map<String, String> values,
            String value
    ) {
        if (value == null) {
            return;
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            return;
        }

        values.putIfAbsent(
                normalized.toLowerCase(
                        Locale.ROOT
                ),
                normalized
        );
    }

    private static int intValue(
            Map<String, Object> row,
            String key
    ) {
        Object value =
                row.get(key);

        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value == null) {
            return 0;
        }

        return Integer.parseInt(
                value.toString()
        );
    }

    private static boolean
    canAutoAccept(
            List<ScoredLyricsCandidate> candidates
    ) {
        if (candidates.isEmpty()) {
            return false;
        }

        double first =
                candidates.get(0)
                        .score();

        if (first < 0.90) {
            return false;
        }

        if (candidates.size() == 1) {
            return true;
        }

        double second =
                candidates.get(1)
                        .score();

        return first - second
                >= 0.15;
    }

    private static List<String>
    extractTranslationSource(
            SongLyricsDO entity
    ) {
        String synced =
                entity.getSyncedLyrics();

        /*
         * 优先使用带时间轴的 LRC。
         */
        if (
                synced != null
                        && !synced.isBlank()
        ) {
            List<TimedText> timed =
                    new ArrayList<>();

            for (
                    String rawLine
                    : synced.split("\\R")
            ) {
                Matcher matcher =
                        LRC_TIME_PATTERN
                                .matcher(
                                        rawLine
                                );

                List<Double> times =
                        new ArrayList<>();

                int lastEnd = -1;

                while (
                        matcher.find()
                ) {
                    int minutes =
                            Integer.parseInt(
                                    matcher.group(1)
                            );

                    int seconds =
                            Integer.parseInt(
                                    matcher.group(2)
                            );

                    String fractionText =
                            matcher.group(3);

                    double fraction =
                            fractionText == null
                                    ? 0.0
                                    : Double.parseDouble(
                                    "0."
                                    + fractionText
                            );

                    times.add(
                            minutes * 60.0
                                    + seconds
                                    + fraction
                    );

                    lastEnd =
                            matcher.end();
                }

                if (
                        times.isEmpty()
                                || lastEnd < 0
                ) {
                    continue;
                }

                String text =
                        rawLine
                                .substring(
                                        lastEnd
                                )
                                .trim();

                /*
                 * 类似：
                 * [03:25.72]
                 *
                 * 这种没有文字的结尾时间行忽略。
                 */
                if (text.isBlank()) {
                    continue;
                }

                for (Double time : times) {
                    timed.add(
                            new TimedText(
                                    time,
                                    text
                            )
                    );
                }
            }

            timed.sort(
                    Comparator.comparingDouble(
                            TimedText::time
                    )
            );

            return timed
                    .stream()
                    .map(
                            TimedText::text
                    )
                    .toList();
        }

        /*
         * 没有同步歌词时退化成普通歌词。
         */
        String plain =
                entity.getPlainLyrics();

        if (
                plain == null
                        || plain.isBlank()
        ) {
            return List.of();
        }

        return Arrays
                .stream(
                        plain.split("\\R")
                )
                .map(
                        String::trim
                )
                .filter(
                        line ->
                                !line.isBlank()
                )
                .toList();
    }

    private List<ScoredLyricsCandidate>
    scoreCandidates(
            String songName,
            List<String> artistNames,
            String albumName,
            int durationSeconds,
            List<LrclibRecord> candidates
    ) {
        return candidates
                .stream()
                .map(
                        record ->
                                new ScoredLyricsCandidate(
                                        record,
                                        calculateScore(
                                                songName,
                                                artistNames,
                                                albumName,
                                                durationSeconds,
                                                record
                                        )
                                )
                )
                .filter(
                        candidate ->
                                candidate.score()
                                        >= 0.50
                )
                .sorted(
                        Comparator.comparingDouble(
                                ScoredLyricsCandidate::score
                        ).reversed()
                )
                .limit(5)
                .toList();
    }

    private static String
    normalizeUploadedLyrics(
            String content
    ) {
        if (content == null) {
            return "";
        }

        String value =
                content;

        /*
         * 去掉 UTF-8 BOM。
         */
        if (
                !value.isEmpty()
                        && value.charAt(0)
                        == '\uFEFF'
        ) {
            value =
                    value.substring(1);
        }

        /*
         * 统一换行。
         */
        value =
                value.replace(
                                "\r\n",
                                "\n"
                        )
                        .replace(
                                '\r',
                                '\n'
                        );

        return value.strip();
    }

    private static boolean
    containsLrcTimestamp(
            String lyrics
    ) {
        if (
                lyrics == null
                        || lyrics.isBlank()
        ) {
            return false;
        }

        return Pattern
                .compile(
                        "\\[\\d{1,3}:"
                                + "\\d{2}"
                                + "(?:\\.\\d{1,3})?]"
                )
                .matcher(
                        lyrics
                )
                .find();
    }

    private static double calculateScore(
            String songName,
            List<String> artistNames,
            String albumName,
            int durationSeconds,
            LrclibRecord candidate
    ) {
        double track =
                textScore(
                        songName,
                        candidate.trackName()
                );

        double artist =
                artistScore(
                        artistNames,
                        candidate.artistName()
                );

        double album =
                textScore(
                        albumName,
                        candidate.albumName()
                );

        double duration =
                durationScore(
                        durationSeconds,
                        candidate.duration()
                );

        return track * 0.40
                + artist * 0.30
                + duration * 0.20
                + album * 0.10;
    }

    private static double durationScore(
            int expected,
            Integer actual
    ) {
        if (expected <= 0
                || actual == null
                || actual <= 0) {
            return 0.0;
        }

        int difference =
                Math.abs(
                        expected - actual
                );

        if (difference <= 2) {
            return 1.0;
        }

        if (difference <= 5) {
            return 0.8;
        }

        if (difference <= 10) {
            return 0.4;
        }

        return 0.0;
    }

    private static double artistScore(
            List<String> artistNames,
            String candidateArtist
    ) {
        double best = 0.0;

        for (String artistName
                : artistNames) {

            best = Math.max(
                    best,
                    textScore(
                            artistName,
                            candidateArtist
                    )
            );
        }

        return best;
    }

    private static double textScore(
            String expected,
            String actual
    ) {
        String left =
                normalizeForMatch(
                        expected
                );

        String right =
                normalizeForMatch(
                        actual
                );

        if (left.isEmpty()
                || right.isEmpty()) {
            return 0.0;
        }

        if (left.equals(right)) {
            return 1.0;
        }

        if (left.contains(right)
                || right.contains(left)) {
            return 0.8;
        }

        return 0.0;
    }

    private List<ScoredNeteaseCandidate>
    scoreNeteaseCandidates(
            String songName,
            List<String> artistNames,
            String albumName,
            int durationSeconds,
            List<NeteaseCloudMusicClient.NeteaseSong> candidates
    ) {
        return candidates
                .stream()
                .map(
                        candidate ->
                                new ScoredNeteaseCandidate(
                                        candidate,

                                        calculateNeteaseScore(
                                                songName,
                                                artistNames,
                                                albumName,
                                                durationSeconds,
                                                candidate
                                        )
                                )
                )
                /*
                 * 先排除明显不相关结果。
                 */
                .filter(
                        candidate ->
                                candidate.score()
                                        >= 0.50
                )
                .sorted(
                        Comparator
                                .comparingDouble(
                                        ScoredNeteaseCandidate
                                                ::score
                                )
                                .reversed()
                )
                .limit(5)
                .toList();
    }

    private List<NeteaseSong>
    searchNeteaseCandidates(
            String songName,
            List<String> artistNames
    ) {
        Map<Long, NeteaseSong> unique =
                new LinkedHashMap<>();

        for (
                int index = 0;
                index < artistNames.size();
                index++
        ) {
            String artistName =
                    artistNames.get(
                            index
                    );

            if (index > 0) {
                sleepQuietly(
                        150
                );
            }

            System.out.println(
                    "[Lyrics-Netease] 搜索："
                            + "song="
                            + songName
                            + ", artist="
                            + artistName
            );

            List<NeteaseCloudMusicClient.NeteaseSong> songs =
                    neteaseClient.search(
                            songName,
                            artistName
                    );

            for (NeteaseCloudMusicClient.NeteaseSong song : songs) {
                unique.putIfAbsent(
                        song.id(),
                        song
                );
            }
        }

        return List.copyOf(
                unique.values()
        );
    }

    private static double
    calculateNeteaseScore(
            String songName,
            List<String> artistNames,
            String albumName,
            int durationSeconds,
            NeteaseCloudMusicClient.NeteaseSong candidate
    ) {
        double track =
                textScore(
                        songName,
                        candidate.trackName()
                );

        double artist =
                artistScore(
                        artistNames,
                        candidate.artistName()
                );

        double album =
                textScore(
                        albumName,
                        candidate.albumName()
                );

        double duration =
                durationScore(
                        durationSeconds,
                        candidate.durationSeconds()
                );

        /*
         * 与 LRCLIB 保持同一套权重。
         */
        return track * 0.40
                + artist * 0.30
                + duration * 0.20
                + album * 0.10;
    }

    private Optional<NeteaseLyrics>
    findFromNetease(
            String songName,
            List<String> artistNames,
            String albumName,
            int durationSeconds
    ) {
        List<NeteaseSong> searched =
                searchNeteaseCandidates(
                        songName,
                        artistNames
                );

        if (searched.isEmpty()) {
            return Optional.empty();
        }

        List<ScoredNeteaseCandidate>
                scored =
                scoreNeteaseCandidates(
                        songName,
                        artistNames,
                        albumName,
                        durationSeconds,
                        searched
                );

        if (scored.isEmpty()) {
            return Optional.empty();
        }

        for (
                ScoredNeteaseCandidate item
                : scored
        ) {
            System.out.println(
                    "[Lyrics-Netease] candidate="
                            + item.song().id()
                            + ", track="
                            + item.song()
                            .trackName()
                            + ", artist="
                            + item.song()
                            .artistName()
                            + ", album="
                            + item.song()
                            .albumName()
                            + ", duration="
                            + item.song()
                            .durationSeconds()
                            + ", score="
                            + String.format(
                            Locale.ROOT,
                            "%.3f",
                            item.score()
                    )
            );
        }

        /*
         * 高分结果：
         * Java直接采用。
         */
        if (
                canAutoAcceptNetease(
                        scored
                )
        ) {
            return neteaseClient.getLyrics(
                    scored.get(0)
                            .song()
                            .id()
            );
        }

        /*
         * 有歧义：
         * 继续让当前 Qwen 候选选择器处理。
         */
        List<
                OllamaClient.LyricsMatchCandidate
                > aiCandidates =
                scored
                        .stream()
                        .map(
                                item ->
                                        new OllamaClient
                                                .LyricsMatchCandidate(
                                                item.song()
                                                        .id(),

                                                item.song()
                                                        .trackName(),

                                                item.song()
                                                        .artistName(),

                                                item.song()
                                                        .albumName(),

                                                item.song()
                                                        .durationSeconds(),

                                                item.score()
                                        )
                        )
                        .toList();

        OllamaClient.LyricsMatchTarget target =
                new OllamaClient
                        .LyricsMatchTarget(
                        songName,
                        artistNames,
                        albumName,
                        durationSeconds
                );

        Optional<
                OllamaClient.MatchDecision
                > decision =
                ollamaClient
                        .selectLyricsCandidate(
                                target,
                                aiCandidates
                        );

        if (
                decision.isEmpty()
                        || !decision.get()
                        .matched()
                        || decision.get()
                        .confidence()
                        < 0.75
        ) {
            return Optional.empty();
        }

        long selectedId =
                decision.get()
                        .selectedId();

        /*
         * 再次保证模型不能凭空生成 ID。
         */
        boolean exists =
                scored
                        .stream()
                        .anyMatch(
                                item ->
                                        item.song()
                                                .id()
                                                == selectedId
                        );

        if (!exists) {
            return Optional.empty();
        }

        return neteaseClient.getLyrics(
                selectedId
        );
    }

    private static boolean
    canAutoAcceptNetease(
            List<ScoredNeteaseCandidate>
                    candidates
    ) {
        if (candidates.isEmpty()) {
            return false;
        }

        double first =
                candidates.get(0)
                        .score();

        if (first < 0.90) {
            return false;
        }

        if (candidates.size() == 1) {
            return true;
        }

        double second =
                candidates.get(1)
                        .score();

        return first - second
                >= 0.15;
    }

    private static String normalizeForMatch(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[\\s\\p{Punct}]+",
                        ""
                )
                .trim();
    }

    private record ScoredLyricsCandidate(
            LrclibRecord record,
            double score
    ) {
    }

    private record TimedText(
            double time,
            String text
    ) {
    }

    private record ScoredNeteaseCandidate(
            NeteaseCloudMusicClient.NeteaseSong song,
            double score
    ) {
    }
}