package com.avemonica.avemusic.gateway.controller.user;

import com.avemonica.avemusic.common.security.UserRole;
import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.gateway.security.RedisSessionStore;
import com.avemonica.avemusic.user.api.dto.UserManagementModels.UserItem;
import com.avemonica.avemusic.user.api.service.UserManagementService;
import com.avemonica.minirpc.spring.annotation.MiniRpcReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @MiniRpcReference(
            group = "user",
            version = "1.0.0"
    )
    private UserManagementService
            userManagementService;

    private final RedisSessionStore
            sessionStore;

    public UserController(
            RedisSessionStore sessionStore
    ) {
        this.sessionStore = sessionStore;
    }

    /**
     * SUPER_ADMIN 和 OPERATOR 都可以查看用户列表。
     * 实际权限仍沿用现有 sys::admin / user::manage。
     */
    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'user::manage'
            )
            """)
    @GetMapping("/manage")
    public ApiResult<List<UserItem>>
    managedUsers() {
        return ApiResult.success(
                userManagementService.listUsers()
        );
    }

    /**
     * 修改用户角色。
     *
     * Gateway 先通过 sys::admin 做第一层拦截；
     * Provider 再根据 operatorUserId 查询数据库，
     * 确认实际角色必须是 SUPER_ADMIN。
     */
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('sys::admin')")
    public ApiResult<Void> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleBody body,
            Authentication authentication
    ) {
        String operatorUserId =
                currentUserId(authentication);

        userManagementService.updateUserRole(
                userId,
                body.role().name(),
                operatorUserId
        );

        /*
         * 数据库角色修改成功后，
         * 立即让目标用户全部登录会话失效。
         *
         * 旧 JWT 即使仍未过期，
         * JwtAuthenticationFilter 也会因为 Redis Session
         * 已不存在而要求用户重新登录。
         */
        sessionStore.deleteAllByUserId(userId);

        return ApiResult.success();
    }

    /**
     * JwtAuthenticationFilter 当前把 userId
     * 放在 Authentication principal/name 中。
     *
     * 不从前端请求体接收 operatorUserId，
     * 防止客户端伪造管理员身份。
     */
    private static String currentUserId(
            Authentication authentication
    ) {
        if (
                authentication == null
                        || !authentication.isAuthenticated()
                        || authentication.getName() == null
                        || authentication.getName().isBlank()
        ) {
            throw new AccessDeniedException(
                    "无权修改用户角色"
            );
        }

        return authentication.getName();
    }

    public record UpdateUserRoleBody(
            @NotNull(
                    message = "用户角色不能为空"
            )
            UserRole role
    ) {
    }
}