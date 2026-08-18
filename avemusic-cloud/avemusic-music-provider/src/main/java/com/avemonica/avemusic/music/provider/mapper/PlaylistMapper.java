package com.avemonica.avemusic.music.provider.mapper;

import com.avemonica.avemusic.music.provider.entity.PlaylistDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface PlaylistMapper
        extends BaseMapper<PlaylistDO> {

    @Select("""
            SELECT
                CAST(p.id AS CHAR) AS playlistId,
                p.name AS playlistName,
                p.introduction AS introduction,
                COALESCE(
                    p.cover_url,

                    (
                        SELECT first_song.cover_url

                        FROM playlist_song_tb first_relation

                        INNER JOIN song_tb first_song
                            ON first_song.id =
                               first_relation.song_id

                        WHERE first_relation.playlist_id =
                              p.id

                        ORDER BY
                            first_relation.created_at ASC,
                            first_relation.id ASC

                        LIMIT 1
                    )
                ) AS coverUrl,

                p.cover_url AS customCoverUrl,
                p.visibility AS visibility,
                COUNT(ps.id) AS songCount,
                DATE_FORMAT(
                    p.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt
            FROM playlist_tb p
            LEFT JOIN playlist_song_tb ps
                ON ps.playlist_id = p.id
            WHERE p.user_id = #{userId}
            GROUP BY
                p.id,
                p.name,
                p.introduction,
                p.cover_url,
                p.visibility,
                p.created_at
            ORDER BY
                p.created_at DESC,
                p.id DESC
            """)
    List<Map<String, Object>> selectMine(
            @Param("userId")
            Long userId
    );

    @Select("""
        <script>

        SELECT
            CAST(p.id AS CHAR)
                AS id,

            p.name
                AS name,

            p.introduction
                AS subtitle,

            COALESCE(
                p.cover_url,

                (
                    SELECT
                        first_song.cover_url

                    FROM playlist_song_tb
                        first_relation

                    INNER JOIN song_tb
                        first_song

                        ON first_song.id =
                           first_relation.song_id

                    WHERE
                        first_relation.playlist_id =
                        p.id

                    ORDER BY
                        first_relation.created_at ASC,
                        first_relation.id ASC

                    LIMIT 1
                )
            ) AS coverUrl,

            NULL
                AS audioUrl,

            0
                AS durationSeconds,

            COALESCE(
                p.favorite_count,
                0
            ) AS popularity,

            ''
                AS artistIds

        FROM playlist_tb p

        WHERE
            p.visibility = 'PUBLIC'

            AND (
                <foreach
                    collection="keywords"
                    item="keyword"
                    separator=" OR "
                >

                    (
                        p.name LIKE
                            CONCAT(
                                '%',
                                #{keyword},
                                '%'
                            )

                        OR p.introduction LIKE
                            CONCAT(
                                '%',
                                #{keyword},
                                '%'
                            )
                    )

                </foreach>
            )

        ORDER BY

            CASE

                WHEN p.name =
                        #{originalKeyword}
                    THEN 0

                WHEN p.name LIKE
                        CONCAT(
                            #{originalKeyword},
                            '%'
                        )
                    THEN 1

                WHEN p.name LIKE
                        CONCAT(
                            '%',
                            #{originalKeyword},
                            '%'
                        )
                    THEN 2

                ELSE 3

            END,

            p.favorite_count DESC,
            p.id DESC

        LIMIT #{limit}

        </script>
        """)
    List<Map<String, Object>>
    searchPublic(
            @Param("keywords")
            List<String> keywords,

            @Param("originalKeyword")
            String originalKeyword,

            @Param("limit")
            int limit
    );

    @Select("""
        SELECT
            CAST(
                p.id AS CHAR
            ) AS playlistId,

            p.name AS playlistName,

            p.introduction AS introduction,

            COALESCE(
                p.cover_url,
                (
                    SELECT
                        first_song.cover_url

                    FROM playlist_song_tb
                        first_relation

                    INNER JOIN song_tb
                        first_song

                        ON first_song.id =
                           first_relation.song_id

                    WHERE
                        first_relation.playlist_id =
                        p.id

                    ORDER BY
                        first_relation.created_at ASC,
                        first_relation.id ASC

                    LIMIT 1
                )
            ) AS coverUrl,

            p.cover_url
                AS customCoverUrl,

            /*
             * 这里就是你现在漏掉的字段
             */
            CAST(
                p.user_id AS CHAR
            ) AS ownerUserId,

            p.visibility
                AS visibility,

            COUNT(ps.id)
                AS songCount,

            /*
             * 收藏数量直接读取 playlist_tb
             */
            p.favorite_count
                AS favoriteCount,

            DATE_FORMAT(
                p.created_at,
                '%Y-%m-%d %H:%i:%s'
            ) AS createdAt

        FROM playlist_tb p

        LEFT JOIN playlist_song_tb ps
            ON ps.playlist_id = p.id

        WHERE p.id = #{playlistId}

        GROUP BY
            p.id,
            p.name,
            p.introduction,
            p.cover_url,
            p.user_id,
            p.visibility,
            p.favorite_count,
            p.created_at

        LIMIT 1
        """)
    Map<String, Object> selectSummary(
            @Param("playlistId")
            Long playlistId
    );

    @Select("""
        SELECT
            CAST(p.id AS CHAR) AS playlistId,
            p.name AS playlistName,
            p.introduction AS introduction,

            COALESCE(
                p.cover_url,
                (
                    SELECT first_song.cover_url
                    FROM playlist_song_tb first_relation
                    INNER JOIN song_tb first_song
                        ON first_song.id = first_relation.song_id
                    WHERE first_relation.playlist_id = p.id
                    ORDER BY
                        first_relation.created_at ASC,
                        first_relation.id ASC
                    LIMIT 1
                )
            ) AS coverUrl,

            p.cover_url AS customCoverUrl,

            CAST(p.user_id AS CHAR) AS ownerUserId,
            p.visibility AS visibility,

            COUNT(ps.id) AS songCount,

           p.favorite_count AS favoriteCount,

            DATE_FORMAT(
                p.created_at,
                '%Y-%m-%d %H:%i:%s'
            ) AS createdAt,

            favorite.created_at AS favoriteAt

        FROM playlist_favorite_tb favorite

        INNER JOIN playlist_tb p
            ON p.id = favorite.playlist_id

        LEFT JOIN playlist_song_tb ps
            ON ps.playlist_id = p.id

        WHERE favorite.user_id = #{userId}
          AND p.visibility = 'PUBLIC'
          AND p.user_id <> #{userId}

        GROUP BY
            favorite.id,
            favorite.created_at,
            p.id,
            p.name,
            p.introduction,
            p.cover_url,
            p.user_id,
            p.visibility,
            p.created_at

        ORDER BY
            favorite.created_at DESC,
            favorite.id DESC
        """)
    List<Map<String, Object>> selectFavorites(
            @Param("userId")
            Long userId
    );

    @Update("""
        UPDATE playlist_tb
        SET favorite_count =
            favorite_count + 1
        WHERE id = #{playlistId}
        """)
    int incrementFavoriteCount(
            @Param("playlistId")
            Long playlistId
    );

    @Update("""
        UPDATE playlist_tb
        SET favorite_count =
            GREATEST(
                favorite_count - 1,
                0
            )
        WHERE id = #{playlistId}
        """)
    int decrementFavoriteCount(
            @Param("playlistId")
            Long playlistId
    );

    @Select("""
        SELECT
            CAST(p.id AS CHAR)
                AS playlistId,

            p.name
                AS playlistName,

            p.introduction
                AS introduction,

            COALESCE(
                p.cover_url,
                (
                    SELECT
                        first_song.cover_url

                    FROM playlist_song_tb
                        first_relation

                    INNER JOIN song_tb
                        first_song

                        ON first_song.id =
                           first_relation.song_id

                    WHERE
                        first_relation.playlist_id =
                        p.id

                    ORDER BY
                        first_relation.created_at ASC,
                        first_relation.id ASC

                    LIMIT 1
                )
            ) AS coverUrl,

            p.cover_url
                AS customCoverUrl,

            CAST(p.user_id AS CHAR)
                AS ownerUserId,

            p.visibility
                AS visibility,

            (
                SELECT COUNT(*)

                FROM playlist_song_tb ps

                WHERE ps.playlist_id =
                      p.id
            ) AS songCount,

            p.favorite_count
                AS favoriteCount,

            DATE_FORMAT(
                p.created_at,
                '%Y-%m-%d %H:%i:%s'
            ) AS createdAt

        FROM playlist_tb p

        WHERE p.visibility =
              'PUBLIC'

        ORDER BY
            p.favorite_count DESC,
            p.created_at DESC,
            p.id DESC

        LIMIT #{offset}, #{size}
        """)
    List<Map<String, Object>>
    selectPublicRanking(
            @Param("offset")
            int offset,

            @Param("size")
            int size
    );

    @Select("""
        SELECT COUNT(*)
        FROM playlist_tb
        WHERE visibility = 'PUBLIC'
        """)
    long countPublicPlaylists();
}
