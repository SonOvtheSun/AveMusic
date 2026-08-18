package com.avemonica.avemusic.music.provider.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public final class NeteaseCloudMusicClient {

    private final RestClient restClient;
    private final boolean enabled;
    private final int searchLimit;
    private final int maxAttempts;
    private final long retryBackoffMillis;

    public NeteaseCloudMusicClient(
            @Value(
                    "${avemusic.lyrics.netease.enabled:true}"
            )
            boolean enabled,

            @Value(
                    "${avemusic.lyrics.netease.base-url:"
                            + "http://127.0.0.1:3000}"
            )
            String baseUrl,

            @Value(
                    "${avemusic.lyrics.netease.search-limit:20}"
            )
            int searchLimit,

            @Value(
                    "${avemusic.lyrics.netease."
                            + "connect-timeout-seconds:3}"
            )
            long connectTimeoutSeconds,

            @Value(
                    "${avemusic.lyrics.netease."
                            + "read-timeout-seconds:12}"
            )
            long readTimeoutSeconds,

            @Value(
                    "${avemusic.lyrics.netease."
                            + "max-attempts:2}"
            )
            int maxAttempts,

            @Value(
                    "${avemusic.lyrics.netease."
                            + "retry-backoff-millis:200}"
            )
            long retryBackoffMillis
    ) {
        this.enabled = enabled;

        this.searchLimit =
                Math.max(
                        1,
                        Math.min(
                                searchLimit,
                                30
                        )
                );

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
                        .baseUrl(
                                baseUrl
                        )
                        .requestFactory(
                                requestFactory
                        )
                        .build();
    }

    /**
     * 搜索网易云歌曲。
     *
     * artistName 允许为空；当“歌名 + 音乐人”搜索不到时，
     * 上层可以再使用纯歌名做一次宽松召回，再由 Java 评分过滤。
     */
    public List<NeteaseSong> search(
            String trackName,
            String artistName
    ) {
        if (
                !enabled
                        || trackName == null
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
                return searchOnce(
                        normalizedTrack,
                        normalizedArtist
                );

            } catch (Exception exception) {
                lastException = exception;

                System.err.println(
                        "[Lyrics-Netease] 搜索失败，"
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
                    "[Lyrics-Netease] 搜索最终失败："
                            + lastException.getMessage()
            );
        }

        return List.of();
    }

    private List<NeteaseSong> searchOnce(
            String trackName,
            String artistName
    ) {
        String keywords =
                artistName.isBlank()
                        ? trackName
                        : (trackName
                           + " "
                           + artistName);

        JsonNode root =
                restClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path(
                                                        "/cloudsearch"
                                                )
                                                .queryParam(
                                                        "keywords",
                                                        keywords
                                                )
                                                .queryParam(
                                                        "type",
                                                        1
                                                )
                                                .queryParam(
                                                        "limit",
                                                        searchLimit
                                                )
                                                .build()
                        )
                        .retrieve()
                        .body(
                                JsonNode.class
                        );

        if (root == null) {
            return List.of();
        }

        JsonNode songs =
                root.path(
                        "result"
                ).path(
                        "songs"
                );

        if (!songs.isArray()) {
            return List.of();
        }

        List<NeteaseSong> result =
                new ArrayList<>();

        for (JsonNode song : songs) {
            long id =
                    song.path("id")
                            .asLong(0);

            String name =
                    song.path("name")
                            .asText("")
                            .trim();

            String albumName =
                    song.path("al")
                            .path("name")
                            .asText("")
                            .trim();

            long durationMillis =
                    song.path("dt")
                            .asLong(0);

            int durationSeconds =
                    durationMillis <= 0
                            ? 0
                            : (int) Math.round(
                            durationMillis
                            / 1000.0
                    );

            String artist =
                    resolveArtistNames(
                            song.path("ar")
                    );

            if (
                    id <= 0
                            || name.isBlank()
            ) {
                continue;
            }

            result.add(
                    new NeteaseSong(
                            id,
                            name,
                            artist,
                            albumName,
                            durationSeconds
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    /**
     * 根据网易云 songId 获取同步歌词。
     */
    public Optional<NeteaseLyrics>
    getLyrics(
            long songId
    ) {
        if (
                !enabled
                        || songId <= 0
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
                return getLyricsOnce(
                        songId
                );

            } catch (
                    HttpClientErrorException.NotFound exception
            ) {
                return Optional.empty();

            } catch (Exception exception) {
                lastException = exception;

                System.err.println(
                        "[Lyrics-Netease] 获取歌词失败，"
                                + "songId="
                                + songId
                                + ", attempt="
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
                    "[Lyrics-Netease] 获取歌词最终失败："
                            + lastException.getMessage()
            );
        }

        return Optional.empty();
    }

    private Optional<NeteaseLyrics>
    getLyricsOnce(
            long songId
    ) {
        JsonNode root =
                restClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path(
                                                        "/lyric"
                                                )
                                                .queryParam(
                                                        "id",
                                                        songId
                                                )
                                                .build()
                        )
                        .retrieve()
                        .body(
                                JsonNode.class
                        );

        if (root == null) {
            return Optional.empty();
        }

        if (
                root.path("nolyric")
                        .asBoolean(false)
                        || root.path("uncollected")
                        .asBoolean(false)
        ) {
            return Optional.empty();
        }

        String syncedLyrics =
                root.path("lrc")
                        .path("lyric")
                        .asText("")
                        .trim();

        if (syncedLyrics.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(
                new NeteaseLyrics(
                        songId,
                        syncedLyrics
                )
        );
    }

    private static String resolveArtistNames(
            JsonNode artists
    ) {
        if (
                artists == null
                        || !artists.isArray()
        ) {
            return "";
        }

        List<String> result =
                new ArrayList<>();

        for (JsonNode artist : artists) {
            String name =
                    artist.path("name")
                            .asText("")
                            .trim();

            if (!name.isBlank()) {
                result.add(
                        name
                );
            }
        }

        return String.join(
                " / ",
                result
        );
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

    public record NeteaseSong(
            long id,
            String trackName,
            String artistName,
            String albumName,
            int durationSeconds
    ) {
    }

    public record NeteaseLyrics(
            long songId,
            String syncedLyrics
    ) {
    }
}
