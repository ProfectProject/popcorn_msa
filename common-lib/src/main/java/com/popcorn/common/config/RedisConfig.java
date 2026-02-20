package com.popcorn.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 표준 Redis 설정
 * - ObjectMapper.copy() 문제 해결
 * - JavaTimeModule 설정 보장
 * - 연결 풀 최적화
 */
@Configuration
public class RedisConfig {

    /**
     * Redis 연결 팩토리 - 연결 풀 최적화
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
            redisProperties.getHost(),
            redisProperties.getPort()
        );

        // 연결 검증 활성화
        factory.setValidateConnection(true);

        // 공유 네이티브 연결 사용
        factory.setShareNativeConnection(true);

        return factory;
    }

    /**
     * 표준 RedisTemplate
     * ⚠️ copy() 대신 별도 생성된 redisObjectMapper 사용
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
        RedisConnectionFactory connectionFactory,
        @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper  // ✅ 별도 Bean 주입
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String 직렬화 (키)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // JSON 직렬화 (값) - JavaTimeModule 설정 보장
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // 기본 직렬화 설정
        template.setDefaultSerializer(jsonSerializer);
        template.setEnableDefaultSerializer(true);

        template.afterPropertiesSet();
        return template;
    }
}
