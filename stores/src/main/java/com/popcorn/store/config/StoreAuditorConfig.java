package com.popcorn.store.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class StoreAuditorConfig {

	@Bean
	public AuditorAware<Long> auditorAware() {
		return () -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

			if (authentication == null || !authentication.isAuthenticated()) {
				return Optional.empty();
			}

			if (authentication.getPrincipal() instanceof String && "anonymousUser".equals(authentication.getPrincipal())) {
				return Optional.empty();
			}

			Object principal = authentication.getPrincipal();

			try {
				var userIdMethod = principal.getClass().getMethod("getUserId");
				Object userId = userIdMethod.invoke(principal);
				return Optional.ofNullable((Long) userId);
			} catch (Exception e) {
				return Optional.empty();
			}
		};
	}
}
