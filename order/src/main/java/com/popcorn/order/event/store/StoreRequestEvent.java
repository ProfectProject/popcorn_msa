package com.popcorn.order.event.store;

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
 * Store 서비스 요청 통합 이벤트
 *
 * [통합된 요청 타입들]
 * - GOODS_RESERVATION: 굿즈 재고 예약 요청
 * - SCHEDULE_RESERVATION: 스케줄 예약 요청
 * - STOCK_DEDUCTION: 재고 차감 요청
 * - RESERVATION_CANCEL: 예약 취소 요청
 * - SCHEDULE_CONFIRMATION: 스케줄 확정 요청
 * - STOCK_RELEASE: 재고 해제 요청
 *
 * [통합 이벤트의 장점]
 * - 모든 Store 서비스 요청을 하나로 관리
 * - requestType으로 요청 타입 구분
 * - 리스너 코드 간소화
 * - 일관된 처리 패턴 제공
 */
@Getter
@ToString(callSuper = true)
public class StoreRequestEvent extends BaseOrderEvent {

    /** 요청 타입 */
    private final RequestType requestType;

    /** 주문 번호 (선택적) */
    private final Optional<String> orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 요청 항목들 (타입별로 다름) */
    private final List<RequestItem> requestItems;

    /** 취소 사유 (취소 요청시) */
    private final String cancelReason;

    /** 예약 토큰 (취소 요청시) */
    private final String reservationToken;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    /**
     * 생성자
     */
    public StoreRequestEvent(UUID orderId, Long customerId, RequestType requestType,
                           Optional<String> orderNo, UUID popupId, List<RequestItem> requestItems,
                           String cancelReason, String reservationToken, LocalDateTime requestedAt) {
        super(orderId, generateEventType(requestType), customerId);
        this.requestType = requestType;
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.requestItems = requestItems;
        this.cancelReason = cancelReason;
        this.reservationToken = reservationToken;
        this.requestedAt = requestedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestType", requestType.name());
        orderNo.ifPresent(no -> payload.put("orderNo", no));
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        payload.put("itemCount", requestItems != null ? requestItems.size() : 0);

        if (requestType == RequestType.RESERVATION_CANCEL) {
            payload.put("cancelReason", cancelReason);
            payload.put("reservationToken", reservationToken);
        }

        return payload;
    }

    /**
     * 굿즈 예약 요청 이벤트 생성
     */
    public static StoreRequestEvent goodsReservation(UUID orderId, Long customerId, Optional<String> orderNo,
                                                   UUID popupId, List<RequestItem> items) {
        return new StoreRequestEvent(orderId, customerId, RequestType.GOODS_RESERVATION,
                orderNo, popupId, items, null, null, LocalDateTime.now());
    }

    /**
     * 스케줄 예약 요청 이벤트 생성
     */
    public static StoreRequestEvent scheduleReservation(UUID orderId, Long customerId, Optional<String> orderNo,
                                                      UUID popupId, List<RequestItem> items) {
        return new StoreRequestEvent(orderId, customerId, RequestType.SCHEDULE_RESERVATION,
                orderNo, popupId, items, null, null, LocalDateTime.now());
    }

    /**
     * 재고 차감 요청 이벤트 생성
     */
    public static StoreRequestEvent stockDeduction(UUID orderId, Long customerId, Optional<String> orderNo,
                                                 UUID popupId, List<RequestItem> items) {
        return new StoreRequestEvent(orderId, customerId, RequestType.STOCK_DEDUCTION,
                orderNo, popupId, items, null, null, LocalDateTime.now());
    }

    /**
     * 예약 취소 요청 이벤트 생성
     */
    public static StoreRequestEvent reservationCancel(UUID orderId, Long customerId, Optional<String> orderNo,
                                                     UUID popupId, String cancelReason, String reservationToken,
                                                     List<RequestItem> items) {
        return new StoreRequestEvent(orderId, customerId, RequestType.RESERVATION_CANCEL,
                orderNo, popupId, items, cancelReason, reservationToken, LocalDateTime.now());
    }

    /**
     * 편의 메서드들
     */
    public boolean isReservationRequest() {
        return requestType == RequestType.GOODS_RESERVATION || requestType == RequestType.SCHEDULE_RESERVATION;
    }

    public boolean isCancelRequest() {
        return requestType == RequestType.RESERVATION_CANCEL;
    }

    public boolean isDeductionRequest() {
        return requestType == RequestType.STOCK_DEDUCTION;
    }

    /**
     * 주문 번호 조회 (Optional에서 값 추출)
     */
    public String getOrderNo() {
        return orderNo.orElse(null);
    }

    /**
     * 이벤트 타입 생성 헬퍼
     */
    private static String generateEventType(RequestType requestType) {
        switch (requestType) {
            case GOODS_RESERVATION: return "goods-reservation-requested";
            case SCHEDULE_RESERVATION: return "schedule-reservation-requested";
            case STOCK_DEDUCTION: return "stock-deduction-requested";
            case RESERVATION_CANCEL: return "reservation-cancel-requested";
            case SCHEDULE_CONFIRMATION: return "schedule-confirmation-requested";
            case STOCK_RELEASE: return "stock-release-requested";
            default: return "store-request";
        }
    }

    /**
     * 요청 타입 Enum
     */
    public enum RequestType {
        GOODS_RESERVATION,      // 굿즈 재고 예약
        SCHEDULE_RESERVATION,   // 스케줄 예약
        STOCK_DEDUCTION,        // 재고 차감
        RESERVATION_CANCEL,     // 예약 취소
        SCHEDULE_CONFIRMATION,  // 스케줄 확정
        STOCK_RELEASE          // 재고 해제
    }

    /**
     * 요청 항목 정보 (범용)
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class RequestItem {
        private String itemType;        // GOODS, SESSION
        private UUID itemId;            // goodsId or sessionId
        private Integer quantity;       // 요청 수량
        private String itemName;        // 아이템명 (로그용)
        private String productName;     // 상품명 (로그용)
        private Integer unitPrice;      // 단가 (차감 요청시)
        private LocalDateTime sessionTime; // 세션 시간 (스케줄 요청시)

        public static RequestItem createGoods(UUID goodsId, Integer quantity, String productName, Integer unitPrice) {
            return RequestItem.builder()
                    .itemType("GOODS")
                    .itemId(goodsId)
                    .quantity(quantity)
                    .itemName(productName)
                    .productName(productName)
                    .unitPrice(unitPrice)
                    .build();
        }

        public static RequestItem createSession(UUID sessionId, Integer quantity, String sessionName,
                                              LocalDateTime sessionTime) {
            return RequestItem.builder()
                    .itemType("SESSION")
                    .itemId(sessionId)
                    .quantity(quantity)
                    .itemName(sessionName)
                    .sessionTime(sessionTime)
                    .build();
        }
    }
}