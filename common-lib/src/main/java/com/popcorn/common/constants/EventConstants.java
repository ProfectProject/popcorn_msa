package com.popcorn.common.constants;

/**
 * 마이크로서비스 간 이벤트 관련 공통 상수들
 *
 * Redis Stream 이름, 이벤트 타입, Consumer Group 등을 중앙에서 관리하여
 * 도메인 간 일관성을 보장하고 변경 시 영향도를 최소화합니다.
 */
public final class EventConstants {

    private EventConstants() {
        // Utility class
    }

    /**
     * Redis Stream 이름 상수들
     */
    public static final class Streams {

        // ================ Payment Domain Streams ================
        public static final String PAYMENT_EVENTS = "payment-events";
        public static final String PAYMENT_REQUESTS = "payment-requests";

        // ================ Order Domain Streams ================
        public static final String ORDER_EVENTS = "order-events";
        public static final String ORDER_REQUESTS = "order-requests";

        // ================ CheckIn Domain Streams ================
        public static final String CHECKIN_EVENTS = "checkin-events";
        public static final String CHECKIN_REQUESTS = "checkin-requests";
        public static final String STANDARD_CHECKINS_EVENTS = "standard-checkins-events";

        // ================ Store Domain Streams ================
        public static final String STOCK_EVENTS = "stock-events";
        public static final String INVENTORY_EVENTS = "inventory-events";
        public static final String GOODS_EVENTS = "goods-events";
        public static final String SCHEDULE_EVENTS = "schedule-events";
        public static final String MIXED_EVENTS = "mixed-events";
        public static final String PRICE_EVENTS = "price-events";
        public static final String STORE_LOOKUP_REQUESTS = "store-lookup-requests";
        public static final String STORE_LOOKUP_RESPONSES = "store-lookup-responses";
        public static final String RESPONSE_EVENTS = "response-events";

        // ================ Query Domain Streams ================
        public static final String ORDER_QUERY_STREAM = "order:query:stream";
        public static final String ORDER_UPDATE_STREAM = "order:update:stream";

        private Streams() {}
    }

    /**
     * Consumer Group 이름 상수들
     */
    public static final class ConsumerGroups {

        public static final String PAYMENT_SERVICE_GROUP = "payment-service-group";
        public static final String ORDER_SERVICE_GROUP = "order-service-group";
        public static final String CHECKIN_SERVICE_GROUP = "checkin-service-group";
        public static final String STORE_SERVICE_GROUP = "store-service-group";
        public static final String QUERY_SERVICE_GROUP = "query-service-group";

        private ConsumerGroups() {}
    }

    /**
     * Consumer 이름 상수들
     */
    public static final class Consumers {

        // Payment 도메인 Consumer들
        public static final String PAYMENT_EVENTS_CONSUMER = "payment-events-consumer-1";
        public static final String PAYMENT_REQUESTS_CONSUMER = "payment-requests-consumer-1";

        // Order 도메인 Consumer들
        public static final String ORDER_EVENTS_CONSUMER = "order-events-consumer-1";
        public static final String ORDER_REQUESTS_CONSUMER = "order-requests-consumer-1";

        // CheckIn 도메인 Consumer들
        public static final String CHECKIN_EVENTS_CONSUMER = "checkin-events-consumer-1";
        public static final String CHECKIN_REQUESTS_CONSUMER = "checkin-requests-consumer-1";
        public static final String STANDARD_CONSUMER = "standard-consumer-1";

        // Store 도메인 Consumer들
        public static final String STORE_EVENTS_CONSUMER = "store-events-consumer-1";
        public static final String STOCK_CONSUMER = "stock-consumer-1";
        public static final String INVENTORY_CONSUMER = "inventory-consumer-1";

        private Consumers() {}
    }

    /**
     * 이벤트 타입 상수들 (kebab-case)
     */
    public static final class EventTypes {

        // ================ Payment Events ================
        public static final String PAYMENT_CREATED = "payment-created";
        public static final String PAYMENT_APPROVED = "payment-approved";
        public static final String PAYMENT_FAILED = "payment-failed";
        public static final String PAYMENT_CANCELLED = "payment-cancelled";
        public static final String PAYMENT_USER_CANCELLED = "payment-user-cancelled";
        public static final String PAYMENT_CANCEL_SUCCEEDED = "payment-cancel-succeeded";
        public static final String PAYMENT_CREATE_REQUESTED = "payment-create-requested";
        public static final String PAYMENT_CANCEL_REQUESTED = "payment-cancel-requested";
        public static final String PAYMENT_CANCEL_RETRY = "payment-cancel-retry";
        public static final String PAYMENT_CANCEL_FINAL_FAILURE = "payment-cancel-final-failure";

        // ================ Order Events ================
        public static final String ORDER_CREATED = "order-created";
        public static final String ORDER_CONFIRMED = "order-confirmed";
        public static final String ORDER_CANCELLED = "order-cancelled";
        public static final String ORDER_STATUS_UPDATED = "order-status-updated";
        public static final String INVENTORY_CONFIRMATION_REQUESTED = "inventory-confirmation-requested";
        public static final String ORDER_STATUS_UPDATE_REQUESTED = "order-status-update-requested";

        // ================ CheckIn Events ================
        public static final String QR_GENERATION_REQUESTED = "qr-generation-requested";
        public static final String QR_GENERATED = "qr-generated";
        public static final String CHECKIN_CREATED = "checkin-created";

        // ================ Store Events ================
        public static final String STOCK_DEDUCTED = "stock-deducted";
        public static final String STOCK_RESTORED = "stock-restored";
        public static final String INVENTORY_CONFIRMED = "inventory-confirmed";
        public static final String GOODS_RESERVED = "goods-reserved";
        public static final String SCHEDULE_RESERVED = "schedule-reserved";

        private EventTypes() {}
    }

    /**
     * 이벤트 상태 상수들
     */
    public static final class EventStatus {

        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String PENDING = "PENDING";
        public static final String PROCESSING = "PROCESSING";
        public static final String CANCELLED = "CANCELLED";
        public static final String EXPIRED = "EXPIRED";

        private EventStatus() {}
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

        private MetadataKeys() {}
    }

    /**
     * 도메인별 Aggregate Type 상수들
     */
    public static final class AggregateTypes {

        public static final String PAYMENT = "Payment";
        public static final String ORDER = "Order";
        public static final String CHECKIN = "CheckIn";
        public static final String QR_CODE = "QrCode";
        public static final String STORE = "Store";
        public static final String INVENTORY = "Inventory";
        public static final String POPUP = "Popup";

        private AggregateTypes() {}
    }
}