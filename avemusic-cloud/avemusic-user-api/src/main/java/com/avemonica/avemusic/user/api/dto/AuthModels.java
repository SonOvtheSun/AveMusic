package com.avemonica.avemusic.user.api.dto;

import com.avemonica.avemusic.common.security.UserRole;

import java.io.Serializable;
import java.util.List;

public final class AuthModels {

    private AuthModels() {
    }

    public enum SmsPurpose {
        REGISTER,
        LOGIN
    }

    public record SendSmsCodeRequest(
            String phone,
            SmsPurpose purpose
    ) implements Serializable {
    }

    public record RegisterRequest(
            String username,
            String phone,
            String password,
            String code
    ) implements Serializable {
    }

    public record PasswordLoginRequest(
            String account,
            String password
    ) implements Serializable {
    }

    public record PhoneLoginRequest(
            String phone,
            String code
    ) implements Serializable {
    }

    public record AuthUser(
            String userId,
            String username,
            String avatarUrl,
            UserRole role,
            List<String> authorities
    ) implements Serializable {

        public AuthUser {
            role = role == null
                    ? UserRole.USER
                    : role;

            authorities = authorities == null
                    || authorities.isEmpty()
                    ? List.of(
                            role.authority()
                    )
                    : List.copyOf(
                            authorities
                    );
        }
    }
}
