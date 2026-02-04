package com.popcorn.payment.constants

/**
 * 결제 서비스 전용 이벤트 상수들
 * 각 서비스에서 자체적으로 관리하는 이벤트 타입들
 */
object EventConstants {

    /**
     * 결제 관련 이벤트 타입들 (UPPERCASE)
     */
    object EventTypes {

        // ================ Payment Domain Events ================
        const val PAYMENT_CREATED = "PAYMENT_CREATED"
        const val PAYMENT_APPROVED = "PAYMENT_APPROVED"
        const val PAYMENT_FAILED = "PAYMENT_FAILED"
        const val PAYMENT_CANCELLED = "PAYMENT_CANCELLED"
        const val PAYMENT_USER_CANCELLED = "PAYMENT_USER_CANCELLED"
        const val PAYMENT_CANCEL_SUCCEEDED = "PAYMENT_CANCEL_SUCCEEDED"
        const val PAYMENT_CANCEL_FAILED = "PAYMENT_CANCEL_FAILED"
        const val PAYMENT_EXPIRED = "PAYMENT_EXPIRED"
        const val PAYMENT_SUCCESS = "PAYMENT_SUCCESS"

        // ================ Payment Request Events ================
        const val PAYMENT_CREATE_REQUESTED = "PAYMENT_CREATE_REQUESTED"
        const val PAYMENT_CANCEL_REQUESTED = "PAYMENT_CANCEL_REQUESTED"

        // ================ Payment Retry/Recovery Events ================
        const val PAYMENT_CANCEL_RETRY = "PAYMENT_CANCEL_RETRY"
        const val PAYMENT_CANCEL_FINAL_FAILURE = "PAYMENT_CANCEL_FINAL_FAILURE"

        // ================ Integration Events ================
        const val QR_GENERATION_REQUESTED = "QR_GENERATION_REQUESTED"
        const val QR_INVALIDATION_REQUESTED = "QR_INVALIDATION_REQUESTED"
        const val ORDER_STATUS_UPDATE_REQUESTED = "ORDER_STATUS_UPDATE_REQUESTED"
        const val INVENTORY_CONFIRMATION_REQUESTED = "INVENTORY_CONFIRMATION_REQUESTED"
        const val ORDER_INFO_REQUEST = "ORDER_INFO_REQUEST"
        const val ORDER_INFO_RESPONSE = "ORDER_INFO_RESPONSE"

        // ================ Compensation Events ================
        const val PAYMENT_VALIDATION_FAILED = "PAYMENT_VALIDATION_FAILED"
        const val COMPENSATION_REQUESTED = "COMPENSATION_REQUESTED"
        const val COMPENSATION_COMPLETED = "COMPENSATION_COMPLETED"
        const val COMPENSATION_FAILED = "COMPENSATION_FAILED"
        const val ORDER_COMPENSATION_REQUESTED = "ORDER_COMPENSATION_REQUESTED"
        const val RESERVATION_CANCELLATION_REQUESTED = "RESERVATION_CANCELLATION_REQUESTED"

        // ================ Legacy Events ================
        const val PAYMENT_COMPLETED = "PAYMENT_COMPLETED"
    }

    /**
     * Redis Stream 이름들
     */
    object Streams {
        const val PAYMENT_EVENTS = "payment-events"
        const val PAYMENT_REQUESTS = "payment-requests"
        const val ORDER_INFO_REQUESTS = "order-info-requests"
    }

    /**
     * Consumer Group 이름들
     */
    object ConsumerGroups {
        const val PAYMENT_SERVICE_GROUP = "payment-service-group"
    }

    /**
     * Consumer 이름들
     */
    object Consumers {
        const val PAYMENT_EVENTS_CONSUMER = "payment-events-consumer-1"
        const val PAYMENT_REQUESTS_CONSUMER = "payment-requests-consumer-1"
    }

    /**
     * 이벤트 상태들
     */
    object EventStatus {
        const val SUCCESS = "SUCCESS"
        const val FAILED = "FAILED"
        const val PENDING = "PENDING"
        const val PROCESSING = "PROCESSING"
        const val CANCELLED = "CANCELLED"
        const val EXPIRED = "EXPIRED"
    }

    /**
     * Aggregate Type 상수들
     */
    object AggregateTypes {
        const val PAYMENT = "Payment"
    }

    /**
     * 이벤트 메타데이터 키 상수들
     */
    object MetadataKeys {
        const val EVENT_TYPE = "eventType"
        const val EVENT_ID = "eventId"
        const val AGGREGATE_ID = "aggregateId"
        const val AGGREGATE_TYPE = "aggregateType"
        const val CORRELATION_ID = "correlationId"
        const val TIMESTAMP = "timestamp"
        const val EVENT_VERSION = "eventVersion"
        const val USER_ID = "userId"
        const val SOURCE_SERVICE = "sourceService"
        const val TARGET_SERVICE = "targetService"
    }
}
