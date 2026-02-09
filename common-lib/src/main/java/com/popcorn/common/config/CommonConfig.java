package com.popcorn.common.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Jackson ObjectMapper 관련 import 제거 (각 서비스에서 개별 관리)

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@ComponentScan(basePackages = {
		"com.popcorn.common"
})
public class CommonConfig {

	// ObjectMapper 빈은 각 서비스의 JacksonConfig에서 정의하도록 변경
	// 중복 빈 정의 방지를 위해 CommonConfig에서는 제거

	@Bean
	public AuditorAware<Long> auditorAware() {
		return () -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

			if (authentication == null || !authentication.isAuthenticated()) {
				return Optional.empty();
			}

			// AnonymousAuthenticationToken인 경우 무시
			if (authentication.getPrincipal() instanceof String && "anonymousUser".equals(authentication.getPrincipal())) {
				return Optional.empty();
			}

			// 📚 Common-Lib에서는 일반적인 UserDetails 처리
			// 구체적인 UserDetails 구현체는 애플리케이션에서 처리
			Object principal = authentication.getPrincipal();

			// 리플렉션을 사용하여 getUserId() 메서드가 있는지 확인하고 호출
			try {
				var userIdMethod = principal.getClass().getMethod("getUserId");
				Object userId = userIdMethod.invoke(principal);
				return Optional.ofNullable((Long) userId);
			} catch (Exception e) {
				// getUserId 메서드가 없거나 호출 실패시 기본 처리
				return Optional.empty();
			}
		};
	}

}
