package com.popcorn.order.event.stock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Builder;
import lombok.Getter;

/**
 * 재고 차감 실패 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 모듈에서 재고 차감 실패 시
 * - 재고 부족, 시스템 오류 등으로 재고 차감이 불가능한 경우
 *
 * [이벤트 수신자]
 * - Order 모듈: 주문 취소 처리
 * - Payment 모듈: 결제 취소 또는 환불 처리
 * - Notification 모듈: 고객에게 실패 알림 발송
 *
 * [보상 트랜잭션]
 * 이 이벤트는 SAGA 패턴의 보상 트랜잭션을 트리거하여
 * 이미 완료된 작업들을 롤백합니다.
 */
@Getter
public class StockDeductionFailedEvent extends BaseOrderEvent {

    /** 실패 사유 */
    private final String reason;

    /** 실패 상세 정보 */
    private final String details;

    /** 실패 코드 */
    private final String failureCode;

    /** 재고 차감 실패 시간 */
    private final LocalDateTime failedAt;

    /** 이벤트 발생 시간 */
    private final LocalDateTime eventTime;

    private StockDeductionFailedEvent(UUID orderId, String reason, String details, String failureCode,
                                    LocalDateTime failedAt, LocalDateTime eventTime, Long userId) {
        super(orderId, "stock-deduction-failed", userId);
        this.reason = reason;
        this.details = details;
        this.failureCode = failureCode;
        this.failedAt = failedAt;
        this.eventTime = eventTime;
    }

    /**
     * Store 모듈에서 발행할 이벤트 생성 팩토리 메서드
     *
     * @param orderId 주문 ID
     * @param reason 실패 사유
     * @param failureCode 실패 코드
     * @return StockDeductionFailedEvent
     */
    public static StockDeductionFailedEvent create(UUID orderId, String reason, String failureCode) {
        return create(orderId, reason, failureCode, null, null);
    }

    public static StockDeductionFailedEvent create(UUID orderId, String reason, String failureCode,
                                                  String details, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return new StockDeductionFailedEvent(orderId, reason, details, failureCode, now, now, userId);
    }

    /**
     * 재고 부족으로 인한 실패 이벤트 생성
     */
    public static StockDeductionFailedEvent forInsufficientStock(UUID orderId, String details) {
        return create(orderId, "재고 부족", "INSUFFICIENT_STOCK", details, null);
    }

    /**
     * 시스템 오류로 인한 실패 이벤트 생성
     */
    public static StockDeductionFailedEvent forSystemError(UUID orderId, String details) {
        return create(orderId, "시스템 오류", "SYSTEM_ERROR", details, null);
    }

    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", getOrderId().toString());
        payload.put("reason", reason);
        payload.put("details", details);
        payload.put("failureCode", failureCode);
        payload.put("failedAt", failedAt.toString());
        payload.put("eventTime", eventTime.toString());
        payload.put("isInsufficientStock", isInsufficientStock());
        payload.put("isSystemError", isSystemError());
        payload.put("isRetryable", isRetryable());
        return payload;
    }

    /**
     * 재고 차감 실패가 재고 부족으로 인한 것인지 확인
     */
    public boolean isInsufficientStock() {
        return "INSUFFICIENT_STOCK".equals(failureCode);
    }

    /**
     * 재고 차감 실패가 시스템 오류로 인한 것인지 확인
     */
    public boolean isSystemError() {
        return "SYSTEM_ERROR".equals(failureCode);
    }

    /**
     * 재시도 가능한 실패인지 확인
     */
    public boolean isRetryable() {
        return "SYSTEM_ERROR".equals(failureCode);
    }
}