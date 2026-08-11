package com.avemonica.avemusic.gateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(
        AuthSecurityProperties.class
)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            JsonSecurityErrorHandler errorHandler
    ) throws Exception {

        http
                .csrf(
                        AbstractHttpConfigurer
                                ::disable
                )

                .cors(
                        Customizer
                                .withDefaults()
                )

                .sessionManagement(
                        session ->
                                session
                                        .sessionCreationPolicy(
                                                SessionCreationPolicy
                                                        .STATELESS
                                        )
                )

                .requestCache(
                        AbstractHttpConfigurer
                                ::disable
                )

                .formLogin(
                        AbstractHttpConfigurer
                                ::disable
                )

                .httpBasic(
                        AbstractHttpConfigurer
                                ::disable
                )

                .logout(
                        AbstractHttpConfigurer
                                ::disable
                )

                .exceptionHandling(
                        exception ->
                                exception
                                        .authenticationEntryPoint(
                                                errorHandler
                                        )
                                        .accessDeniedHandler(
                                                errorHandler
                                        )
                )

                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        /*
                                         * CORS 预检。
                                         */
                                        .requestMatchers(
                                                HttpMethod.OPTIONS,
                                                "/**"
                                        )
                                        .permitAll()

                                        /*
                                         * 认证公开接口。
                                         */
                                        .requestMatchers(
                                                "/api/auth/csrf",
                                                "/api/auth/login",
                                                "/api/auth/register",
                                                "/api/auth/phone-login",
                                                "/api/auth/sms/code",
                                                "/api/auth/refresh",
                                                "/error"
                                        )
                                        .permitAll()

                                        /*
                                         * C端公开读取接口。
                                         */
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/music/songs/home",
                                                "/api/music/artists/home",
                                                "/api/music/artists/detail/**",
                                                "/api/music/albums/detail/**",
                                                "/api/playlists/ranking"
                                        )
                                        .permitAll()

                                        /*
                                         * 服务端 playSession。
                                         * 未登录用户同样允许播放公开歌曲。
                                         */
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/music/songs/*/play-session",
                                                "/api/music/songs/play-session/*/heartbeat"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.DELETE,
                                                "/api/music/songs/play-session/*"
                                        )
                                        .permitAll()

                                        /*
                                         * 歌单、管理中心、上传 Ticket 等其他接口
                                         * 仍然要求登录。
                                         */
                                        .anyRequest()
                                        .authenticated()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource
    corsConfigurationSource(
            AuthSecurityProperties properties
    ) {
        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                properties.allowedOrigins()
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",

                        /*
                         * playSession 使用。
                         */
                        "X-Playback-Client"
                )
        );

        configuration.setAllowCredentials(
                false
        );

        configuration.setMaxAge(
                3600L
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/api/**",
                configuration
        );

        return source;
    }
}
