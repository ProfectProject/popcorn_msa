package com.popcorn.checkIns.constants;

/**
 * 체크인 서비스 전용 이벤트 상수들
 * 각 서비스에서 자체적으로 관리하는 이벤트 타입들
 */
public final class EventConstants {

    private EventConstants() {
        // Utility class
    }

    /**
     * 체크인 관련 이벤트 타입들 (UPPERCASE)
     */
    public static final class EventTypes {

        // ================ QR Code Events ================
        public static final String QR_GENERATION_REQUESTED = "QR_GENERATION_REQUESTED";
        public static final String QR_GENERATED = "QR_GENERATED";
        public static final String QR_INVALIDATION_REQUESTED = "QR_INVALIDATION_REQUESTED";

        // ================ CheckIn Events ================
        public static final String CHECKIN_CREATED = "CHECKIN_CREATED";
        public static final String QR_CHECKIN_REQUESTED = "QR_CHECKIN_REQUESTED";
        public static final String QR_CHECKIN_COMPLETED = "QR_CHECKIN_COMPLETED";

        // ================ External Domain Events ================
        public static final String PAYMENT_APPROVED = "PAYMENT_APPROVED";

        private EventTypes() {}
    }

    /**
     * Redis Stream 이름들
     */
    public static final class Streams {
        public static final String CHECKIN_EVENTS = "checkin-events";
        public static final String CHECKIN_REQUESTS = "checkin-requests";
        public static final String STANDARD_CHECKINS_EVENTS = "standard-checkins-events";
        public static final String QR_EVENTS = "qr-events";

        // External streams that CheckIns service subscribes to
        public static final String PAYMENT_EVENTS = "payment-events";
        public static final String ORDER_EVENTS = "order-events";

        private Streams() {}
    }

    /**
     * Consumer Group 이름들
     */
    public static final class ConsumerGroups {
        public static final String CHECKIN_SERVICE_GROUP = "checkin-service-group";

        private ConsumerGroups() {}
    }

    /**
     * Consumer 이름들
     */
    public static final class Consumers {
        public static final String CHECKIN_EVENTS_CONSUMER = "checkin-events-consumer-1";
        public static final String CHECKIN_REQUESTS_CONSUMER = "checkin-requests-consumer-1";
        public static final String STANDARD_CONSUMER = "standard-consumer-1";
        public static final String QR_EVENTS_CONSUMER = "qr-events-consumer-1";

        // External stream consumers
        public static final String PAYMENT_EVENTS_CONSUMER = "payment-events-consumer-1";
        public static final String ORDER_EVENTS_CONSUMER = "order-events-consumer-1";

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
        public static final String CANCELLED = "CANCELLED";
        public static final String EXPIRED = "EXPIRED";

        private EventStatus() {}
    }

    /**
     * Aggregate Type 상수들
     */
    public static final class AggregateTypes {
        public static final String CHECKIN = "CheckIn";
        public static final String QR_CODE = "QrCode";

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
        public static final String PAYMENT_ID = "paymentId";
        public static final String QR_ID = "qrId";
        public static final String QR_CODE = "qrCode";
        public static final String CHECKIN_ID = "checkinId";

        private MetadataKeys() {}
    }
}
