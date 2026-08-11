package com.avemonica.avemusic.user.provider.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PermissionMapper {

    @Select("""
            SELECT p.permission_value
            FROM permission_tb p
            INNER JOIN role_permission_tb relation
                ON relation.permission_id = p.id
            WHERE relation.role_value = #{role}
            ORDER BY p.id
            """)
    List<String> selectByRole(
            @Param("role")
            String role
    );
}
