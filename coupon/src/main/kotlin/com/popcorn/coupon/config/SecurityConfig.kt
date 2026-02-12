package com.popcorn.coupon.config

import com.popcorn.coupon.security.JwtAuthenticationEntryPoint
import com.popcorn.coupon.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(jwtAuthenticationEntryPoint) }
            .authorizeHttpRequests { auth ->
                auth
                    // CORS preflight
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // Swagger/OpenAPI
                    .requestMatchers(
                        "/v3/api-docs",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-ui/index.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs/swagger-config",
                        "/swagger-resources/**",
                        "/webjars/**"
                    ).permitAll()

                    // Health check
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers("/error", "/error/**").permitAll()

                    // Public endpoints
                    .requestMatchers(HttpMethod.GET, "/api/v1/coupons/available/**").permitAll()

                    // User endpoints
                    .requestMatchers(HttpMethod.GET, "/api/v1/coupons/my/**").hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/coupons/download").hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/coupons/use").hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/coupons/usage-history/**").hasRole("CUSTOMER")

                    // Admin endpoints
                    .requestMatchers("/api/v1/admin/**").hasAnyRole("MANAGER", "OWNER")

                    // Internal service endpoints (SYSTEM role)
                    .requestMatchers("/api/v1/internal/**").hasAnyRole("MANAGER", "OWNER")

                    // Any other request requires authentication
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
