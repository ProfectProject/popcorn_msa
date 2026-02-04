package com.popcorn.payment.constants

/**
 * 결제 서비스 전용 이벤트 상수들
 * 각 서비스에서 자체적으로 관리하는 이벤트 타입들
 */
object EventConstants {

    /**
     * 결제 관련 이벤트 타입들 (kebab-case)
     */
    object EventTypes {

        // ================ Payment Domain Events ================
        const val PAYMENT_CREATED = "payment-created"
        const val PAYMENT_APPROVED = "payment-approved"
        const val PAYMENT_FAILED = "payment-failed"
        const val PAYMENT_CANCELLED = "payment-cancelled"
        const val PAYMENT_USER_CANCELLED = "payment-user-cancelled"
        const val PAYMENT_CANCEL_SUCCEEDED = "payment-cancel-succeeded"
        const val PAYMENT_CANCEL_FAILED = "payment-cancel-failed"
        const val PAYMENT_EXPIRED = "payment-expired"
        const val PAYMENT_SUCCESS = "payment-success"

        // ================ Payment Request Events ================
        const val PAYMENT_CREATE_REQUESTED = "payment-create-requested"
        const val PAYMENT_CANCEL_REQUESTED = "payment-cancel-requested"

        // ================ Payment Retry/Recovery Events ================
        const val PAYMENT_CANCEL_RETRY = "payment-cancel-retry"
        const val PAYMENT_CANCEL_FINAL_FAILURE = "payment-cancel-final-failure"

        // ================ Integration Events ================
        const val QR_GENERATION_REQUESTED = "qr-generation-requested"
        const val QR_INVALIDATION_REQUESTED = "qr-invalidation-requested"
        const val ORDER_STATUS_UPDATE_REQUESTED = "order-status-update-requested"
        const val INVENTORY_CONFIRMATION_REQUESTED = "inventory-confirmation-requested"
        const val ORDER_INFO_REQUEST = "order-info-request"

        // ================ Legacy Events ================
        const val PAYMENT_COMPLETED = "payment-completed"
    }

    /**
     * Redis Stream 이름들
     */
    object Streams {
        const val PAYMENT_EVENTS = "payment-events"
        const val PAYMENT_REQUESTS = "payment-requests"
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