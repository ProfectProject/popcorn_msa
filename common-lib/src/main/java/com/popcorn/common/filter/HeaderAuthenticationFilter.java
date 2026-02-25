package com.popcorn.common.filter;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.popcorn.common.dto.PassportPayload;
import com.popcorn.common.dto.PassportUser;
import com.popcorn.common.util.PassportVerifier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(HeaderAuthenticationFilter.class);

    private final String passportSecret;

    public HeaderAuthenticationFilter() {
        String secret = System.getenv("PASSPORT_SECRET");
        this.passportSecret = (secret == null || secret.isBlank()) ? "passport-shared-secret" : secret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();
        if (uri == null) return false;
        if ("/".equals(uri) || "/error".equals(uri) || "/favicon.ico".equals(uri)) return true;
        if (uri.startsWith("/actuator")) return true;
        if (uri.startsWith("/api/pay/v")) return true;
        if (isPublicApiPath(uri)) return true;
        return uri.startsWith("/api/users/v1/auth/refresh") || uri.startsWith("/users/v1/auth/refresh");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // 기존 인증이 없을 때만 헤더에서 인증 정보 추출
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String internalServiceHeader = request.getHeader("X-Internal-Service");
            String internalCallHeader = request.getHeader("X-Internal-Call");
            String passportHeader = request.getHeader("X-Passport");

            log.debug("HeaderAuthenticationFilter uri={} hasPassport={} internalCall={} internalService={}",
                    uri, passportHeader != null, internalCallHeader, internalServiceHeader);

            if (passportHeader != null) {
                try {
                    PassportPayload payload =
                            PassportVerifier.verify(passportHeader, passportSecret);

                    PassportUser user = payload.user();

                    PassportPrincipal principal =
                            new PassportPrincipal(
                                    Long.valueOf(user.id()),
                                    user.role(),
                                    user.email()
                            );

                    String authority = "ROLE_" + user.role();

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    List.of(new SimpleGrantedAuthority(authority))
                            );

                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Passport auth success userId={}", user.id());
                } catch (Exception e) {
                    log.warn("Passport auth failed: {}", e.getMessage());
                }

                filterChain.doFilter(request, response);
                return;
            }

            // 내부 서비스 호출인 경우 시스템 인증으로 처리
            if ("true".equals(internalCallHeader) && internalServiceHeader != null) {
                PassportPrincipal systemPrincipal = new PassportPrincipal(0L, "SYSTEM", internalServiceHeader + "@internal");
                UsernamePasswordAuthenticationToken systemAuth =
                        new UsernamePasswordAuthenticationToken(
                                systemPrincipal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))
                        );
                SecurityContextHolder.getContext().setAuthentication(systemAuth);
                log.debug("Internal service auth success service={}", internalServiceHeader);
            } 
            else {
                log.debug("Missing auth headers uri={}", uri);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 공개 API 경로인지 확인
     * SecurityConfig에서 permitAll로 설정된 경로들과 동일하게 유지
     */
    private boolean isPublicApiPath(String uri) {
        if (uri == null) return false;

        // Store service public APIs
        if (uri.startsWith("/api/stores/v1/popups")) return true;

        // User service public APIs
        if (uri.startsWith("/api/users/v1/auth/login")) return true;
        if (uri.startsWith("/api/users/v1/auth/signup")) return true;
        if (uri.startsWith("/api/users/v1/auth/refresh") || uri.startsWith("/users/v1/auth/refresh")) return true;
        // refresh API는 공개 처리 (SecurityConfig와 일치)

        // Swagger and documentation
        if (uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs")) return true;

        return false;
    }

    /*private String resolveRoleHeader(HttpServletRequest request) {
        String roleHeader = request.getHeader("X-User-Role");
        if (roleHeader != null) {
            return roleHeader;
        }
        return request.getHeader("X-Role");
    }*/
}
