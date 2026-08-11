package com.avemonica.avemusic.gateway.service;

import com.avemonica.avemusic.music.api.service.MusicService;
import com.avemonica.minirpc.spring.annotation.MiniRpcReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端播放会话。
 *
 * 设计目标：
 * 1. 不相信前端 currentTime；
 * 2. 不相信前端直接上报“我播放了多少秒”；
 * 3. 有效时间只由 Redis 中相邻 heartbeat 的服务端时间差累计；
 * 4. heartbeat 间隔过长时不累计，避免暂停/断网期间被算进播放时间；
 * 5. 同一个 playSession 最多只增加一次播放量；
 * 6. 同一浏览器 + IP + 歌曲同时只保留一个活动 playSession。
 */
@Service
public class PlaySessionService {

    private static final String SESSION_PREFIX =
            "avemusic:play:session:";

    private static final String ACTIVE_PREFIX =
            "avemusic:play:active:";

    private static final String HEARTBEAT_LOCK_PREFIX =
            "avemusic:play:heartbeat-lock:";

    private static final String COUNT_LOCK_PREFIX =
            "avemusic:play:count-lock:";

    /**
     * 前端建议每 5 秒发送一次。
     */
    private static final int HEARTBEAT_INTERVAL_SECONDS =
            5;

    /**
     * 小于 1 秒的 heartbeat 不累计：
     * 防止高频并发请求刷时间。
     */
    private static final long MIN_VALID_GAP_MILLIS =
            1_000L;

    /**
     * 大于 8 秒的 heartbeat 不累计：
     *
     * 正常 heartbeat 是 5 秒。
     * 如果暂停/缓冲/切后台几十秒后再次恢复，
     * 第一次 heartbeat 只重置基线，不把这段空档算进去。
     */
    private static final long MAX_VALID_GAP_MILLIS =
            8_000L;

    private static final int PLAY_RATIO_PERCENT =
            20;

    @MiniRpcReference(
            host = "127.0.0.1",
            port = 20882,
            group = "music",
            version = "1.0.0"
    )
    private MusicService musicService;

    private final StringRedisTemplate redisTemplate;

