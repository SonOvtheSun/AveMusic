package com.avemonica.avemusic.music.provider.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

@Component
public final class OllamaClient {

    /*
     * Qwen 只允许返回下面三个字段。
     *
     * matched:
     *   是否认为候选中存在目标歌曲。
     *
     * selectedId:
     *   matched=true 时必须为候选中的 LRCLIB id；
     *   matched=false 时返回 0。
     *
     * confidence:
     *   0 ~ 1。
     */
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

    /*
     * Qwen 的职责非常有限：
     *
     * 只判断候选是不是目标歌曲，
     * 不允许生成歌词，
     * 不允许自己创造候选 ID。
     */
    private static final String SYSTEM_PROMPT = """
            你是音乐元数据匹配器。

            你的唯一任务是：
            根据目标歌曲元数据，从给定候选列表中判断
            哪一个候选最可能与目标歌曲是同一首歌曲。

            判断时重点考虑：
            1. 歌曲名称；
            2. 音乐人名称及译名；
            3. 专辑名称；
            4. 歌曲时长；
            5. 本地程序提供的 localScore。

            注意：
            - 不同语言、罗马音、中文译名可以表示同一音乐人。
            - 专辑可能存在普通版、限定版、单曲版等轻微名称差异。
            - Live、Remix、Instrumental、Cover 等不同版本不能轻易视为同一首。
            - 时长接近是重要证据。
            - 只能从 candidates 中选择。
            - 不允许编造候选。
            - 不允许生成或补全歌词。
            - 如果没有足够可信的候选，matched 必须为 false。
            - matched=false 时 selectedId 必须为 0。
            - matched=true 时 selectedId 必须是 candidates 中存在的 id。
            - confidence 必须在 0 到 1 之间。

            只按照指定 JSON Schema 返回结果。
            """;

    private static final String
            TRANSLATION_SYSTEM_PROMPT = """
        你是音乐歌词翻译器。

        请将输入歌词逐行翻译为自然、准确的简体中文。

        必须严格遵守：

        1. 每个输入 index 必须恰好返回一次。
        2. 不允许合并歌词行。
        3. 不允许拆分歌词行。
        4. 不允许改变 index。
        5. 不允许新增歌词内容。
        6. 不允许删除歌词内容。
        7. 不要输出解释、注释或翻译说明。
        8. 人名、乐队名等专有名词无法自然翻译时可以保留原文。
        9. 如果原句本身已经是自然的简体中文，
           text 返回空字符串。
        10. 只返回指定 JSON Schema。
        11. 每个 translations 元素必须且只能包含
            index 和 text 两个字段。
        12. 同一个 JSON 对象中禁止重复出现
            index 或 text 字段。
        """;

