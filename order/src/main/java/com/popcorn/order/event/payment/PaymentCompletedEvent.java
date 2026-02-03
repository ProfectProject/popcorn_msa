package com.popcorn.order.event.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 완료 이벤트
 *
 * [이벤트 발행 시점]
 * - Payment 모듈에서 실제 결제가 완료된 시점
 * - 토스페이먼츠, 네이버페이 등 PG사로부터 결제 성공 응답 수신 시
 *
 * [이벤트 수신자]
 * - Order 모듈: 주문 상태를 PAID로 변경 및 재고 차감 요청
 * - Notification 모듈: 결제 완료 알림 발송
 * - Analytics 모듈: 매출 집계 및 분석
 *
 * [주의사항]
 * 이 이벤트는 실제 결제가 완료된 후에만 발행되어야 하며,
 * 결제 요청이나 결제 대기 상태에서는 발행하지 않습니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
public class PaymentCompletedEvent {

    /** 이벤트 ID (추적용) */
    private String eventId;

    /** 주문 ID */
    private UUID orderId;

    /** 결제 ID */
    private UUID paymentId;

    /** 결제 키 (PG사 결제 키) */
    private String paymentKey;

    /** 결제 금액 */
    private Integer amount;

    /** 결제 수단 */
    private String paymentMethod;

    /** 결제 완료 시간 */
    private LocalDateTime completedAt;

    /** 이벤트 발생 시간 */
    private LocalDateTime eventTime;

    /** PG사 응답 정보 (원본 응답) */
    private String pgResponse;

    /**
     * Payment 모듈에서 발행할 이벤트 생성 팩토리 메서드
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @param paymentKey 결제 키
     * @param amount 결제 금액
     * @param paymentMethod 결제 수단
     * @return PaymentCompletedEvent
     */
    public static PaymentCompletedEvent create(UUID orderId, UUID paymentId,
                                              String paymentKey, Integer amount,
                                              String paymentMethod) {
        return PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .paymentId(paymentId)
                .paymentKey(paymentKey)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .completedAt(LocalDateTime.now())
                .eventTime(LocalDateTime.now())
                .build();
    }

    /**
     * PG사 응답 정보를 포함한 이벤트 생성
     *
     * @param orderId 주문 ID
     * @param paymentId 결제 ID
     * @param paymentKey 결제 키
     * @param amount 결제 금액
     * @param paymentMethod 결제 수단
     * @param pgResponse PG사 원본 응답
     * @return PaymentCompletedEvent
     */
    public static PaymentCompletedEvent createWithPgResponse(UUID orderId, UUID paymentId,
                                                           String paymentKey, Integer amount,
                                                           String paymentMethod, String pgResponse) {
        return create(orderId, paymentId, paymentKey, amount, paymentMethod)
                .toBuilder()
                .pgResponse(pgResponse)
                .build();
    }
}