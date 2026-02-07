package com.popcorn.store.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 🚀 극한 성능 최적화된 Redis 설정
 *
 * - Lettuce 커넥션 팩토리로 더 나은 성능 제공
 * - 커넥션 풀 최적화로 대기 시간 최소화
 * - 클라이언트 옵션 튜닝으로 처리량 극대화
 */
@Configuration
@Slf4j
public class OptimizedRedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Bean(destroyMethod = "shutdown")
    public ClientResources lettuceClientResources() {
        return DefaultClientResources.builder()
                .ioThreadPoolSize(8)  // IO 스레드 풀 크기 증가 🚀
                .computationThreadPoolSize(8)  // 계산 스레드 풀 크기 증가 🚀
                .build();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(ClientResources clientResources) {
        // 🚀 성능 최적화된 클라이언트 옵션
        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(true)
                .pingBeforeActivateConnection(false)  // 연결 활성화 전 ping 비활성화 (성능 향상)
                .build();

        // 🚀 연결 풀 최적화 설정
        org.apache.commons.pool2.impl.GenericObjectPoolConfig<io.lettuce.core.api.StatefulConnection<?, ?>> poolConfig =
                new org.apache.commons.pool2.impl.GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(50);     // 최대 연결 수
        poolConfig.setMaxIdle(30);      // 최대 유휴 연결
        poolConfig.setMinIdle(15);      // 최소 유휴 연결
        poolConfig.setMaxWait(Duration.ofMillis(500));  // 최대 대기 시간
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));  // 유휴 연결 정리 주기
        poolConfig.setMinEvictableIdleTime(Duration.ofMinutes(5));     // 최소 유휴 시간
        poolConfig.setTestOnBorrow(false);    // 성능 향상을 위해 비활성화
        poolConfig.setTestOnReturn(false);    // 성능 향상을 위해 비활성화
        poolConfig.setTestWhileIdle(true);    // 유휴 중에만 검사

        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .clientOptions(clientOptions)
                .clientResources(clientResources)
                .commandTimeout(Duration.ofMillis(10000))  // 명령 타임아웃 증가 (10초)
                .build();

        // Redis 서버 설정
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            serverConfig.setPassword(redisPassword);
        }
        serverConfig.setDatabase(redisDatabase);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.setValidateConnection(false);  // 🚀 연결 검증 비활성화로 성능 향상

        log.info("🚀 극한 성능 최적화된 Redis 연결 팩토리 생성 완료 - host: {}, port: {}, database: {}",
                 redisHost, redisPort, redisDatabase);

        return factory;
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> optimizedRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 🚀 성능 최적화된 시리얼라이저 설정
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setDefaultSerializer(jsonSerializer);
        template.setEnableTransactionSupport(false);  // 🚀 트랜잭션 비활성화로 성능 향상

        template.afterPropertiesSet();

        log.info("🚀 극한 성능 최적화된 RedisTemplate 생성 완료");
        return template;
    }

    @Bean
    public StringRedisTemplate optimizedStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.setEnableTransactionSupport(false);  // 🚀 트랜잭션 비활성화로 성능 향상

        log.info("🚀 극한 성능 최적화된 StringRedisTemplate 생성 완료");
        return template;
    }
}
