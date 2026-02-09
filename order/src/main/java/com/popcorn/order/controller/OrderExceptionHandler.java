package com.popcorn.order.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.popcorn.common.dto.BaseResponse;
import com.popcorn.order.dto.response.OrderResponseCode;
import com.popcorn.order.exception.*;

import lombok.extern.slf4j.Slf4j;

/**
 * 주문 서비스 전역 예외 처리기
 *
 * @RestControllerAdvice 어노테이션을 통해 전체 애플리케이션에서 발생하는
 * 예외들을 한 곳에서 일관되게 처리합니다.
 *
 * 예외 처리의 중요성:
 * 1. 사용자에게 일관된 오류 메시지 제공
 * 2. 민감한 시스템 정보 노출 방지
 * 3. 로깅을 통한 문제 추적과 디버깅 지원
 * 4. 클라이언트가 이해할 수 있는 오류 코드 제공
 *
 * 예외 처리 원칙:
 * - 비즈니스 예외: 사용자에게 친화적인 메시지
 * - 시스템 예외: 일반적인 오류 메시지 (보안상 상세 정보 숨김)
 * - 검증 예외: 구체적인 필드별 오류 정보
 * - 모든 예외는 로그에 기록하여 추후 분석 가능
 */
@RestControllerAdvice(basePackages = "com.popcorn.order.controller")
@Slf4j
public class OrderExceptionHandler {

    /**
     * 주문을 찾을 수 없는 경우 처리
     *
     * 사용자가 존재하지 않는 주문을 조회하거나 수정하려 할 때 발생합니다.
     * 악의적인 접근을 방지하기 위해 과도한 정보를 노출하지 않습니다.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleOrderNotFoundException(
            OrderNotFoundException ex, WebRequest request) {

        log.warn("주문을 찾을 수 없음 - 주문ID: {}, 요청: {}",
                ex.getOrderId(), request.getDescription(false));

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.ORDER_NOT_FOUND, null);

        return ResponseEntity.status(OrderResponseCode.ORDER_NOT_FOUND.getHttpStatus())
                .body(response);
    }

    /**
     * 주문 상태 변경이 불가능한 경우 처리
     *
     * 이미 완료된 주문을 취소하려 하거나, 잘못된 상태 전환을 시도할 때 발생합니다.
     * 비즈니스 규칙 위반에 대한 명확한 안내를 제공합니다.
     */
    @ExceptionHandler(OrderStatusChangeNotAllowedException.class)
    public ResponseEntity<BaseResponse<Void>> handleOrderStatusChangeNotAllowedException(
            OrderStatusChangeNotAllowedException ex, WebRequest request) {

        log.warn("주문 상태 변경 불가 - 주문ID: {}, 현재상태: {}, 요청상태: {}, 사유: {}",
                ex.getOrderId(), ex.getCurrentStatus(), ex.getRequestedStatus(), ex.getMessage());

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.ORDER_STATUS_CHANGE_NOT_ALLOWED, null);

