package com.avemonica.avemusic.music.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("singer_tb")
public class ArtistDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long ownerUserId;
    private String name;
    private String introduction;
    private String avatarUrl;
    private String countryRegion;
    private String translatedName;
    private String style;
    private String nameInitial;
    private Long followerCount;
    private String auditStatus;
    private Integer status;
    private Long createdBy;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
