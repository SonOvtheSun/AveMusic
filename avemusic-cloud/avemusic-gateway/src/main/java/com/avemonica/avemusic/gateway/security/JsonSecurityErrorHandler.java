package com.avemonica.avemusic.gateway.security;

import com.avemonica.avemusic.common.web.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public final class JsonSecurityErrorHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public JsonSecurityErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 未登录、Access Token 无效或会话过期时调用。
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        write(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "AUTH-401",
                "登录状态无效或已经过期"
        );
    }

    /**
     * 已登录，但是没有访问权限时调用。
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        write(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "AUTH-403",
                "没有访问该资源的权限"
        );
    }

    /**
     * Redis 等认证基础设施不可用时调用。
     */
    public void serviceUnavailable(
            HttpServletResponse response
    ) throws IOException {
        write(
                response,
                HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "AUTH-503",
                "认证服务暂时不可用"
        );
    }

    private void write(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status);
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );
        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        jsonMapper.writeValue(
                response.getOutputStream(),
                ApiResult.failure(code, message)
        );
    }
}