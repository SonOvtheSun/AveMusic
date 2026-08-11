package com.avemonica.avemusic.music.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("song_artist_tb")
public class SongArtistDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long songId;
    private Long artistId;
}
