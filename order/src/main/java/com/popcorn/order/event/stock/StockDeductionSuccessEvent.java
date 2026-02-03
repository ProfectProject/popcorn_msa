package com.popcorn.order.event.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 차감 성공 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 모듈에서 재고 차감 성공 시
 * - 예약석 확보 성공 시
 * - 굿즈 재고 차감 성공 시
 *
 * [이벤트 수신자]
 * - Order 모듈: 주문 확정 처리 (PAID → CONFIRMED)
 * - Notification 모듈: 주문 확정 알림 발송
 * - Analytics 모듈: 재고 추적 및 분석
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class StockDeductionSuccessEvent {

    /** 이벤트 ID (추적용) */
    private String eventId;

    /** 주문 ID */
    private UUID orderId;

    /** 주문 번호 */
    private String orderNo;

    /** 팝업 ID */
    private UUID popupId;

    /** 차감된 재고 정보 */
    private String stockDetails;

    /** 재고 차감 성공 시간 */
    private LocalDateTime succeededAt;

    /** 이벤트 발생 시간 */
    private LocalDateTime eventTime;

    /**
     * Store 모듈에서 발행할 이벤트 생성 팩토리 메서드
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param popupId 팝업 ID
     * @param stockDetails 차감된 재고 정보
     * @return StockDeductionSuccessEvent
     */
    public static StockDeductionSuccessEvent create(UUID orderId, String orderNo,
                                                   UUID popupId, String stockDetails) {
        return StockDeductionSuccessEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .stockDetails(stockDetails)
                .succeededAt(LocalDateTime.now())
                .eventTime(LocalDateTime.now())
                .build();
    }
}