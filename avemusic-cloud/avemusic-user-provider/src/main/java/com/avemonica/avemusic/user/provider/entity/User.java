package com.avemonica.avemusic.user.provider.entity;

import com.avemonica.avemusic.common.security.UserRole;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_tb")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;
    private String passwordHash;
    private String avatarUrl;
    private String phone;
    private String email;
    private String bio;
    private Integer gender;
    private LocalDate birthDate;
    private String province;
    private String city;
    private Integer status;

    /**
     * SUPER_ADMIN、OPERATOR、REVIEWER、ARTIST、USER。
     */
    private UserRole role;

    /**
     * 关联 user_id_tb.id。
     * 非空只表示存在实名记录，是否通过还要看 verify_status。
     */
    private Long realNameInfoId;

    /**
     * 关联 avemusic_music.singer_tb.id，不建立跨库外键。
     */
    private Long artistId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
