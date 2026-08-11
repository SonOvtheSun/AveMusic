package com.avemonica.avemusic.file;

import com.avemonica.avemusic.file.config.FileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@SpringBootApplication(
        scanBasePackages =
                "com.avemonica.avemusic.file"
)
@EnableConfigurationProperties(
        FileStorageProperties.class
)
public class FileServiceApplication
        implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    public FileServiceApplication(
            FileStorageProperties properties
    ) {
        this.properties = properties;
    }

    public static void main(String[] args) {
        SpringApplication.run(
                FileServiceApplication.class,
                args
        );
    }

    @Override
    public void addResourceHandlers(
            ResourceHandlerRegistry registry
    ) {
        String location =
                properties.root()
                        .toUri()
                        .toString();

        if (!location.endsWith("/")) {
            location += "/";
        }

        registry
                .addResourceHandler("/files/**")
                .addResourceLocations(location)
                .setCacheControl(
                        CacheControl
                                .maxAge(
                                        Duration.ofDays(30)
                                )
                                .cachePublic()
                );
    }

    @Override
    public void addCorsMappings(
            CorsRegistry registry
    ) {
        registry
                .addMapping(
                        "/upload/files/**"
                )
                .allowedOrigins(
                        "http://127.0.0.1:5173",
                        "http://localhost:5173"
                )
                .allowedMethods(
                        "POST",
                        "OPTIONS"
                )
                .allowedHeaders(
                        "Content-Type",
                        "X-Upload-Ticket"
                )
                .maxAge(3600);
    }
}
