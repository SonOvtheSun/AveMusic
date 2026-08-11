package com.avemonica.avemusic.gateway.controller.user;

import com.avemonica.avemusic.common.security.UserRole;
import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.gateway.security.AuthSecurityProperties;
import com.avemonica.avemusic.gateway.security.JwtAuthenticationFilter;
import com.avemonica.avemusic.gateway.security.JwtTokenService;
import com.avemonica.avemusic.gateway.security.RedisSessionStore;
import com.avemonica.avemusic.user.api.dto.AuthModels;
import com.avemonica.avemusic.user.api.dto.AuthModels.AuthUser;
import com.avemonica.avemusic.user.api.service.UserService;
import com.avemonica.minirpc.spring.annotation.MiniRpcReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public final class AuthController {

    /**
     * MiniRPC 生成的 UserService 远程代理。
     */
    @MiniRpcReference
    private UserService userService;

    private final JwtTokenService tokenService;
    private final RedisSessionStore sessionStore;
    private final AuthSecurityProperties securityProperties;

    public AuthController(
            JwtTokenService tokenService,
            RedisSessionStore sessionStore,
            AuthSecurityProperties securityProperties
    ) {
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/sms/code")
    public ApiResult<Void> sendSmsCode(
            @Valid @RequestBody SendSmsCodeBody body
    ) {
        userService.sendSmsCode(
                new AuthModels.SendSmsCodeRequest(
                        body.phone(),
                        body.purpose()
                )
        );

        return ApiResult.success();
    }

    /**
     * 注册成功后直接创建登录会话。
     */
    @PostMapping("/register")
    public ApiResult<TokenResponse> register(
            @Valid @RequestBody RegisterBody body
    ) {
        AuthUser user = userService.register(
                new AuthModels.RegisterRequest(
                        body.username(),
                        body.phone(),
                        body.password(),
                        body.code()
                )
        );

        return ApiResult.success(
                createSession(user)
        );
    }

    /**
     * 用户名或手机号 + 密码登录。
     */
    @PostMapping("/login")
    public ApiResult<TokenResponse> login(
            @Valid @RequestBody PasswordLoginBody body
    ) {
        AuthUser user = userService.authenticate(
                new AuthModels.PasswordLoginRequest(
                        body.account(),
                        body.password()
                )
        );

        return ApiResult.success(
                createSession(user)
        );
    }

    /**
     * 手机号 + 验证码登录。
     */
    @PostMapping("/phone-login")
    public ApiResult<TokenResponse> phoneLogin(
            @Valid @RequestBody PhoneLoginBody body
    ) {
        AuthUser user = userService.loginByPhone(
                new AuthModels.PhoneLoginRequest(
                        body.phone(),
                        body.code()
                )
        );

        return ApiResult.success(
                createSession(user)
        );
    }

    /**
     * 刷新 Access Token 和 Refresh Token。
     */
    @PostMapping("/refresh")
    public ApiResult<TokenResponse> refresh(
            @Valid @RequestBody RefreshBody body
    ) {
        JwtTokenService.VerifiedToken refreshToken =
                tokenService.parseRefreshToken(
                        body.refreshToken()
                );

        RedisSessionStore.SessionData session =
                sessionStore.find(
                        refreshToken.sessionId()
                ).orElseThrow(
                        () -> new BadCredentialsException(
                                "登录会话不存在或已经过期"
                        )
                );

        if (!session.userId().equals(
                refreshToken.subject()
        )) {
            throw new BadCredentialsException(
                    "Refresh Token 与登录会话不匹配"
            );
        }

        if (refreshToken.role() != session.role()) {
            throw new BadCredentialsException(
                    "Refresh Token 角色与登录会话不匹配"
            );
        }

        /*
         * 使用 Redis Session 中的角色重新签发 JWT。
         */
        JwtTokenService.IssuedTokens newTokens =
                tokenService.issueTokens(
                        session.userId(),
                        refreshToken.sessionId(),
                        session.role(),
                        session.absoluteExpiresAt()
                );

        boolean rotated =
                sessionStore.rotateRefreshJti(
                        refreshToken.sessionId(),
                        refreshToken.jti(),
                        newTokens.refreshJti(),
                        session.absoluteExpiresAt()
                );

        if (!rotated) {
            throw new BadCredentialsException(
                    "Refresh Token 已失效或已经被使用"
            );
        }

        /*
         * 头像不属于认证 Session，因此刷新 Token 时返回 null。
         * 后续头像由 Profile 接口查询。
         */
        AuthUser user = new AuthUser(
                session.userId(),
                session.username(),
                null,
                session.role(),
                session.authorities()
        );

        return ApiResult.success(
                buildTokenResponse(
                        user,
                        newTokens
                )
        );
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(
            Authentication authentication
    ) {
        Object details =
                authentication.getDetails();

        if (details instanceof
                JwtAuthenticationFilter
                        .RequestAuthenticationDetails authDetails) {

            sessionStore.delete(
                    authDetails.sessionId()
            );
        }

        return ApiResult.success();
    }

    /**
     * 首页和前端权限判断通过该接口获取当前用户。
     */
    @GetMapping("/me")
    public ApiResult<AuthUser> currentUser(
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new BadCredentialsException(
                    "当前用户未登录"
            );
        }

        String username = null;
        UserRole role = UserRole.USER;

        Object details =
                authentication.getDetails();

        if (details instanceof
                JwtAuthenticationFilter
                        .RequestAuthenticationDetails authDetails) {

            username = authDetails.username();
            role = authDetails.role();
        }

        List<String> authorities =
                authentication.getAuthorities()
                        .stream()
                        .map(authority ->
                                authority.getAuthority()
                        )
                        .toList();

        return ApiResult.success(
                new AuthUser(
                        authentication.getName(),
                        username,
                        null,
                        role,
                        authorities
                )
        );
    }

    private TokenResponse createSession(
            AuthUser user
    ) {
        validateAuthenticationResult(user);

        String sessionId =
                UUID.randomUUID().toString();

        Instant absoluteExpiresAt =
                Instant.now().plus(
                        securityProperties.absoluteTimeout()
                );

        /*
         * role 会交给 JwtTokenService 写入 JWT Claim。
         */
        JwtTokenService.IssuedTokens tokens =
                tokenService.issueTokens(
                        user.userId(),
                        sessionId,
                        user.role(),
                        absoluteExpiresAt
                );

        /*
         * Redis Session 同时保存 role 和 authorities。
         */
        sessionStore.create(
                sessionId,
                user,
                tokens.refreshJti(),
                absoluteExpiresAt
        );

        return buildTokenResponse(
                user,
                tokens
        );
    }

    private static void validateAuthenticationResult(
            AuthUser user
    ) {
        if (user == null) {
            throw new IllegalStateException(
                    "User Provider 返回了 null"
            );
        }

        if (user.userId() == null
                || user.userId().isBlank()) {
            throw new IllegalStateException(
                    "User Provider 返回了空 userId"
            );
        }

        if (user.username() == null
                || user.username().isBlank()) {
            throw new IllegalStateException(
                    "User Provider 返回了空 username"
            );
        }

        if (user.role() == null) {
            throw new IllegalStateException(
                    "User Provider 返回了空 role"
            );
        }

        if (user.authorities() == null
                || user.authorities().isEmpty()) {
            throw new IllegalStateException(
                    "User Provider 返回了空 authorities"
            );
        }
    }

    private static TokenResponse buildTokenResponse(
            AuthUser user,
            JwtTokenService.IssuedTokens tokens
    ) {
        long accessExpiresIn = Math.max(
                0,
                Duration.between(
                        Instant.now(),
                        tokens.accessExpiresAt()
                ).toSeconds()
        );

        return new TokenResponse(
                user,
                "Bearer",
                tokens.accessToken(),
                tokens.refreshToken(),
                accessExpiresIn,
                tokens.absoluteExpiresAt()
                        .getEpochSecond()
        );
    }

    public record SendSmsCodeBody(
            @NotBlank(message = "手机号不能为空")
            @Pattern(
                    regexp = "^1[3-9]\\d{9}$",
                    message = "手机号格式不正确"
            )
            String phone,

            @NotNull(message = "验证码用途不能为空")
            AuthModels.SmsPurpose purpose
    ) {
    }

    public record RegisterBody(
            @NotBlank(message = "用户名不能为空")
            String username,

            @NotBlank(message = "手机号不能为空")
            @Pattern(
                    regexp = "^1[3-9]\\d{9}$",
                    message = "手机号格式不正确"
            )
            String phone,

            @NotBlank(message = "密码不能为空")
            @Size(
                    min = 8,
                    max = 64,
                    message = "密码长度必须为8到64位"
            )
            String password,

            @NotBlank(message = "验证码不能为空")
            @Pattern(
                    regexp = "^\\d{6}$",
                    message = "验证码必须为6位数字"
            )
            String code
    ) {
    }

    public record PasswordLoginBody(
            @NotBlank(message = "账号不能为空")
            String account,

            @NotBlank(message = "密码不能为空")
            String password
    ) {
    }

    public record PhoneLoginBody(
            @NotBlank(message = "手机号不能为空")
            @Pattern(
                    regexp = "^1[3-9]\\d{9}$",
                    message = "手机号格式不正确"
            )
            String phone,

            @NotBlank(message = "验证码不能为空")
            @Pattern(
                    regexp = "^\\d{6}$",
                    message = "验证码必须为6位数字"
            )
            String code
    ) {
    }

    public record RefreshBody(
            @NotBlank(message = "Refresh Token 不能为空")
            String refreshToken
    ) {
    }

    /**
     * 前端登录、注册和刷新后都会从 user.role 读取角色。
     */
    public record TokenResponse(
            AuthUser user,
            String tokenType,
            String accessToken,
            String refreshToken,
            long accessExpiresIn,
            long absoluteExpiresAt
    ) {
    }
}
