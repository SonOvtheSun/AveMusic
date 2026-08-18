package com.avemonica.avemusic.music.provider.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public final class LrclibClient {

    private final RestClient restClient;
    private final int maxAttempts;
    private final long retryBackoffMillis;

    public LrclibClient(
            @Value(
                    "${avemusic.lyrics.lrclib-base-url:"
                            + "https://lrclib.net}"
            )
            String baseUrl,

            @Value(
                    "${avemusic.lyrics.lrclib."
                            + "connect-timeout-seconds:4}"
            )
            long connectTimeoutSeconds,

            @Value(
                    "${avemusic.lyrics.lrclib."
                            + "read-timeout-seconds:12}"
            )
            long readTimeoutSeconds,

            @Value(
                    "${avemusic.lyrics.lrclib."
                            + "max-attempts:2}"
            )
            int maxAttempts,

            @Value(
                    "${avemusic.lyrics.lrclib."
                            + "retry-backoff-millis:250}"
            )
            long retryBackoffMillis
    ) {
        this.maxAttempts =
                Math.max(
                        1,
                        Math.min(
                                maxAttempts,
                                4
                        )
                );

        this.retryBackoffMillis =
                Math.max(
                        0,
                        Math.min(
                                retryBackoffMillis,
                                3_000
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
                        .defaultHeader(
                                HttpHeaders.USER_AGENT,
                                "AveMusic/1.0"
                        )
                        .build();
    }

    public List<LrclibRecord> search(
            String trackName,
            String artistName
    ) {
        if (
                trackName == null
                        || trackName.isBlank()
        ) {
            return List.of();
        }

        String normalizedTrack =
                trackName.trim();

        String normalizedArtist =
                artistName == null
                        ? ""
                        : artistName.trim();

        Exception lastException = null;

        for (
                int attempt = 1;
                attempt <= maxAttempts;
                attempt++
        ) {
            try {
                LrclibRecord[] result =
                        restClient
                                .get()
                                .uri(
                                        uriBuilder -> {
                                            var builder =
                                                    uriBuilder
                                                            .path(
                                                                    "/api/search"
                                                            )
                                                            .queryParam(
                                                                    "track_name",
                                                                    normalizedTrack
                                                            );

                                            if (!normalizedArtist.isBlank()) {
                                                builder =
                                                        builder.queryParam(
                                                                "artist_name",
                                                                normalizedArtist
                                                        );
                                            }

                                            return builder.build();
                                        }
                                )
                                .retrieve()
                                .body(
                                        LrclibRecord[].class
                                );

                if (result == null) {
                    return List.of();
                }

                return List.of(
                        result
                );

            } catch (Exception exception) {
                lastException = exception;

                System.err.println(
                        "[Lyrics-LRCLIB] 搜索失败，"
                                + "attempt="
                                + attempt
                                + "/"
                                + maxAttempts
                                + ", song="
                                + normalizedTrack
                                + ", artist="
                                + normalizedArtist
                                + ", error="
                                + exception.getMessage()
                );

                if (
                        attempt < maxAttempts
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
                    "[Lyrics-LRCLIB] 搜索最终失败："
                            + lastException.getMessage()
            );
        }

        return List.of();
    }

    public Optional<LrclibRecord>
    getExact(
            String trackName,
            String artistName,
            String albumName,
            int durationSeconds
    ) {
        if (
                trackName == null
                        || trackName.isBlank()
                        || artistName == null
                        || artistName.isBlank()
                        || albumName == null
                        || albumName.isBlank()
                        || durationSeconds <= 0
        ) {
            return Optional.empty();
        }

        Exception lastException = null;

        for (
                int attempt = 1;
                attempt <= maxAttempts;
                attempt++
        ) {
            try {
                LrclibRecord result =
                        restClient
                                .get()
                                .uri(
                                        uriBuilder ->
                                                uriBuilder
                                                        .path(
                                                                "/api/get"
                                                        )
                                                        .queryParam(
                                                                "track_name",
                                                                trackName.trim()
                                                        )
                                                        .queryParam(
                                                                "artist_name",
                                                                artistName.trim()
                                                        )
                                                        .queryParam(
                                                                "album_name",
                                                                albumName.trim()
                                                        )
                                                        .queryParam(
                                                                "duration",
                                                                durationSeconds
                                                        )
                                                        .build()
                                )
                                .retrieve()
                                .body(
                                        LrclibRecord.class
                                );

                return Optional.ofNullable(
                        result
                );

            } catch (
                    HttpClientErrorException.NotFound exception
            ) {
                return Optional.empty();

            } catch (Exception exception) {
                lastException = exception;

                System.err.println(
                        "[Lyrics-LRCLIB] 精确查询失败，"
                                + "attempt="
                                + attempt
                                + "/"
                                + maxAttempts
                                + ", error="
                                + exception.getMessage()
                );

                if (
                        attempt < maxAttempts
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
                    "[Lyrics-LRCLIB] 精确查询最终失败："
                            + lastException.getMessage()
            );
        }

        return Optional.empty();
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

    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public record LrclibRecord(
            Long id,
            String trackName,
            String artistName,
            String albumName,
            Integer duration,
            boolean instrumental,
            String plainLyrics,
            String syncedLyrics
    ) {
    }
}