    public PlaySessionService(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public StartResult start(
            String songId,
            String playbackClientId,
            String remoteAddress,
            String userAgent
    ) {
        String resolvedSongId =
                requiredText(
                        songId,
                        "歌曲ID不能为空"
                );

        String clientKey =
                resolveClientKey(
                        playbackClientId,
                        remoteAddress,
                        userAgent
                );

        /*
         * duration 来自 music-provider / 数据库，
         * 不接受客户端传 duration。
         */
        int durationSeconds =
                musicService
                        .getPlayableSongDuration(
                                resolvedSongId
                        );

        long durationMillis =
                durationSeconds * 1_000L;

        Duration ttl =
                resolveSessionTtl(
                        durationSeconds
                );

        String activeKey =
                activeKey(
                        clientKey,
                        resolvedSongId
                );

        /*
         * 同一客户端同一首歌同时只允许一个活动 session。
         * 新的播放会话会使旧 session 失效。
         */
        String previousSessionId =
                redisTemplate
                        .opsForValue()
                        .get(activeKey);

        if (
            previousSessionId != null
            && !previousSessionId.isBlank()
        ) {
            redisTemplate.delete(
                    sessionKey(
                            previousSessionId
                    )
            );
        }

        String sessionId =
                UUID.randomUUID()
                        .toString();

        long now =
                System.currentTimeMillis();

        Map<String, String> values =
                new HashMap<>();

        values.put(
                "songId",
                resolvedSongId
        );
        values.put(
                "clientKey",
                clientKey
        );
        values.put(
                "durationMillis",
                String.valueOf(
                        durationMillis
                )
        );
        values.put(
                "playedMillis",
                "0"
        );
        values.put(
                "lastHeartbeatAt",
                String.valueOf(now)
        );
        values.put(
                "counted",
                "0"
        );
        values.put(
                "playCount",
                ""
        );

        String sessionKey =
                sessionKey(sessionId);

        redisTemplate
                .opsForHash()
                .putAll(
                        sessionKey,
                        values
                );

        redisTemplate.expire(
                sessionKey,
                ttl
        );

        redisTemplate
                .opsForValue()
                .set(
                        activeKey,
                        sessionId,
                        ttl
                );

        return new StartResult(
                sessionId,
                HEARTBEAT_INTERVAL_SECONDS
        );
    }

    public HeartbeatResult heartbeat(
            String sessionId,
            String playbackClientId,
            String remoteAddress,
            String userAgent
    ) {
        String resolvedSessionId =
                requiredText(
                        sessionId,
                        "播放会话ID不能为空"
                );

        String clientKey =
                resolveClientKey(
                        playbackClientId,
                        remoteAddress,
                        userAgent
                );

        String key =
                sessionKey(
                        resolvedSessionId
                );

        Map<Object, Object> state =
                redisTemplate
                        .opsForHash()
                        .entries(key);

        if (state.isEmpty()) {
            throw new IllegalArgumentException(
                    "播放会话不存在或已经过期"
            );
        }

        String storedClientKey =
                value(
                        state,
                        "clientKey"
                );

        if (
            !clientKey.equals(
                    storedClientKey
            )
        ) {
            throw new SecurityException(
                    "播放会话与当前客户端不匹配"
            );
        }

        String songId =
                value(
                        state,
                        "songId"
                );

        String activeKey =
                activeKey(
                        clientKey,
                        songId
                );

        String activeSessionId =
                redisTemplate
                        .opsForValue()
                        .get(activeKey);

        if (
            !resolvedSessionId.equals(
                    activeSessionId
            )
        ) {
            redisTemplate.delete(key);

            throw new IllegalArgumentException(
                    "播放会话已经失效"
            );
        }

        /*
         * 分布式 heartbeat 锁：
         * 即使用户并发发送很多 heartbeat，
         * 同一 session 也只允许一个请求进入累计逻辑。
         *
         * 锁故意不手动删除，1 秒后自动过期，
         * 同时起到基础限频作用。
         */
        Boolean heartbeatLocked =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(
                                HEARTBEAT_LOCK_PREFIX
                                        + resolvedSessionId,
                                "1",
                                Duration.ofSeconds(1)
                        );

        if (
            !Boolean.TRUE.equals(
                    heartbeatLocked
            )
        ) {
            return currentResult(
                    key
            );
        }

        /*
         * 获得锁后重新读取，避免使用锁前的旧状态。
         */
        state =
                redisTemplate
                        .opsForHash()
                        .entries(key);

        if (state.isEmpty()) {
            throw new IllegalArgumentException(
                    "播放会话不存在或已经过期"
            );
        }

        boolean counted =
                "1".equals(
                        value(
                                state,
                                "counted"
                        )
                );

        if (counted) {
            return currentResult(key);
        }

        long durationMillis =
                longValue(
                        state,
                        "durationMillis"
                );

        long playedMillis =
                longValue(
                        state,
                        "playedMillis"
                );

        long lastHeartbeatAt =
                longValue(
                        state,
                        "lastHeartbeatAt"
                );

        long now =
                System.currentTimeMillis();

        long gapMillis =
                Math.max(
                        0L,
                        now - lastHeartbeatAt
                );

        long validMillis = 0L;

        /*
         * 关键防作弊逻辑：
         *
         * 这里只看“服务器两次 heartbeat 之间真实过了多久”。
         * 完全不读取前端 currentTime。
         *
         * 因此：
         * 0秒 -> 直接拖到80%
         * 不会瞬间增加任何 playedMillis。
         */
        if (
            gapMillis
                    >= MIN_VALID_GAP_MILLIS
            && gapMillis
                    <= MAX_VALID_GAP_MILLIS
        ) {
            validMillis =
                    gapMillis;
        }

        long nextPlayedMillis =
                playedMillis
                        + validMillis;

        redisTemplate
                .opsForHash()
                .put(
                        key,
                        "lastHeartbeatAt",
                        String.valueOf(now)
                );

        redisTemplate
                .opsForHash()
                .put(
                        key,
                        "playedMillis",
                        String.valueOf(
                                nextPlayedMillis
                        )
                );

        Duration ttl =
                resolveSessionTtl(
                        Math.max(
                                1,
                                (int) (
                                    durationMillis
                                    / 1_000L
                                )
                        )
                );

        redisTemplate.expire(
                key,
                ttl
        );

        redisTemplate.expire(
                activeKey,
                ttl
        );

        long thresholdMillis =
                durationMillis
                        * PLAY_RATIO_PERCENT
                        / 100L;

        /*
         * 用户要求“超过 40%”，所以这里用 >，
         * 不是 >=。
         */
        if (
            nextPlayedMillis
                    <= thresholdMillis
        ) {
            return new HeartbeatResult(
                    false,
                    null
            );
        }

        return persistPlayCount(
                resolvedSessionId,
                key,
                songId,
                ttl
        );
    }

    public void finish(
            String sessionId,
            String playbackClientId,
            String remoteAddress,
            String userAgent
    ) {
        String resolvedSessionId =
                requiredText(
                        sessionId,
                        "播放会话ID不能为空"
                );

        String key =
                sessionKey(
                        resolvedSessionId
                );

        Map<Object, Object> state =
                redisTemplate
                        .opsForHash()
                        .entries(key);

        if (state.isEmpty()) {
            return;
        }

        String clientKey =
                resolveClientKey(
                        playbackClientId,
                        remoteAddress,
                        userAgent
                );

        if (
            !clientKey.equals(
                    value(
                            state,
                            "clientKey"
                    )
            )
        ) {
            throw new SecurityException(
                    "播放会话与当前客户端不匹配"
            );
        }

        String songId =
                value(
                        state,
                        "songId"
                );

        String activeKey =
                activeKey(
                        clientKey,
                        songId
                );

        String activeSessionId =
                redisTemplate
                        .opsForValue()
                        .get(activeKey);

        redisTemplate.delete(key);

        if (
            resolvedSessionId.equals(
                    activeSessionId
            )
        ) {
            redisTemplate.delete(
                    activeKey
            );
        }
    }

    private HeartbeatResult persistPlayCount(
            String sessionId,
            String sessionKey,
            String songId,
            Duration ttl
    ) {
        String countLockKey =
                COUNT_LOCK_PREFIX
                        + sessionId;

        /*
         * “一次 playSession 最多记一次”。
         *
         * count lock 在整个 session TTL 内保留。
         * 只有 RPC 明确失败时才删除，允许后续 heartbeat 重试。
         */
        Boolean locked =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(
                                countLockKey,
                                "1",
                                ttl
                        );

        if (!Boolean.TRUE.equals(locked)) {
            return currentResult(
                    sessionKey
            );
        }

        try {
            long playCount =
                    musicService
                            .incrementPlayCount(
                                    songId
                            );

            redisTemplate
                    .opsForHash()
                    .put(
                            sessionKey,
                            "counted",
                            "1"
                    );

            redisTemplate
                    .opsForHash()
                    .put(
                            sessionKey,
                            "playCount",
                            String.valueOf(
                                    playCount
                            )
                    );

            return new HeartbeatResult(
                    true,
                    playCount
            );
        } catch (RuntimeException exception) {
            /*
             * RPC/数据库明确失败时释放锁，
             * 下一次 heartbeat 可以重试。
             */
            redisTemplate.delete(
                    countLockKey
            );

            throw exception;
        }
    }

    private HeartbeatResult currentResult(
            String sessionKey
    ) {
        Map<Object, Object> state =
                redisTemplate
                        .opsForHash()
                        .entries(sessionKey);

        if (state.isEmpty()) {
            throw new IllegalArgumentException(
                    "播放会话不存在或已经过期"
            );
        }

        boolean counted =
                "1".equals(
                        value(
                                state,
                                "counted"
                        )
                );

        String playCountValue =
                value(
                        state,
                        "playCount"
                );

        Long playCount =
                playCountValue == null
                || playCountValue.isBlank()
                        ? null
                        : Long.parseLong(
                                playCountValue
                        );

        return new HeartbeatResult(
                counted,
                playCount
        );
    }

    private static Duration resolveSessionTtl(
            int durationSeconds
    ) {
        /*
         * 至少 30 分钟；
         * 长音频则保留大约 2 倍时长 + 10 分钟。
         */
        long ttlSeconds =
                Math.max(
                        30L * 60L,
                        durationSeconds
                                * 2L
                                + 10L * 60L
                );

        return Duration.ofSeconds(
                ttlSeconds
        );
    }

    private static String sessionKey(
            String sessionId
    ) {
        return SESSION_PREFIX
                + sessionId;
    }

    private static String activeKey(
            String clientKey,
            String songId
    ) {
        return ACTIVE_PREFIX
                + clientKey
                + ":"
                + songId;
    }

    private static String resolveClientKey(
            String playbackClientId,
            String remoteAddress,
            String userAgent
    ) {
        String clientId =
                requiredText(
                        playbackClientId,
                        "缺少 X-Playback-Client"
                );

        if (clientId.length() > 128) {
            throw new IllegalArgumentException(
                    "播放客户端标识过长"
            );
        }

        String raw =
                clientId
                + "|"
                + nullable(
                        remoteAddress
                )
                + "|"
                + nullable(
                        userAgent
                );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    raw.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );
        } catch (
            NoSuchAlgorithmException exception
        ) {
            throw new IllegalStateException(
                    "SHA-256 不可用",
                    exception
            );
        }
    }

    private static long longValue(
            Map<Object, Object> state,
            String field
    ) {
        String value =
                value(
                        state,
                        field
                );

        if (
            value == null
            || value.isBlank()
        ) {
            return 0L;
        }

        return Long.parseLong(
                value
        );
    }

    private static String value(
            Map<Object, Object> state,
            String field
    ) {
        Object value =
                state.get(field);

        return value == null
                ? null
                : value.toString();
    }

    private static String requiredText(
            String value,
            String message
    ) {
        if (
            value == null
            || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value.trim();
    }

    private static String nullable(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    public record StartResult(
            String sessionId,
            int heartbeatIntervalSeconds
    ) {
    }

    public record HeartbeatResult(
            boolean counted,
            Long playCount
    ) {
    }
}
