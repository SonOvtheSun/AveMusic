package com.avemonica.avemusic.music.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("song_tb")
public class SongDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long albumId;
    private String name;
    private Integer durationSeconds;
    private String style;
    private String introduction;
    private String coverUrl;
    private String audioUrl;
    private Long playCount;
    private Integer status;
    private String auditStatus;
    private Long createdBy;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
