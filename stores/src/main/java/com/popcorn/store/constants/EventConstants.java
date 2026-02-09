package com.popcorn.store.constants;

/**
 * 상점 서비스 전용 이벤트 상수들
 * 실제 Stores 서비스에서 발행/수신하는 이벤트만 포함
 */
public final class EventConstants {

    private EventConstants() {
        // Utility class
    }

    /**
     * 이벤트 타입들 (UPPERCASE)
     * Stores 서비스가 실제로 사용하는 이벤트만 정의
     */
    public static final class EventTypes {

        // ================ Stores가 발행하는 이벤트들 ================

        // Store events (store-events stream)
        public static final String GOODS_RESERVATION_SUCCEEDED = "GOODS_RESERVATION_SUCCEEDED";
        public static final String GOODS_RESERVATION_FAILED = "GOODS_RESERVATION_FAILED";
        public static final String SCHEDULE_RESERVATION_SUCCEEDED = "SCHEDULE_RESERVATION_SUCCEEDED";
        public static final String SCHEDULE_RESERVATION_FAILED = "SCHEDULE_RESERVATION_FAILED";
        public static final String MIXED_RESERVATION_SUCCEEDED = "MIXED_RESERVATION_SUCCEEDED";
        public static final String MIXED_RESERVATION_FAILED = "MIXED_RESERVATION_FAILED";
        public static final String RESERVATION_EXPIRED = "RESERVATION_EXPIRED";
        public static final String STOCK_DEDUCTION_SUCCEEDED = "STOCK_DEDUCTION_SUCCEEDED";
        public static final String STOCK_DEDUCTION_FAILED = "STOCK_DEDUCTION_FAILED";
        public static final String SCHEDULE_CONFIRMATION_SUCCEEDED = "SCHEDULE_CONFIRMATION_SUCCEEDED";
        public static final String SCHEDULE_CONFIRMATION_FAILED = "SCHEDULE_CONFIRMATION_FAILED";
        public static final String INVENTORY_CONFIRMATION_SUCCEEDED = "INVENTORY_CONFIRMATION_SUCCEEDED";
        public static final String INVENTORY_CONFIRMATION_FAILED = "INVENTORY_CONFIRMATION_FAILED";
        public static final String STOCK_RELEASED = "STOCK_RELEASED";
        public static final String SCHEDULE_RELEASED = "SCHEDULE_RELEASED";

        // Popup events (store-events stream)
        public static final String POPUP_CREATED = "POPUP_CREATED";
        public static final String POPUP_STATUS_UPDATED = "POPUP_STATUS_UPDATED";
        public static final String POPUP_INFO_UPDATED = "POPUP_INFO_UPDATED";

        // Response events (store-responses stream)
        public static final String PRICE_LOOKUP_RESPONSE = "PRICE_LOOKUP_RESPONSE";
        public static final String POPUP_INFO_LOOKUP_RESPONSE = "POPUP_INFO_LOOKUP_RESPONSE";
        public static final String GOODS_INFO_RESPONSE = "GOODS_INFO_RESPONSE";
        public static final String STOCK_STATUS_RESPONSE = "STOCK_STATUS_RESPONSE";

        // ================ Stores가 수신하는 이벤트들 ================

        // Order events (order-events stream)
        public static final String ORDER_PAID = "ORDER_PAID";
        public static final String ORDER_CREATED = "ORDER_CREATED";
        public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

        // Request events (store-requests stream)
        public static final String GOODS_RESERVATION_REQUESTED = "GOODS_RESERVATION_REQUESTED";
        public static final String SCHEDULE_RESERVATION_REQUESTED = "SCHEDULE_RESERVATION_REQUESTED";
        public static final String MIXED_RESERVATION_REQUESTED = "MIXED_RESERVATION_REQUESTED";
        public static final String GOODS_RESERVATION_CANCEL_REQUESTED = "GOODS_RESERVATION_CANCEL_REQUESTED";
        public static final String SCHEDULE_RESERVATION_CANCEL_REQUESTED = "SCHEDULE_RESERVATION_CANCEL_REQUESTED";
        public static final String STOCK_DEDUCTION_REQUESTED = "STOCK_DEDUCTION_REQUESTED";
        public static final String SCHEDULE_CONFIRMATION_REQUESTED = "SCHEDULE_CONFIRMATION_REQUESTED";
        public static final String INVENTORY_CONFIRMATION_REQUESTED = "INVENTORY_CONFIRMATION_REQUESTED";
        public static final String PRICE_LOOKUP_REQUESTED = "PRICE_LOOKUP_REQUESTED";
        public static final String POPUP_INFO_LOOKUP_REQUESTED = "POPUP_INFO_LOOKUP_REQUESTED";
        public static final String STOCK_RELEASE_REQUESTED = "STOCK_RELEASE_REQUESTED";
        public static final String SCHEDULE_RELEASE_REQUESTED = "SCHEDULE_RELEASE_REQUESTED";

