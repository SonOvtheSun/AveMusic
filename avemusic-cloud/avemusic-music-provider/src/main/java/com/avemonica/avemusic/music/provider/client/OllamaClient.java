package com.avemonica.avemusic.music.provider.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public final class OllamaClient {

    private static final Map<String, Object>
            MATCH_RESPONSE_SCHEMA =
            Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                            "matched",
                            Map.of(
                                    "type",
                                    "boolean"
                            ),
                            "selectedId",
                            Map.of(
                                    "type",
                                    "integer",
                                    "minimum",
                                    0
                            ),
                            "confidence",
                            Map.of(
                                    "type",
                                    "number",
                                    "minimum",
                                    0,
                                    "maximum",
                                    1
                            )
                    ),
                    "required",
                    List.of(
                            "matched",
                            "selectedId",
                            "confidence"
                    ),
                    "additionalProperties",
                    false
            );

    private static final String
            MATCH_SYSTEM_PROMPT = """
            你是音乐元数据匹配器。

            你的任务只有一个：
            根据 target 与 candidates，判断候选中是否存在同一首歌曲，
            如果存在，只能从 candidates 中选一个已有 id。

            判断重点：
            1. 歌曲名最重要；
            2. 音乐人原名、译名、罗马音可能指向同一音乐人；
            3. 专辑名可以有普通版、限定版等轻微差异；
            4. 时长接近是重要证据；
            5. localScore 是 Java 已计算的先验分数，应重点参考；
            6. Live、Remix、Instrumental、Cover、Acoustic、Demo、Karaoke、Off Vocal
               等版本标签不一致时，不要轻易判定为同一版本。

            严格限制：
            - 不允许生成歌词；
            - 不允许创造候选；
            - matched=false 时 selectedId 必须为 0；
            - matched=true 时 selectedId 必须来自 candidates；
            - confidence 必须在 0 到 1 之间；
            - 证据不足时宁可 matched=false；
            - 只返回符合 JSON Schema 的 JSON，不要 Markdown，不要解释。
            """;

    private static final String
            TRANSLATION_SYSTEM_PROMPT = """
            你是严格的逐行音乐歌词翻译器。

            目标：把输入歌词逐行翻译成自然、准确的简体中文。

            必须严格遵守：
            1. translations 数组长度必须与输入 lines 数组长度完全一致；
            2. translations[i] 只对应 lines[i].text；
            3. 严格保持顺序；
            4. 不允许合并、拆分、新增或删除歌词行；
            5. 每个数组元素都必须是一个合法 JSON 字符串；
            6. 字符串内部出现英文双引号时必须正确 JSON 转义；
            7. 不要输出 Markdown、代码块、注释、解释或额外字段；
            8. 人名、乐队名、作品名等无法自然翻译时可保留原文；
            9. 原句已经是自然简体中文时，对应项返回空字符串 ""；
            10. 空行对应空字符串 ""；
            11. 不要在翻译外层自行添加 []、【】、引号或编号；
            12. 只返回指定 JSON Schema。
            """;

    private static final String
            SEARCH_EXPANSION_SYSTEM_PROMPT = """
        你是音乐平台搜索词扩展器。

        用户会输入一个用于搜索歌曲、音乐人、专辑或歌单的关键词。

        你的任务不是返回搜索结果，而是根据原搜索词生成 2~3 个
        最可能帮助数据库检索到同一目标的搜索词。

        优先考虑：
        1. 中文名、英文名、日文名之间的常见转换；
        2. 音乐人、乐队的常见别名；
        3. 罗马音或英文写法；
        4. 常见简称对应的完整名称；
        5. 明显输入错误的合理纠正；
        6. 简繁体差异。

        严格禁止：
        1. 不得推荐与用户目标无关的音乐；
        2. 不得扩展成音乐风格、情绪等宽泛概念；
        3. 不得凭空创造不存在的作品；
        4. 不得返回原关键词本身；
        5. 不要解释；
        6. 只返回 JSON Schema 指定内容。

        例如：

        周董
        -> 周杰伦
        -> Jay Chou
        -> 周杰倫

        mygo
        -> MyGO!!!!!
        -> BanG Dream! It's MyGO!!!!!

        进击巨人
        -> 进击的巨人
        -> Attack on Titan
        -> 進撃の巨人
        """;

    private static final Map<String, Object>
            SEARCH_EXPANSION_SCHEMA =
            Map.of(
                    "type",
                    "object",

                    "properties",
                    Map.of(
                            "queries",
                            Map.of(
                                    "type",
                                    "array",
                                    "minItems",
                                    2,
                                    "maxItems",
                                    3,
                                    "items",
                                    Map.of(
                                            "type",
                                            "string",
                                            "minLength",
                                            1,
                                            "maxLength",
                                            64
                                    )
                            )
                    ),

                    "required",
                    List.of(
                            "queries"
                    ),

                    "additionalProperties",
                    false
            );



    private final RestClient restClient;
    private final ObjectMapper objectMapper =
            new ObjectMapper();
    private final String model;

    private final int translationBatchSize;
    private final int translationMaxAttempts;
    private final int matchMaxAttempts;
    private final long retryBackoffMillis;
    private final int maxLogContentChars;

    public OllamaClient(
            @Value(
                    "${avemusic.ai.ollama.base-url:"
                            + "http://127.0.0.1:11434}"
            )
            String baseUrl,

            @Value(
                    "${avemusic.ai.ollama.model:"
                            + "qwen3.5:9b}"
            )
            String model,

            @Value(
                    "${avemusic.ai.ollama."
                            + "connect-timeout-seconds:5}"
            )
            long connectTimeoutSeconds,

            @Value(
                    "${avemusic.ai.ollama."
                            + "read-timeout-seconds:300}"
            )
            long readTimeoutSeconds,

            @Value(
                    "${avemusic.ai.ollama."
                            + "translation-batch-size:16}"
            )
            int translationBatchSize,

            @Value(
                    "${avemusic.ai.ollama."
                            + "translation-max-attempts:3}"
            )
            int translationMaxAttempts,

            @Value(
                    "${avemusic.ai.ollama."
                            + "match-max-attempts:2}"
            )
            int matchMaxAttempts,

            @Value(
                    "${avemusic.ai.ollama."
                            + "retry-backoff-millis:250}"
            )
            long retryBackoffMillis,

            @Value(
                    "${avemusic.ai.ollama."
                            + "max-log-content-chars:2000}"
            )
            int maxLogContentChars
    ) {
        this.model = model;

        this.translationBatchSize =
                Math.max(
                        4,
                        Math.min(
                                translationBatchSize,
                                25
                        )
                );

        this.translationMaxAttempts =
                Math.max(
                        1,
                        Math.min(
                                translationMaxAttempts,
                                5
                        )
                );

        this.matchMaxAttempts =
                Math.max(
                        1,
                        Math.min(
                                matchMaxAttempts,
                                4
                        )
                );

        this.retryBackoffMillis =
                Math.max(
                        0,
                        Math.min(
                                retryBackoffMillis,
                                5_000
                        )
                );

        this.maxLogContentChars =
                Math.max(
                        200,
                        Math.min(
                                maxLogContentChars,
                                10_000
                        )
                );

        HttpClient httpClient =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(
                                        Math.max(
                                                1,
                                                connectTimeoutSeconds
                                        )
                                )
                        )
                        .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(
                        httpClient
                );

        requestFactory.setReadTimeout(
                Duration.ofSeconds(
                        Math.max(
                                1,
                                readTimeoutSeconds
                        )
                )
        );

        this.restClient =
                RestClient
                        .builder()
                        .baseUrl(baseUrl)
                        .requestFactory(
                                requestFactory
                        )
                        .build();
    }

    /**
     * 逐行翻译。
     *
     * 核心容错策略：
     * 1. 小批次；
     * 2. JSON Schema 严格限定数组长度；
     * 3. 每批自动重试；
     * 4. 连续失败时自动二分批次；
     * 5. 单行仍失败才判整首翻译失败；
     * 6. 不尝试用正则“修坏 JSON”，避免误改歌词正文。
     */
    public Optional<List<String>>
    translateLyrics(
            List<String> lines
    ) {
        if (
                lines == null
                        || lines.isEmpty()
        ) {
            return Optional.of(
                    List.of()
            );
        }

        List<String> normalizedLines =
                new ArrayList<>(
                        lines.size()
                );

        for (String line : lines) {
            normalizedLines.add(
                    line == null
                            ? ""
                            : line
            );
        }

        List<String> result =
                new ArrayList<>(
                        Collections.nCopies(
                                normalizedLines.size(),
                                ""
                        )
                );

        for (
                int start = 0;
                start < normalizedLines.size();
                start += translationBatchSize
        ) {
            int end =
                    Math.min(
                            start
                                    + translationBatchSize,
                            normalizedLines.size()
                    );

            boolean success =
                    translateRange(
                            normalizedLines,
                            start,
                            end,
                            result
                    );

            if (!success) {
                System.err.println(
                        "[Lyrics-AI] 翻译最终失败，"
                                + "range="
                                + start
                                + ".."
                                + (end - 1)
                );

                return Optional.empty();
            }
        }

        return Optional.of(
                List.copyOf(result)
        );
    }

    public List<String> expandSearchKeywords(
            String keyword
    ) {
        if (
                keyword == null
                        || keyword.isBlank()
        ) {
            return List.of();
        }

        String normalized =
                keyword.trim();

        try {
            ChatRequest request =
                    new ChatRequest(
                            model,

                            List.of(
                                    new ChatMessage(
                                            "system",
                                            SEARCH_EXPANSION_SYSTEM_PROMPT
                                    ),

                                    new ChatMessage(
                                            "user",
                                            "原始搜索词："
                                                    + normalized
                                    )
                            ),

                            false,
                            false,

                            SEARCH_EXPANSION_SCHEMA,

                            Map.of(
                                    "temperature",
                                    0.1,
                                    "seed",
                                    42,
                                    "num_predict",
                                    128
                            )
                    );

            ChatResponse response =
                    restClient
                            .post()
                            .uri("/api/chat")
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(request)
                            .retrieve()
                            .body(
                                    ChatResponse.class
                            );

            if (
                    response == null
                            || response.message() == null
                            || response.message()
                            .content() == null
            ) {
                return List.of();
            }

            JsonNode root =
                    objectMapper.readTree(
                            normalizeJsonContent(
                                    response.message()
                                            .content()
                            )
                    );

            JsonNode queries =
                    root.path("queries");

            if (!queries.isArray()) {
                return List.of();
            }

            LinkedHashMap<
                    String,
                    String
                    > result =
                    new LinkedHashMap<>();

            for (JsonNode item : queries) {

                if (!item.isTextual()) {
                    continue;
                }

                String value =
                        item.asText()
                                .trim();

                if (
                        value.isBlank()
                                || value.length() > 64
                                || value.equalsIgnoreCase(
                                normalized
                        )
                ) {
                    continue;
                }

                result.putIfAbsent(
                        value.toLowerCase(
                                Locale.ROOT
                        ),
                        value
                );

                if (result.size() >= 3) {
                    break;
                }
            }

            return List.copyOf(
                    result.values()
            );

        } catch (Exception exception) {

            /*
             * AI增强失败绝对不能导致搜索功能不可用。
             */
            System.err.println(
                    "[Search-AI] 搜索词扩展失败："
                            + exception.getMessage()
            );

            return List.of();
        }
    }

    private boolean translateRange(
            List<String> lines,
            int start,
            int end,
            List<String> output
    ) {
        List<TranslationLine> batch =
                new ArrayList<>(
                        end - start
                );

        for (
                int index = start;
                index < end;
                index++
        ) {
            batch.add(
                    new TranslationLine(
                            index,
                            lines.get(index)
                    )
            );
        }

        Optional<List<String>> translated =
                translateLyricsBatchWithRetry(
                        batch
                );

        if (translated.isPresent()) {
            List<String> values =
                    translated.get();

            for (
                    int offset = 0;
                    offset < values.size();
                    offset++
            ) {
                output.set(
                        start + offset,
                        values.get(offset)
                );
            }

            return true;
        }

        int size = end - start;

        if (size <= 1) {
            return false;
        }

        int middle =
                start + size / 2;

        System.err.println(
                "[Lyrics-AI] 批次连续失败，"
                        + "自动二分："
                        + start
                        + ".."
                        + (end - 1)
                        + " -> "
                        + start
                        + ".."
                        + (middle - 1)
                        + " + "
                        + middle
                        + ".."
                        + (end - 1)
        );

        return translateRange(
                lines,
                start,
                middle,
                output
        ) && translateRange(
                lines,
                middle,
                end,
                output
        );
    }

    private Optional<List<String>>
    translateLyricsBatchWithRetry(
            List<TranslationLine> batch
    ) {
        Exception lastException = null;

        for (
                int attempt = 1;
                attempt <= translationMaxAttempts;
                attempt++
        ) {
            try {
                List<String> result =
                        translateLyricsBatchOnce(
                                batch
                        );

                return Optional.of(
                        result
                );

            } catch (Exception exception) {
                lastException = exception;

                System.err.println(
                        "[Lyrics-AI] 批次翻译失败，"
                                + "attempt="
                                + attempt
                                + "/"
                                + translationMaxAttempts
                                + ", size="
                                + batch.size()
                                + ", error="
                                + exception
                                .getClass()
                                .getSimpleName()
                                + ": "
                                + exception.getMessage()
                );

                if (
                        attempt
                                < translationMaxAttempts
                ) {
                    sleepQuietly(
                            retryBackoffMillis
                                    * attempt
                    );
                }
            }
        }

        if (lastException != null) {
            System.err.println(
                    "[Lyrics-AI] 当前批次连续失败："
                            + lastException.getMessage()
            );
        }

        return Optional.empty();
    }

    private List<String>
    translateLyricsBatchOnce(
            List<TranslationLine> batch
    ) throws Exception {
        if (
                batch == null
                        || batch.isEmpty()
        ) {
            return List.of();
        }

        int expectedSize =
                batch.size();

        Map<String, Object> schema =
                translationResponseSchema(
                        expectedSize
                );

        String input =
                objectMapper
                        .writeValueAsString(
                                Map.of(
                                        "lines",
                                        batch
                                )
                        );

        String schemaJson =
                objectMapper
                        .writeValueAsString(
                                schema
                        );



        String userPrompt = """
                将下面 %d 行歌词逐行翻译成简体中文。

                输出必须满足：
                - translations 必须恰好包含 %d 个字符串；
                - 第 i 项只能翻译第 i 行；
                - 不得缺行、增行、合并或拆分；
                - 不得输出 Markdown 或解释；
                - JSON 字符串中的双引号必须正确转义；
                - 返回内容只能是一个 JSON object。

                JSON Schema：
                %s

                输入：
                %s
                """.formatted(
                expectedSize,
                expectedSize,
                schemaJson,
                input
        );

        Map<String, Object> options =
                new LinkedHashMap<>();

        options.put(
                "temperature",
                0
        );

        options.put(
                "top_p",
                0.8
        );

        options.put(
                "seed",
                42
        );

        options.put(
                "num_predict",
                Math.max(
                        1024,
                        Math.min(
                                8192,
                                512
                                        + expectedSize
                                        * 256
                        )
                )
        );

        ChatRequest request =
                new ChatRequest(
                        model,
                        List.of(
                                new ChatMessage(
                                        "system",
                                        TRANSLATION_SYSTEM_PROMPT
                                ),
                                new ChatMessage(
                                        "user",
                                        userPrompt
                                )
                        ),
                        false,
                        false,
                        schema,
                        options
                );

        ChatResponse response =
                restClient
                        .post()
                        .uri(
                                "/api/chat"
                        )
                        .contentType(
                                MediaType
                                        .APPLICATION_JSON
                        )
                        .body(request)
                        .retrieve()
                        .body(
                                ChatResponse.class
                        );

        if (
                response == null
                        || response.message()
                        == null
        ) {
            throw new IllegalStateException(
                    "Ollama 返回空响应"
            );
        }

        String rawContent =
                response.message()
                        .content();

        if (
                rawContent == null
                        || rawContent.isBlank()
        ) {
            throw new IllegalStateException(
                    "Ollama 翻译 content 为空"
            );
        }

        System.out.println(
                "[Lyrics-AI] 翻译原始输出："
                        + abbreviate(
                        rawContent
                )
        );

        String jsonContent =
                normalizeJsonContent(
                        rawContent
                );

        if (jsonContent.isBlank()) {
            throw new IllegalStateException(
                    "模型输出中不存在 JSON object"
            );
        }

        JsonNode root =
                objectMapper.readTree(
                        jsonContent
                );

        if (
                root == null
                        || !root.isObject()
        ) {
            throw new IllegalStateException(
                    "翻译结果不是 JSON object"
            );
        }

        JsonNode translationsNode =
                root.get(
                        "translations"
                );

        if (
                translationsNode == null
                        || !translationsNode
                        .isArray()
        ) {
            throw new IllegalStateException(
                    "translations 不是数组"
            );
        }

        if (
                translationsNode.size()
                        != expectedSize
        ) {
            throw new IllegalStateException(
                    "翻译行数不匹配，expected="
                            + expectedSize
                            + ", actual="
                            + translationsNode.size()
            );
        }

        List<String> result =
                new ArrayList<>(
                        expectedSize
                );

        for (
                int index = 0;
                index < translationsNode.size();
                index++
        ) {
            JsonNode item =
                    translationsNode.get(
                            index
                    );

            if (
                    item == null
                            || !item.isTextual()
            ) {
                throw new IllegalStateException(
                        "translations["
                                + index
                                + "] 不是字符串"
                );
            }

            result.add(
                    item.asText("")
                            .trim()
            );
        }

        return List.copyOf(
                result
        );
    }

    /**
     * 让 Qwen 只在 Java 已筛过的候选中做消歧。
     */
    public Optional<MatchDecision>
    selectLyricsCandidate(
            LyricsMatchTarget target,
            List<LyricsMatchCandidate> candidates
    ) {
        if (
                target == null
                        || candidates == null
                        || candidates.isEmpty()
        ) {
            return Optional.empty();
        }

        Exception lastException = null;

        for (
                int attempt = 1;
                attempt <= matchMaxAttempts;
                attempt++
        ) {
            try {
                MatchDecision decision =
                        selectLyricsCandidateOnce(
                                target,
                                candidates
                        );

                return Optional.ofNullable(
                        decision
                );

            } catch (Exception exception) {
                lastException = exception;

                System.err.println(
                        "[Lyrics-AI] 候选消歧失败，"
                                + "attempt="
                                + attempt
                                + "/"
                                + matchMaxAttempts
                                + ", error="
                                + exception
                                .getClass()
                                .getSimpleName()
                                + ": "
                                + exception.getMessage()
                );

                if (
                        attempt
                                < matchMaxAttempts
                ) {
                    sleepQuietly(
                            retryBackoffMillis
                                    * attempt
                    );
                }
            }
        }

        if (lastException != null) {
            System.err.println(
                    "[Lyrics-AI] 候选消歧最终失败："
                            + lastException.getMessage()
            );
        }

        return Optional.empty();
    }

    private MatchDecision
    selectLyricsCandidateOnce(
            LyricsMatchTarget target,
            List<LyricsMatchCandidate> candidates
    ) throws Exception {
        MatchInput input =
                new MatchInput(
                        target,
                        candidates
                );

        String inputJson =
                objectMapper
                        .writeValueAsString(
                                input
                        );

        String userPrompt = """
                下面是目标歌曲 target 与 Java 预筛选后的候选 candidates。
                只能在 candidates 中选择，证据不足就返回 matched=false。

                输入：
                %s
                """.formatted(
                inputJson
        );

        ChatRequest request =
                new ChatRequest(
                        model,
                        List.of(
                                new ChatMessage(
                                        "system",
                                        MATCH_SYSTEM_PROMPT
                                ),
                                new ChatMessage(
                                        "user",
                                        userPrompt
                                )
                        ),
                        false,
                        false,
                        MATCH_RESPONSE_SCHEMA,
                        Map.of(
                                "temperature",
                                0,
                                "seed",
                                42,
                                "num_predict",
                                256
                        )
                );

        ChatResponse response =
                restClient
                        .post()
                        .uri(
                                "/api/chat"
                        )
                        .contentType(
                                MediaType
                                        .APPLICATION_JSON
                        )
                        .body(request)
                        .retrieve()
                        .body(
                                ChatResponse.class
                        );

        if (
                response == null
                        || response.message()
                        == null
        ) {
            throw new IllegalStateException(
                    "Ollama 返回空响应"
            );
        }

        String content =
                response.message()
                        .content();

        if (
                content == null
                        || content.isBlank()
        ) {
            throw new IllegalStateException(
                    "Ollama 候选消歧 content 为空"
            );
        }

        String jsonContent =
                normalizeJsonContent(
                        content
                );

        MatchDecision decision =
                objectMapper.readValue(
                        jsonContent,
                        MatchDecision.class
                );

        if (
                decision.confidence() < 0
                        || decision.confidence() > 1
        ) {
            throw new IllegalStateException(
                    "confidence 非法："
                            + decision.confidence()
            );
        }

        if (!decision.matched()) {
            return new MatchDecision(
                    false,
                    0,
                    decision.confidence()
            );
        }

        if (decision.selectedId() <= 0) {
            throw new IllegalStateException(
                    "matched=true 但 selectedId 非法"
            );
        }

        boolean candidateExists =
                candidates
                        .stream()
                        .anyMatch(
                                candidate ->
                                        candidate.id()
                                                == decision
                                                .selectedId()
                        );

        if (!candidateExists) {
            throw new IllegalStateException(
                    "模型返回候选集之外的 ID："
                            + decision.selectedId()
            );
        }

        return decision;
    }

    private Map<String, Object>
    translationResponseSchema(
            int expectedSize
    ) {
        Map<String, Object> translations =
                new LinkedHashMap<>();

        translations.put(
                "type",
                "array"
        );

        translations.put(
                "minItems",
                expectedSize
        );

        translations.put(
                "maxItems",
                expectedSize
        );

        translations.put(
                "items",
                Map.of(
                        "type",
                        "string"
                )
        );

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                Map.of(
                        "translations",
                        translations
                )
        );

        schema.put(
                "required",
                List.of(
                        "translations"
                )
        );

        schema.put(
                "additionalProperties",
                false
        );

        return schema;
    }

    private static String normalizeJsonContent(
            String content
    ) {
        if (
                content == null
                        || content.isBlank()
        ) {
            return "";
        }

        String result =
                content
                        .replace(
                                "\uFEFF",
                                ""
                        )
                        .trim();

        if (result.startsWith("```")) {
            int firstLineEnd =
                    result.indexOf('\n');

            int lastFence =
                    result.lastIndexOf(
                            "```"
                    );

            if (
                    firstLineEnd >= 0
                            && lastFence
                            > firstLineEnd
            ) {
                result =
                        result.substring(
                                firstLineEnd + 1,
                                lastFence
                        ).trim();
            }
        }

        int objectStart =
                result.indexOf('{');

        int objectEnd =
                result.lastIndexOf('}');

        if (
                objectStart >= 0
                        && objectEnd
                        > objectStart
        ) {
            result =
                    result.substring(
                            objectStart,
                            objectEnd + 1
                    );
        }

        return result.trim();
    }

    private String abbreviate(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String normalized =
                value.trim();

        if (
                normalized.length()
                        <= maxLogContentChars
        ) {
            return normalized;
        }

        return normalized.substring(
                0,
                maxLogContentChars
        ) + "...<truncated>";
    }

    private static void sleepQuietly(
            long millis
    ) {
        if (millis <= 0) {
            return;
        }

        try {
            Thread.sleep(
                    millis
            );

        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
        }
    }

    public record LyricsMatchTarget(
            String trackName,
            List<String> artistNames,
            String albumName,
            int durationSeconds
    ) {
        public LyricsMatchTarget {
            artistNames =
                    artistNames == null
                            ? List.of()
                            : List.copyOf(
                            artistNames
                    );
        }
    }

    public record LyricsMatchCandidate(
            long id,
            String trackName,
            String artistName,
            String albumName,
            Integer durationSeconds,
            double localScore
    ) {
    }

    public record MatchDecision(
            boolean matched,
            long selectedId,
            double confidence
    ) {
    }

    private record MatchInput(
            LyricsMatchTarget target,
            List<LyricsMatchCandidate> candidates
    ) {
    }

    private record TranslationLine(
            int index,
            String text
    ) {
    }

    private record ChatRequest(
            String model,
            List<ChatMessage> messages,
            boolean stream,
            boolean think,
            Map<String, Object> format,
            Map<String, Object> options
    ) {
    }

    private record ChatMessage(
            String role,
            String content
    ) {
    }

    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    private record ChatResponse(
            String model,
            ChatResponseMessage message,
            boolean done
    ) {
    }

    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    private record ChatResponseMessage(
            String role,
            String content,
            String thinking
    ) {
    }
}
