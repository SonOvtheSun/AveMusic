package com.avemonica.avemusic.gateway.security;

import com.avemonica.avemusic.common.security.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public final class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String AUTHORIZATION =
            "Authorization";

    private static final String BEARER_PREFIX =
            "Bearer ";

    private final JwtTokenService tokenService;
    private final RedisSessionStore sessionStore;
    private final JsonSecurityErrorHandler errorHandler;

    public JwtAuthenticationFilter(
            JwtTokenService tokenService,
            RedisSessionStore sessionStore,
            JsonSecurityErrorHandler errorHandler
    ) {
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization =
                request.getHeader(
                        AUTHORIZATION
                );

        if (authorization == null
                || authorization.isBlank()) {
            filterChain.doFilter(
                    request,
                    response
            );
            return;
        }

        try {
            authenticate(authorization);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();

            errorHandler.commence(
                    request,
                    response,
                    exception
            );
            return;
        } catch (DataAccessException exception) {
            SecurityContextHolder.clearContext();
            errorHandler.serviceUnavailable(response);
            return;
        }

        filterChain.doFilter(
                request,
                response
        );
    }

    private void authenticate(
            String authorization
    ) {
        if (!authorization.startsWith(
                BEARER_PREFIX
        )) {
            throw new BadCredentialsException(
                    "Authorization header must use Bearer"
            );
        }

        String rawToken = authorization
                .substring(
                        BEARER_PREFIX.length()
                )
                .trim();

        JwtTokenService.VerifiedToken token =
                tokenService.parseAccessToken(
                        rawToken
                );

        RedisSessionStore.SessionData session =
                sessionStore.findAndTouch(
                        token.sessionId()
                ).orElseThrow(
                        () -> new BadCredentialsException(
                                "登录会话不存在或已经过期"
                        )
                );

        if (!session.userId().equals(
                token.subject()
        )) {
            throw new BadCredentialsException(
                    "令牌用户与登录会话不匹配"
            );
        }

        /*
         * JWT 与 Redis 必须保存同一角色。
         * 管理页面的 Gateway Controller 会从这里取得角色。
         */
        if (token.role() != session.role()) {
            throw new BadCredentialsException(
                    "令牌角色与登录会话不匹配"
            );
        }

        List<SimpleGrantedAuthority> authorities =
                session.authorities()
                        .stream()
                        .map(
                                SimpleGrantedAuthority
                                        ::new
                        )
                        .toList();

        UsernamePasswordAuthenticationToken
                authentication =
                new UsernamePasswordAuthenticationToken(
                        session.userId(),
                        null,
                        authorities
                );

        authentication.setDetails(
                new RequestAuthenticationDetails(
                        token.sessionId(),
                        session.username(),
                        session.role()
                )
        );

        SecurityContext context =
                SecurityContextHolder
                        .createEmptyContext();

        context.setAuthentication(
                authentication
        );

        SecurityContextHolder.setContext(
                context
        );
    }

    public record RequestAuthenticationDetails(
            String sessionId,
            String username,
            UserRole role
    ) {
    }
}
