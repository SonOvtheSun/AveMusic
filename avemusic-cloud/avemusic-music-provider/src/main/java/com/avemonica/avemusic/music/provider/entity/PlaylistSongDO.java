package com.avemonica.avemusic.music.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("playlist_song_tb")
public class PlaylistSongDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long playlistId;

    private Long songId;

    private LocalDateTime createdAt;
}
