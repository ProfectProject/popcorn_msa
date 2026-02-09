package com.popcorn.checkIns.client;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.popcorn.checkIns.util.SystemPassportGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Order 서비스 HTTP API 클라이언트
 * CheckIns 서비스에서 주문 정보를 조회하기 위해 사용
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderApiClient {

    private final RestTemplate restTemplate;
    private final SystemPassportGenerator passportGenerator;

    @Value("${app.order-service.base-url:http://popcorn-gateway:8080}")
    private String orderServiceBaseUrl;

    /**
     * 주문 상태 조회 (MSA 호환 HTTP API 방식)
     *
     * @param orderId 주문 ID
     * @return 주문 상태 ("PAID", "COMPLETED", "CANCELLED" 등)
     */
    public String getOrderStatus(UUID orderId) {
        try {
            log.debug("🌐 [ORDER-API] 주문 상태 조회 시작: orderId={}", orderId);

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + passportGenerator.generateSystemPassport());
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Order 서비스 API 호출
            String url = orderServiceBaseUrl + "/api/orders/v1/{orderId}";
            ResponseEntity<ApiResponse> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                ApiResponse.class,
                orderId
            );

            ApiResponse apiResponse = responseEntity.getBody();
            if (apiResponse == null || !"ORDER_RETRIEVED".equals(apiResponse.getCode())) {
                log.warn("⚠️ [ORDER-API] API 응답 오류: orderId={} response={}", orderId, apiResponse);
                throw new OrderApiException("Order API 응답 오류: " + (apiResponse != null ? apiResponse.getMessage() : "null response"));
            }

            OrderDetailResponse response = apiResponse.getData();
            if (response == null) {
                log.warn("⚠️ [ORDER-API] 주문을 찾을 수 없음: orderId={}", orderId);
                throw new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId);
            }

            String orderStatus = response.getStatus();
            log.debug("✅ [ORDER-API] 주문 상태 조회 완료: orderId={} status={}", orderId, orderStatus);

            return orderStatus;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("🚫 [ORDER-API] 주문을 찾을 수 없음: orderId={}", orderId);
                throw new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId);
            }

            log.error("❌ [ORDER-API] HTTP 오류: orderId={} status={} error={}",
                     orderId, e.getStatusCode(), e.getMessage());
            throw new OrderApiException("주문 상태 조회 실패: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("💥 [ORDER-API] 예상치 못한 오류: orderId={} error={}", orderId, e.getMessage(), e);
            throw new OrderApiException("주문 API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Order API 응답 래퍼
     */
    private static class ApiResponse {
        private String code;
        private String message;
        private OrderDetailResponse data;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public OrderDetailResponse getData() { return data; }
        public void setData(OrderDetailResponse data) { this.data = data; }
    }

    /**
     * 주문 상세 응답 (필요한 필드만)
     */
    private static class OrderDetailResponse {
        private String status;
        private UUID orderId;
        private String orderNo;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public UUID getOrderId() { return orderId; }
        public void setOrderId(UUID orderId) { this.orderId = orderId; }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    }

    /**
     * 주문을 찾을 수 없을 때 발생하는 예외
     */
    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Order API 호출 실패 시 발생하는 예외
     */
    public static class OrderApiException extends RuntimeException {
        public OrderApiException(String message, Throwable cause) {
            super(message, cause);
        }

        public OrderApiException(String message) {
            super(message);
        }
    }
}