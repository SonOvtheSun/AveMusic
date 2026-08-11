package com.avemonica.avemusic.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "avemusic.security")
public record AuthSecurityProperties(
        String issuer,
        String secretBase64,
        Duration accessTokenTtl,
        Duration idleTimeout,
        Duration absoluteTimeout,
        List<String> allowedOrigins
) {

    public AuthSecurityProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "avemusic.security.issuer must not be blank"
            );
        }

        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "avemusic.security.secret-base64 must not be blank"
            );
        }

        Objects.requireNonNull(
                accessTokenTtl,
                "access-token-ttl must not be null"
        );
        Objects.requireNonNull(
                idleTimeout,
                "idle-timeout must not be null"
        );
        Objects.requireNonNull(
                absoluteTimeout,
                "absolute-timeout must not be null"
        );

        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "access-token-ttl must be greater than 0"
            );
        }

        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "idle-timeout must be greater than 0"
            );
        }

        if (absoluteTimeout.compareTo(idleTimeout) < 0) {
            throw new IllegalArgumentException(
                    "absolute-timeout must not be less than idle-timeout"
            );
        }

        allowedOrigins = allowedOrigins == null
                ? List.of()
                : List.copyOf(allowedOrigins);
    }
}