        return ResponseEntity.status(OrderResponseCode.ORDER_STATUS_CHANGE_NOT_ALLOWED.getHttpStatus())
                .body(response);
    }

    /**
     * 주문 취소가 불가능한 경우 처리
     *
     * 이미 배송이 시작된 주문이나 완료된 주문을 취소하려 할 때 발생합니다.
     * 취소 정책에 대한 명확한 안내를 제공합니다.
     */
    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<BaseResponse<Void>> handleOrderNotCancellableException(
            OrderNotCancellableException ex, WebRequest request) {

        log.warn("주문 취소 불가 - 주문ID: {}, 상태: {}, 사유: {}",
                ex.getOrderId(), ex.getCurrentStatus(), ex.getMessage());

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.ORDER_NOT_CANCELLABLE, null);

        return ResponseEntity.status(OrderResponseCode.ORDER_NOT_CANCELLABLE.getHttpStatus())
                .body(response);
    }

    /**
     * 재고 부족 예외 처리
     *
     * 주문하려는 상품의 재고가 부족할 때 발생합니다.
     * 사용자에게 현재 재고 상황을 안내합니다.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<BaseResponse<Void>> handleInsufficientStockException(
            InsufficientStockException ex, WebRequest request) {

        log.warn("재고 부족 - 상품: {}, 요청수량: {}, 재고수량: {}",
                ex.getProductId(), ex.getRequestedQuantity(), ex.getAvailableStock());

        String customMessage = String.format("재고가 부족합니다. 요청: %d개, 재고: %d개",
                ex.getRequestedQuantity(), ex.getAvailableStock());
        BaseResponse<Void> response = BaseResponse.of(
                OrderResponseCode.INSUFFICIENT_STOCK.getCode(),
                customMessage,
                null
        );

        return ResponseEntity.status(OrderResponseCode.INSUFFICIENT_STOCK.getHttpStatus())
                .body(response);
    }

    /**
     * 예약 실패 예외 처리
     */
    @ExceptionHandler(OrderReservationFailedException.class)
    public ResponseEntity<BaseResponse<Void>> handleOrderReservationFailed(
            OrderReservationFailedException ex, WebRequest request) {

        log.warn("예약 처리 실패 - 사유: {}", ex.getMessage());

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.ORDER_RESERVATION_FAILED,
                null
        );

        return ResponseEntity.status(OrderResponseCode.ORDER_RESERVATION_FAILED.getHttpStatus())
                .body(response);
    }

    /**
     * 예약 타임아웃 예외 처리
     */
    @ExceptionHandler(OrderReservationTimeoutException.class)
    public ResponseEntity<BaseResponse<Void>> handleOrderReservationTimeout(
            OrderReservationTimeoutException ex, WebRequest request) {

        log.warn("예약 응답 타임아웃 - 사유: {}", ex.getMessage());

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.ORDER_RESERVATION_TIMEOUT,
                null
        );

        return ResponseEntity.status(OrderResponseCode.ORDER_RESERVATION_TIMEOUT.getHttpStatus())
                .body(response);
    }

    /**
     * 결제 처리 실패 예외 처리
     *
     * 결제 서비스 연동 중 오류가 발생했을 때 처리합니다.
     * 결제 실패 원인을 사용자에게 안내하고 재시도를 유도합니다.
     */
    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<BaseResponse<Void>> handlePaymentProcessingException(
            PaymentProcessingException ex, WebRequest request) {

        log.error("결제 처리 실패 - 주문ID: {}, 결제방식: {}, 오류: {}",
                ex.getOrderId(), ex.getPaymentMethod(), ex.getMessage());

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.PAYMENT_PROCESSING_FAILED,
                null
        );

        return ResponseEntity.status(OrderResponseCode.PAYMENT_PROCESSING_FAILED.getHttpStatus())
                .body(response);
    }

    /**
     * 멱등성 키 관련 예외 처리
     *
     * 중복된 요청이거나 유효하지 않은 멱등성 키를 사용했을 때 발생합니다.
     * 클라이언트가 올바른 키를 사용하도록 안내합니다.
     */
    @ExceptionHandler(IdempotencyKeyException.class)
    public ResponseEntity<BaseResponse<Void>> handleIdempotencyKeyException(
            IdempotencyKeyException ex, WebRequest request) {

        log.warn("멱등성 키 오류 - 키: {}, 오류: {}", ex.getKey(), ex.getMessage());

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.INVALID_IDEMPOTENCY_KEY,
                null
        );

        return ResponseEntity.status(OrderResponseCode.INVALID_IDEMPOTENCY_KEY.getHttpStatus())
                .body(response);
    }

    /**
     * 요청 데이터 검증 실패 처리
     *
     * @Valid 어노테이션으로 검증한 요청 데이터가 유효하지 않을 때 발생합니다.
     * 각 필드별 오류 메시지를 자세히 제공합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<List<String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        log.warn("요청 데이터 검증 실패 - 오류 필드 수: {}", ex.getBindingResult().getErrorCount());

        // 실제 받은 객체 로깅
        Object target = ex.getBindingResult().getTarget();
        log.info("🔍 실제 받은 요청 객체: {}", target);

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.toList());

        BaseResponse<List<String>> response = BaseResponse.from(
                OrderResponseCode.INVALID_ORDER_REQUEST,
                errors
        );

        return ResponseEntity.status(OrderResponseCode.INVALID_ORDER_REQUEST.getHttpStatus())
                .body(response);
    }

    /**
     * 잘못된 요청 인수 처리
     *
     * 경로 변수나 요청 파라미터의 타입이 맞지 않을 때 발생합니다.
     * 예: UUID가 필요한데 일반 문자열을 전달한 경우
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        log.warn("잘못된 요청 인수 - 오류: {}, 요청: {}",
                ex.getMessage(), request.getDescription(false));

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.INVALID_ORDER_REQUEST,
                null
        );

        return ResponseEntity.status(OrderResponseCode.INVALID_ORDER_REQUEST.getHttpStatus())
                .body(response);
    }

    /**
     * 데이터베이스 관련 예외 처리
     *
     * JPA, 트랜잭션, 데이터베이스 연결 등의 문제로 발생하는 예외입니다.
     * 사용자에게는 일반적인 메시지를, 로그에는 상세 정보를 기록합니다.
     */
    @ExceptionHandler({
        org.springframework.dao.DataAccessException.class,
        jakarta.persistence.PersistenceException.class,
        org.springframework.transaction.TransactionException.class
    })
    public ResponseEntity<BaseResponse<Void>> handleDatabaseException(
            Exception ex, WebRequest request) {

        log.error("데이터베이스 오류 - 요청: {}, 오류: {}",
                request.getDescription(false), ex.getMessage(), ex);

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.DATABASE_ERROR,
                null
        );

        return ResponseEntity.status(OrderResponseCode.DATABASE_ERROR.getHttpStatus())
                .body(response);
    }

    /**
     * 외부 서비스 연동 실패 처리
     *
     * 다른 마이크로서비스나 외부 API 호출이 실패했을 때 발생합니다.
     * 서비스 복구 안내와 함께 재시도를 유도합니다.
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<BaseResponse<Void>> handleExternalServiceException(
            ExternalServiceException ex, WebRequest request) {

        log.error("외부 서비스 연동 실패 - 서비스: {}, 오류: {}",
                ex.getServiceName(), ex.getMessage(), ex);

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.EXTERNAL_SERVICE_ERROR,
                null
        );

        return ResponseEntity.status(OrderResponseCode.EXTERNAL_SERVICE_ERROR.getHttpStatus())
                .body(response);
    }

    /**
     * 비즈니스 규칙 위반 예외 처리
     *
     * 애플리케이션의 비즈니스 로직 규칙을 위반했을 때 발생합니다.
     * 사용자에게 명확한 규칙 안내를 제공합니다.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessRuleViolationException(
            BusinessRuleViolationException ex, WebRequest request) {

        log.warn("비즈니스 규칙 위반 - 규칙: {}, 오류: {}",
                ex.getRuleName(), ex.getMessage());

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.BUSINESS_RULE_VIOLATION,
                null
        );

        return ResponseEntity.status(OrderResponseCode.BUSINESS_RULE_VIOLATION.getHttpStatus())
                .body(response);
    }

    /**
     * 권한 접근 거부 예외 처리
     *
     * 사용자가 접근 권한이 없는 리소스에 접근하려 할 때 발생합니다.
     * JWT 토큰이 유효하지만 해당 리소스에 대한 권한이 없는 경우입니다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {

        log.warn("권한 접근 거부 - 요청: {}, 메시지: {}",
                request.getDescription(false), ex.getMessage());

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.ORDER_ACCESS_DENIED,
                null
        );

        return ResponseEntity.status(OrderResponseCode.ORDER_ACCESS_DENIED.getHttpStatus())
                .body(response);
    }

    /**
     * 예상하지 못한 모든 예외 처리
     *
     * 위에서 처리되지 않은 모든 예외를 포착합니다.
     * 보안상 상세 정보는 숨기고 일반적인 오류 메시지만 반환합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {

        log.error("예상치 못한 오류 발생 - 요청: {}, 오류: {}",
                request.getDescription(false), ex.getMessage(), ex);

        BaseResponse<Void> response = BaseResponse.from(
                OrderResponseCode.INTERNAL_SERVER_ERROR,
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    // ========================= 유틸리티 메서드 =========================

    /**
     * 필드 오류를 사용자에게 친화적인 메시지로 변환
     * @param error 검증 오류 정보
     * @return 포맷된 오류 메시지
     */
    private String formatFieldError(FieldError error) {
        String fieldName = getKoreanFieldName(error.getField());
        String message = error.getDefaultMessage();

        return String.format("%s: %s", fieldName, message);
    }

    /**
     * 영어 필드명을 한국어로 변환
     * @param fieldName 영어 필드명
     * @return 한국어 필드명
     */
    private String getKoreanFieldName(String fieldName) {
        return switch (fieldName) {
            case "userId" -> "사용자 ID";
            case "orderType" -> "주문 타입";
            case "popupId" -> "팝업 ID";
            case "paymentMethod" -> "결제 방식";
            case "items" -> "주문 항목";
            case "orderItemType" -> "항목 타입";
            case "qty" -> "수량";
            case "unitPrice" -> "단가";
            case "sessionId" -> "세션 ID";
            case "goodsId" -> "상품 변형 ID";
            default -> fieldName;
        };
    }

}
