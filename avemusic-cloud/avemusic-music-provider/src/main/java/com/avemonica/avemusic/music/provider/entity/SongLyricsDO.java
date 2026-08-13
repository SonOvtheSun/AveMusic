package com.avemonica.avemusic.music.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("song_lyrics_tb")
public class SongLyricsDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long songId;

    private String provider;

    private String providerLyricId;

    private String translationJson;

    private Boolean instrumental;

    private String plainLyrics;

    private String syncedLyrics;

    private String status;

    private LocalDateTime matchedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}