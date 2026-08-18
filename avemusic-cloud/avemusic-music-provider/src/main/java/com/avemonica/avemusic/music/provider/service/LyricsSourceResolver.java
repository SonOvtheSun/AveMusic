package com.avemonica.avemusic.music.provider.service;

import com.avemonica.avemusic.music.provider.client.LrclibClient;
import com.avemonica.avemusic.music.provider.client.LrclibClient.LrclibRecord;
import com.avemonica.avemusic.music.provider.client.NeteaseCloudMusicClient;
import com.avemonica.avemusic.music.provider.client.NeteaseCloudMusicClient.NeteaseLyrics;
import com.avemonica.avemusic.music.provider.client.NeteaseCloudMusicClient.NeteaseSong;
import com.avemonica.avemusic.music.provider.client.OllamaClient;
import com.avemonica.avemusic.music.provider.client.OllamaClient.LyricsMatchCandidate;
import com.avemonica.avemusic.music.provider.client.OllamaClient.LyricsMatchTarget;
import com.avemonica.avemusic.music.provider.client.OllamaClient.MatchDecision;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 在线歌词来源统一解析器。
 *
 * 自动获取顺序固定为：
 * 1. NeteaseCloudMusicApi
 * 2. LRCLIB
 * 3. 全部失败才返回 Optional.empty()
 *
 * 这个类只负责“找哪条歌词”和“拿到歌词正文”，
 * 不负责数据库写入，也不会修改旧歌词。
 */
@Component
public final class LyricsSourceResolver {

    private static final double
            MIN_CANDIDATE_SCORE = 0.50;

    private static final double
            AUTO_ACCEPT_SCORE = 0.92;

    private static final double
            AUTO_ACCEPT_MARGIN = 0.08;

    private static final double
            AI_MIN_CONFIDENCE = 0.62;

    private static final int
            AI_CANDIDATE_LIMIT = 5;

    private static final Pattern
            LRC_TIME_TAG =
            Pattern.compile(
                    "^\\[(?:\\d{1,3}:)?\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]"
            );

