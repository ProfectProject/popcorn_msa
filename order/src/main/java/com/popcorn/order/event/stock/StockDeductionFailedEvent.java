package com.popcorn.order.event.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

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
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
public class StockDeductionFailedEvent {

    /** 이벤트 ID (추적용) */
    private String eventId;

    /** 주문 ID */
    private UUID orderId;

    /** 주문 번호 */
    private String orderNo;

    /** 실패 사유 */
    private String reason;

    /** 실패 상세 정보 */
    private String details;

    /** 실패 코드 */
    private String failureCode;

    /** 재고 차감 실패 시간 */
    private LocalDateTime failedAt;

    /** 이벤트 발생 시간 */
    private LocalDateTime eventTime;

    /**
     * Store 모듈에서 발행할 이벤트 생성 팩토리 메서드
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param reason 실패 사유
     * @param failureCode 실패 코드
     * @return StockDeductionFailedEvent
     */
    public static StockDeductionFailedEvent create(UUID orderId, String orderNo,
                                                  String reason, String failureCode) {
        return StockDeductionFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .reason(reason)
                .failureCode(failureCode)
                .failedAt(LocalDateTime.now())
                .eventTime(LocalDateTime.now())
                .build();
    }

    /**
     * 재고 부족으로 인한 실패 이벤트 생성
     */
    public static StockDeductionFailedEvent forInsufficientStock(UUID orderId, String orderNo,
                                                                String details) {
        return create(orderId, orderNo, "재고 부족", "INSUFFICIENT_STOCK")
                .toBuilder()
                .details(details)
                .build();
    }

    /**
     * 시스템 오류로 인한 실패 이벤트 생성
     */
    public static StockDeductionFailedEvent forSystemError(UUID orderId, String orderNo,
                                                          String details) {
        return create(orderId, orderNo, "시스템 오류", "SYSTEM_ERROR")
                .toBuilder()
                .details(details)
                .build();
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