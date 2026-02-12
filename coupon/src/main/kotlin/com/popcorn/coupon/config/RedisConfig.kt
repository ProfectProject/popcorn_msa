package com.popcorn.coupon.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.resource.DefaultClientResources
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
class RedisConfig(
    private val redisProperties: RedisProperties
) {
    private val logger = KotlinLogging.logger {}

    @Value("\${coupon.redis.key-prefix:coupon:}")
    private lateinit var keyPrefix: String

    @Value("\${coupon.redis.lock-timeout:5000}")
    private var lockTimeout: Long = 5000

    /**
     * Redis 연결 팩토리 - 향상된 연결 설정
     */
    @Bean
    @Primary
    fun redisConnectionFactory(): RedisConnectionFactory {
        logger.info { "🔧 Redis 연결 설정 초기화: host=${redisProperties.host}, port=${redisProperties.port}" }

        // Redis 서버 설정
        val redisConfig = RedisStandaloneConfiguration().apply {
            hostName = redisProperties.host
            port = redisProperties.port
            if (!redisProperties.password.isNullOrBlank()) {
                setPassword(redisProperties.password)
            }
        }

        // 클라이언트 옵션 설정 - 연결 안정성 강화
        val clientOptions = ClientOptions.builder()
            .autoReconnect(true)
            .socketOptions(
                SocketOptions.builder()
                    .connectTimeout(Duration.ofMillis(redisProperties.connectTimeout?.toMillis() ?: 10000))
                    .keepAlive(true)
                    .build()
            )
            .timeoutOptions(
                TimeoutOptions.builder()
                    .fixedTimeout(Duration.ofMillis(redisProperties.timeout?.toMillis() ?: 5000))
                    .build()
            )
            .build()

        // Lettuce 클라이언트 설정
        val clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(redisProperties.timeout?.toMillis() ?: 5000))
            .clientOptions(clientOptions)
            .clientResources(
                DefaultClientResources.builder()
                    .ioThreadPoolSize(4)
                    .computationThreadPoolSize(4)
                    .build()
            )

        return LettuceConnectionFactory(redisConfig, clientConfig.build()).also {
            it.validateConnection = true
            it.setShareNativeConnection(false) // 멀티스레드 안전성
            logger.info { "✅ Redis 연결 팩토리 생성 완료" }
        }
    }

    /**
     * Redis Template - 캐시용
     */
    @Bean
    @Primary
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            val jackson2JsonRedisSerializer = GenericJackson2JsonRedisSerializer()
            valueSerializer = jackson2JsonRedisSerializer
            hashValueSerializer = jackson2JsonRedisSerializer
            setDefaultSerializer(jackson2JsonRedisSerializer)
            setEnableTransactionSupport(true)
            afterPropertiesSet()
            logger.info { "✅ Redis Template 설정 완료" }
        }
    }

    fun getKeyWithPrefix(key: String): String = "$keyPrefix$key"
    fun getLockTimeout(): Duration = Duration.ofMillis(lockTimeout)
}
