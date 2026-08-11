package com.avemonica.avemusic.music.provider.mapper;

import com.avemonica.avemusic.music.provider.entity.SongArtistDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SongArtistMapper
        extends BaseMapper<SongArtistDO> {

    @Select("""
            SELECT artist_id
            FROM song_artist_tb
            WHERE song_id = #{songId}
            ORDER BY id
            """)
    List<Long> selectArtistIdsBySongId(
            @Param("songId")
            Long songId
    );
}
