package com.popcorn.order.event.stock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 재고 차감 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 결제 완료 후 실제 재고 차감이 필요할 때
 * - Store 서비스로 재고 차감 요청
 *
 * [이벤트 수신자]
 * - Store 모듈: 실제 재고 차감 처리
 */
@Getter
@ToString(callSuper = true)
public class StockDeductionRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 차감 요청 항목들 */
    private final List<DeductionItem> deductionItems;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private StockDeductionRequestedEvent(UUID orderId, String orderNo, UUID popupId,
                                       List<DeductionItem> deductionItems, LocalDateTime requestedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.STOCK_DEDUCTION_REQUESTED, userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.deductionItems = deductionItems;
        this.requestedAt = requestedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderNo", orderNo);
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("deductionItemCount", deductionItems != null ? deductionItems.size() : 0);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static StockDeductionRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                    List<DeductionItem> deductionItems) {
        return create(orderId, orderNo, popupId, deductionItems, null);
    }

    public static StockDeductionRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                    List<DeductionItem> deductionItems, Long userId) {
        return new StockDeductionRequestedEvent(orderId, orderNo, popupId, deductionItems,
                                              LocalDateTime.now(), userId);
    }

    /**
     * 차감 항목 정보
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class DeductionItem {
        /** 굿즈 ID */
        private UUID goodsId;

        /** 차감할 수량 */
        private Integer quantity;

        /** 상품명 (로그용) */
        private String productName;

        /** 단가 */
        private Integer unitPrice;

        public static DeductionItem create(UUID goodsId, Integer quantity, String productName, Integer unitPrice) {
            return DeductionItem.builder()
                    .goodsId(goodsId)
                    .quantity(quantity)
                    .productName(productName)
                    .unitPrice(unitPrice)
                    .build();
        }
    }
}