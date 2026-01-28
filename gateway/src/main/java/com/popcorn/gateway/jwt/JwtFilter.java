package com.popcorn.gateway.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.gateway.dto.PassportEnvelope;
import com.popcorn.gateway.dto.PassportPayload;
import com.popcorn.gateway.dto.PassportUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter implements GlobalFilter, Ordered{
    private static final List<String> EXCLUDE_PATH_PREFIXES = List.of(
            // 인증 관련 경로
            "/api/users/v1/auth/login",
            "/api/users/v1/auth/refresh",
            "/api/users/v1/auth/",
            "/api/users/v1/users/signup",
            "/api/pay/v1/payments/decode",
            "/api/pay/v1/payments/refresh",
            "/api/pay/v1/payments/confirm-async",
            "/api/pay/v1/payments/orders",
            "/api/stores/v1/popups",

            // Swagger/OpenAPI 관련 경로 (전체) - 포괄적 설정
            "/v3/api-docs",           // 모든 서비스 OpenAPI 문서
            "/swagger-ui",            // Swagger UI 리소스
            "/webjars",               // Swagger UI 의존성 (CSS, JS 등)
            "/openapi",               // Gateway OpenAPI 프록시
            "/swagger-resources",     // Swagger 리소스
            "/configuration",         // Swagger 설정

            // 각 서비스별 Swagger 경로 (모든 하위 경로 포함)
            "/api/users/v3",
            "/api/users/swagger-ui",
            "/api/stores/v3",
            "/api/stores/swagger-ui",
            "/api/orders/v3",
            "/api/orders/swagger-ui",
            "/api/pay/v3",
            "/api/pay/swagger-ui",
            "/api/qr/v3",
            "/api/qr/swagger-ui",
            "/api/orderquery/v3",
            "/api/orderquery/swagger-ui",
            "/api/backend/v3",
            "/api/backend/swagger-ui",
            "/api/stores/v3/api-docs",
            "/api/stores/v3/api-docs/public",

            // 기타 정적 리소스
            "/favicon.ico",
            "/actuator/health"        // 헬스체크는 JWT 없이도 접근 가능
    );

    private final JwtUtil jwtUtils;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${passport.secret}")
    private String passportSecret;

    @Value("${passport.ttl-seconds:60}")
    private long passportTtlSeconds;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // 모든 요청 로깅
        log.info("🌐 Gateway 요청 - Method: {}, Path: {}", method, path);
        log.info("🔍 Gateway passport.secret: {}", passportSecret);

        // OPTIONS 요청은 항상 허용
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            log.info("🔓 OPTIONS 요청 통과: {}", path);
            return chain.filter(exchange);
        }

        // 모든 OpenAPI/Swagger 경로 무조건 허용 (최고 우선순위)
        if (path.contains("api-docs") ||
            path.contains("swagger") ||
            path.contains("webjars") ||
            path.contains("openapi") ||
            path.equals("/") ||
            path.equals("/swagger-ui.html")) {
            return chain.filter(exchange);
        }

        // 기타 예외 경로 처리
        if (EXCLUDE_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
            log.info("🔓 예외 경로 통과: {}", path);
            return chain.filter(exchange);
        }

        // 결제 생성은 내부 호출/클라이언트 흐름 모두 허용 (POST /api/pay/v1/payments)
        if (HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && "/api/pay/v1/payments".equals(path)) {
            log.info("🔓 결제 생성 경로 통과: {}", path);
            return chain.filter(exchange);
        }

        // 내부 서비스 호출은 인증 없이 통과
        String internalCall = exchange.getRequest().getHeaders().getFirst("X-Internal-Call");
        String internalService = exchange.getRequest().getHeaders().getFirst("X-Internal-Service");
        if ("true".equalsIgnoreCase(internalCall) && internalService != null && !internalService.isBlank()) {
            log.info("🔓 내부 서비스 호출 통과: service={}, path={}", internalService, path);
            return chain.filter(exchange);
        }

        // 2) Authorization Header 체크
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        log.info("🔐 Authorization 헤더: {}", authHeader != null ? "Bearer ***" : "없음");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("❌ Authorization 헤더 없음 또는 형식 오류");
            return onError(exchange, "Missing Authorization Header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        log.info("🔐 JWT 토큰 추출 완료 (길이: {})", token.length());

        // 3) JWT 유효성 검사
        if (!jwtUtils.validateToken(token)) {
            log.warn("❌ JWT 토큰 검증 실패");
            return onError(exchange, "Invalid Token", HttpStatus.UNAUTHORIZED);
        }

        log.info("✅ JWT 토큰 검증 성공");

        // +) passport 발급
        // 캐시 키 = passport:sha256(token)
        String cacheKey = "passport:" + sha256(token);
        // 토큰 만료까지 남은 시간(초) (JwtUtil에 맞게 구현)
        long tokenRemainSeconds = jwtUtils.getRemainingSeconds(token); // 구현 필요
        long ttl = Math.max(1, Math.min(passportTtlSeconds, tokenRemainSeconds));

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cachedPassportJson -> forwardWithPassport(exchange, chain, cachedPassportJson))
                .switchIfEmpty(Mono.defer(() -> {
                    // 새 Passport 생성
                    String userId = String.valueOf(jwtUtils.getUserId(token));
                    String email = jwtUtils.getUsername(token);
                    String role = jwtUtils.getRole(token);

                    long now = Instant.now().getEpochSecond();
                    long exp = now + ttl;

                    PassportPayload payload = new PassportPayload(
                            new PassportUser(userId, email, role),
                            now,
                            exp
                    );

                    try {
                        // payload를 안정적으로 직렬화(서명 대상)
                        String payloadJson = objectMapper.writeValueAsString(payload);
                        log.info("🔍 Gateway payloadJson: {}", payloadJson);
                        String integrity = HmacUtil.hmacSha256Base64Url(passportSecret, payloadJson);
                        log.info("🔍 Gateway integrity: {}", integrity);

                        PassportEnvelope envelope = new PassportEnvelope(payload, integrity);
                        String passportJson = objectMapper.writeValueAsString(envelope);

                        // 캐시에 저장 후 전달
                        return redisTemplate.opsForValue()
                                .set(cacheKey, passportJson, java.time.Duration.ofSeconds(ttl))
                                .then(forwardWithPassport(exchange, chain, passportJson));

                    } catch (Exception e) {
                        return onError(exchange,"Invalid passport", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }));
    }

    @Override
    public int getOrder() {
        return -1; // GlobalFilter에서 가장 먼저 실행되도록
    }


    private boolean isSwaggerOrOpenApiPath(String path) {
        return path.contains("/v3/api-docs") ||
               path.contains("/swagger-ui") ||
               path.contains("/webjars") ||
               path.contains("/openapi") ||
               path.contains("/swagger-resources") ||
               path.contains("/configuration") ||
               path.equals("/swagger-ui.html") ||
               path.equals("/") ||  // 루트 경로도 허용
               path.startsWith("/api/") && (
                   path.contains("/v3/") ||
                   path.contains("/swagger-ui")
               );
    }

    private Mono<Void> forwardWithPassport(ServerWebExchange exchange, GatewayFilterChain chain, String passportJson) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-Passport", passportJson) // 다운스트림으로 passport 전달
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
        log.error("🚫 Gateway 인증 실패 - 경로: {}, 오류: {}, 상태: {}",
                exchange.getRequest().getURI().getPath(), err, status);
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
    private boolean isDocumentationRequest(String path) {
        return path.contains("/v3/api-docs") || path.contains("/swagger-ui") || path.contains("/webjars");
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
