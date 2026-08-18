package com.avemonica.avemusic.gateway.security;

import com.avemonica.avemusic.common.security.UserRole;
import com.avemonica.avemusic.user.api.dto.AuthModels;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public final class RedisSessionStore {

    private static final String KEY_PREFIX =
            "auth:session:";

    private static final String USER_ID =
            "userId";

    private static final String USERNAME =
            "username";

    private static final String ROLE =
            "role";

    private static final String AUTHORITIES =
            "authorities";

    private static final String REFRESH_JTI =
            "refreshJti";

    private static final String ABSOLUTE_EXPIRES_AT =
            "absoluteExpiresAt";

    private static final String
            USER_SESSION_PREFIX =
            "auth:user-sessions:";

    private static final DefaultRedisScript<Long>
            ROTATE_REFRESH_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local current = redis.call(
                        'HGET',
                        KEYS[1],
                        'refreshJti'
                    )

                    if (not current)
                        or current ~= ARGV[1] then
                        return 0
                    end

                    redis.call(
                        'HSET',
                        KEYS[1],
                        'refreshJti',
                        ARGV[2]
                    )

                    redis.call(
                        'PEXPIRE',
                        KEYS[1],
                        ARGV[3]
                    )

                    return 1
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final AuthSecurityProperties properties;

    public RedisSessionStore(
            StringRedisTemplate redisTemplate,
            AuthSecurityProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void create(
            String sessionId,
            AuthModels.AuthUser user,
            String refreshJti,
            Instant absoluteExpiresAt
    ) {
        String key = key(sessionId);

        Map<String, String> values =
                new HashMap<>();

        values.put(
                USER_ID,
                user.userId()
        );

        values.put(
                USERNAME,
                user.username()
        );

        values.put(
                ROLE,
                user.role().name()
        );

        values.put(
                AUTHORITIES,
                String.join(
                        ",",
                        user.authorities()
                )
        );

        values.put(
                REFRESH_JTI,
                refreshJti
        );

        values.put(
                ABSOLUTE_EXPIRES_AT,
                String.valueOf(
                        absoluteExpiresAt
                                .toEpochMilli()
                )
        );

        redisTemplate
                .opsForHash()
                .putAll(key, values);

        Duration ttl = calculateTtl(
                absoluteExpiresAt
        );

        if (ttl.isZero()
                || ttl.isNegative()) {
            redisTemplate.delete(key);

            throw new IllegalStateException(
                    "Cannot create an already expired session"
            );
        }

        String userSessionKey =
                userSessionKey(
                        user.userId()
                );

        redisTemplate
                .opsForSet()
                .add(
                        userSessionKey,
                        sessionId
                );

        Duration absoluteTtl =
                Duration.between(
                        Instant.now(),
                        absoluteExpiresAt
                );

        if (
                !absoluteTtl.isNegative()
                        && !absoluteTtl.isZero()
        ) {
            redisTemplate.expire(
                    userSessionKey,
                    absoluteTtl
            );
        }

        redisTemplate.expire(key, ttl);
    }

    public void deleteAllByUserId(
            String userId
    ) {
        String indexKey =
                userSessionKey(
                        userId
                );

        Set<String> sessionIds =
                redisTemplate
                        .opsForSet()
                        .members(
                                indexKey
                        );

        if (
                sessionIds != null
                        && !sessionIds
                        .isEmpty()
        ) {
            List<String> sessionKeys =
                    sessionIds
                            .stream()
                            .map(
                                    RedisSessionStore
                                            ::key
                            )
                            .toList();

            redisTemplate.delete(
                    sessionKeys
            );
        }

        redisTemplate.delete(
                indexKey
        );
    }

    private static String
    userSessionKey(
            String userId
    ) {
        return USER_SESSION_PREFIX
                + userId;
    }

    public Optional<SessionData> findAndTouch(
            String sessionId
    ) {
        Optional<SessionData> session =
                find(sessionId);

        session.ifPresent(data -> {
            Duration ttl = calculateTtl(
                    data.absoluteExpiresAt()
            );

            if (ttl.isZero()
                    || ttl.isNegative()) {
                delete(sessionId);
            } else {
                redisTemplate.expire(
                        key(sessionId),
                        ttl
                );
            }
        });

        return session;
    }

    public Optional<SessionData> find(
            String sessionId
    ) {
        String key = key(sessionId);

        Map<Object, Object> values =
                redisTemplate
                        .opsForHash()
                        .entries(key);

        if (values == null
                || values.isEmpty()) {
            return Optional.empty();
        }

        Instant absoluteExpiresAt =
                Instant.ofEpochMilli(
                        Long.parseLong(
                                requiredValue(
                                        values,
                                        ABSOLUTE_EXPIRES_AT
                                )
                        )
                );

        if (!absoluteExpiresAt.isAfter(
                Instant.now()
        )) {
            delete(sessionId);
            return Optional.empty();
        }

        UserRole role = parseRole(
                values,
                sessionId
        );

        if (role == null) {
            /*
             * 兼容升级前不包含role的旧Session：
             * 删除并要求用户重新登录。
             */
            return Optional.empty();
        }

        String authorityText =
                value(values, AUTHORITIES);

        List<String> authorities =
                authorityText == null
                        || authorityText.isBlank()
                        ? List.of()
                        : Arrays.stream(
                                authorityText
                                        .split(",")
                        )
                        .map(String::trim)
                        .filter(text ->
                                !text.isBlank()
                        )
                        .collect(
                                Collectors
                                        .toUnmodifiableList()
                        );

        return Optional.of(
                new SessionData(
                        requiredValue(
                                values,
                                USER_ID
                        ),
                        requiredValue(
                                values,
                                USERNAME
                        ),
                        role,
                        authorities,
                        absoluteExpiresAt
                )
        );
    }

    public boolean rotateRefreshJti(
            String sessionId,
            String expectedJti,
            String replacementJti,
            Instant absoluteExpiresAt
    ) {
        Duration ttl = calculateTtl(
                absoluteExpiresAt
        );

        if (ttl.isZero()
                || ttl.isNegative()) {
            delete(sessionId);
            return false;
        }

        Long result = redisTemplate.execute(
                ROTATE_REFRESH_SCRIPT,
                List.of(key(sessionId)),
                expectedJti,
                replacementJti,
                String.valueOf(ttl.toMillis())
        );

        return Long.valueOf(1L)
                .equals(result);
    }

    public void delete(
            String sessionId
    ) {
        String sessionKey =
                key(sessionId);

        Object userIdValue =
                redisTemplate
                        .opsForHash()
                        .get(
                                sessionKey,
                                USER_ID
                        );

        redisTemplate.delete(sessionKey);

        if (userIdValue == null) {
            return;
        }

        String indexKey =
                userSessionKey(
                        userIdValue.toString()
                );

        redisTemplate
                .opsForSet()
                .remove(
                        indexKey,
                        sessionId
                );

        Long remaining =
                redisTemplate
                        .opsForSet()
                        .size(indexKey);

        if (remaining == null
                || remaining == 0L) {
            redisTemplate.delete(indexKey);
        }
    }

    private UserRole parseRole(
            Map<Object, Object> values,
            String sessionId
    ) {
        String roleText =
                value(values, ROLE);

        if (roleText == null
                || roleText.isBlank()) {
            delete(sessionId);
            return null;
        }

        try {
            return UserRole.valueOf(
                    roleText
            );
        } catch (IllegalArgumentException exception) {
            delete(sessionId);
            return null;
        }
    }

    private Duration calculateTtl(
            Instant absoluteExpiresAt
    ) {
        Duration remaining =
                Duration.between(
                        Instant.now(),
                        absoluteExpiresAt
                );

        if (remaining.isZero()
                || remaining.isNegative()) {
            return Duration.ZERO;
        }

        return remaining.compareTo(
                properties.idleTimeout()
        ) < 0
                ? remaining
                : properties.idleTimeout();
    }

    private static String key(
            String sessionId
    ) {
        return KEY_PREFIX + sessionId;
    }

    private static String requiredValue(
            Map<Object, Object> values,
            String name
    ) {
        String value = value(
                values,
                name
        );

        if (value == null
                || value.isBlank()) {
            throw new IllegalStateException(
                    "Redis session field is missing: "
                            + name
            );
        }

        return value;
    }

    private static String value(
            Map<Object, Object> values,
            String name
    ) {
        Object value = values.get(name);

        return value == null
                ? null
                : value.toString();
    }

    public record SessionData(
            String userId,
            String username,
            UserRole role,
            List<String> authorities,
            Instant absoluteExpiresAt
    ) {
    }
}