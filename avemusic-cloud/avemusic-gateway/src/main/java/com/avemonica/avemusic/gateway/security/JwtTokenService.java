package com.avemonica.avemusic.gateway.security;

import com.avemonica.avemusic.common.security.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Component
public final class JwtTokenService {

    private static final String TOKEN_TYPE = "typ";
    private static final String SESSION_ID = "sid";
    private static final String ROLE_CLAIM = "role";

    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final AuthSecurityProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenService(
            AuthSecurityProperties properties
    ) {
        this.properties = properties;

        byte[] keyBytes;

        try {
            keyBytes = Base64
                    .getDecoder()
                    .decode(
                            properties.secretBase64()
                    );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT secret is not valid Base64",
                    exception
            );
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "HS256 JWT secret must contain at least 32 bytes"
            );
        }

        SecretKey secretKey =
                new SecretKeySpec(
                        keyBytes,
                        "HmacSHA256"
                );

        this.encoder =
                new NimbusJwtEncoder(
                        new ImmutableSecret<
                                SecurityContext
                                >(secretKey)
                );

        NimbusJwtDecoder jwtDecoder =
                NimbusJwtDecoder
                        .withSecretKey(secretKey)
                        .macAlgorithm(
                                MacAlgorithm.HS256
                        )
                        .build();

        jwtDecoder.setJwtValidator(
                JwtValidators
                        .createDefaultWithIssuer(
                                properties.issuer()
                        )
        );

        this.decoder = jwtDecoder;
    }

    /**
     * JWT 只保存角色标识。
     *
     * 完整 authorities 继续保存在 Redis Session 中，
     * 由 JwtAuthenticationFilter 读取。
     */
    public IssuedTokens issueTokens(
            String userId,
            String sessionId,
            UserRole role,
            Instant absoluteExpiresAt
    ) {
        requireText(userId, "userId");
        requireText(sessionId, "sessionId");
        Objects.requireNonNull(
                role,
                "role must not be null"
        );
        Objects.requireNonNull(
                absoluteExpiresAt,
                "absoluteExpiresAt must not be null"
        );

        Instant now = Instant.now();

        if (!absoluteExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "absoluteExpiresAt must be in the future"
            );
        }

        Instant accessExpiresAt =
                now.plus(
                        properties.accessTokenTtl()
                );

        if (accessExpiresAt.isAfter(
                absoluteExpiresAt
        )) {
            accessExpiresAt =
                    absoluteExpiresAt;
        }

        String accessJti =
                UUID.randomUUID().toString();

        String refreshJti =
                UUID.randomUUID().toString();

        String accessToken = encode(
                userId,
                sessionId,
                role,
                ACCESS,
                accessJti,
                now,
                accessExpiresAt
        );

        String refreshToken = encode(
                userId,
                sessionId,
                role,
                REFRESH,
                refreshJti,
                now,
                absoluteExpiresAt
        );

        return new IssuedTokens(
                accessToken,
                refreshToken,
                refreshJti,
                accessExpiresAt,
                absoluteExpiresAt
        );
    }

    public VerifiedToken parseAccessToken(
            String token
    ) {
        return parse(token, ACCESS);
    }

    public VerifiedToken parseRefreshToken(
            String token
    ) {
        return parse(token, REFRESH);
    }

    private String encode(
            String userId,
            String sessionId,
            UserRole role,
            String tokenType,
            String jti,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(
                                properties.issuer()
                        )
                        .subject(userId)
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .id(jti)
                        .claim(
                                SESSION_ID,
                                sessionId
                        )
                        .claim(
                                TOKEN_TYPE,
                                tokenType
                        )
                        .claim(
                                ROLE_CLAIM,
                                role.name()
                        )
                        .build();

        return encoder.encode(
                JwtEncoderParameters.from(
                        header,
                        claims
                )
        ).getTokenValue();
    }

    private VerifiedToken parse(
            String token,
            String expectedType
    ) {
        if (token == null
                || token.isBlank()) {
            throw new BadCredentialsException(
                    "令牌不能为空"
            );
        }

        final Jwt jwt;

        try {
            jwt = decoder.decode(token);
        } catch (JwtException exception) {
            throw new BadCredentialsException(
                    "令牌无效或已经过期",
                    exception
            );
        }

        String actualType =
                jwt.getClaimAsString(
                        TOKEN_TYPE
                );

        String sessionId =
                jwt.getClaimAsString(
                        SESSION_ID
                );

        String roleText =
                jwt.getClaimAsString(
                        ROLE_CLAIM
                );

        String subject =
                jwt.getSubject();

        String jti =
                jwt.getId();

        if (!expectedType.equals(
                actualType
        )) {
            throw new BadCredentialsException(
                    "令牌类型错误"
            );
        }

        if (subject == null
                || subject.isBlank()
                || sessionId == null
                || sessionId.isBlank()
                || jti == null
                || jti.isBlank()) {
            throw new BadCredentialsException(
                    "令牌缺少必要信息"
            );
        }

        final UserRole role;

        try {
            role = UserRole.valueOf(
                    roleText
            );
        } catch (
                IllegalArgumentException
                | NullPointerException exception
        ) {
            throw new BadCredentialsException(
                    "令牌中的角色信息无效",
                    exception
            );
        }

        return new VerifiedToken(
                subject,
                sessionId,
                jti,
                role,
                jwt.getExpiresAt()
        );
    }

    private static void requireText(
            String value,
            String name
    ) {
        if (value == null
                || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must not be blank"
            );
        }
    }

    public record IssuedTokens(
            String accessToken,
            String refreshToken,
            String refreshJti,
            Instant accessExpiresAt,
            Instant absoluteExpiresAt
    ) {
    }

    public record VerifiedToken(
            String subject,
            String sessionId,
            String jti,
            UserRole role,
            Instant expiresAt
    ) {
    }
}
