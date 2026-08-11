package com.avemonica.avemusic.music.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("playlist_tb")
public class PlaylistDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String name;

    private String introduction;

    private String coverUrl;

    private String visibility;

    private Long favoriteCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