    private static final Pattern
            LRC_METADATA_TAG =
            Pattern.compile(
                    "^\\[(?:ar|ti|al|by|offset|re|ve|length):.*]$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Set<String>
            VERSION_TAGS =
            Set.of(
                    "live",
                    "ライブ",
                    "remix",
                    "リミックス",
                    "instrumental",
                    "inst",
                    "off vocal",
                    "offvocal",
                    "karaoke",
                    "カラオケ",
                    "cover",
                    "カバー",
                    "acoustic",
                    "アコースティック",
                    "demo",
                    "デモ"
            );

    private final NeteaseCloudMusicClient
            neteaseClient;

    private final LrclibClient
            lrclibClient;

    private final OllamaClient
            ollamaClient;

    public LyricsSourceResolver(
            NeteaseCloudMusicClient neteaseClient,
            LrclibClient lrclibClient,
            OllamaClient ollamaClient
    ) {
        this.neteaseClient =
                neteaseClient;

        this.lrclibClient =
                lrclibClient;

        this.ollamaClient =
                ollamaClient;
    }

    /**
     * 自动匹配：网易云优先，LRCLIB 兜底。
     */
    public Optional<ResolvedLyrics> resolve(
            LyricsTarget target
    ) {
        LyricsTarget normalized =
                normalizeTarget(
                        target
                );

        Optional<ResolvedLyrics> netease =
                resolveNetease(
                        normalized
                );

        if (netease.isPresent()) {
            return netease;
        }

        return resolveLrclib(
                normalized
        );
    }

    /**
     * 后台“强制指定歌词源”使用。
     *
     * 注意：这里绝不跨源 fallback。
     */
    public Optional<ResolvedLyrics>
    resolveFromSource(
            LyricsTarget target,
            String source
    ) {
        LyricsTarget normalized =
                normalizeTarget(
                        target
                );

        String normalizedSource =
                source == null
                        ? ""
                        : source
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        return switch (normalizedSource) {
            case "NETEASE" ->
                    resolveNetease(
                            normalized
                    );

            case "LRCLIB" ->
                    resolveLrclib(
                            normalized
                    );

            default ->
                    Optional.empty();
        };
    }

    private Optional<ResolvedLyrics>
    resolveNetease(
            LyricsTarget target
    ) {
        System.out.println(
                "[Lyrics] 开始网易云优先匹配："
                        + target.songName()
        );

        List<NeteaseSong> candidates =
                searchNeteaseCandidates(
                        target
                );

        if (candidates.isEmpty()) {
            System.out.println(
                    "[Lyrics] 网易云无候选，准备尝试 LRCLIB"
            );

            return Optional.empty();
        }

        List<ScoredNetease> scored =
                scoreNeteaseCandidates(
                        target,
                        candidates
                );

        if (scored.isEmpty()) {
            System.out.println(
                    "[Lyrics] 网易云候选评分均过低，准备尝试 LRCLIB"
            );

            return Optional.empty();
        }

        Optional<ScoredNetease> selected =
                selectNeteaseCandidate(
                        target,
                        scored
                );

        if (selected.isEmpty()) {
            System.out.println(
                    "[Lyrics] 网易云候选无法可靠确认，准备尝试 LRCLIB"
            );

            return Optional.empty();
        }

        List<ScoredNetease> attemptOrder =
                buildNeteaseAttemptOrder(
                        selected.get(),
                        scored
                );

        for (ScoredNetease candidate
                : attemptOrder) {

            Optional<NeteaseLyrics> lyrics =
                    neteaseClient.getLyrics(
                            candidate.song().id()
                    );

            if (lyrics.isEmpty()) {
                continue;
            }

            String synced =
                    lyrics.get()
                            .syncedLyrics();

            if (
                    synced == null
                            || synced.isBlank()
            ) {
                continue;
            }

            String plain =
                    stripLrcTimestamps(
                            synced
                    );

            boolean instrumental =
                    looksInstrumental(
                            plain
                    );

            System.out.println(
                    "[Lyrics] 网易云匹配成功："
                            + "id="
                            + candidate.song().id()
                            + ", score="
                            + String.format(
                            Locale.ROOT,
                            "%.3f",
                            candidate.score()
                    )
            );

            return Optional.of(
                    new ResolvedLyrics(
                            "NETEASE",
                            String.valueOf(
                                    candidate.song()
                                            .id()
                            ),
                            instrumental,
                            plain.isBlank()
                                    ? null
                                    : plain,
                            synced,
                            candidate.score()
                    )
            );
        }

        System.out.println(
                "[Lyrics] 网易云候选存在但均无可用歌词，准备尝试 LRCLIB"
        );

        return Optional.empty();
    }

    private List<NeteaseSong>
    searchNeteaseCandidates(
            LyricsTarget target
    ) {
        Map<Long, NeteaseSong> unique =
                new LinkedHashMap<>();

        List<String> artistNames =
                target.artistNames();

        if (artistNames.isEmpty()) {
            addNeteaseCandidates(
                    unique,
                    neteaseClient.search(
                            target.songName(),
                            ""
                    )
            );

        } else {
            for (String artistName
                    : artistNames) {

                System.out.println(
                        "[Lyrics] 网易云搜索："
                                + "song="
                                + target.songName()
                                + ", artist="
                                + artistName
                );

                addNeteaseCandidates(
                        unique,
                        neteaseClient.search(
                                target.songName(),
                                artistName
                        )
                );
            }
        }

        /*
         * 别名全部查不到时再用“纯歌名”做一次宽松召回。
         * 后续必须经过本地评分，不会直接接受。
         */
        if (unique.isEmpty()) {
            System.out.println(
                    "[Lyrics] 网易云歌名+音乐人无候选，"
                            + "追加纯歌名召回"
            );

            addNeteaseCandidates(
                    unique,
                    neteaseClient.search(
                            target.songName(),
                            ""
                    )
            );
        }

        return List.copyOf(
                unique.values()
        );
    }

    private static void addNeteaseCandidates(
            Map<Long, NeteaseSong> unique,
            List<NeteaseSong> records
    ) {
        if (records == null) {
            return;
        }

        for (NeteaseSong song : records) {
            if (
                    song == null
                            || song.id() <= 0
            ) {
                continue;
            }

            unique.putIfAbsent(
                    song.id(),
                    song
            );
        }
    }

    private List<ScoredNetease>
    scoreNeteaseCandidates(
            LyricsTarget target,
            List<NeteaseSong> candidates
    ) {
        List<ScoredNetease> result =
                new ArrayList<>();

        for (NeteaseSong candidate
                : candidates) {

            double score =
                    scoreCandidate(
                            target,
                            candidate.trackName(),
                            candidate.artistName(),
                            candidate.albumName(),
                            candidate.durationSeconds()
                    );

            if (score < MIN_CANDIDATE_SCORE) {
                continue;
            }

            result.add(
                    new ScoredNetease(
                            candidate,
                            score
                    )
            );
        }

        result.sort(
                Comparator.comparingDouble(
                                ScoredNetease::score
                        )
                        .reversed()
        );

        return List.copyOf(
                result
        );
    }

    private Optional<ScoredNetease>
    selectNeteaseCandidate(
            LyricsTarget target,
            List<ScoredNetease> scored
    ) {
        if (canAutoAccept(
                scored.stream()
                        .map(
                                ScoredNetease::score
                        )
                        .toList()
        )) {
            return Optional.of(
                    scored.get(0)
            );
        }

        List<ScoredNetease> top =
                scored.subList(
                        0,
                        Math.min(
                                AI_CANDIDATE_LIMIT,
                                scored.size()
                        )
                );

        Optional<MatchDecision> decision =
                ollamaClient
                        .selectLyricsCandidate(
                                toAiTarget(
                                        target
                                ),
                                top.stream()
                                        .map(
                                                item ->
                                                        new LyricsMatchCandidate(
                                                                item.song().id(),
                                                                item.song().trackName(),
                                                                item.song().artistName(),
                                                                item.song().albumName(),
                                                                item.song().durationSeconds(),
                                                                item.score()
                                                        )
                                        )
                                        .toList()
                        );

        if (
                decision.isEmpty()
                        || !decision.get()
                        .matched()
                        || decision.get()
                        .confidence()
                        < AI_MIN_CONFIDENCE
        ) {
            return Optional.empty();
        }

        long selectedId =
                decision.get()
                        .selectedId();

        return top.stream()
                .filter(
                        item ->
                                item.song().id()
                                        == selectedId
                )
                .findFirst();
    }

    private static List<ScoredNetease>
    buildNeteaseAttemptOrder(
            ScoredNetease selected,
            List<ScoredNetease> scored
    ) {
        List<ScoredNetease> result =
                new ArrayList<>();

        result.add(
                selected
        );

        for (ScoredNetease item
                : scored) {
            if (
                    item.song().id()
                            == selected.song().id()
            ) {
                continue;
            }

            /*
             * 只有极高置信的备用候选才尝试取歌词，
             * 防止“首选没有歌词”时误切到同名不同版本。
             */
            if (item.score() >= AUTO_ACCEPT_SCORE) {
                result.add(
                        item
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    private Optional<ResolvedLyrics>
    resolveLrclib(
            LyricsTarget target
    ) {
        System.out.println(
                "[Lyrics] 开始 LRCLIB fallback："
                        + target.songName()
        );

        /*
         * LRCLIB 的 /api/get 参数非常严格，
         * 元数据完整时先尝试精确匹配。
         */
        if (
                !target.albumName().isBlank()
                        && target.durationSeconds() > 0
        ) {
            for (String artistName
                    : target.artistNames()) {

                Optional<LrclibRecord> exact =
                        lrclibClient.getExact(
                                target.songName(),
                                artistName,
                                target.albumName(),
                                target.durationSeconds()
                        );

                if (
                        exact.isPresent()
                                && hasUsableLrclibLyrics(
                                exact.get()
                        )
                ) {
                    return Optional.of(
                            toResolvedLrclib(
                                    target,
                                    exact.get(),
                                    1.0
                            )
                    );
                }
            }
        }

        List<LrclibRecord> candidates =
                searchLrclibCandidates(
                        target
                );

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<ScoredLrclib> scored =
                scoreLrclibCandidates(
                        target,
                        candidates
                );

        if (scored.isEmpty()) {
            return Optional.empty();
        }

        Optional<ScoredLrclib> selected =
                selectLrclibCandidate(
                        target,
                        scored
                );

        if (selected.isEmpty()) {
            return Optional.empty();
        }

        ScoredLrclib chosen =
                selected.get();

        return Optional.of(
                toResolvedLrclib(
                        target,
                        chosen.record(),
                        chosen.score()
                )
        );
    }

    private List<LrclibRecord>
    searchLrclibCandidates(
            LyricsTarget target
    ) {
        Map<Long, LrclibRecord> unique =
                new LinkedHashMap<>();

        for (String artistName
                : target.artistNames()) {

            List<LrclibRecord> records =
                    lrclibClient.search(
                            target.songName(),
                            artistName
                    );

            addLrclibCandidates(
                    unique,
                    records
            );
        }

        if (unique.isEmpty()) {
            addLrclibCandidates(
                    unique,
                    lrclibClient.search(
                            target.songName(),
                            ""
                    )
            );
        }

        return List.copyOf(
                unique.values()
        );
    }

    private static void addLrclibCandidates(
            Map<Long, LrclibRecord> unique,
            List<LrclibRecord> records
    ) {
        if (records == null) {
            return;
        }

        for (LrclibRecord record : records) {
            if (
                    record == null
                            || record.id() == null
                            || record.id() <= 0
                            || !hasUsableLrclibLyrics(
                            record
                    )
            ) {
                continue;
            }

            unique.putIfAbsent(
                    record.id(),
                    record
            );
        }
    }

    private List<ScoredLrclib>
    scoreLrclibCandidates(
            LyricsTarget target,
            List<LrclibRecord> candidates
    ) {
        List<ScoredLrclib> result =
                new ArrayList<>();

        for (LrclibRecord record
                : candidates) {

            double score =
                    scoreCandidate(
                            target,
                            record.trackName(),
                            record.artistName(),
                            record.albumName(),
                            record.duration()
                                    == null
                                    ? 0
                                    : record.duration()
                    );

            if (score < MIN_CANDIDATE_SCORE) {
                continue;
            }

            result.add(
                    new ScoredLrclib(
                            record,
                            score
                    )
            );
        }

        result.sort(
                Comparator.comparingDouble(
                                ScoredLrclib::score
                        )
                        .reversed()
        );

        return List.copyOf(
                result
        );
    }

    private Optional<ScoredLrclib>
    selectLrclibCandidate(
            LyricsTarget target,
            List<ScoredLrclib> scored
    ) {
        if (canAutoAccept(
                scored.stream()
                        .map(
                                ScoredLrclib::score
                        )
                        .toList()
        )) {
            return Optional.of(
                    scored.get(0)
            );
        }

        List<ScoredLrclib> top =
                scored.subList(
                        0,
                        Math.min(
                                AI_CANDIDATE_LIMIT,
                                scored.size()
                        )
                );

        Optional<MatchDecision> decision =
                ollamaClient
                        .selectLyricsCandidate(
                                toAiTarget(
                                        target
                                ),
                                top.stream()
                                        .map(
                                                item ->
                                                        new LyricsMatchCandidate(
                                                                item.record().id(),
                                                                textOrEmpty(
                                                                        item.record().trackName()
                                                                ),
                                                                textOrEmpty(
                                                                        item.record().artistName()
                                                                ),
                                                                textOrEmpty(
                                                                        item.record().albumName()
                                                                ),
                                                                item.record().duration(),
                                                                item.score()
                                                        )
                                        )
                                        .toList()
                        );

        if (
                decision.isEmpty()
                        || !decision.get()
                        .matched()
                        || decision.get()
                        .confidence()
                        < AI_MIN_CONFIDENCE
        ) {
            return Optional.empty();
        }

        long selectedId =
                decision.get()
                        .selectedId();

        return top.stream()
                .filter(
                        item ->
                                item.record().id()
                                        == selectedId
                )
                .findFirst();
    }

    private static ResolvedLyrics
    toResolvedLrclib(
            LyricsTarget target,
            LrclibRecord record,
            double score
    ) {
        String synced =
                nullableTrim(
                        record.syncedLyrics()
                );

        String plain =
                nullableTrim(
                        record.plainLyrics()
                );

        if (
                plain == null
                        && synced != null
        ) {
            String derived =
                    stripLrcTimestamps(
                            synced
                    );

            plain =
                    derived.isBlank()
                            ? null
                            : derived;
        }

        return new ResolvedLyrics(
                "LRCLIB",
                record.id() == null
                        ? null
                        : record.id()
                        .toString(),
                record.instrumental(),
                plain,
                synced,
                score
        );
    }

    private static boolean hasUsableLrclibLyrics(
            LrclibRecord record
    ) {
        if (record == null) {
            return false;
        }

        return record.instrumental()
                || hasText(
                record.syncedLyrics()
        )
                || hasText(
                record.plainLyrics()
        );
    }

    private static LyricsMatchTarget
    toAiTarget(
            LyricsTarget target
    ) {
        return new LyricsMatchTarget(
                target.songName(),
                target.artistNames(),
                target.albumName(),
                target.durationSeconds()
        );
    }

    private static boolean canAutoAccept(
            List<Double> scores
    ) {
        if (
                scores == null
                        || scores.isEmpty()
        ) {
            return false;
        }

        double first =
                scores.get(0);

        if (first < AUTO_ACCEPT_SCORE) {
            return false;
        }

        if (
                scores.size() == 1
                        || first >= 0.98
        ) {
            return true;
        }

        double second =
                scores.get(1);

        return first - second
                >= AUTO_ACCEPT_MARGIN;
    }

    /**
     * 动态权重评分：
     * - 歌名 50%
     * - 音乐人 30%
     * - 时长 15%
     * - 专辑 5%
     *
     * 缺失的可选元数据不会硬扣分，
     * 而是只在可用字段上重新归一化。
     */
    private static double scoreCandidate(
            LyricsTarget target,
            String candidateTrack,
            String candidateArtist,
            String candidateAlbum,
            int candidateDuration
    ) {
        double weighted = 0;
        double totalWeight = 0;

        weighted +=
                0.50
                        * textSimilarity(
                        target.songName(),
                        candidateTrack
                );

        totalWeight += 0.50;

        if (
                !target.artistNames()
                        .isEmpty()
                        && hasText(
                        candidateArtist
                )
        ) {
            double bestArtist =
                    target.artistNames()
                            .stream()
                            .mapToDouble(
                                    artist ->
                                            textSimilarity(
                                                    artist,
                                                    candidateArtist
                                            )
                            )
                            .max()
                            .orElse(0);

            weighted +=
                    0.30
                            * bestArtist;

            totalWeight += 0.30;
        }

        if (
                target.durationSeconds() > 0
                        && candidateDuration > 0
        ) {
            weighted +=
                    0.15
                            * durationSimilarity(
                            target.durationSeconds(),
                            candidateDuration
                    );

            totalWeight += 0.15;
        }

        if (
                hasText(
                        target.albumName()
                )
                        && hasText(
                        candidateAlbum
                )
        ) {
            weighted +=
                    0.05
                            * textSimilarity(
                            target.albumName(),
                            candidateAlbum
                    );

            totalWeight += 0.05;
        }

        double score =
                totalWeight <= 0
                        ? 0
                        : weighted
                        / totalWeight;

        score *=
                versionCompatibilityPenalty(
                        target.songName(),
                        candidateTrack
                );

        return clamp01(
                score
        );
    }

    private static double durationSimilarity(
            int expected,
            int actual
    ) {
        int diff =
                Math.abs(
                        expected - actual
                );

        if (diff <= 2) {
            return 1.0;
        }

        if (diff <= 5) {
            return 0.85;
        }

        if (diff <= 10) {
            return 0.55;
        }

        if (diff <= 20) {
            return 0.20;
        }

        return 0;
    }

    private static double textSimilarity(
            String left,
            String right
    ) {
        String a =
                normalizeText(
                        left
                );

        String b =
                normalizeText(
                        right
                );

        if (
                a.isEmpty()
                        || b.isEmpty()
        ) {
            return 0;
        }

        if (a.equals(b)) {
            return 1.0;
        }

        if (
                a.contains(b)
                        || b.contains(a)
        ) {
            return 0.88;
        }

        return diceSimilarity(
                a,
                b
        );
    }

    private static double diceSimilarity(
            String left,
            String right
    ) {
        if (
                left.length() < 2
                        || right.length() < 2
        ) {
            return left.equals(right)
                    ? 1
                    : 0;
        }

        Map<String, Integer> leftPairs =
                bigramCounts(
                        left
                );

        Map<String, Integer> rightPairs =
                bigramCounts(
                        right
                );

        int intersection = 0;

        for (Map.Entry<String, Integer> entry
                : leftPairs.entrySet()) {

            intersection +=
                    Math.min(
                            entry.getValue(),
                            rightPairs.getOrDefault(
                                    entry.getKey(),
                                    0
                            )
                    );
        }

        int leftCount =
                leftPairs.values()
                        .stream()
                        .mapToInt(
                                Integer::intValue
                        )
                        .sum();

        int rightCount =
                rightPairs.values()
                        .stream()
                        .mapToInt(
                                Integer::intValue
                        )
                        .sum();

        return 2.0
                * intersection
                / Math.max(
                1,
                leftCount + rightCount
        );
    }

    private static Map<String, Integer>
    bigramCounts(
            String value
    ) {
        Map<String, Integer> result =
                new LinkedHashMap<>();

        for (
                int i = 0;
                i < value.length() - 1;
                i++
        ) {
            String pair =
                    value.substring(
                            i,
                            i + 2
                    );

            result.merge(
                    pair,
                    1,
                    Integer::sum
            );
        }

        return result;
    }

    private static double versionCompatibilityPenalty(
            String targetTrack,
            String candidateTrack
    ) {
        Set<String> targetTags =
                versionTags(
                        targetTrack
                );

        Set<String> candidateTags =
                versionTags(
                        candidateTrack
                );

        if (targetTags.equals(
                candidateTags
        )) {
            return 1.0;
        }

        if (
                targetTags.isEmpty()
                        && candidateTags.isEmpty()
        ) {
            return 1.0;
        }

        /*
         * 版本标签不一致是强负证据，
         * 但不直接归零，留给 AI/其他元数据兜底。
         */
        return 0.62;
    }

    private static Set<String> versionTags(
            String text
    ) {
        String normalized =
                text == null
                        ? ""
                        : Normalizer
                        .normalize(
                                text,
                                Normalizer.Form.NFKC
                        )
                        .toLowerCase(
                                Locale.ROOT
                        );

        Set<String> result =
                new LinkedHashSet<>();

        for (String tag : VERSION_TAGS) {
            if (normalized.contains(
                    tag
            )) {
                result.add(
                        tag
                );
            }
        }

        return result;
    }

    private static String normalizeText(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return Normalizer
                .normalize(
                        value,
                        Normalizer.Form.NFKC
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[\\p{P}\\p{S}\\s]+",
                        ""
                )
                .trim();
    }

    /**
     * 把 LRC 转成纯歌词文本。
     * 同一行存在多个时间戳时会全部去除。
     */
    public static String stripLrcTimestamps(
            String syncedLyrics
    ) {
        if (
                syncedLyrics == null
                        || syncedLyrics.isBlank()
        ) {
            return "";
        }

        List<String> result =
                new ArrayList<>();

        String[] lines =
                syncedLyrics.split(
                        "\\R",
                        -1
                );

        for (String rawLine : lines) {
            String line =
                    rawLine == null
                            ? ""
                            : rawLine.trim();

            if (
                    line.isEmpty()
                            || LRC_METADATA_TAG
                            .matcher(line)
                            .matches()
            ) {
                continue;
            }

            while (true) {
                var matcher =
                        LRC_TIME_TAG.matcher(
                                line
                        );

                if (!matcher.find()) {
                    break;
                }

                line =
                        line.substring(
                                matcher.end()
                        ).trim();
            }

            if (!line.isBlank()) {
                result.add(
                        line
                );
            }
        }

        return String.join(
                "\n",
                result
        );
    }

    private static boolean looksInstrumental(
            String plainLyrics
    ) {
        if (
                plainLyrics == null
                        || plainLyrics.isBlank()
        ) {
            return false;
        }

        String normalized =
                normalizeText(
                        plainLyrics
                );

        return normalized.equals(
                normalizeText(
                        "纯音乐，请欣赏"
                )
        )
                || normalized.contains(
                normalizeText(
                        "此歌曲为没有填词的纯音乐"
                )
        );
    }

    private static LyricsTarget normalizeTarget(
            LyricsTarget target
    ) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "歌词匹配目标不能为空"
            );
        }

        String songName =
                textOrEmpty(
                        target.songName()
                ).trim();

        if (songName.isBlank()) {
            throw new IllegalArgumentException(
                    "歌曲名称不能为空"
            );
        }

        LinkedHashMap<String, String> aliases =
                new LinkedHashMap<>();

        if (target.artistNames() != null) {
            for (String artistName
                    : target.artistNames()) {

                if (
                        artistName == null
                                || artistName.isBlank()
                ) {
                    continue;
                }

                String trimmed =
                        artistName.trim();

                aliases.putIfAbsent(
                        normalizeText(
                                trimmed
                        ),
                        trimmed
                );
            }
        }

        return new LyricsTarget(
                songName,
                List.copyOf(
                        aliases.values()
                ),
                textOrEmpty(
                        target.albumName()
                ).trim(),
                Math.max(
                        0,
                        target.durationSeconds()
                )
        );
    }

    private static boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private static String textOrEmpty(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private static String nullableTrim(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private static double clamp01(
            double value
    ) {
        return Math.max(
                0,
                Math.min(
                        1,
                        value
                )
        );
    }

    public record LyricsTarget(
            String songName,
            List<String> artistNames,
            String albumName,
            int durationSeconds
    ) {
    }

    public record ResolvedLyrics(
            String provider,
            String providerLyricId,
            boolean instrumental,
            String plainLyrics,
            String syncedLyrics,
            double matchScore
    ) {
    }

    private record ScoredNetease(
            NeteaseSong song,
            double score
    ) {
    }

    private record ScoredLrclib(
            LrclibRecord record,
            double score
    ) {
    }
}
