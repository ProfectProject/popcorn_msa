package com.popcorn.order.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Order 서비스 Jackson 설정
 * - LocalDateTime 직렬화/역직렬화 지원
 * - IdempotencyService JSON 처리 오류 해결
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // JavaTimeModule 등록 (LocalDateTime 지원)
        mapper.registerModule(new JavaTimeModule());

        // LocalDateTime을 타임스탬프로 직렬화하지 않음
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 알 수 없는 속성 무시
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // null 값 제외
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // snake_case 사용
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        return mapper;
    }
}