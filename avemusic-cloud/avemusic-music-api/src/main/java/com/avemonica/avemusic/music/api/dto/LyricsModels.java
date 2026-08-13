package com.avemonica.avemusic.music.api.dto;

import java.io.Serializable;
import java.util.List;

public final class LyricsModels {

    private LyricsModels() {
    }

    public record LyricsResult(
            String songId,
            String status,
            boolean instrumental,
            boolean synced,
            String plainLyrics,
            String syncedLyrics,
            List<String> translatedLines,
            String source
    ) implements Serializable {
        public LyricsResult {
            translatedLines =
                    translatedLines == null
                            ? List.of()
                            : List.copyOf(
                            translatedLines
                    );
        }
    }
}