package com.avemonica.avemusic.music.provider.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
public final class LrclibClient {

    private final RestClient restClient;

    public LrclibClient(
            @Value(
                    "${avemusic.lyrics.lrclib-base-url:"
                            + "https://lrclib.net}"
            )
            String baseUrl
    ) {
        this.restClient =
                RestClient
                        .builder()
                        .baseUrl(baseUrl)
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
        try {
            LrclibRecord[] result =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/api/search")
                                                    .queryParam(
                                                            "track_name",
                                                            trackName
                                                    )
                                                    .queryParam(
                                                            "artist_name",
                                                            artistName
                                                    )
                                                    .build()
                            )
                            .retrieve()
                            .body(
                                    LrclibRecord[].class
                            );

            if (result == null) {
                return List.of();
            }

            return List.of(result);

        } catch (Exception exception) {
            System.err.println(
                    "[Lyrics] LRCLIB搜索失败："
                            + exception.getMessage()
            );

            exception.printStackTrace();

            return List.of();
        }
    }

    public Optional<LrclibRecord>
    getExact(
            String trackName,
            String artistName,
            String albumName,
            int durationSeconds
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
                                                            trackName
                                                    )
                                                    .queryParam(
                                                            "artist_name",
                                                            artistName
                                                    )
                                                    .queryParam(
                                                            "album_name",
                                                            albumName
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