package com.avemonica.avemusic.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "avemusic.file")
public record FileStorageProperties(
        Path root,
        String publicBaseUrl,
        String internalToken
) {

    public FileStorageProperties {
        if (root == null) {
            throw new IllegalArgumentException(
                    "avemusic.file.root 不能为空"
            );
        }

        if (publicBaseUrl == null
                || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "avemusic.file.public-base-url 不能为空"
            );
        }

        if (internalToken == null
                || internalToken.isBlank()) {
            throw new IllegalArgumentException(
                    "avemusic.file.internal-token 不能为空"
            );
        }

        root = root.toAbsolutePath().normalize();
        publicBaseUrl = removeTrailingSlash(
                publicBaseUrl.trim()
        );
    }

    private static String removeTrailingSlash(
            String value
    ) {
        while (value.endsWith("/")) {
            value = value.substring(
                    0,
                    value.length() - 1
            );
        }

        return value;
    }
}
