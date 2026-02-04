package com.popcorn.order.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 외부 서비스 상태 모니터링
 * Circuit Breaker 상태와 서비스 가용성을 추적
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalServiceHealthMonitor {

    private final WebClient defaultWebClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Value("${external.services.users.base-url}")
    private String usersServiceBaseUrl;

    @Value("${external.services.store.base-url}")
    private String storeServiceBaseUrl;

    /**
     * 모든 외부 서비스 상태 조회
     */
    public Map<String, ServiceHealthStatus> getAllServiceHealth() {
        Map<String, ServiceHealthStatus> healthMap = new HashMap<>();

        healthMap.put("user-service", getUserServiceHealth());
        healthMap.put("store-service", getStoreServiceHealth());

        return healthMap;
    }

    /**
     * 사용자 서비스 상태 확인
     */
    public ServiceHealthStatus getUserServiceHealth() {
        CircuitBreaker userCircuitBreaker = circuitBreakerFactory.create("user-service");

        try {
            // Simple health check - actuator health endpoint if available, or fallback to root
            Boolean isHealthy = userCircuitBreaker.run(
                () -> checkServiceHealth(usersServiceBaseUrl, "user-service"),
                throwable -> {
                    log.warn("사용자 서비스 health check fallback - reason: {}", throwable.getMessage());
                    return false;
                }
            );

            return ServiceHealthStatus.builder()
                    .serviceName("user-service")
                    .isHealthy(isHealthy)
                    .circuitBreakerState(getCircuitBreakerState(userCircuitBreaker))
                    .baseUrl(usersServiceBaseUrl)
                    .build();

        } catch (Exception e) {
            log.error("사용자 서비스 health check 실패", e);
            return ServiceHealthStatus.builder()
                    .serviceName("user-service")
                    .isHealthy(false)
                    .circuitBreakerState(getCircuitBreakerState(userCircuitBreaker))
                    .baseUrl(usersServiceBaseUrl)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 스토어 서비스 상태 확인
     */
    public ServiceHealthStatus getStoreServiceHealth() {
        CircuitBreaker storeCircuitBreaker = circuitBreakerFactory.create("store-service");

        try {
            Boolean isHealthy = storeCircuitBreaker.run(
                () -> checkServiceHealth(storeServiceBaseUrl, "store-service"),
                throwable -> {
                    log.warn("스토어 서비스 health check fallback - reason: {}", throwable.getMessage());
                    return false;
                }
            );

            return ServiceHealthStatus.builder()
                    .serviceName("store-service")
                    .isHealthy(isHealthy)
                    .circuitBreakerState(getCircuitBreakerState(storeCircuitBreaker))
                    .baseUrl(storeServiceBaseUrl)
                    .build();

        } catch (Exception e) {
            log.error("스토어 서비스 health check 실패", e);
            return ServiceHealthStatus.builder()
                    .serviceName("store-service")
                    .isHealthy(false)
                    .circuitBreakerState(getCircuitBreakerState(storeCircuitBreaker))
                    .baseUrl(storeServiceBaseUrl)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 서비스 헬스 체크 수행
     */
    private Boolean checkServiceHealth(String baseUrl, String serviceName) {
        try {
            log.debug("🔍 [HEALTH-CHECK] {} 상태 확인 시작 - baseUrl: {}", serviceName, baseUrl);

            // Try actuator health endpoint first, fallback to root if not available
            String response = defaultWebClient
                    .get()
                    .uri(baseUrl + "/actuator/health")
                    .headers(headers -> {
                        headers.set("X-Internal-Service", "order-service");
                        headers.set("X-Internal-Call", "true");
                    })
                    .retrieve()
                    .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), clientResponse -> {
                        // Try root endpoint if actuator is not available
                        return defaultWebClient
                                .get()
                                .uri(baseUrl)
                                .headers(headers -> {
                                    headers.set("X-Internal-Service", "order-service");
                                    headers.set("X-Internal-Call", "true");
                                })
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(body -> true)
                                .onErrorReturn(false)
                                .then(clientResponse.createException());
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(2))
                    .block();

            boolean isHealthy = response != null && !response.isEmpty();
            log.debug("✅ [HEALTH-CHECK] {} 상태 확인 완료 - healthy: {}", serviceName, isHealthy);
            return isHealthy;

        } catch (Exception e) {
            log.warn("⚠️ [HEALTH-CHECK] {} 상태 확인 실패 - error: {}", serviceName, e.getMessage());
            throw e;
        }
    }

    /**
     * Circuit Breaker 상태 조회
     */
    private String getCircuitBreakerState(CircuitBreaker circuitBreaker) {
        try {
            // Circuit Breaker state can be obtained through metrics or state exposure
            // For simplicity, we'll return "UNKNOWN" here
            // In a real implementation, you might want to expose this through Spring Boot Actuator
            return "UNKNOWN";
        } catch (Exception e) {
            log.debug("Circuit Breaker 상태 조회 실패: {}", e.getMessage());
            return "ERROR";
        }
    }

    /**
     * 서비스 상태 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class ServiceHealthStatus {
        private String serviceName;
        private boolean isHealthy;
        private String circuitBreakerState;
        private String baseUrl;
        private String errorMessage;
        private long lastCheckedAt = System.currentTimeMillis();
    }
}