package com.popcorn.checkIns.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * CheckIns 서비스용 RestTemplate 설정
 * Order 서비스와의 HTTP 통신을 위한 설정
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 마이크로서비스 간 HTTP 통신용 RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}