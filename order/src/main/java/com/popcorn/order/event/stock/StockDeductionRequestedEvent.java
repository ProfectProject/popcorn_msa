package com.popcorn.order.event.stock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Builder;
import lombok.Getter;

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
public class StockDeductionRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 차감 요청 항목들 */
    private final List<StockDeductionItem> deductionItems;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private StockDeductionRequestedEvent(UUID orderId, String orderNo, UUID popupId, List<StockDeductionItem> deductionItems,
                                       LocalDateTime requestedAt, Long userId) {
        super(orderId, "stock-deduction-requested", userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.deductionItems = deductionItems;
        this.requestedAt = requestedAt;
    }

    /**
     * 재고 차감 요청 이벤트 생성
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param popupId 팝업 ID
     * @param deductionItems 차감 항목들
     * @return StockDeductionRequestedEvent
     */
    public static StockDeductionRequestedEvent create(UUID orderId, String orderNo, UUID popupId, List<StockDeductionItem> deductionItems) {
        return create(orderId, orderNo, popupId, deductionItems, null);
    }

    public static StockDeductionRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                    List<StockDeductionItem> deductionItems, Long userId) {
        return new StockDeductionRequestedEvent(orderId, orderNo, popupId, deductionItems, LocalDateTime.now(), userId);
    }

    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", getOrderId().toString());
        payload.put("popupId", popupId.toString());
        payload.put("deductionItems", deductionItems);
        payload.put("requestedAt", requestedAt.toString());
        return payload;
    }

    /**
     * 재고 차감 항목
     */
    @Getter
    @Builder
    public static class StockDeductionItem {
        /** 굿즈 변형 ID */
        private final UUID goodsId;

        /** 차감할 수량 */
        private final Integer quantity;

        /** 상품명 */
        private final String productName;

        /** 변형명 */
        private final String variantName;

        public StockDeductionItem(UUID goodsId, Integer quantity, String productName, String variantName) {
            this.goodsId = goodsId;
            this.quantity = quantity;
            this.productName = productName;
            this.variantName = variantName;
        }

        public static StockDeductionItem create(UUID goodsId, Integer quantity) {
            return new StockDeductionItem(goodsId, quantity,
                    "상품명 조회 예정", "변형명 조회 예정"); // Store 서비스에서 조회
        }
    }
}