package com.avemonica.avemusic.music.provider.mapper;

import com.avemonica.avemusic.music.provider.entity.AlbumDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface AlbumMapper
        extends BaseMapper<AlbumDO> {

    @Select("""
            <script>
            SELECT
                CAST(album.id AS CHAR) AS albumId,
                album.name AS albumName,
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
                album.style AS style,
                album.cover_url AS coverUrl,
                DATE_FORMAT(
                    album.release_date,
                    '%Y-%m-%d'
                ) AS releaseDate,
                album.introduction AS introduction,
                album.audit_status AS auditStatus,
                DATE_FORMAT(
                    album.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt
            FROM album_tb album
            LEFT JOIN artist_album_tb relation
                ON relation.album_id = album.id
            LEFT JOIN singer_tb singer
                ON singer.id = relation.artist_id
            <if test="pendingOnly">
                WHERE album.audit_status = 'PENDING'
            </if>
            GROUP BY
                album.id,
                album.name,
                album.style,
                album.cover_url,
                album.release_date,
                album.introduction,
                album.audit_status,
                album.created_at
            ORDER BY album.id DESC
            LIMIT 200
            </script>
            """)
    List<Map<String, Object>> selectManagementAlbums(
            @Param("pendingOnly")
            boolean pendingOnly
    );

    @Select("""
            SELECT
                CAST(album.id AS CHAR) AS albumId,
                album.name AS albumName,
                COALESCE(
                    GROUP_CONCAT(
                        DISTINCT singer.name
                        ORDER BY singer.name
                        SEPARATOR ' / '
                    ),
                    '未知音乐人'
                ) AS artistName,
                album.cover_url AS coverUrl,
                album.style AS style
            FROM album_tb album
            LEFT JOIN artist_album_tb relation
                ON relation.album_id = album.id
            LEFT JOIN singer_tb singer
                ON singer.id = relation.artist_id
            WHERE album.audit_status = 'APPROVED'
              AND album.name LIKE CONCAT('%', #{keyword}, '%')
            GROUP BY
                album.id,
                album.name,
                album.cover_url,
                album.style
            ORDER BY
                album.release_date DESC,
                album.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> searchForSong(
            @Param("keyword")
            String keyword,

            @Param("limit")
            int limit
    );

    @Insert("""
            INSERT INTO artist_album_tb(
                artist_id,
                album_id
            ) VALUES (
                #{artistId},
                #{albumId}
            )
            """)
    int insertArtistAlbumRelation(
            @Param("artistId")
            Long artistId,

            @Param("albumId")
            Long albumId
    );

    @Update("""
            <script>
            UPDATE song_tb
            SET album_id = NULL
            WHERE album_id IN
            <foreach
                collection="albumIds"
                item="id"
                open="("
                separator=","
                close=")">
                #{id}
            </foreach>
            </script>
            """)
    int clearSongAlbumIds(
            @Param("albumIds")
            List<Long> albumIds
    );

    @Delete("""
            <script>
            DELETE FROM artist_album_tb
            WHERE album_id IN
            <foreach
                collection="albumIds"
                item="id"
                open="("
                separator=","
                close=")">
                #{id}
            </foreach>
            </script>
            """)
    int deleteArtistAlbumRelations(
            @Param("albumIds")
            List<Long> albumIds
    );

    /**
     * C端专辑头部信息。
     * 仅返回审核通过的专辑。
     */
    @Select("""
            SELECT
                CAST(album.id AS CHAR) AS albumId,
                album.name AS albumName,
                album.cover_url AS coverUrl,
                album.style AS style,
                album.introduction AS introduction,
                DATE_FORMAT(
                    album.release_date,
                    '%Y-%m-%d'
                ) AS releaseDate,

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
                        DISTINCT CAST(
                            artist.id AS CHAR
                        )
                        ORDER BY artist.name
                        SEPARATOR ','
                    ),
                    ''
                ) AS artistIds,

                SUBSTRING_INDEX(
                    GROUP_CONCAT(
                        artist.avatar_url
                        ORDER BY artist.name
                        SEPARATOR ','
                    ),
                    ',',
                    1
                ) AS artistAvatarUrl

            FROM album_tb album

            LEFT JOIN artist_album_tb relation
                ON relation.album_id = album.id

            LEFT JOIN singer_tb artist
                ON artist.id = relation.artist_id

            WHERE album.id = #{albumId}
              AND album.audit_status = 'APPROVED'

            GROUP BY
                album.id,
                album.name,
                album.cover_url,
                album.style,
                album.introduction,
                album.release_date

            LIMIT 1
            """)
    Map<String, Object> selectPublicAlbumDetail(
            @Param("albumId")
            Long albumId
    );

    /**
     * C端专辑歌曲。
     * 只返回审核通过且已上架的歌曲。
     */
    @Select("""
            SELECT
                CAST(song.id AS CHAR) AS songId,
                song.name AS songName,
                album.name AS albumName,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT artist.name
                            ORDER BY artist.name
                            SEPARATOR ' / '
                        )
                        FROM song_artist_tb song_relation
                        INNER JOIN singer_tb artist
                            ON artist.id = song_relation.artist_id
                        WHERE song_relation.song_id = song.id
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
                        FROM song_artist_tb song_relation
                        INNER JOIN singer_tb artist
                            ON artist.id = song_relation.artist_id
                        WHERE song_relation.song_id = song.id
                    ),
                    ''
                ) AS artistIds,

                song.cover_url AS coverUrl,
                song.audio_url AS audioUrl,
                song.duration_seconds AS durationSeconds,
                COALESCE(
                    song.play_count,
                    0
                ) AS playCount

            FROM song_tb song

            INNER JOIN album_tb album
                ON album.id = song.album_id

            WHERE song.album_id = #{albumId}
              AND song.audit_status = 'APPROVED'
              AND song.status = 1

            ORDER BY song.id ASC
            """)
    List<Map<String, Object>> selectPublicAlbumSongs(
            @Param("albumId")
            Long albumId
    );

    /**
     * 管理中心专辑分页。
     *
     * keyword 支持：
     *
     * 1. 专辑名称
     * 2. 专辑音乐人
     * 3. 专辑中的歌曲名称
     * 4. 专辑中歌曲的音乐人
     */
    @Select("""
            <script>
            SELECT
                CAST(album.id AS CHAR) AS albumId,

                album.name AS albumName,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT artist.name
                            ORDER BY artist.name
                            SEPARATOR ' / '
                        )

                        FROM artist_album_tb album_artist

                        INNER JOIN singer_tb artist
                            ON artist.id =
                               album_artist.artist_id

                        WHERE album_artist.album_id =
                              album.id
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

                        FROM artist_album_tb album_artist

                        INNER JOIN singer_tb artist
                            ON artist.id =
                               album_artist.artist_id

                        WHERE album_artist.album_id =
                              album.id
                    ),
                    ''
                ) AS artistIds,

                album.style AS style,

                album.cover_url AS coverUrl,

                DATE_FORMAT(
                    album.release_date,
                    '%Y-%m-%d'
                ) AS releaseDate,

                album.introduction AS introduction,

                album.audit_status AS auditStatus,

                DATE_FORMAT(
                    album.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt

            FROM album_tb album

            WHERE 1 = 1

            <if test="
                keyword != null
                and keyword != ''
            ">
                AND (
                    /*
                     * 专辑名称
                     */
                    album.name LIKE
                        CONCAT(
                            '%',
                            #{keyword},
                            '%'
                        )

                    /*
                     * 专辑本身绑定的音乐人
                     */
                    OR EXISTS (
                        SELECT 1

                        FROM artist_album_tb
                            search_album_artist

                        INNER JOIN singer_tb
                            search_artist

                            ON search_artist.id =
                               search_album_artist.artist_id

                        WHERE search_album_artist.album_id =
                              album.id

                          AND search_artist.name LIKE
                              CONCAT(
                                  '%',
                                  #{keyword},
                                  '%'
                              )
                    )

                    /*
                     * 专辑中的歌曲名称
                     * 或歌曲对应的音乐人
                     */
                    OR EXISTS (
                        SELECT 1

                        FROM song_tb search_song

                        WHERE search_song.album_id =
                              album.id

                          AND (
                              search_song.name LIKE
                                  CONCAT(
                                      '%',
                                      #{keyword},
                                      '%'
                                  )

                              OR EXISTS (
                                  SELECT 1

                                  FROM song_artist_tb
                                      search_song_artist

                                  INNER JOIN singer_tb
                                      search_artist2

                                      ON search_artist2.id =
                                         search_song_artist.artist_id

                                  WHERE search_song_artist.song_id =
                                        search_song.id

                                    AND search_artist2.name LIKE
                                        CONCAT(
                                            '%',
                                            #{keyword},
                                            '%'
                                        )
                              )
                          )
                    )
                )
            </if>

            ORDER BY
                album.created_at DESC,
                album.id DESC

            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>>
    selectManagementAlbumPage(
            @Param("keyword")
            String keyword,

            @Param("offset")
            int offset,

            @Param("size")
            int size
    );


    /**
     * 专辑管理搜索结果总数。
     *
     * 搜索条件必须和分页查询保持一致。
     */
    @Select("""
            <script>
            SELECT COUNT(*)

            FROM album_tb album

            WHERE 1 = 1

            <if test="
                keyword != null
                and keyword != ''
            ">
                AND (
                    album.name LIKE
                        CONCAT(
                            '%',
                            #{keyword},
                            '%'
                        )

                    OR EXISTS (
                        SELECT 1

                        FROM artist_album_tb
                            search_album_artist

                        INNER JOIN singer_tb
                            search_artist

                            ON search_artist.id =
                               search_album_artist.artist_id

                        WHERE search_album_artist.album_id =
                              album.id

                          AND search_artist.name LIKE
                              CONCAT(
                                  '%',
                                  #{keyword},
                                  '%'
                              )
                    )

                    OR EXISTS (
                        SELECT 1

                        FROM song_tb search_song

                        WHERE search_song.album_id =
                              album.id

                          AND (
                              search_song.name LIKE
                                  CONCAT(
                                      '%',
                                      #{keyword},
                                      '%'
                                  )

                              OR EXISTS (
                                  SELECT 1

                                  FROM song_artist_tb
                                      search_song_artist

                                  INNER JOIN singer_tb
                                      search_artist2

                                      ON search_artist2.id =
                                         search_song_artist.artist_id

                                  WHERE search_song_artist.song_id =
                                        search_song.id

                                    AND search_artist2.name LIKE
                                        CONCAT(
                                            '%',
                                            #{keyword},
                                            '%'
                                        )
                              )
                          )
                    )
                )
            </if>

            </script>
            """)
    long countManagementAlbums(
            @Param("keyword")
            String keyword
    );


    /**
     * 根据专辑 ID 查询单条管理数据。
     *
     * 用来替换 findAlbumItem() 中
     * listManagedAlbums() 的全量查询。
     */
    @Select("""
            SELECT
                CAST(album.id AS CHAR) AS albumId,

                album.name AS albumName,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT artist.name
                            ORDER BY artist.name
                            SEPARATOR ' / '
                        )

                        FROM artist_album_tb album_artist

                        INNER JOIN singer_tb artist
                            ON artist.id =
                               album_artist.artist_id

                        WHERE album_artist.album_id =
                              album.id
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

                        FROM artist_album_tb album_artist

                        INNER JOIN singer_tb artist
                            ON artist.id =
                               album_artist.artist_id

                        WHERE album_artist.album_id =
                              album.id
                    ),
                    ''
                ) AS artistIds,

                album.style AS style,

                album.cover_url AS coverUrl,

                DATE_FORMAT(
                    album.release_date,
                    '%Y-%m-%d'
                ) AS releaseDate,

                album.introduction AS introduction,

                album.audit_status AS auditStatus,

                DATE_FORMAT(
                    album.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt

            FROM album_tb album

            WHERE album.id = #{albumId}

            LIMIT 1
            """)
    Map<String, Object>
    selectManagementAlbumById(
            @Param("albumId")
            Long albumId
    );

}
