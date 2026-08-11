package com.avemonica.avemusic.user.provider.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface UserManagementMapper {

    @Select("""
            SELECT
                CAST(u.id AS CHAR) AS userId,
                u.username AS username,
                CASE
                    WHEN u.phone IS NULL THEN NULL
                    WHEN CHAR_LENGTH(u.phone) < 7
                        THEN '***'
                    ELSE CONCAT(
                        LEFT(u.phone, 3),
                        '****',
                        RIGHT(u.phone, 4)
                    )
                END AS phoneMasked,
                u.role AS role,
                CASE
                    WHEN u.real_name_info_id IS NULL
                        THEN 'NONE'
                    WHEN identity.verify_status IS NULL
                        THEN 'PENDING'
                    ELSE identity.verify_status
                END AS realNameStatus,
                CASE
                    WHEN u.status = 1
                        THEN 'ENABLED'
                    ELSE 'DISABLED'
                END AS accountStatus,
                CAST(u.artist_id AS CHAR) AS artistId,
                DATE_FORMAT(
                    u.created_at,
                    '%Y-%m-%d %H:%i:%s'
                ) AS createdAt
            FROM user_tb u
            LEFT JOIN user_id_tb identity
                ON identity.id =
                    u.real_name_info_id
            ORDER BY u.id DESC
            LIMIT 200
            """)
    List<Map<String, Object>> selectUsers();
}
