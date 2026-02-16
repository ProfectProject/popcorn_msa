package com.popcorn.coupon.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Configuration
class WebClientConfig {

    @Value("\${user.service.base-url:http://localhost:8082}")
    private lateinit var userServiceBaseUrl: String

    @Bean
    fun userServiceWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(userServiceBaseUrl)
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
            }
            .build()
    }
}