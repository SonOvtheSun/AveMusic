package com.avemonica.avemusic.music.api.service;

import com.avemonica.avemusic.music.api.dto
        .LyricsModels.LyricsResult;

public interface LyricsService {

    LyricsResult getLyrics(
            String songId
    );

    LyricsResult translateLyrics(
            String songId
    );
}