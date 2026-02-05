package com.popcorn.common.filter;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${passport.secret}")
    private String passportSecret;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        // Allow actuator endpoints without passport/internal headers
        if (uri != null && uri.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 기존 인증이 없을 때만 헤더에서 인증 정보 추출
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String internalServiceHeader = request.getHeader("X-Internal-Service");
            String internalCallHeader = request.getHeader("X-Internal-Call");
            String passportHeader = request.getHeader("X-Passport");

            // 디버그 로깅 추가
            System.out.println("🔍 HeaderAuthenticationFilter - URI: " + uri);
            System.out.println("🔍 X-Internal-Service: " + internalServiceHeader);
            System.out.println("🔍 X-Internal-Call: " + internalCallHeader);
            System.out.println("🔍 X-Passport: " + passportHeader);
            System.out.println("🔍 passport.secret: " + passportSecret);

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

                    System.out.println("✅ Passport 인증 성공 - userId=" + user.id());
                } catch (Exception e) {
                    System.out.println("❌ Passport 인증 실패: " + e.getMessage());
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
                System.out.println("✅ 내부 서비스 호출 인증 성공 - 서비스: " + internalServiceHeader);
            } 
            else {
                System.out.println("❌ 인증 헤더 누락 - Gateway 헤더 또는 내부 호출 헤더가 필요함");
            }
        }

        filterChain.doFilter(request, response);
    }

    /*private String resolveRoleHeader(HttpServletRequest request) {
        String roleHeader = request.getHeader("X-User-Role");
        if (roleHeader != null) {
            return roleHeader;
        }
        return request.getHeader("X-Role");
    }*/
}
