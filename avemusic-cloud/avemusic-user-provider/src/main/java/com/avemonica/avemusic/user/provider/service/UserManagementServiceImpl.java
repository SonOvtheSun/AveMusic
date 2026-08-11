package com.avemonica.avemusic.user.provider.service;

import com.avemonica.avemusic.user.api.dto.UserManagementModels.UserItem;
import com.avemonica.avemusic.user.api.service.UserManagementService;
import com.avemonica.avemusic.user.provider.mapper.UserManagementMapper;
import com.avemonica.minirpc.spring.annotation.MiniRpcService;

import java.util.List;
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

    public UserManagementServiceImpl(
            UserManagementMapper mapper
    ) {
        this.mapper = mapper;
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
}
