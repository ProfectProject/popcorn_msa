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
 * 재고 차감 성공 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 재고 차감이 성공했을 때
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 재고 차감 성공 처리 및 주문 완료
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - stockDetails: 차감된 재고 상세 정보
 * - committedAt: 차감 확정 시간
 */
@Getter
@ToString(callSuper = true)
public class StockDeductionSucceededEvent extends BaseOrderEvent {

    /** 차감된 재고 상세 정보 */
    private final List<StockDetail> stockDetails;

    /** 차감 확정 시간 */
    private final LocalDateTime committedAt;

    private StockDeductionSucceededEvent(UUID orderId, List<StockDetail> stockDetails,
                                       LocalDateTime committedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.STOCK_DEDUCTION_SUCCEEDED, userId);
        this.stockDetails = stockDetails;
        this.committedAt = committedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("stockDetailCount", stockDetails != null ? stockDetails.size() : 0);
        payload.put("committedAt", committedAt != null ? committedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static StockDeductionSucceededEvent create(UUID orderId, List<StockDetail> stockDetails,
                                                     LocalDateTime committedAt) {
        return create(orderId, stockDetails, committedAt, null);
    }

    public static StockDeductionSucceededEvent create(UUID orderId, List<StockDetail> stockDetails,
                                                     LocalDateTime committedAt, Long userId) {
        return new StockDeductionSucceededEvent(orderId, stockDetails, committedAt, userId);
    }

    /**
     * 재고 상세 정보
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class StockDetail {
        /** 굿즈 ID */
        private UUID goodsId;

        /** 차감된 수량 */
        private Integer deductedQuantity;

        /** 차감 전 재고 */
        private Integer previousStock;

        /** 차감 후 재고 */
        private Integer currentStock;

        /** 굿즈명 */
        private String goodsName;

        /** 차감 시간 */
        private LocalDateTime deductedAt;

        public static StockDetail create(UUID goodsId, Integer deductedQuantity,
                                       Integer previousStock, Integer currentStock,
                                       String goodsName) {
            return StockDetail.builder()
                    .goodsId(goodsId)
                    .deductedQuantity(deductedQuantity)
                    .previousStock(previousStock)
                    .currentStock(currentStock)
                    .goodsName(goodsName)
                    .deductedAt(LocalDateTime.now())
                    .build();
        }
    }
}