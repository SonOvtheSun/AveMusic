package com.avemonica.avemusic.music.provider.service;

import com.avemonica.avemusic.music.api.dto
        .LyricsModels.LyricsResult;
import com.avemonica.avemusic.music.api.enums.MusicErrorCode;
import com.avemonica.avemusic.music.api.service.LyricsService;
import com.avemonica.avemusic.music.provider.client.LrclibClient;
import com.avemonica.avemusic.music.provider.client
        .LrclibClient.LrclibRecord;
import com.avemonica.avemusic.music.provider.entity.ArtistDO;
import com.avemonica.avemusic.music.provider.entity.SongLyricsDO;
import com.avemonica.avemusic.music.provider.mapper.ArtistMapper;
import com.avemonica.avemusic.music.provider.mapper.SongArtistMapper;
import com.avemonica.avemusic.music.provider.mapper.SongLyricsMapper;
import com.avemonica.avemusic.music.provider.mapper.SongMapper;
import com.avemonica.minirpc.core.exception.RpcBusinessException;
import com.avemonica.minirpc.spring.annotation.MiniRpcService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private final OllamaClient
            ollamaClient;

    private final ArtistMapper
            artistMapper;

    private final SongArtistMapper
            songArtistMapper;

    private static final ObjectMapper
            JSON_MAPPER =
            new ObjectMapper();

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
            OllamaClient ollamaClient
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

        this.ollamaClient =
                ollamaClient;
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
    public LyricsResult getLyrics(
            String songId
    ) {
        Long resolvedSongId =
                requiredId(songId);

        /*
         * 1. 优先查本地数据库。
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
         * 2. 查询本地歌曲元数据。
         */
        Map<String, Object> meta =
                songMapper
                        .selectLyricsMeta(
                                resolvedSongId
                        );

        if (
                meta == null
                        || meta.isEmpty()
        ) {
            throw new RpcBusinessException(
                    MusicErrorCode
                            .SONG_NOT_FOUND,
                    "歌曲不存在或当前不可播放"
            );
        }

        String songName =
                text(
                        meta,
                        "songName"
                );

        String artistName =
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

        /*
         * 第一阶段只做精确匹配。
         *
         * 没有专辑、音乐人或时长时，
         * 不猜测，不让模型生成歌词。
         */
        if (
                songName.isBlank()
                        || artistName.isBlank()
                        || albumName.isBlank()
                        || durationSeconds <= 0
        ) {
            return notFound(
                    songId
            );
        }

        /*
         * 3. 调用 LRCLIB 精确查询。
         */
        List<String> artistCandidates =
                resolveArtistNameCandidates(
                        resolvedSongId,
                        artistName
                );

        Optional<LrclibRecord> matched =
                Optional.empty();

        for (
                int index = 0;
                index < artistCandidates.size();
                index++
        ) {
            String candidate =
                    artistCandidates.get(
                            index
                    );

            System.out.println(
                    "[Lyrics] 尝试 LRCLIB："
                            + "song=" + songName
                            + ", artist=" + candidate
                            + ", album=" + albumName
                            + ", duration="
                            + durationSeconds
            );

            /*
             * 第二个候选开始稍微间隔一下，
             * 避免连续快速请求 LRCLIB。
             */
            if (index > 0) {
                sleepQuietly(250);
            }

            Optional<LrclibRecord> current =
                    lrclibClient.getExact(
                            songName,
                            candidate,
                            albumName,
                            durationSeconds
                    );

            if (current.isPresent()) {
                matched = current;

                System.out.println(
                        "[Lyrics] 匹配成功，artist="
                                + candidate
                );

                break;
            }
        }

        /*
         * =========================================================
         * 4. 精确匹配成功
         * =========================================================
         */
        if (matched.isPresent()) {
            return saveMatchedLyrics(
                    resolvedSongId,
                    matched.get()
            );
        }

        /*
         * =========================================================
         * 5. 精确匹配失败：
         *    LRCLIB /api/search 模糊召回
         * =========================================================
         */
        System.out.println(
                "[Lyrics] 精确匹配失败，"
                        + "进入第二阶段模糊检索"
        );

        List<LrclibRecord> searched =
                searchCandidates(
                        songName,
                        artistCandidates
                );

        System.out.println(
                "[Lyrics] LRCLIB原始候选数="
                        + searched.size()
        );

        if (searched.isEmpty()) {
            return notFound(
                    songId
            );
        }

        /*
         * =========================================================
         * 6. Java 本地规则评分
         * =========================================================
         */
        List<ScoredLyricsCandidate> scored =
                scoreCandidates(
                        songName,
                        artistCandidates,
                        albumName,
                        durationSeconds,
                        searched
                );

        System.out.println(
                "[Lyrics] 本地评分后候选数="
                        + scored.size()
        );

        for (
                ScoredLyricsCandidate candidate
                : scored
        ) {
            System.out.println(
                    "[Lyrics] candidate="
                            + candidate.record().id()
                            + ", track="
                            + candidate.record()
                            .trackName()
                            + ", artist="
                            + candidate.record()
                            .artistName()
                            + ", album="
                            + candidate.record()
                            .albumName()
                            + ", duration="
                            + candidate.record()
                            .duration()
                            + ", score="
                            + String.format(
                            Locale.ROOT,
                            "%.3f",
                            candidate.score()
                    )
            );
        }

        if (scored.isEmpty()) {
            return notFound(
                    songId
            );
        }

        /*
         * =========================================================
         * 7. 本地规则已经非常确定：
         *    不调用 Qwen，直接采用。
         * =========================================================
         */
        if (canAutoAccept(scored)) {

            ScoredLyricsCandidate best =
                    scored.get(0);

            System.out.println(
                    "[Lyrics] Java高置信直接匹配："
                            + "id="
                            + best.record().id()
                            + ", score="
                            + best.score()
            );

            return saveMatchedLyrics(
                    resolvedSongId,
                    best.record()
            );
        }

        /*
         * =========================================================
         * 8. 候选存在歧义：
         *    交给 Qwen3.5 做候选消歧。
         * =========================================================
         */
        Optional<LrclibRecord> aiMatched =
                selectWithQwen(
                        songName,
                        artistCandidates,
                        albumName,
                        durationSeconds,
                        scored
                );

        if (aiMatched.isEmpty()) {
            System.out.println(
                    "[Lyrics] Qwen未能确认候选"
            );

            return notFound(
                    songId
            );
        }

        /*
         * =========================================================
         * 9. Qwen确认成功
         * =========================================================
         */
        return saveMatchedLyrics(
                resolvedSongId,
                aiMatched.get()
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
}