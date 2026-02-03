package com.popcorn.order.event.stock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 재고 해제 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 주문 취소나 환불 시 차감된 재고를 복구할 때
 * - Store 서비스로 재고 해제(복구) 요청
 *
 * [이벤트 수신자]
 * - Store 모듈: 차감된 재고 복구 처리
 */
@Getter
@ToString(callSuper = true)
public class StockReleaseRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 해제할 재고 항목들 */
    private final List<ReleaseItem> releaseItems;

    /** 해제 사유 */
    private final String reason;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private StockReleaseRequestedEvent(UUID orderId, String orderNo, UUID popupId,
                                     List<ReleaseItem> releaseItems, String reason,
                                     LocalDateTime requestedAt, Long userId) {
        super(orderId, "stock-release-requested", userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.releaseItems = releaseItems;
        this.reason = reason;
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
        payload.put("releaseItemCount", releaseItems != null ? releaseItems.size() : 0);
        payload.put("reason", reason);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static StockReleaseRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                  List<ReleaseItem> releaseItems, String reason) {
        return create(orderId, orderNo, popupId, releaseItems, reason, null);
    }

    public static StockReleaseRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                  List<ReleaseItem> releaseItems, String reason, Long userId) {
        return new StockReleaseRequestedEvent(orderId, orderNo, popupId, releaseItems, reason,
                                            LocalDateTime.now(), userId);
    }

    /**
     * 해제할 재고 항목 정보
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class ReleaseItem {
        /** 굿즈 ID */
        private UUID goodsId;

        /** 해제할 수량 */
        private Integer quantity;

        /** 상품명 (로그용) */
        private String productName;

        /** 단가 */
        private Integer unitPrice;

        /** 원래 차감된 시점 */
        private LocalDateTime originalDeductedAt;

        public static ReleaseItem create(UUID goodsId, Integer quantity, String productName,
                                       Integer unitPrice, LocalDateTime originalDeductedAt) {
            return ReleaseItem.builder()
                    .goodsId(goodsId)
                    .quantity(quantity)
                    .productName(productName)
                    .unitPrice(unitPrice)
                    .originalDeductedAt(originalDeductedAt)
                    .build();
        }
    }
}