package com.avemonica.avemusic.gateway.security;

import com.avemonica.avemusic.music.api.dto.MusicManagementModels.Actor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class ManagementActorResolver {

    public Actor resolve(
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication
                .isAuthenticated()) {
            throw new BadCredentialsException(
                    "当前用户未登录"
            );
        }

        Object details =
                authentication.getDetails();

        if (details instanceof
                JwtAuthenticationFilter
                        .RequestAuthenticationDetails authDetails) {

            return new Actor(
                    authentication.getName(),
                    authDetails.role().name()
            );
        }

        throw new BadCredentialsException(
                "登录上下文缺少角色信息"
        );
    }
}
