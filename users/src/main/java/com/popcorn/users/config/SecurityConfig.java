package com.popcorn.users.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.beans.factory.annotation.Value;
import com.popcorn.common.filter.HeaderAuthenticationFilter;

//import com.popcorn.users.auth.jwt.JwtFilter;
import com.popcorn.users.auth.jwt.JwtUtil;
import com.popcorn.users.auth.jwt.LoginFilter;
import com.popcorn.common.security.JwtAuthenticationFilter;
import com.popcorn.users.auth.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;
import java.util.List;
//TODO: 각 domian
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final AuthenticationConfiguration authenticationConfiguration;
	private final JwtUtil jwtUtil;
	private final HeaderAuthenticationFilter headerAuthenticationFilter;
	private final RefreshTokenService refreshTokenService;

	@Value("${jwt.expiration:3600000}")
	private long accessTokenExpirationMs;

	@Value("${jwt.refresh-expiration-ms:1209600000}")
	private long refreshTokenExpirationMs;

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder(){
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		AuthenticationManager authManager = authenticationManager(authenticationConfiguration);

		// ★ LoginFilter 비활성화 - AuthController 사용
        // LoginFilter loginFilter = new LoginFilter(authManager, jwtUtil,refreshTokenExpirationMs);
        // loginFilter.setFilterProcessesUrl("/api/users/v1/auth/login");

		http.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.authorizeHttpRequests(authz -> authz
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight 요청 허용
						.requestMatchers("/api/auth/login").permitAll()
						.requestMatchers("/api/users/v1/auth/login").permitAll() // 로그인 API - 공개
						.requestMatchers("/api/users/v1/auth/signup").permitAll() // 회원가입 API - 공개
						.requestMatchers(HttpMethod.POST, "/api/users/v1/auth/refresh", "/api/users/v1/auth/refresh/**", "/users/v1/auth/refresh", "/users/v1/auth/refresh/**").permitAll() // 리프레시 API - 리프레시 토큰 기반 인증
						.requestMatchers("/api/users/v1/users/signup").permitAll()
						.requestMatchers("/api/users/v1/users/**").permitAll()
						//.requestMatchers("/api/users/v1/users/**").hasAnyRole("CUSTOMER", "OWNER")


						// Swagger UI 관련 엔드포인트 허용 (Gateway 재작성 경로 포함)
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs", "/swagger-resources/**", "/webjars/**").permitAll()
						.requestMatchers("/api/users/v3/api-docs/**", "/api/users/v3/api-docs", "/api/users/swagger-ui/**", "/api/users/swagger-ui.html").permitAll()
						// Actuator 엔드포인트 허용
						.requestMatchers("/actuator/**").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated()
				)
				.formLogin(form -> form.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		// Gateway 인증 헤더 기반 필터
		http.addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		// ★ 로그인 필터 비활성화 - AuthController 사용
		// http.addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
			JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(List.of("*"));
		configuration.setAllowedMethods(List.of("GET", "POST","PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(false);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
