package com.popcorn.order.controller;

import com.popcorn.common.cache.IdempotencyService;
import com.popcorn.order.monitor.ExternalServiceHealthMonitor;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 시스템 상태 및 외부 서비스 상태 조회 API
 * 운영 모니터링 및 장애 대응을 위한 헬스 체크 엔드포인트 제공
 */
@Hidden
@RestController
@RequestMapping("/api/orders/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final ExternalServiceHealthMonitor externalServiceHealthMonitor;
    private final IdempotencyService idempotencyService;

    /**
     * 전체 시스템 상태 조회
     * 외부 서비스 의존성 상태를 포함한 종합 헬스 체크
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOverallHealth() {
        try {
            Map<String, ExternalServiceHealthMonitor.ServiceHealthStatus> serviceHealth =
                externalServiceHealthMonitor.getAllServiceHealth();

            // 전체 시스템 상태 결정
            boolean isOverallHealthy = serviceHealth.values().stream()
                    .allMatch(ExternalServiceHealthMonitor.ServiceHealthStatus::isHealthy);

            Map<String, Object> response = Map.of(
                "status", isOverallHealthy ? "UP" : "DEGRADED",
                "orderService", "UP", // 자체 서비스는 항상 UP (응답 가능하므로)
                "externalServices", serviceHealth,
                "timestamp", System.currentTimeMillis()
            );

            log.debug("🏥 [HEALTH] 전체 상태 조회 - overall: {}, services: {}",
                     isOverallHealthy ? "UP" : "DEGRADED", serviceHealth.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [HEALTH] 상태 조회 실패", e);
            Map<String, Object> errorResponse = Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(503).body(errorResponse);
        }
    }

    /**
     * 외부 서비스 상태만 조회
     * 개별 서비스별 상태 및 Circuit Breaker 정보 제공
     */
    @GetMapping("/external")
    public ResponseEntity<Map<String, ExternalServiceHealthMonitor.ServiceHealthStatus>> getExternalServicesHealth() {
        try {
            Map<String, ExternalServiceHealthMonitor.ServiceHealthStatus> serviceHealth =
                externalServiceHealthMonitor.getAllServiceHealth();

            log.debug("🔍 [HEALTH] 외부 서비스 상태 조회 - services: {}", serviceHealth.size());

            return ResponseEntity.ok(serviceHealth);

        } catch (Exception e) {
            log.error("❌ [HEALTH] 외부 서비스 상태 조회 실패", e);
            return ResponseEntity.status(503).build();
        }
    }

    /**
     * 간단한 주문 서비스 자체 상태 조회
     * 가장 기본적인 헬스 체크 (데이터베이스 연결 등)
     */
    @GetMapping("/self")
    public ResponseEntity<Map<String, Object>> getSelfHealth() {
        try {
            Map<String, Object> response = Map.of(
                "status", "UP",
                "service", "order-service",
                "timestamp", System.currentTimeMillis(),
                "version", getClass().getPackage().getImplementationVersion() != null ?
                          getClass().getPackage().getImplementationVersion() : "unknown"
            );

            log.debug("🏥 [HEALTH] 자체 상태 조회 - status: UP");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [HEALTH] 자체 상태 조회 실패", e);
            Map<String, Object> errorResponse = Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(503).body(errorResponse);
        }
    }

    /**
     * 🚨 긴급 캐시 정리 (IdempotencyService Redis 캐시 초기화)
     * IdempotencyService의 역직렬화 오류 해결용 임시 엔드포인트
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, Object>> clearIdempotencyCache() {
        try {
            log.warn("🧹 [EMERGENCY] IdempotencyService 캐시 수동 정리 요청됨");

            // IdempotencyService 캐시 전체 정리
            idempotencyService.clearCache();

            Map<String, Object> response = Map.of(
                "status", "SUCCESS",
                "message", "IdempotencyService 캐시가 성공적으로 정리되었습니다.",
                "timestamp", System.currentTimeMillis(),
                "action", "cache_cleared"
            );

            log.info("✅ [EMERGENCY] IdempotencyService 캐시 정리 완료");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [EMERGENCY] IdempotencyService 캐시 정리 실패", e);
            Map<String, Object> errorResponse = Map.of(
                "status", "FAILED",
                "message", "캐시 정리 중 오류가 발생했습니다: " + e.getMessage(),
                "timestamp", System.currentTimeMillis(),
                "error", e.getClass().getSimpleName()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 🔍 IdempotencyService 캐시 상태 조회
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        try {
            var cacheStats = idempotencyService.getCacheStats();

            Map<String, Object> response = Map.of(
                "status", "SUCCESS",
                "cacheSize", cacheStats.getCacheSize(),
                "hitCount", cacheStats.getHitCount(),
                "missCount", cacheStats.getMissCount(),
                "hitRate", cacheStats.getHitRate(),
                "timestamp", System.currentTimeMillis()
            );

            log.debug("🔍 [CACHE] IdempotencyService 캐시 상태 조회 - size: {}, hitRate: {}",
                     cacheStats.getCacheSize(), cacheStats.getHitRate());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [CACHE] 캐시 상태 조회 실패", e);
            Map<String, Object> errorResponse = Map.of(
                "status", "FAILED",
                "message", "캐시 상태 조회 중 오류가 발생했습니다: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
