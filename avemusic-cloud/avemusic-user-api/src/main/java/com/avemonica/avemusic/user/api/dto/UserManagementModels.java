package com.avemonica.avemusic.user.api.dto;

import java.io.Serializable;

public final class UserManagementModels {

    private UserManagementModels() {
    }

    public record UserItem(
            String id,
            String username,
            String phoneMasked,
            String role,
            String realNameStatus,
            String accountStatus,
            String artistId,
            String createdAt
    ) implements Serializable {
    }
}
