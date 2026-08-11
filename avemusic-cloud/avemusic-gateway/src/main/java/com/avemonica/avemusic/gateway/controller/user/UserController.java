package com.avemonica.avemusic.gateway.controller.user;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.user.api.dto.UserManagementModels.UserItem;
import com.avemonica.avemusic.user.api.service.UserManagementService;
import com.avemonica.minirpc.spring.annotation.MiniRpcReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
}
