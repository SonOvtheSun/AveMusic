package com.avemonica.avemusic.music.provider.mapper;

import com.avemonica.avemusic.music.provider.entity.PlaylistSongDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface PlaylistSongMapper
        extends BaseMapper<PlaylistSongDO> {

    @Select("""
            SELECT
                CAST(song.id AS CHAR) AS songId,
                song.name AS songName,

                COALESCE(
                    GROUP_CONCAT(
                        DISTINCT artist.name
                        ORDER BY artist.name
                        SEPARATOR ' / '
                    ),
                    '未知音乐人'
                ) AS artistName,

                COALESCE(
                    GROUP_CONCAT(
                        DISTINCT CAST(artist.id AS CHAR)
                        ORDER BY artist.name
                        SEPARATOR ','
                    ),
                    ''
                ) AS artistIds,

                album.name AS albumName,
                song.cover_url AS coverUrl,
                song.audio_url AS audioUrl,
                song.duration_seconds AS durationSeconds,
                COALESCE(
                    song.play_count,
                    0
                ) AS playCount,

                relation.created_at AS collectedAt

            FROM playlist_song_tb relation

            INNER JOIN song_tb song
                ON song.id = relation.song_id

            LEFT JOIN album_tb album
                ON album.id = song.album_id

            LEFT JOIN song_artist_tb song_artist
                ON song_artist.song_id = song.id

            LEFT JOIN singer_tb artist
                ON artist.id = song_artist.artist_id

            WHERE relation.playlist_id = #{playlistId}

            GROUP BY
                relation.id,
                relation.created_at,
                song.id,
                song.name,
                album.name,
                song.cover_url,
                song.audio_url,
                song.duration_seconds,
                song.play_count

            ORDER BY
                relation.created_at ASC,
                relation.id ASC
            """)
    List<Map<String, Object>> selectPlaylistSongs(
            @Param("playlistId")
            Long playlistId
    );
}
