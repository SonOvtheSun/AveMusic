package com.avemonica.avemusic.music.provider.mapper;

import com.avemonica.avemusic.music.provider.entity.SongDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface SongMapper
        extends BaseMapper<SongDO> {

    @Select("""
            SELECT
                CAST(s.id AS CHAR) AS songId,
                s.name AS songName,
                COALESCE(
                    GROUP_CONCAT(
                        DISTINCT singer.name
                        ORDER BY singer.name
                        SEPARATOR ' / '
                    ),
                    '未知音乐人'
                ) AS artistName,
                COALESCE(
                    GROUP_CONCAT(
                        DISTINCT CAST(singer.id AS CHAR)
                        ORDER BY singer.name
                        SEPARATOR ','
                    ),
                    ''
                ) AS artistIds,
                s.cover_url AS coverUrl,
                s.audio_url AS audioUrl,
                s.duration_seconds AS durationSeconds,
                s.play_count AS playCount
            FROM song_tb s
            LEFT JOIN song_artist_tb relation
                ON relation.song_id = s.id
            LEFT JOIN singer_tb singer
                ON singer.id = relation.artist_id
            WHERE s.status = 1
              AND s.audit_status = 'APPROVED'
            GROUP BY
                s.id,
                s.name,
                s.cover_url,
                s.audio_url,
                s.duration_seconds,
                s.play_count
            ORDER BY
                s.play_count DESC,
                s.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectHomeSongs(
            @Param("limit")
            int limit
    );

    @Select("""
            <script>
            SELECT
                CAST(s.id AS CHAR) AS songId,
                s.name AS songName,
                COALESCE(
                    GROUP_CONCAT(
                        DISTINCT singer.name
                        ORDER BY singer.name
                        SEPARATOR ' / '
                    ),
                    '未知音乐人'
                ) AS artistName,
                COALESCE(
                    GROUP_CONCAT(
                        DISTINCT CAST(singer.id AS CHAR)
                        ORDER BY singer.name
                        SEPARATOR ','
                    ),
                    ''
                ) AS artistIds,
                CAST(s.album_id AS CHAR) AS albumId,
                album.name AS albumName,
                s.duration_seconds AS durationSeconds,
                s.style AS style,
                s.introduction AS introduction,
                s.cover_url AS coverUrl,
                s.audio_url AS audioUrl,
                s.audit_status AS auditStatus,
                CASE
                    WHEN s.status = 1 THEN 'ONLINE'
                    ELSE 'OFFLINE'
                END AS publishStatus,
                DATE_FORMAT(
                    s.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt
            FROM song_tb s
            LEFT JOIN album_tb album
                ON album.id = s.album_id
            LEFT JOIN song_artist_tb relation
                ON relation.song_id = s.id
            LEFT JOIN singer_tb singer
                ON singer.id = relation.artist_id
            <if test="pendingOnly">
                WHERE s.audit_status = 'PENDING'
            </if>
            GROUP BY
                s.id,
                s.name,
                s.album_id,
                album.name,
                s.duration_seconds,
                s.style,
                s.introduction,
                s.cover_url,
                s.audio_url,
                s.audit_status,
                s.status,
                s.created_at
            ORDER BY s.id DESC
            LIMIT 200
            </script>
            """)
    List<Map<String, Object>> selectManagementSongs(
            @Param("pendingOnly")
            boolean pendingOnly
    );

    /**
     * 只查询真正可在 C 端播放的歌曲时长。
     * playSession 不接受前端传来的 duration 作为可信依据。
     */
    @Select("""
            SELECT duration_seconds
            FROM song_tb
            WHERE id = #{songId}
              AND audit_status = 'APPROVED'
              AND status = 1
              AND audio_url IS NOT NULL
              AND audio_url <> ''
            LIMIT 1
            """)
    Integer selectPlayableDurationSeconds(
            @Param("songId")
            Long songId
    );

    /**
     * 数据库原子自增，避免并发播放导致丢失更新。
     */
    @Update("""
            UPDATE song_tb
            SET play_count = COALESCE(
                    play_count,
                    0
                ) + 1
            WHERE id = #{songId}
              AND audit_status = 'APPROVED'
              AND status = 1
            """)
    int incrementPlayCount(
            @Param("songId")
            Long songId
    );

    @Select("""
            SELECT COALESCE(
                play_count,
                0
            )
            FROM song_tb
            WHERE id = #{songId}
            LIMIT 1
            """)
    Long selectPlayCount(
            @Param("songId")
            Long songId
    );

    /**
     * 管理中心歌曲分页。
     *
     * keyword 支持搜索：
     * 1. 歌曲名称
     * 2. 音乐人名称
     * 3. 专辑名称
     */
    @Select("""
            <script>
            SELECT
                CAST(song.id AS CHAR) AS songId,

                song.name AS songName,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT artist.name
                            ORDER BY artist.name
                            SEPARATOR ' / '
                        )
                        FROM song_artist_tb song_artist
                        INNER JOIN singer_tb artist
                            ON artist.id = song_artist.artist_id
                        WHERE song_artist.song_id = song.id
                    ),
                    '未知音乐人'
                ) AS artistName,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT CAST(
                                artist.id AS CHAR
                            )
                            ORDER BY artist.name
                            SEPARATOR ','
                        )
                        FROM song_artist_tb song_artist
                        INNER JOIN singer_tb artist
                            ON artist.id = song_artist.artist_id
                        WHERE song_artist.song_id = song.id
                    ),
                    ''
                ) AS artistIds,

                CAST(song.album_id AS CHAR) AS albumId,

                album.name AS albumName,

                song.duration_seconds AS durationSeconds,

                song.style AS style,

                song.introduction AS introduction,

                song.cover_url AS coverUrl,

                song.audio_url AS audioUrl,

                song.audit_status AS auditStatus,

                CASE
                    WHEN song.status = 1
                        THEN 'ONLINE'
                    ELSE 'OFFLINE'
                END AS publishStatus,

                DATE_FORMAT(
                    song.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt

            FROM song_tb song

            LEFT JOIN album_tb album
                ON album.id = song.album_id

            WHERE 1 = 1

            <if test="
                keyword != null
                and keyword != ''
            ">
                AND (
                    song.name LIKE
                        CONCAT(
                            '%',
                            #{keyword},
                            '%'
                        )

                    OR album.name LIKE
                        CONCAT(
                            '%',
                            #{keyword},
                            '%'
                        )

                    OR EXISTS (
                        SELECT 1
                        FROM song_artist_tb search_relation

                        INNER JOIN singer_tb search_artist
                            ON search_artist.id =
                               search_relation.artist_id

                        WHERE search_relation.song_id =
                              song.id

                          AND search_artist.name LIKE
                              CONCAT(
                                  '%',
                                  #{keyword},
                                  '%'
                              )
                    )
                )
            </if>

            ORDER BY
                song.created_at DESC,
                song.id DESC

            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>>
    selectManagementSongPage(
            @Param("keyword")
            String keyword,

            @Param("offset")
            int offset,

            @Param("size")
            int size
    );


    /**
     * 管理中心歌曲分页总数。
     *
     * COUNT 的 WHERE 必须与上面的分页 SQL
     * 使用完全相同的搜索条件。
     */
    @Select("""
            <script>
            SELECT COUNT(*)

            FROM song_tb song

            LEFT JOIN album_tb album
                ON album.id = song.album_id

            WHERE 1 = 1

            <if test="
                keyword != null
                and keyword != ''
            ">
                AND (
                    song.name LIKE
                        CONCAT(
                            '%',
                            #{keyword},
                            '%'
                        )

                    OR album.name LIKE
                        CONCAT(
                            '%',
                            #{keyword},
                            '%'
                        )

                    OR EXISTS (
                        SELECT 1
                        FROM song_artist_tb search_relation

                        INNER JOIN singer_tb search_artist
                            ON search_artist.id =
                               search_relation.artist_id

                        WHERE search_relation.song_id =
                              song.id

                          AND search_artist.name LIKE
                              CONCAT(
                                  '%',
                                  #{keyword},
                                  '%'
                              )
                    )
                )
            </if>

            </script>
            """)
    long countManagementSongs(
            @Param("keyword")
            String keyword
    );


    /**
     * 根据 ID 查询单条管理端歌曲。
     *
     * 用来替代：
     *
     * listManagedSongs()
     *     .stream()
     *     .filter(...)
     *
     * 避免为了查询一首歌而把整张表查出来。
     */
    @Select("""
            SELECT
                CAST(song.id AS CHAR) AS songId,

                song.name AS songName,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT artist.name
                            ORDER BY artist.name
                            SEPARATOR ' / '
                        )
                        FROM song_artist_tb song_artist
                        INNER JOIN singer_tb artist
                            ON artist.id = song_artist.artist_id
                        WHERE song_artist.song_id = song.id
                    ),
                    '未知音乐人'
                ) AS artistName,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT CAST(
                                artist.id AS CHAR
                            )
                            ORDER BY artist.name
                            SEPARATOR ','
                        )
                        FROM song_artist_tb song_artist
                        INNER JOIN singer_tb artist
                            ON artist.id = song_artist.artist_id
                        WHERE song_artist.song_id = song.id
                    ),
                    ''
                ) AS artistIds,

                CAST(song.album_id AS CHAR) AS albumId,

                album.name AS albumName,

                song.duration_seconds AS durationSeconds,

                song.style AS style,

                song.introduction AS introduction,

                song.cover_url AS coverUrl,

                song.audio_url AS audioUrl,

                song.audit_status AS auditStatus,

                CASE
                    WHEN song.status = 1
                        THEN 'ONLINE'
                    ELSE 'OFFLINE'
                END AS publishStatus,

                DATE_FORMAT(
                    song.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt

            FROM song_tb song

            LEFT JOIN album_tb album
                ON album.id = song.album_id

            WHERE song.id = #{songId}

            LIMIT 1
            """)
    Map<String, Object>
    selectManagementSongById(
            @Param("songId")
            Long songId
    );

}
