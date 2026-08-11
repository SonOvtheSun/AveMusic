package com.avemonica.avemusic.music.provider.mapper;

import com.avemonica.avemusic.music.provider.entity.ArtistDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ArtistMapper extends BaseMapper<ArtistDO> {

    @Select("""
            SELECT
                CAST(id AS CHAR) AS artistId,
                name AS artistName,
                translated_name AS translatedName,
                avatar_url AS avatarUrl,
                country_region AS countryRegion,
                follower_count AS followerCount
            FROM singer_tb
            WHERE audit_status = 'APPROVED'
              AND status = 1
            ORDER BY follower_count DESC, id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectHomeArtists(
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT
                CAST(artist.id AS CHAR) AS artistId,
                artist.name AS artistName,
                artist.translated_name AS translatedName,
                CAST(artist.owner_user_id AS CHAR) AS ownerUserId,
                artist.country_region AS countryRegion,
                artist.style AS style,
                artist.avatar_url AS avatarUrl,
                artist.introduction AS introduction,
                artist.follower_count AS followerCount,
                COUNT(DISTINCT song_relation.song_id) AS songCount,
                COUNT(DISTINCT album_relation.album_id) AS albumCount,
                artist.audit_status AS auditStatus,
                CASE WHEN artist.status = 1
                    THEN 'ONLINE' ELSE 'OFFLINE'
                END AS publishStatus,
                DATE_FORMAT(
                    artist.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt
            FROM singer_tb artist
            LEFT JOIN song_artist_tb song_relation
                ON song_relation.artist_id = artist.id
            LEFT JOIN artist_album_tb album_relation
                ON album_relation.artist_id = artist.id
            <if test="pendingOnly">
                WHERE artist.audit_status = 'PENDING'
            </if>
            GROUP BY
                artist.id,
                artist.name,
                artist.translated_name,
                artist.owner_user_id,
                artist.country_region,
                artist.style,
                artist.avatar_url,
                artist.introduction,
                artist.follower_count,
                artist.audit_status,
                artist.status,
                artist.created_at
            ORDER BY artist.id DESC
            LIMIT 200
            </script>
            """)
    List<Map<String, Object>> selectManagementArtists(
            @Param("pendingOnly") boolean pendingOnly
    );

    @Select("""
            SELECT
                CAST(id AS CHAR) AS artistId,
                name AS artistName,
                translated_name AS translatedName,
                avatar_url AS avatarUrl,
                country_region AS countryRegion,
                audit_status AS auditStatus
            FROM singer_tb
            WHERE audit_status IN ('APPROVED', 'PENDING')
              AND (
                    name LIKE CONCAT('%', #{keyword}, '%')
                    OR translated_name LIKE CONCAT('%', #{keyword}, '%')
              )
            ORDER BY
                CASE audit_status
                    WHEN 'APPROVED' THEN 0
                    ELSE 1
                END,
                follower_count DESC,
                id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> searchForSong(
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    @Select("""
            SELECT COUNT(*)
            FROM song_artist_tb
            WHERE artist_id = #{artistId}
            """)
    long countSongsByArtist(
            @Param("artistId") Long artistId
    );

    @Select("""
            SELECT COUNT(*)
            FROM artist_album_tb
            WHERE artist_id = #{artistId}
            """)
    long countAlbumsByArtist(
            @Param("artistId") Long artistId
    );

    /**
     * C端音乐人头部资料。
     * 只允许读取已审核且已上架的音乐人。
     */
    @Select("""
            SELECT
                CAST(artist.id AS CHAR) AS artistId,
                artist.name AS artistName,
                artist.translated_name AS translatedName,
                CAST(artist.owner_user_id AS CHAR) AS ownerUserId,
                artist.avatar_url AS avatarUrl,
                artist.country_region AS countryRegion,
                artist.style AS style,
                artist.introduction AS introduction,
                artist.follower_count AS followerCount,
                (
                    SELECT COUNT(DISTINCT relation.song_id)
                    FROM song_artist_tb relation
                    INNER JOIN song_tb song
                        ON song.id = relation.song_id
                    WHERE relation.artist_id = artist.id
                      AND song.audit_status = 'APPROVED'
                      AND song.status = 1
                ) AS songCount,
                (
                    SELECT COUNT(DISTINCT relation.album_id)
                    FROM artist_album_tb relation
                    INNER JOIN album_tb album
                        ON album.id = relation.album_id
                    WHERE relation.artist_id = artist.id
                      AND album.audit_status = 'APPROVED'
                ) AS albumCount
            FROM singer_tb artist
            WHERE artist.id = #{artistId}
              AND artist.audit_status = 'APPROVED'
              AND artist.status = 1
            LIMIT 1
            """)
    Map<String, Object> selectPublicArtistDetail(
            @Param("artistId") Long artistId
    );

    /**
     * 音乐人页面“热门作品”。
     * 只返回C端可播放歌曲。
     */
    @Select("""
            SELECT
                CAST(song.id AS CHAR) AS songId,
                song.name AS songName,
                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT all_artist.name
                            ORDER BY all_artist.name
                            SEPARATOR ' / '
                        )
                        FROM song_artist_tb all_relation
                        INNER JOIN singer_tb all_artist
                            ON all_artist.id = all_relation.artist_id
                        WHERE all_relation.song_id = song.id
                    ),
                    '未知音乐人'
                ) AS artistName,
                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT CAST(
                                all_artist.id AS CHAR
                            )
                            ORDER BY all_artist.name
                            SEPARATOR ','
                        )
                        FROM song_artist_tb all_relation
                        INNER JOIN singer_tb all_artist
                            ON all_artist.id = all_relation.artist_id
                        WHERE all_relation.song_id = song.id
                    ),
                    ''
                ) AS artistIds,
                CAST(song.album_id AS CHAR) AS albumId,
                album.name AS albumName,
                song.cover_url AS coverUrl,
                song.audio_url AS audioUrl,
                song.duration_seconds AS durationSeconds,
                song.play_count AS playCount
            FROM song_artist_tb relation
            INNER JOIN song_tb song
                ON song.id = relation.song_id
            LEFT JOIN album_tb album
                ON album.id = song.album_id
            WHERE relation.artist_id = #{artistId}
              AND song.audit_status = 'APPROVED'
              AND song.status = 1
            ORDER BY
                song.play_count DESC,
                song.id DESC
            LIMIT 100
            """)
    List<Map<String, Object>> selectPublicArtistSongs(
            @Param("artistId") Long artistId
    );

    /**
     * 音乐人页面“所有专辑”。
     */
    @Select("""
            SELECT
                CAST(album.id AS CHAR) AS albumId,
                album.name AS albumName,
                album.cover_url AS coverUrl,
                DATE_FORMAT(
                    album.release_date,
                    '%Y-%m-%d'
                ) AS releaseDate,
                album.style AS style
            FROM artist_album_tb relation
            INNER JOIN album_tb album
                ON album.id = relation.album_id
            WHERE relation.artist_id = #{artistId}
              AND album.audit_status = 'APPROVED'
            GROUP BY
                album.id,
                album.name,
                album.cover_url,
                album.release_date,
                album.style
            ORDER BY
                album.release_date DESC,
                album.id DESC
            LIMIT 100
            """)
    List<Map<String, Object>> selectPublicArtistAlbums(
            @Param("artistId") Long artistId
    );

}
