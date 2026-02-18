package com.popcorn.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * 표준 Jackson 설정
 * - LocalDateTime 직렬화 문제 해결
 * - Spring Boot 호환성 보장
 * - 모든 서비스에서 동일한 JSON 처리
 */
@Configuration
public class JacksonConfig {

    /**
     * 표준 ObjectMapper - 모든 서비스에서 동일한 설정 사용
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
            // Java 8 Time API 모듈 등록 (LocalDateTime 직렬화)
            .modules(new JavaTimeModule())

            // 타임존 설정
            .timeZone(TimeZone.getTimeZone("Asia/Seoul"))

            // 날짜 형식 설정
            .dateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))

            // LocalDateTime 형식 설정
            .simpleDateFormat("yyyy-MM-dd HH:mm:ss")

            // 직렬화 설정
            .featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,  // ISO-8601 형식 사용
                SerializationFeature.FAIL_ON_EMPTY_BEANS
            )

            // 역직렬화 설정
            .featuresToDisable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES  // 알 수 없는 필드 무시
            )
            .featuresToEnable(
                DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
            )

            .build();
    }

    /**
     * Redis 전용 ObjectMapper - 타입 정보 포함
     * ⚠️ Redis에서만 사용! copy() 대신 별도 생성
     */
    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        return Jackson2ObjectMapperBuilder.json()
            // 동일한 기본 설정 적용
            .modules(new JavaTimeModule())
            .timeZone(TimeZone.getTimeZone("Asia/Seoul"))
            .dateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
            .featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                SerializationFeature.FAIL_ON_EMPTY_BEANS,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
            )
            .featuresToEnable(
                DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
            )

            // Redis 전용: 다형성 타입 처리 (Object 직렬화)
            .build()
            .activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType(Object.class)
                    .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
            );
    }
}