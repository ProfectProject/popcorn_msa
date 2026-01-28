package com.popcorn.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Order 서비스 전용 Redis 설정
 * Common-lib의 설정을 오버라이드하여 타임아웃 문제 해결
 */
@Configuration
@Slf4j
public class OrderRedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:10000ms}")
    private Duration timeout;

    @Value("${spring.data.redis.command-timeout:10000ms}")
    private Duration commandTimeout;

    /**
     * Redis 연결 팩토리 오버라이드 (타임아웃 해결)
     */
    @Bean
    @Primary  // Common-lib의 설정을 오버라이드
    public RedisConnectionFactory orderRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisDatabase);

        // 10초 타임아웃 설정
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(10))  // 명령 타임아웃 10초 (300ms 해결!)
                .shutdownTimeout(Duration.ofSeconds(2))   // 셧다운 타임아웃
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();

        log.info("✅ Order 서비스 Redis 연결 팩토리 생성 - 타임아웃: 10초 (300ms 문제 해결)");
        return factory;
    }
}