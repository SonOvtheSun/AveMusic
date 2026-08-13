package com.avemonica.avemusic.music.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName(
        value = "singer_tb",
        autoResultMap = true
)
public class ArtistDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long ownerUserId;
    private String name;
    private String introduction;
    private String avatarUrl;
    private String countryRegion;

    @TableField(
            value = "translated_name",
            typeHandler = JacksonTypeHandler.class
    )
    private List<String> translatedNames =
            new ArrayList<>();

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
