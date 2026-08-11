package com.avemonica.avemusic.user.provider.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;

@ConfigurationProperties(
        prefix = "avemusic.identity.crypto"
)
public record IdentityCryptoProperties(
        String aesKeyBase64,
        String hmacKeyBase64,
        int keyVersion
) {

    public IdentityCryptoProperties {
        validateKey(
                aesKeyBase64,
                "AES"
        );

        validateKey(
                hmacKeyBase64,
                "HMAC"
        );

        if (keyVersion <= 0) {
            throw new IllegalArgumentException(
                    "key-version 必须大于0"
            );
        }
    }

    private static void validateKey(
            String value,
            String name
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " 密钥不能为空"
            );
        }

        byte[] key;

        try {
            key = Base64
                    .getDecoder()
                    .decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    name + " 密钥不是有效Base64",
                    exception
            );
        }

        if (key.length != 32) {
            throw new IllegalArgumentException(
                    name + " 密钥必须为32字节"
            );
        }
    }
}