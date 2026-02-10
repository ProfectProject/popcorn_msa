package com.popcorn.order.constants;

/**
 * 주문 서비스 전용 이벤트 상수들
 * 실제 Order 서비스에서 발행/수신하는 이벤트만 포함
 */
public final class EventConstants {

    private EventConstants() {
        // Utility class
    }

    /**
     * 이벤트 타입들 (UPPER_SNAKE_CASE)
     * Order 서비스가 실제로 사용하는 이벤트만 정의
     */
    public static final class EventTypes {

        // ================ Order가 발행하는 이벤트들 ================

        // Order events (order-events topic) - UPPER_SNAKE_CASE
        public static final String ORDER_CREATED = "ORDER_CREATED";
        public static final String ORDER_PAID = "ORDER_PAID";
        public static final String ORDER_STATUS_UPDATED = "ORDER_STATUS_UPDATED";  // 표준 이름
        public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
        public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

        // Payment requests (payment-requests topic)
        public static final String PAYMENT_CANCEL_REQUESTED = "PAYMENT_CANCEL_REQUESTED";
        public static final String PAYMENT_CREATE_REQUESTED = "PAYMENT_CREATE_REQUESTED";

        // Store requests (store-requests topic)
        public static final String STOCK_DEDUCTION_REQUESTED = "STOCK_DEDUCTION_REQUESTED";
        public static final String SCHEDULE_CONFIRMATION_REQUESTED = "SCHEDULE_CONFIRMATION_REQUESTED";
        public static final String GOODS_RESERVATION_REQUESTED = "GOODS_RESERVATION_REQUESTED";
        public static final String SCHEDULE_RESERVATION_REQUESTED = "SCHEDULE_RESERVATION_REQUESTED";
        public static final String GOODS_RESERVATION_CANCEL_REQUESTED = "GOODS_RESERVATION_CANCEL_REQUESTED";
        public static final String SCHEDULE_RESERVATION_CANCEL_REQUESTED = "SCHEDULE_RESERVATION_CANCEL_REQUESTED";
        public static final String STOCK_RELEASE_REQUESTED = "STOCK_RELEASE_REQUESTED";
        public static final String SCHEDULE_RELEASE_REQUESTED = "SCHEDULE_RELEASE_REQUESTED";
        public static final String MIXED_RESERVATION_REQUESTED = "MIXED_RESERVATION_REQUESTED";

        // ================ Order가 수신하는 이벤트들 ================

        // Payment events (payment-events topic)
        public static final String PAYMENT_CREATED = "PAYMENT_CREATED";
        public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
        public static final String PAYMENT_APPROVED = "PAYMENT_APPROVED";
        public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
        public static final String PAYMENT_CANCELLED = "PAYMENT_CANCELLED";
        public static final String PAYMENT_USER_CANCELLED = "PAYMENT_USER_CANCELLED";
        public static final String PAYMENT_CANCEL_SUCCEEDED = "PAYMENT_CANCEL_SUCCEEDED";
        public static final String PAYMENT_CANCEL_FAILED = "PAYMENT_CANCEL_FAILED";
        public static final String PAYMENT_VALIDATION_FAILED = "PAYMENT_VALIDATION_FAILED";
        public static final String ORDER_COMPENSATION_REQUESTED = "ORDER_COMPENSATION_REQUESTED";

        // Store events (store-events topic)
        public static final String GOODS_RESERVATION_SUCCEEDED = "GOODS_RESERVATION_SUCCEEDED";
        public static final String GOODS_RESERVATION_FAILED = "GOODS_RESERVATION_FAILED";
        public static final String SCHEDULE_RESERVATION_SUCCEEDED = "SCHEDULE_RESERVATION_SUCCEEDED";
        public static final String SCHEDULE_RESERVATION_FAILED = "SCHEDULE_RESERVATION_FAILED";
        public static final String STOCK_DEDUCTION_SUCCEEDED = "STOCK_DEDUCTION_SUCCEEDED";
        public static final String STOCK_DEDUCTION_FAILED = "STOCK_DEDUCTION_FAILED";
        public static final String SCHEDULE_CONFIRMATION_SUCCEEDED = "SCHEDULE_CONFIRMATION_SUCCEEDED";
        public static final String SCHEDULE_CONFIRMATION_FAILED = "SCHEDULE_CONFIRMATION_FAILED";
        public static final String MIXED_RESERVATION_SUCCEEDED = "MIXED_RESERVATION_SUCCEEDED";
        public static final String MIXED_RESERVATION_FAILED = "MIXED_RESERVATION_FAILED";
        public static final String RESERVATION_EXPIRED = "RESERVATION_EXPIRED";
        public static final String STOCK_RELEASED = "STOCK_RELEASED";
        public static final String SCHEDULE_RELEASED = "SCHEDULE_RELEASED";
        public static final String POPUP_CREATED = "POPUP_CREATED";
        public static final String POPUP_STATUS_UPDATED = "POPUP_STATUS_UPDATED";
        public static final String POPUP_INFO_UPDATED = "POPUP_INFO_UPDATED";


        // CheckIn events (checkin-events topic)
        public static final String QR_GENERATED = "QR_GENERATED";
        public static final String CHECKIN_CREATED = "CHECKIN_CREATED";

        // Order requests (order-requests topic)
        public static final String ORDER_INFO_REQUESTED = "ORDER_INFO_REQUESTED";
        public static final String ORDER_QUERY_REQUESTED = "ORDER_QUERY_REQUESTED";

        // ================ 하위 호환성 ================

        // 기존 코드 호환성을 위한 별칭 (점진적 마이그레이션용)
        public static final String ORDER_STATUS_CHANGED = ORDER_STATUS_UPDATED;  // 별칭

        private EventTypes() {}
    }

    /**
     * Kafka Topic 이름들
     */
    public static final class Topics {

        // Order가 발행하는 토픽들
        public static final String ORDER_EVENTS = "order-events";
        public static final String ORDER_REQUESTS = "order-requests";

        // Order가 구독하는 외부 토픽들
        public static final String PAYMENT_EVENTS = "payment-events";
        public static final String PAYMENT_REQUESTS = "payment-requests";
        public static final String STORE_EVENTS = "store-events";
        public static final String STORE_REQUESTS = "store-requests";
        public static final String CHECKIN_EVENTS = "checkin-events";
        public static final String CHECKIN_REQUESTS = "checkin-requests";

        private Topics() {}
    }

    /**
     * Consumer Group 이름들
     */
    public static final class ConsumerGroups {

        public static final String ORDER_CG = "order-cg";

        private ConsumerGroups() {}
    }
}
