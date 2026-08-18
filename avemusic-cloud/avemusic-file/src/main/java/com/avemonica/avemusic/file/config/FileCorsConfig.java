package com.avemonica.avemusic.file.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FileCorsConfig
        implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(
            CorsRegistry registry
    ) {
        registry.addMapping(
                        "/upload/files/**"
                )
                .allowedOrigins(
                        "http://127.0.0.1:5173",
                        "http://localhost:5173",
                        "https://music.3s.tunnelfrp.com"
                )
                .allowedMethods(
                        "POST",
                        "OPTIONS"
                )
                .allowedHeaders(
                        "Content-Type",
                        "X-Upload-Ticket",
                        "Authorization"
                )
                .allowCredentials(false)
                .maxAge(3600);
    }
}