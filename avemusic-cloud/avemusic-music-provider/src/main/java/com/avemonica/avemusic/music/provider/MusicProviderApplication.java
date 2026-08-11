package com.avemonica.avemusic.music.provider;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages =
                "com.avemonica.avemusic.music.provider"
)
@MapperScan(
        "com.avemonica.avemusic.music.provider.mapper"
)
public class MusicProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                MusicProviderApplication.class,
                args
        );
    }
}
