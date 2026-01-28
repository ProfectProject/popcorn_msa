package com.popcorn.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 이벤트 기반 외부 서비스 클라이언트
 *
 * 이벤트 기반 아키텍처에서 Redis Stream을 통해 다른 서비스들과 안전하게 통신합니다.
 * HTTP 호출을 제거하고 이벤트 기반 비동기 통신으로 시스템 안정성과 성능을 향상시킵니다.
 *
 * 지원하는 이벤트:
 * - Payment Service: 결제 처리 이벤트
 * - User Service: 사용자 정보 조회 이벤트
 * - Inventory Service: 재고 확인/차감 이벤트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventDrivenServiceClient {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis Stream 키 정의
    private static final String PAYMENT_STREAM = "payment:request:stream";
    private static final String USER_STREAM = "user:request:stream";
    private static final String INVENTORY_STREAM = "inventory:request:stream";

    // 응답 대기 키 패턴
    private static final String RESPONSE_KEY_PREFIX = "response:";
    private static final int RESPONSE_TIMEOUT_SECONDS = 10;

    // ========================= 결제 서비스 이벤트 =========================

    /**
     * 결제 요청 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param paymentMethod 결제 수단
     * @return 결제 처리 결과
     */
    public CompletableFuture<Map<String, Object>> processPayment(UUID orderId, Long amount, String paymentMethod) {
        String correlationId = UUID.randomUUID().toString();

        log.info("🔄 [이벤트] 결제 요청 발행: orderId={}, amount={}, correlationId={}",
            orderId, amount, correlationId);

        try {
            // 결제 요청 이벤트 발행
            Map<String, String> paymentEvent = Map.of(
                "eventType", "PAYMENT_PROCESS_REQUEST",
                "orderId", orderId.toString(),
                "amount", amount.toString(),
                "paymentMethod", paymentMethod,
                "correlationId", correlationId,
                "requestedBy", "order-service",
                "timestamp", LocalDateTime.now().toString()
            );

            redisTemplate.opsForStream().add(PAYMENT_STREAM, paymentEvent);
            log.debug("📤 결제 이벤트 발행 완료: correlationId={}", correlationId);

            // 비동기 응답 대기
            return waitForResponse(correlationId, "PAYMENT")
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.warn("⚠️ 결제 서비스 이벤트 응답 실패 - 폴백 실행: orderId={}, error={}",
                            orderId, throwable.getMessage());

                        // 폴백: 결제 보류 상태
                        return Map.of(
                            "status", "PENDING",
                            "orderId", orderId.toString(),
                            "message", "결제 서비스 일시 장애로 결제가 보류되었습니다.",
                            "retryable", true,
                            "fallbackReason", throwable.getMessage()
                        );
                    }
                    return result;
                });

        } catch (Exception e) {
            log.error("❌ 결제 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            return CompletableFuture.completedFuture(Map.of(
                "status", "ERROR",
                "message", "결제 이벤트 발행 실패",
                "error", e.getMessage()
            ));
        }
    }

    // ========================= 사용자 서비스 이벤트 =========================

    /**
     * 사용자 정보 조회 이벤트 발행
     *
     * @param userId 사용자 ID
     * @return 사용자 정보
     */
    public CompletableFuture<Map<String, Object>> getUserInfo(Long userId) {
        String correlationId = UUID.randomUUID().toString();

        log.info("🔄 [이벤트] 사용자 조회 요청 발행: userId={}, correlationId={}",
            userId, correlationId);

        try {
            // 사용자 조회 요청 이벤트 발행
            Map<String, String> userEvent = Map.of(
                "eventType", "USER_INFO_REQUEST",
                "userId", userId.toString(),
                "correlationId", correlationId,
                "requestedBy", "order-service",
                "timestamp", LocalDateTime.now().toString()
            );

            redisTemplate.opsForStream().add(USER_STREAM, userEvent);
            log.debug("📤 사용자 조회 이벤트 발행 완료: correlationId={}", correlationId);

            // 비동기 응답 대기
            return waitForResponse(correlationId, "USER")
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.warn("⚠️ 사용자 서비스 이벤트 응답 실패 - 폴백 실행: userId={}, error={}",
                            userId, throwable.getMessage());

                        // 폴백: 기본 사용자 정보
                        return Map.of(
                            "userId", userId,
                            "name", "사용자" + userId,
                            "email", "user" + userId + "@temp.com",
                            "status", "FALLBACK",
                            "message", "사용자 서비스 장애로 기본 정보를 제공합니다.",
                            "fallbackReason", throwable.getMessage()
                        );
                    }
                    return result;
                });

        } catch (Exception e) {
            log.error("❌ 사용자 조회 이벤트 발행 실패: userId={}, error={}", userId, e.getMessage(), e);
            return CompletableFuture.completedFuture(Map.of(
                "status", "ERROR",
                "message", "사용자 조회 이벤트 발행 실패",
                "error", e.getMessage()
            ));
        }
    }

    // ========================= 재고 서비스 이벤트 =========================

    /**
     * 재고 확인 및 차감 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param productId 상품 ID
     * @param quantity 수량
     * @return 재고 처리 결과
     */
    public CompletableFuture<Map<String, Object>> checkAndReserveStock(UUID orderId, Long productId, Integer quantity) {
        String correlationId = UUID.randomUUID().toString();

        log.info("🔄 [이벤트] 재고 차감 요청 발행: orderId={}, productId={}, quantity={}, correlationId={}",
            orderId, productId, quantity, correlationId);

        try {
            // 재고 차감 요청 이벤트 발행
            Map<String, String> inventoryEvent = Map.of(
                "eventType", "STOCK_RESERVE_REQUEST",
                "orderId", orderId.toString(),
                "productId", productId.toString(),
                "quantity", quantity.toString(),
                "correlationId", correlationId,
                "requestedBy", "order-service",
                "timestamp", LocalDateTime.now().toString()
            );

            redisTemplate.opsForStream().add(INVENTORY_STREAM, inventoryEvent);
            log.debug("📤 재고 차감 이벤트 발행 완료: correlationId={}", correlationId);

            // 비동기 응답 대기
            return waitForResponse(correlationId, "INVENTORY")
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("⚠️ 재고 서비스 이벤트 응답 실패 - 폴백 실행: orderId={}, error={}",
                            orderId, throwable.getMessage());

                        // 폴백: 재고 부족으로 안전하게 처리
                        return Map.of(
                            "status", "INSUFFICIENT_STOCK",
                            "available", false,
                            "orderId", orderId.toString(),
                            "productId", productId,
                            "requestedQuantity", quantity,
                            "message", "재고 서비스 장애로 재고 확인이 불가능합니다.",
                            "fallbackReason", throwable.getMessage(),
                            "action", "RETRY_LATER"
                        );
                    }
                    return result;
                });

        } catch (Exception e) {
            log.error("❌ 재고 차감 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            return CompletableFuture.completedFuture(Map.of(
                "status", "ERROR",
                "message", "재고 차감 이벤트 발행 실패",
                "error", e.getMessage()
            ));
        }
    }

    // ========================= 통합 주문 처리 이벤트 =========================

    /**
     * 이벤트 기반 통합 주문 처리
     *
     * 각 서비스별로 독립적인 이벤트 처리가 적용되어
     * 일부 서비스 장애가 전체 시스템에 미치는 영향을 최소화
     */
    public CompletableFuture<Map<String, Object>> processOrderWithEvents(
            UUID orderId, Long userId, Long productId, Integer quantity, Long amount, String paymentMethod) {

        log.info("🚀 [이벤트] 통합 주문 처리 시작: orderId={}", orderId);

        // 1. 사용자 정보 조회 (병렬 실행)
        CompletableFuture<Map<String, Object>> userFuture = getUserInfo(userId);

        // 2. 재고 확인 및 차감 (병렬 실행)
        CompletableFuture<Map<String, Object>> stockFuture = checkAndReserveStock(orderId, productId, quantity);

        // 3. 사용자 정보와 재고 확인 완료 후 결제 진행
        return CompletableFuture.allOf(userFuture, stockFuture)
            .thenCompose(v -> {
                try {
                    Map<String, Object> userInfo = userFuture.get();
                    Map<String, Object> stockInfo = stockFuture.get();

                    // 재고 확인 성공한 경우에만 결제 진행
                    if (Boolean.TRUE.equals(stockInfo.get("available"))) {
                        log.info("✅ 재고 확인 성공 - 결제 진행: orderId={}", orderId);
                        return processPayment(orderId, amount, paymentMethod);
                    } else {
                        log.warn("⚠️ 재고 부족으로 주문 실패: orderId={}", orderId);
                        return CompletableFuture.completedFuture(Map.of(
                            "status", "FAILED",
                            "reason", "INSUFFICIENT_STOCK",
                            "stockInfo", stockInfo
                        ));
                    }
                } catch (Exception e) {
                    log.error("❌ 통합 주문 처리 중 오류 발생: orderId={}", orderId, e);
                    return CompletableFuture.completedFuture(Map.of(
                        "status", "ERROR",
                        "message", "주문 처리 중 오류가 발생했습니다.",
                        "error", e.getMessage()
                    ));
                }
            });
    }

    // ========================= 응답 대기 및 유틸리티 =========================

    /**
     * 이벤트 응답 대기
     */
    private CompletableFuture<Map<String, Object>> waitForResponse(String correlationId, String serviceType) {
        return CompletableFuture.supplyAsync(() -> {
            String responseKey = RESPONSE_KEY_PREFIX + correlationId;

            try {
                // 폴링 방식으로 응답 대기 (Redis Pub/Sub 대신 간단한 방식)
                for (int i = 0; i < RESPONSE_TIMEOUT_SECONDS * 10; i++) {
                    String response = redisTemplate.opsForValue().get(responseKey);
                    if (response != null) {
                        redisTemplate.delete(responseKey); // 응답 확인 후 삭제
                        log.debug("✅ [{}] 이벤트 응답 수신: correlationId={}", serviceType, correlationId);
                        return objectMapper.readValue(response, Map.class);
                    }
                    Thread.sleep(100); // 100ms 대기
                }

                throw new RuntimeException(serviceType + " 서비스 응답 타임아웃: " + correlationId);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(serviceType + " 서비스 응답 대기 중 인터럽트: " + correlationId, e);
            } catch (Exception e) {
                throw new RuntimeException(serviceType + " 서비스 응답 처리 실패: " + correlationId, e);
            }
        });
    }

    /**
     * 이벤트 처리 상태 모니터링
     */
    public Map<String, Object> getEventProcessingStatus() {
        try {
            // Redis Stream 정보 조회 (실제 구현에서는 더 상세한 정보 제공)
            return Map.of(
                "paymentStream", getStreamInfo(PAYMENT_STREAM),
                "userStream", getStreamInfo(USER_STREAM),
                "inventoryStream", getStreamInfo(INVENTORY_STREAM),
                "timestamp", LocalDateTime.now().toString()
            );
        } catch (Exception e) {
            log.error("이벤트 처리 상태 조회 실패: {}", e.getMessage());
            return Map.of(
                "status", "ERROR",
                "message", "이벤트 처리 상태 조회 실패",
                "error", e.getMessage()
            );
        }
    }

    private Map<String, Object> getStreamInfo(String streamKey) {
        try {
            // 실제로는 Redis Stream의 XINFO 명령어 사용
            return Map.of(
                "stream", streamKey,
                "status", "ACTIVE"
            );
        } catch (Exception e) {
            return Map.of(
                "stream", streamKey,
                "status", "ERROR",
                "error", e.getMessage()
            );
        }
    }
}