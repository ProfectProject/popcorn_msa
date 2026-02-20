package com.popcorn.order.client;

import com.popcorn.order.dto.response.OrderStatisticsResponse;
import com.popcorn.order.dto.response.OrderStatusSummaryResponse;
import com.popcorn.order.dto.response.OrderListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.popcorn.common.dto.BaseResponse;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;

/**
 * 📊 OrderQuery 서비스와의 HTTP 통신 클라이언트
 *
 * 통계 및 조회 로직을 orderQuery 마이크로서비스에 위임
 * Circuit Breaker 패턴으로 서비스 장애 격리 및 fallback 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryServiceClient {

    private final WebClient defaultWebClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Value("${external.services.orderquery.base-url}")
    private String orderQueryServiceBaseUrl;

    @Value("${order.orderquery.timeout-ms:3000}")
    private long timeoutMs;

    // TypeReference를 정적 필드로 정의
    private static final ParameterizedTypeReference<BaseResponse<OrderStatisticsResponse>> STATISTICS_RESPONSE_TYPE =
            new ParameterizedTypeReference<BaseResponse<OrderStatisticsResponse>>() {};

    private static final ParameterizedTypeReference<BaseResponse<OrderStatusSummaryResponse>> STATUS_SUMMARY_RESPONSE_TYPE =
            new ParameterizedTypeReference<BaseResponse<OrderStatusSummaryResponse>>() {};

    /**
     * 📈 상세 주문 통계 조회 (Circuit Breaker 적용)
     */
    public OrderStatisticsResponse getDetailedStatistics(int days, String includeTypes) {
        CircuitBreaker orderQueryCircuitBreaker = circuitBreakerFactory.create("orderquery-service");

        return orderQueryCircuitBreaker.run(
            () -> getDetailedStatisticsInternal(days, includeTypes),
            throwable -> {
                log.warn("🔄 [FALLBACK] 주문 통계 조회 fallback - days: {}, reason: {}",
                        days, throwable.getMessage());
                return createFallbackStatistics();
            }
        );
    }

    /**
     * 📊 실시간 주문 상태별 요약 조회 (Circuit Breaker 적용)
     */
    public OrderStatusSummaryResponse getRealtimeStatusSummary() {
        CircuitBreaker orderQueryCircuitBreaker = circuitBreakerFactory.create("orderquery-service");

        return orderQueryCircuitBreaker.run(
            () -> getRealtimeStatusSummaryInternal(),
            throwable -> {
                log.warn("🔄 [FALLBACK] 주문 상태별 요약 조회 fallback - reason: {}",
                        throwable.getMessage());
                return createFallbackStatusSummary();
            }
        );
    }

    /**
     * 📋 전체 주문 목록 조회 (고급 필터링, Circuit Breaker 적용)
     */
    public java.util.List<OrderListResponse.OrderItemDto> getAllOrdersWithFilters(
            String status, String startDate, String endDate, Long userId,
            Integer minAmount, Integer maxAmount, int page, int size,
            String sortBy, String sortDirection) {

        CircuitBreaker orderQueryCircuitBreaker = circuitBreakerFactory.create("orderquery-service");

        return orderQueryCircuitBreaker.run(
            () -> getAllOrdersWithFiltersInternal(
                status, startDate, endDate, userId, minAmount, maxAmount,
                page, size, sortBy, sortDirection),
            throwable -> {
                log.warn("🔄 [FALLBACK] 전체 주문 목록 조회 fallback - reason: {}",
                        throwable.getMessage());
                return Collections.emptyList(); // 빈 리스트 반환
            }
        );
    }

    // ============ Internal Implementation Methods ============

    private OrderStatisticsResponse getDetailedStatisticsInternal(int days, String includeTypes) {
        try {
            log.info("📈 [HTTP] 주문 통계 조회 요청 - days: {}, includeTypes: {}", days, includeTypes);

            String currentAuthHeader = getCurrentAuthorizationHeader();

            BaseResponse<OrderStatisticsResponse> response = defaultWebClient
                    .get()
                    .uri(orderQueryServiceBaseUrl + "/api/orderquery/v1/dashboard/statistics?days={days}&includeTypes={includeTypes}",
                         days, includeTypes)
                    .headers(headers -> {
                        if (currentAuthHeader != null) {
                            headers.set("Authorization", currentAuthHeader);
                        }
                        headers.set("X-Internal-Service", "order-service");
                        headers.set("X-Internal-Call", "true");
                    })
                    .retrieve()
                    .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals, clientResponse -> {
                        log.error("🚨 [HTTP] OrderQuery 서비스 사용불가");
                        return clientResponse.createException();
                    })
                    .bodyToMono(STATISTICS_RESPONSE_TYPE)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (response != null && response.getData() != null) {
                log.info("✅ [HTTP] 주문 통계 조회 성공 - 총 주문: {}",
                        response.getData().getTotalOrders());
                return response.getData();
            } else {
                log.warn("⚠️ [HTTP] 주문 통계 응답이 null");
                return createFallbackStatistics();
            }

        } catch (WebClientResponseException e) {
            log.error("❌ [HTTP] 주문 통계 조회 실패 - status: {}, error: {}",
                    e.getStatusCode(), e.getMessage());
            throw e; // Circuit Breaker가 감지할 수 있도록 예외 재발생
        } catch (Exception e) {
            log.error("❌ [HTTP] 주문 통계 조회 예외 - error: {}", e.getMessage(), e);
            throw e; // Circuit Breaker가 감지할 수 있도록 예외 재발생
        }
    }

    private OrderStatusSummaryResponse getRealtimeStatusSummaryInternal() {
        try {
            log.info("📊 [HTTP] 주문 상태별 요약 조회 요청");

            String currentAuthHeader = getCurrentAuthorizationHeader();

            BaseResponse<OrderStatusSummaryResponse> response = defaultWebClient
                    .get()
                    .uri(orderQueryServiceBaseUrl + "/api/orderquery/v1/dashboard/status-summary")
                    .headers(headers -> {
                        if (currentAuthHeader != null) {
                            headers.set("Authorization", currentAuthHeader);
                        }
                        headers.set("X-Internal-Service", "order-service");
                        headers.set("X-Internal-Call", "true");
                    })
                    .retrieve()
                    .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals, clientResponse -> {
                        log.error("🚨 [HTTP] OrderQuery 서비스 사용불가");
                        return clientResponse.createException();
                    })
                    .bodyToMono(STATUS_SUMMARY_RESPONSE_TYPE)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (response != null && response.getData() != null) {
                log.info("✅ [HTTP] 주문 상태별 요약 조회 성공");
                return response.getData();
            } else {
                log.warn("⚠️ [HTTP] 주문 상태별 요약 응답이 null");
                return createFallbackStatusSummary();
            }

        } catch (WebClientResponseException e) {
            log.error("❌ [HTTP] 주문 상태별 요약 조회 실패 - status: {}, error: {}",
                    e.getStatusCode(), e.getMessage());
            throw e; // Circuit Breaker가 감지할 수 있도록 예외 재발생
        } catch (Exception e) {
            log.error("❌ [HTTP] 주문 상태별 요약 조회 예외 - error: {}", e.getMessage(), e);
            throw e; // Circuit Breaker가 감지할 수 있도록 예외 재발생
        }
    }

    private java.util.List<OrderListResponse.OrderItemDto> getAllOrdersWithFiltersInternal(
            String status, String startDate, String endDate, Long userId,
            Integer minAmount, Integer maxAmount, int page, int size,
            String sortBy, String sortDirection) {

        try {
            log.info("📋 [HTTP] 전체 주문 목록 조회 요청 - status: {}, page: {}, size: {}",
                    status, page, size);

            String currentAuthHeader = getCurrentAuthorizationHeader();

            // URL 파라미터 빌드
            StringBuilder uriBuilder = new StringBuilder(orderQueryServiceBaseUrl + "/api/orderquery/v1/dashboard/orders?");
            uriBuilder.append("page=").append(page).append("&size=").append(size);
            uriBuilder.append("&sortBy=").append(sortBy).append("&sortDirection=").append(sortDirection);

            if (status != null) uriBuilder.append("&status=").append(status);
            if (startDate != null) uriBuilder.append("&startDate=").append(startDate);
            if (endDate != null) uriBuilder.append("&endDate=").append(endDate);
            if (userId != null) uriBuilder.append("&userId=").append(userId);
            if (minAmount != null) uriBuilder.append("&minAmount=").append(minAmount);
            if (maxAmount != null) uriBuilder.append("&maxAmount=").append(maxAmount);

            // OrderQuery 서비스는 OrderItemPageDto를 반환하므로 해당 타입으로 매핑
            var response = defaultWebClient
                    .get()
                    .uri(uriBuilder.toString())
                    .headers(headers -> {
                        if (currentAuthHeader != null) {
                            headers.set("Authorization", currentAuthHeader);
                        }
                        headers.set("X-Internal-Service", "order-service");
                        headers.set("X-Internal-Call", "true");
                    })
                    .retrieve()
                    .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals, clientResponse -> {
                        log.error("🚨 [HTTP] OrderQuery 서비스 사용불가");
                        return clientResponse.createException();
                    })
                    .bodyToMono(String.class) // 우선 String으로 받아서 디버깅
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            log.info("📋 [HTTP] OrderQuery 응답 수신: {}", response);

            // TODO: 실제로는 JSON을 파싱해서 OrderItemDto 리스트로 변환해야 함
            // 현재는 빈 리스트 반환
            return Collections.emptyList();

        } catch (WebClientResponseException e) {
            log.error("❌ [HTTP] 전체 주문 목록 조회 실패 - status: {}, error: {}",
                    e.getStatusCode(), e.getMessage());
            throw e; // Circuit Breaker가 감지할 수 있도록 예외 재발생
        } catch (Exception e) {
            log.error("❌ [HTTP] 전체 주문 목록 조회 예외 - error: {}", e.getMessage(), e);
            throw e; // Circuit Breaker가 감지할 수 있도록 예외 재발생
        }
    }

    // ============ Fallback Methods ============

    private OrderStatisticsResponse createFallbackStatistics() {
        return OrderStatisticsResponse.builder()
                .totalOrders(0L)
                .totalRevenue(java.math.BigDecimal.ZERO)
                .averageOrderAmount(java.math.BigDecimal.ZERO)
                .todayOrders(0L)
                .weeklyOrders(0L)
                .monthlyOrders(0L)
                .build();
    }

    private OrderStatusSummaryResponse createFallbackStatusSummary() {
        return OrderStatusSummaryResponse.builder()
                .statusCounts(OrderStatusSummaryResponse.StatusCounts.builder()
                    .pending(0L)
                    .confirmed(0L)
                    .paid(0L)
                    .completed(0L)
                    .cancelled(0L)
                    .reserved(0L)
                    .paymentPending(0L)
                    .build())
                .statusRatios(OrderStatusSummaryResponse.StatusRatios.builder()
                    .pending(0.0)
                    .confirmed(0.0)
                    .paid(0.0)
                    .completed(0.0)
                    .cancelled(0.0)
                    .reserved(0.0)
                    .paymentPending(0.0)
                    .build())
                .build();
    }

    // ============ Utility Methods ============

    private String getCurrentAuthorizationHeader() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest().getHeader("Authorization");
        } catch (Exception e) {
            log.debug("Authorization 헤더 추출 실패: {}", e.getMessage());
            return null;
        }
    }
}