    private final RestClient restClient;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final String model;

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
                            + "read-timeout-seconds:180}"
            )
            long readTimeoutSeconds
    ) {
        this.model =
                model;

        /*
         * Ollama 第一次加载模型可能比较慢，
         * 所以读取超时不能设置得像普通 HTTP
         * 接口一样只有几秒。
         */
        HttpClient httpClient =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(
                                        connectTimeoutSeconds
                                )
                        )
                        .build();

        JdkClientHttpRequestFactory
                requestFactory =
                new JdkClientHttpRequestFactory(
                        httpClient
                );

        requestFactory.setReadTimeout(
                Duration.ofSeconds(
                        readTimeoutSeconds
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

        /*
         * 不一次把整首几百行歌词全部扔给模型。
         * 每次最多40行。
         */
        final int batchSize = 40;

        List<String> result =
                new ArrayList<>(
                        Collections.nCopies(
                                lines.size(),
                                ""
                        )
                );

        for (
                int start = 0;
                start < lines.size();
                start += batchSize
        ) {
            int end =
                    Math.min(
                            start + batchSize,
                            lines.size()
                    );

            List<TranslationLine> batch =
                    new ArrayList<>();

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

            Optional<TranslationResponse>
                    translated =
                    translateLyricsBatch(
                            batch
                    );

            if (translated.isEmpty()) {
                /*
                 * 某个批次失败：
                 * 整首翻译都不缓存，
                 * 防止出现半截翻译。
                 */
                return Optional.empty();
            }

            TranslationResponse response =
                    translated.get();

            if (
                    response.translations()
                            == null
            ) {
                return Optional.empty();
            }

            Set<Integer> received =
                    new HashSet<>();

            for (
                    TranslationItem item
                    : response.translations()
            ) {
                if (item == null) {
                    return Optional.empty();
                }

                int index =
                        item.index();

                /*
                 * 模型不允许返回当前批次外的行号。
                 */
                if (
                        index < start
                                || index >= end
                                || !received.add(index)
                ) {
                    return Optional.empty();
                }

                result.set(
                        index,
                        item.text() == null
                                ? ""
                                : item.text().trim()
                );
            }

            /*
             * 必须一行不少。
             */
            if (
                    received.size()
                            != end - start
            ) {
                return Optional.empty();
            }
        }

        return Optional.of(
                List.copyOf(result)
        );
    }

    private Optional<TranslationResponse>
    translateLyricsBatch(
            List<TranslationLine> batch
    ) {
        try {
            String input =
                    objectMapper
                            .writeValueAsString(
                                    Map.of(
                                            "lines",
                                            batch
                                    )
                            );

            /*
             * Ollama 官方也建议：
             * 除了 format 传 schema，
             * prompt 中也带上 schema，
             * 能进一步约束结构化输出。
             */
            String schemaJson =
                    objectMapper
                            .writeValueAsString(
                                    TRANSLATION_RESPONSE_SCHEMA
                            );

            String userPrompt = """
                请逐行翻译下面的歌词为简体中文。

                必须严格按照给定 JSON Schema 返回。

                不要输出 Markdown。
                不要输出 ```json。
                不要输出代码块。
                不要输出任何解释。
                返回内容的第一个字符必须是 {
                最后一个字符必须是 }

                JSON Schema：
                %s

                输入：
                %s
                """.formatted(
                    schemaJson,
                    input
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

                            /*
                             * 不需要 thinking。
                             */
                            false,

                            TRANSLATION_RESPONSE_SCHEMA,

                            Map.of(
                                    "temperature",
                                    0
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
                System.err.println(
                        "[Lyrics-AI] Ollama返回空响应"
                );

                return Optional.empty();
            }

            String rawContent =
                    response.message()
                            .content();

            if (
                    rawContent == null
                            || rawContent.isBlank()
            ) {
                System.err.println(
                        "[Lyrics-AI] Ollama翻译content为空"
                );

                return Optional.empty();
            }

            System.out.println(
                    "[Lyrics-AI] 翻译原始输出："
                            + rawContent
            );

            String jsonContent =
                    normalizeJsonContent(
                            rawContent
                    );

            if (jsonContent.isBlank()) {
                return Optional.empty();
            }

            TranslationResponse result =
                    objectMapper.readValue(
                            jsonContent,
                            TranslationResponse.class
                    );

            return Optional.ofNullable(
                    result
            );

        } catch (Exception exception) {
            System.err.println(
                    "[Lyrics-AI] 歌词翻译失败："
                            + exception
                            .getMessage()
            );

            exception.printStackTrace();

            return Optional.empty();
        }
    }

    /**
     * 让 Qwen 从经过 Java 预筛选的候选中
     * 选择最可能的一条。
     *
     * AI 故障不会直接导致歌词接口失败：
     * 返回 Optional.empty()，
     * 由 LyricsServiceImpl 按“未匹配”处理。
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

        try {
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
                    下面是目标歌曲和候选歌曲的 JSON 数据。

                    请判断 candidates 中是否存在与 target
                    相同的歌曲。

                    输入数据：
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
                                            SYSTEM_PROMPT
                                    ),

                                    new ChatMessage(
                                            "user",
                                            userPrompt
                                    )
                            ),

                            false,

                            /*
                             * 这里只需要最终 JSON，
                             * 不需要 reasoning/thinking。
                             */
                            false,

                            MATCH_RESPONSE_SCHEMA,

                            Map.of(
                                    "temperature",
                                    0
                            )
                    );

            System.out.println(
                    "[Lyrics-AI] 开始调用 Ollama，"
                            + "model="
                            + model
                            + ", candidates="
                            + candidates.size()
            );

            ChatResponse response =
                    restClient
                            .post()
                            .uri("/api/chat")
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
                            || response.message() == null
            ) {
                System.err.println(
                        "[Lyrics-AI] Ollama返回空响应"
                );

                return Optional.empty();
            }

            String content =
                    response.message()
                            .content();

            if (
                    content == null
                            || content.isBlank()
            ) {
                System.err.println(
                        "[Lyrics-AI] Ollama content为空"
                );

                return Optional.empty();
            }

            System.out.println(
                    "[Lyrics-AI] Ollama结果："
                            + content
            );

            String jsonContent =
                    normalizeJsonContent(
                            content
                    );

            if (jsonContent.isBlank()) {
                return Optional.empty();
            }

            MatchDecision decision =
                    objectMapper.readValue(
                            jsonContent,
                            MatchDecision.class
                    );

            /*
             * 二次校验模型输出。
             * 即使模型违反提示词，也不能让非法 ID
             * 进入后续业务流程。
             */
            if (
                    decision.confidence() < 0
                            || decision.confidence() > 1
            ) {
                System.err.println(
                        "[Lyrics-AI] confidence非法："
                                + decision.confidence()
                );

                return Optional.empty();
            }

            if (!decision.matched()) {
                return Optional.of(
                        new MatchDecision(
                                false,
                                0,
                                decision.confidence()
                        )
                );
            }

            if (decision.selectedId() <= 0) {
                System.err.println(
                        "[Lyrics-AI] matched=true，"
                                + "但selectedId非法"
                );

                return Optional.empty();
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
                System.err.println(
                        "[Lyrics-AI] 模型返回了候选集之外的ID："
                                + decision.selectedId()
                );

                return Optional.empty();
            }

            return Optional.of(
                    decision
            );

        } catch (Exception exception) {
            /*
             * AI 在整个歌词服务里属于增强能力，
             * 不能因为 Ollama 没启动就把歌曲播放页
             * 直接打成 500。
             */
            System.err.println(
                    "[Lyrics-AI] Ollama调用失败："
                            + exception.getClass()
                            .getSimpleName()
                            + ": "
                            + exception.getMessage()
            );

            exception.printStackTrace();

            return Optional.empty();
        }
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
                content.trim();

        /*
         * 兼容模型偶尔返回：
         *
         * ```json
         * {...}
         * ```
         *
         * 或：
         *
         * ```
         * {...}
         * ```
         */
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

        /*
         * 最后一层容错。
         *
         * 即便模型写成：
         *
         * 这是结果：
         * {"translations":[...]}
         *
         * 也只截取 JSON object。
         */
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



    private static final Map<String, Object>
            TRANSLATION_RESPONSE_SCHEMA =
            Map.of(
                    "type",
                    "object",

                    "properties",
                    Map.of(
                            "translations",
                            Map.of(
                                    "type",
                                    "array",

                                    "items",
                                    Map.of(
                                            "type",
                                            "object",

                                            "properties",
                                            Map.of(
                                                    "index",
                                                    Map.of(
                                                            "type",
                                                            "integer"
                                                    ),

                                                    "text",
                                                    Map.of(
                                                            "type",
                                                            "string"
                                                    )
                                            ),

                                            "required",
                                            List.of(
                                                    "index",
                                                    "text"
                                            ),

                                            "additionalProperties",
                                            false
                                    )
                            )
                    ),

                    "required",
                    List.of(
                            "translations"
                    ),

                    "additionalProperties",
                    false
            );

    /*
     * =========================================================
     * 给 LyricsServiceImpl 使用的业务输入结构
     * =========================================================
     */

    public record LyricsMatchTarget(
            String trackName,
            List<String> artistNames,
            String albumName,
            int durationSeconds
    ) {
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

    /*
     * =========================================================
     * Ollama HTTP DTO
     * =========================================================
     */

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

    private record TranslationLine(
            int index,
            String text
    ) {
    }

    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    private static final class TranslationItem {

        private int index;

        private String text;


        /*
         * Jackson 反序列化需要无参构造器。
         */
        public TranslationItem() {
        }


        /*
         * 继续保留 index()，
         * 这样现有业务代码不用改。
         */
        public int index() {
            return index;
        }


        public String text() {
            return text;
        }


        public void setIndex(
                int index
        ) {
            this.index =
                    index;
        }


        public void setText(
                String text
        ) {
            this.text =
                    text;
        }
    }

    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    private static final class TranslationResponse {

        private List<TranslationItem>
                translations;


        public TranslationResponse() {
        }


        /*
         * 同样保留 translations()，
         * 所以后面的：
         *
         * response.translations()
         *
         * 不需要修改。
         */
        public List<TranslationItem>
        translations() {
            return translations;
        }


        public void setTranslations(
                List<TranslationItem>
                        translations
        ) {
            this.translations =
                    translations;
        }
    }
}