package com.popcorn.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.popcorn.common.filter.HeaderAuthenticationFilter;
import com.popcorn.common.security.JwtAuthenticationFilter;

import java.util.Arrays;

/**
 * Order 서비스 보안 설정
 *
 * [JWT 인증 설정]
 * - JWT 토큰 기반 인증 활성화
 * - CORS 허용으로 브라우저에서 API 호출 가능
 * - Swagger/Actuator는 공개
 * - OPTIONS 요청(CORS preflight) 허용
 * - API 경로는 JWT 인증 필요
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Method-level security 활성화
public class SecurityConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // REST API이므로 CSRF 비활성화
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))  // CORS 설정 적용

            // 🔑 JWT 기반 인증 설정
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(authz -> authz
                // 🔓 CORS preflight 요청 허용 (매우 중요!)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 🔓 Swagger 관련 경로 허용
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                // 🔓 Actuator 허용
                .requestMatchers("/actuator/**").permitAll()

                // 🔓 MSA 간 내부 통신용 API (인증 불필요)
                .requestMatchers(HttpMethod.GET, "/api/orders/v1/*/internal").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/orders/v1/*/status").permitAll()

                // 🔒 나머지 모든 API 경로는 JWT 인증 필요
                .requestMatchers("/api/orders/v1/**").authenticated()

                // 🔒 나머지는 인증 필요
                .anyRequest().authenticated()
            )

            // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
            .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())  // H2 Console 허용 (최신 방식)
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig.disable()) // 개발용 HSTS 비활성화
            );

        return http.build();
    }

    /**
     * 🌐 CORS 설정
     * 브라우저에서 다른 도메인의 API 호출을 허용
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 Origin (개발용)
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",     // 로컬 개발 서버
            "http://127.0.0.1:*",     // 로컬호스트
            "https://api.goormpopcorn.shop",
            "https://goormpopcorn.shop",
            "https://www.goormpopcorn.shop",
            "https://*.vercel.app",   // Vercel 배포
            "https://*.netlify.app"   // Netlify 배포
        ));

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // 허용할 헤더
        configuration.setAllowedHeaders(Arrays.asList(
            "*"  // 모든 헤더 허용 (Authorization 포함)
        ));

        // 자격 증명(쿠키, Authorization 헤더) 허용
        configuration.setAllowCredentials(true);

        // Preflight 요청 캐시 시간 (초)
        configuration.setMaxAge(3600L);

        // 브라우저에 노출할 응답 헤더
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Total-Count"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