        // Payment events (payment-events stream)
        public static final String PAYMENT_APPROVED = "PAYMENT_APPROVED";
        public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
        public static final String PAYMENT_CANCELLED = "PAYMENT_CANCELLED";


        private EventTypes() {}
    }

    /**
     * Redis Stream 이름들
     */
    public static final class Streams {

        // Stores가 발행하는 스트림들
        public static final String STORE_EVENTS = "store-events";
        public static final String STORE_RESPONSES = "store-responses";

        // Stores가 구독하는 외부 스트림들
        public static final String ORDER_EVENTS = "order-events";
        public static final String STORE_REQUESTS = "store-requests";
        public static final String PAYMENT_EVENTS = "payment-events";
        public static final String INVENTORY_EVENTS = "inventory-events";

        private Streams() {}
    }

    /**
     * Consumer Group 이름들
     */
    public static final class ConsumerGroups {

        public static final String STORE_CG = "store-cg";
        public static final String STORE_SERVICE_GROUP = "store-service-group";

        private ConsumerGroups() {}
    }

    /**
     * Consumer 이름들
     */
    public static final class Consumers {

        public static final String STORE_EVENTS_CONSUMER = "store-events-consumer-1";
        public static final String STORE_REQUESTS_CONSUMER = "store-requests-consumer-1";

        private Consumers() {}
    }

    /**
     * 이벤트 상태들
     */
    public static final class EventStatus {

        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String PENDING = "PENDING";
        public static final String PROCESSING = "PROCESSING";
        public static final String RESERVED = "RESERVED";
        public static final String CONFIRMED = "CONFIRMED";
        public static final String CANCELLED = "CANCELLED";
        public static final String EXPIRED = "EXPIRED";
        public static final String RELEASED = "RELEASED";

        private EventStatus() {}
    }

    /**
     * Aggregate Type 상수들
     */
    public static final class AggregateTypes {

        public static final String POPUP = "Popup";
        public static final String GOODS = "Goods";
        public static final String GOODS_VARIANT = "GoodsVariant";
        public static final String POPUP_SCHEDULE = "PopupSchedule";
        public static final String STORE = "Store";
        public static final String INVENTORY = "Inventory";

        private AggregateTypes() {}
    }

    /**
     * 이벤트 메타데이터 키 상수들
     */
    public static final class MetadataKeys {

        public static final String EVENT_TYPE = "eventType";
        public static final String EVENT_ID = "eventId";
        public static final String AGGREGATE_ID = "aggregateId";
        public static final String AGGREGATE_TYPE = "aggregateType";
        public static final String CORRELATION_ID = "correlationId";
        public static final String TIMESTAMP = "timestamp";
        public static final String EVENT_VERSION = "eventVersion";
        public static final String USER_ID = "userId";
        public static final String SOURCE_SERVICE = "sourceService";
        public static final String TARGET_SERVICE = "targetService";
        public static final String ORDER_ID = "orderId";
        public static final String ORDER_NO = "orderNo";
        public static final String POPUP_ID = "popupId";
        public static final String GOODS_ID = "goodsId";
        public static final String SCHEDULE_ID = "scheduleId";
        public static final String QUANTITY = "quantity";
        public static final String PRICE = "price";
        public static final String STOCK_QUANTITY = "stockQuantity";

        private MetadataKeys() {}
    }

    /**
     * 재고 상태 상수들
     */
    public static final class StockStatus {

        public static final String AVAILABLE = "AVAILABLE";
        public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
        public static final String RESERVED = "RESERVED";
        public static final String CONFIRMED = "CONFIRMED";
        public static final String LOW_STOCK = "LOW_STOCK";

        private StockStatus() {}
    }

    /**
     * 예약 타입 상수들
     */
    public static final class ReservationType {

        public static final String GOODS_ONLY = "GOODS_ONLY";
        public static final String SCHEDULE_ONLY = "SCHEDULE_ONLY";
        public static final String MIXED = "MIXED";

        private ReservationType() {}
    }
}
