package com.avemonica.avemusic.user.provider;

import com.avemonica.avemusic.user.provider.security.IdentityCryptoProperties;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@MapperScan("com.avemonica.avemusic.user.provider.mapper")
@EnableConfigurationProperties(
        IdentityCryptoProperties.class
)
public class UserProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                UserProviderApplication.class,
                args
        );
    }

    /**
     * 默认使用 bcrypt，并在结果前保存 {bcrypt} 前缀。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    /**
     * 防止误执行无条件 UPDATE 或 DELETE。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor =
                new MybatisPlusInterceptor();

        interceptor.addInnerInterceptor(
                new BlockAttackInnerInterceptor()
        );

        return interceptor;
    }
}