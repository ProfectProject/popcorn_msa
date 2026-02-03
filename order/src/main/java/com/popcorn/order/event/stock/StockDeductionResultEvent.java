package com.popcorn.order.event.stock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 재고 차감 결과 이벤트 (성공/실패 통합)
 *
 * [통합 이벤트의 장점]
 * - 재고 차감 성공/실패를 하나로 관리
 * - status 필드로 결과 분기 처리
 * - 리스너 코드 간소화
 */
@Getter
@ToString(callSuper = true)
public class StockDeductionResultEvent extends BaseOrderEvent {

    /** 차감 결과 상태 */
    private final ResultStatus status;

    /** 주문 번호 (선택적) */
    private final Optional<String> orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 차감된 아이템들 (성공 시) */
    private final List<DeductedItem> deductedItems;

    /** 차감 실패 사유 (실패 시) */
    private final String failureReason;

    /** 실패한 아이템들 (실패 시) */
    private final List<FailedItem> failedItems;

    /** 처리 완료 시간 */
    private final LocalDateTime processedAt;

    /**
     * 생성자
     */
    public StockDeductionResultEvent(UUID orderId, Long customerId, ResultStatus status,
                                   Optional<String> orderNo, UUID popupId,
                                   List<DeductedItem> deductedItems, List<FailedItem> failedItems,
                                   String failureReason, LocalDateTime processedAt) {
        super(orderId, status == ResultStatus.SUCCESS ? "stock-deduction-success" : "stock-deduction-failed", customerId);
        this.status = status;
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.deductedItems = deductedItems;
        this.failedItems = failedItems;
        this.failureReason = failureReason;
        this.processedAt = processedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        orderNo.ifPresent(no -> payload.put("orderNo", no));
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("processedAt", processedAt != null ? processedAt.toString() : null);

        if (status == ResultStatus.SUCCESS) {
            payload.put("deductedItemCount", deductedItems != null ? deductedItems.size() : 0);
        } else {
            payload.put("failureReason", failureReason);
            payload.put("failedItemCount", failedItems != null ? failedItems.size() : 0);
        }

        return payload;
    }

    /**
     * 성공 결과 팩토리 메서드 (orderNo Optional)
     */
    public static StockDeductionResultEvent success(UUID orderId, Long customerId, Optional<String> orderNo,
                                                   UUID popupId, List<DeductedItem> deductedItems) {
        return new StockDeductionResultEvent(orderId, customerId, ResultStatus.SUCCESS,
                orderNo, popupId, deductedItems, null, null, LocalDateTime.now());
    }

    /**
     * 실패 결과 팩토리 메서드 (orderNo Optional)
     */
    public static StockDeductionResultEvent failure(UUID orderId, Long customerId, Optional<String> orderNo,
                                                   UUID popupId, String failureReason,
                                                   List<FailedItem> failedItems) {
        return new StockDeductionResultEvent(orderId, customerId, ResultStatus.FAILED,
                orderNo, popupId, null, failedItems, failureReason, LocalDateTime.now());
    }

    /**
     * 성공/실패 편의 메서드들
     */
    public boolean isSuccess() {
        return status == ResultStatus.SUCCESS;
    }

    public boolean isFailure() {
        return status == ResultStatus.FAILED;
    }

    /**
     * 주문 번호 조회 (Optional에서 값 추출)
     */
    public String getOrderNo() {
        return orderNo.orElse(null);
    }

    /**
     * 결과 상태 Enum
     */
    public enum ResultStatus {
        SUCCESS, FAILED
    }

    /**
     * 차감된 아이템 정보 (성공 시)
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class DeductedItem {
        private String itemType; // GOODS, SESSION
        private UUID itemId; // goodsId or sessionId
        private Integer deductedQuantity;
        private Integer remainingStock;
        private String itemName;
        private LocalDateTime deductedAt;

        public static DeductedItem create(String itemType, UUID itemId, Integer deductedQuantity,
                                        Integer remainingStock, String itemName) {
            return new DeductedItem(itemType, itemId, deductedQuantity, remainingStock,
                                  itemName, LocalDateTime.now());
        }
    }

    /**
     * 차감 실패한 아이템 정보 (실패 시)
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class FailedItem {
        private String itemType; // GOODS, SESSION
        private UUID itemId; // goodsId or sessionId
        private Integer requestedQuantity;
        private Integer availableStock;
        private String itemName;
        private String failureReason;

        public static FailedItem create(String itemType, UUID itemId, Integer requestedQuantity,
                                      Integer availableStock, String itemName, String failureReason) {
            return new FailedItem(itemType, itemId, requestedQuantity, availableStock,
                                itemName, failureReason);
        }
    }
}