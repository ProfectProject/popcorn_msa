package com.popcorn.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {

        // 1) host/port/database/credentials
        RedisStandaloneConfiguration conf =
                new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort());
        conf.setDatabase(redisProperties.getDatabase());

        if (StringUtils.hasText(redisProperties.getUsername())) {
            conf.setUsername(redisProperties.getUsername());
        }

        if (StringUtils.hasText(redisProperties.getPassword())) {
            conf.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }

        // 2) client options from spring.data.redis.*
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientBuilder =
                LettuceClientConfiguration.builder();

        if (redisProperties.getTimeout() != null) {
            clientBuilder.commandTimeout(redisProperties.getTimeout());
        }
        if (redisProperties.getLettuce() != null && redisProperties.getLettuce().getShutdownTimeout() != null) {
            clientBuilder.shutdownTimeout(redisProperties.getLettuce().getShutdownTimeout());
        }
        if (redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled()) {
            clientBuilder.useSsl();
        }

        LettuceClientConfiguration clientConfig = clientBuilder.build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(conf, clientConfig);

        // keep previous factory options
        factory.setValidateConnection(true);
        factory.setShareNativeConnection(true);

        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
