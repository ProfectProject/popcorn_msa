package com.popcorn.order.constants;

/**
 * 주문 서비스 전용 이벤트 상수들
 * 각 서비스에서 자체적으로 관리하는 이벤트 타입들
 */
public final class EventConstants {

    private EventConstants() {
        // Utility class
    }

    /**
     * 주문 관련 이벤트 타입들 (kebab-case)
     */
    public static final class EventTypes {

        public static final String ORDER_CREATED = "order-created";
        public static final String ORDER_STATUS_CHANGED = "order-status-changed";
        public static final String ORDER_CANCELLED = "order-cancelled";
        public static final String ORDER_COMPLETED = "order-completed";
        public static final String ORDER_PAID = "order-paid";

        private EventTypes() {}
    }

    /**
     * Redis Stream 이름들
     */
    public static final class Streams {

        public static final String ORDER_EVENTS = "order-events";
        public static final String SCHEDULE_EVENTS = "schedule-events";

        private Streams() {}
    }

    /**
     * 이벤트 상태들
     */
    public static final class EventStatus {

        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String PENDING = "PENDING";
        public static final String CANCELLED = "CANCELLED";

        private EventStatus() {}
    }
}