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


    /**
     * 管理端人工上传歌词。
     *
     * fileName:
     *   xxx.lrc / xxx.txt
     *
     * content:
     *   歌词文件文本内容。
     */
    LyricsResult replaceManualLyrics(
            String songId,
            String fileName,
            String content
    );


    /**
     * 管理端指定在线来源重新匹配歌词。
     *
     * source:
     * LRCLIB / NETEASE
     */
    LyricsResult replaceLyricsFromSource(
            String songId,
            String source
    );
}