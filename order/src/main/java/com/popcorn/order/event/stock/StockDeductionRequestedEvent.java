package com.popcorn.order.event.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 재고 차감 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 결제 완료 후 재고 차감이 필요할 때
 * - HTTP 동기 호출 대신 비동기 이벤트로 요청
 *
 * [이벤트 수신자]
 * - Store 모듈: 실제 재고 차감 처리
 *
 * [처리 결과]
 * - 성공: StockDeductionSuccessEvent 발행
 * - 실패: StockDeductionFailedEvent 발행
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class StockDeductionRequestedEvent {

    /** 이벤트 ID (추적용) */
    private String eventId;

    /** 주문 ID */
    private UUID orderId;

    /** 주문 번호 */
    private String orderNo;

    /** 팝업 ID */
    private UUID popupId;

    /** 차감 요청 항목들 */
    private List<StockDeductionItem> deductionItems;

    /** 요청 시간 */
    private LocalDateTime requestedAt;

    /**
     * 재고 차감 요청 이벤트 생성
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param popupId 팝업 ID
     * @param deductionItems 차감 항목들
     * @return StockDeductionRequestedEvent
     */
    public static StockDeductionRequestedEvent create(UUID orderId, String orderNo,
                                                    UUID popupId, List<StockDeductionItem> deductionItems) {
        return StockDeductionRequestedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .deductionItems(deductionItems)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 재고 차감 항목
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    public static class StockDeductionItem {
        /** 굿즈 변형 ID */
        private UUID goodsId;

        /** 차감할 수량 */
        private Integer quantity;

        /** 상품명 */
        private String productName;

        /** 변형명 */
        private String variantName;

        public static StockDeductionItem create(UUID goodsId, Integer quantity) {
            return StockDeductionItem.builder()
                    .goodsId(goodsId)
                    .quantity(quantity)
                    .productName("상품명 조회 예정") // Store 서비스에서 조회
                    .variantName("변형명 조회 예정") // Store 서비스에서 조회
                    .build();
        }
    }
}