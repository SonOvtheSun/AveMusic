package com.avemonica.avemusic.music.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("album_tb")
public class AlbumDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String style;
    private String coverUrl;
    private LocalDate releaseDate;
    private Long favoriteCount;
    private String introduction;
    private String auditStatus;
    private Long createdBy;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
