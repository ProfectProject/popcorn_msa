package com.popcorn.store.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.popcorn.common.filter.HeaderAuthenticationFilter;
import com.popcorn.common.security.JwtAuthenticationFilter;

/**
 * Filter beans configuration for stores service
 * Explicitly defines required filters from common-lib
 */
@Configuration
public class FilterConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}