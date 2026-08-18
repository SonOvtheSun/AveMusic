package com.avemonica.avemusic.user.provider.service;

import com.avemonica.avemusic.common.security.UserRole;
import com.avemonica.avemusic.user.api.dto.UserManagementModels.UserItem;
import com.avemonica.avemusic.user.api.enums.UserErrorCode;
import com.avemonica.avemusic.user.api.service.UserManagementService;
import com.avemonica.avemusic.user.provider.entity.UserDO;
import com.avemonica.avemusic.user.provider.mapper.UserManagementMapper;
import com.avemonica.avemusic.user.provider.mapper.UserMapper;
import com.avemonica.minirpc.core.exception.RpcBusinessException;
import com.avemonica.minirpc.spring.annotation.MiniRpcService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@MiniRpcService(
        interfaceClass =
                UserManagementService.class,
        group = "user",
        version = "1.0.0"
)
public class UserManagementServiceImpl
        implements UserManagementService {

    private final UserManagementMapper mapper;
    private final UserMapper userMapper;

    public UserManagementServiceImpl(
            UserManagementMapper mapper,
            UserMapper userMapper
    ) {
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<UserItem> listUsers() {
        return mapper.selectUsers()
                .stream()
                .map(
                        UserManagementServiceImpl
                                ::toUserItem
                )
                .toList();
    }

    @Override
    @Transactional
    public void updateUserRole(
            String targetUserId,
            String role,
            String operatorUserId
    ) {
        Long targetId = requiredId(targetUserId);
        Long operatorId = requiredId(operatorUserId);

        /*
         * Provider 再做一次真正的数据库角色校验。
         *
         * 不能只相信 Gateway 传来的权限。
         */
        UserDO operator =
                userMapper.selectById(operatorId);

        if (
                operator == null
                        || operator.getRole()
                        != UserRole.SUPER_ADMIN
        ) {
            throw new RpcBusinessException(
                    UserErrorCode.FORBIDDEN,
                    "只有超级管理员可以修改用户角色"
            );
        }

        /*
         * 禁止修改自己的角色。
         */
        if (targetId.equals(operatorId)) {
            throw new RpcBusinessException(
                    UserErrorCode.INVALID_PARAMETER,
                    "不能修改自己的角色"
            );
        }

        UserDO target =
                userMapper.selectById(targetId);

        if (target == null) {
            throw new RpcBusinessException(
                    UserErrorCode.USER_NOT_FOUND
            );
        }

        UserRole nextRole;

        try {
            nextRole = UserRole.valueOf(
                    role == null
                            ? ""
                            : role.trim()
                            .toUpperCase(Locale.ROOT)
            );
        } catch (Exception exception) {
            throw new RpcBusinessException(
                    UserErrorCode.INVALID_PARAMETER,
                    "非法用户角色"
            );
        }

        /*
         * 没有变化就不执行 UPDATE。
         */
        if (target.getRole() == nextRole) {
            return;
        }

        target.setRole(nextRole);

        userMapper.updateById(target);
    }

    private static UserItem toUserItem(
            Map<String, Object> row
    ) {
        return new UserItem(
                text(row, "userId"),
                text(row, "username"),
                nullableText(
                        row,
                        "phoneMasked"
                ),
                text(row, "role"),
                text(
                        row,
                        "realNameStatus"
                ),
                text(
                        row,
                        "accountStatus"
                ),
                nullableText(
                        row,
                        "artistId"
                ),
                text(row, "createdAt")
        );
    }

    private static String text(
            Map<String, Object> row,
            String key
    ) {
        String value = nullableText(
                row,
                key
        );

        return value == null ? "" : value;
    }

    private static String nullableText(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        return value == null
                ? null
                : value.toString();
    }

    private static Long requiredId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception exception) {
            throw new RpcBusinessException(
                    UserErrorCode.INVALID_PARAMETER,
                    "非法用户ID"
            );
        }
    }
}
