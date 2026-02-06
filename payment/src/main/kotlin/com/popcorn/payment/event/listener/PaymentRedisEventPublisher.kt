package com.popcorn.payment.event.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import com.popcorn.payment.event.domain.payment.*
import com.popcorn.payment.event.domain.cancellation.*
import com.popcorn.payment.event.domain.legacy.PaymentCompletedEvent
import com.popcorn.payment.event.integration.request.*
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.connection.stream.StringRecord
import java.time.LocalDateTime
import org.springframework.stereotype.Component

@Component
class PaymentRedisEventPublisher(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(PaymentRedisEventPublisher::class.java)

    // Stream 이름 상수
    companion object {
        private val PAYMENT_EVENTS_STREAM = EventConstants.Streams.PAYMENT_EVENTS
        private val PAYMENT_REQUESTS_STREAM = EventConstants.Streams.PAYMENT_REQUESTS
        private val ORDER_REQUESTS_STREAM = EventConstants.Streams.ORDER_REQUESTS
        private val ORDER_INFO_REQUESTS_STREAM = EventConstants.Streams.ORDER_INFO_REQUESTS
        private const val QR_EVENTS_STREAM = "qr-events"
        private const val ORDER_EVENTS_STREAM = "order-events"
        private const val INVENTORY_EVENTS_STREAM = "inventory-events"
    }

    fun publish(event: Any) {
        val (streamName, eventType) = resolveStreamAndType(event) ?: return
        try {
            val eventData = mutableMapOf<String, Any?>()

            // BasePaymentEvent인 경우 BaseEvent의 메타데이터 활용
            if (event is BasePaymentEvent) {
                eventData.putAll(mapOf(
                    EventConstants.MetadataKeys.EVENT_TYPE to event.eventType,
                    EventConstants.MetadataKeys.EVENT_ID to event.eventId.toString(),
                    EventConstants.MetadataKeys.AGGREGATE_ID to event.aggregateId.toString(),
                    EventConstants.MetadataKeys.AGGREGATE_TYPE to event.aggregateType,
                    EventConstants.MetadataKeys.TIMESTAMP to event.timestamp.toString(),
                    EventConstants.MetadataKeys.EVENT_VERSION to event.eventVersion,
                    EventConstants.MetadataKeys.CORRELATION_ID to event.correlationId.toString(),
                    EventConstants.MetadataKeys.USER_ID to event.userId?.toString(),
                    // eventPayload 추가
                    *event.eventPayload.toList().toTypedArray()
                ))

                // metadata가 있다면 추가
                if (event.metadata.isNotEmpty()) {
                    eventData["metadata"] = objectMapper.writeValueAsString(event.metadata)
                }
            } else {
                // 기존 방식 (비-BaseEvent 이벤트용)
                eventData.putAll(mapOf(
                    EventConstants.MetadataKeys.EVENT_TYPE to eventType,
                    EventConstants.MetadataKeys.EVENT_ID to java.util.UUID.randomUUID().toString(),
                    "eventTime" to LocalDateTime.now().toString()
                ))

                // 이벤트 객체를 Map으로 변환하여 추가
                val eventJson = objectMapper.writeValueAsString(event)
                @Suppress("UNCHECKED_CAST")
                val eventMap = objectMapper.readValue(eventJson, Map::class.java) as Map<String, Any?>
                eventData.putAll(eventMap)
            }

            // Map<String, Any?>를 Map<String, String>으로 변환
            val stringEventData = eventData.mapValues { (_, value) -> value?.toString() ?: "" }

            val streamOps = redisTemplate.opsForStream<String, Any>()
            val record: StringRecord = StreamRecords.string(stringEventData).withStreamKey(streamName)
            val recordId = streamOps.add(record)

            log.info(
                "💳 [PAYMENT] Redis Stream 이벤트 발행 완료: stream={} eventType={} recordId={} eventId={}",
                streamName,
                eventType,
                recordId,
                if (event is BasePaymentEvent) event.eventId else "N/A"
            )
        } catch (e: Exception) {
            log.error("❌ [PAYMENT] Redis Stream 이벤트 발행 실패: eventType={} error={}", eventType, e.message, e)
        }
    }

    private fun resolveStreamAndType(event: Any): Pair<String, String>? {
        return when (event) {
            // 기존 Payment Events
            is PaymentCreatedEvent -> PAYMENT_EVENTS_STREAM to event.eventType
            is PaymentApprovedEvent -> PAYMENT_EVENTS_STREAM to event.eventType
            is PaymentFailedEvent -> PAYMENT_EVENTS_STREAM to event.eventType
            is PaymentCancelledEvent -> PAYMENT_EVENTS_STREAM to event.eventType
            is PaymentCancelFailedEvent -> PAYMENT_EVENTS_STREAM to event.eventType
            is PaymentExpiredEvent -> PAYMENT_EVENTS_STREAM to event.eventType
            is PaymentSuccessEvent -> PAYMENT_EVENTS_STREAM to event.eventType

            // 새로운 Payment Events (사용자 요청)
            is PaymentUserCancelledEvent -> PAYMENT_EVENTS_STREAM to event.eventType
            is PaymentCancelSucceededEvent -> PAYMENT_EVENTS_STREAM to event.eventType

            // 새로운 Payment Requests (사용자 요청)
            is PaymentCreateRequestedEvent -> PAYMENT_REQUESTS_STREAM to event.eventType
            is PaymentCancelRequestedEvent -> PAYMENT_REQUESTS_STREAM to event.eventType

            // 기존 외부 도메인 이벤트들
            is PaymentCompletedEvent -> PAYMENT_EVENTS_STREAM to EventConstants.EventTypes.PaymentDomain.PAYMENT_SUCCESS
            is QrCodeGenerationRequestedEvent -> QR_EVENTS_STREAM to event.eventType
            is QrCodeInvalidationRequestedEvent -> QR_EVENTS_STREAM to event.eventType
            is InventoryConfirmationRequestedEvent -> INVENTORY_EVENTS_STREAM to event.eventType
            is OrderStatusUpdateRequestedEvent -> ORDER_EVENTS_STREAM to event.eventType
            is PaymentCancelRetryEvent -> PAYMENT_EVENTS_STREAM to event.eventType
            is PaymentCancelFinalFailureEvent -> PAYMENT_EVENTS_STREAM to event.eventType

            // BasePaymentEvent 기반 이벤트 처리 (제네릭)
            is BasePaymentEvent -> when (event.eventType) {
                EventConstants.EventTypes.PaymentRequest.PAYMENT_CREATE_REQUESTED,
                EventConstants.EventTypes.PaymentRequest.PAYMENT_CANCEL_REQUESTED -> PAYMENT_REQUESTS_STREAM to event.eventType
                EventConstants.EventTypes.Integration.ORDER_INFO_REQUESTED -> ORDER_INFO_REQUESTS_STREAM to event.eventType
                EventConstants.EventTypes.Integration.ORDER_QUERY_REQUESTED -> ORDER_REQUESTS_STREAM to event.eventType
                else -> PAYMENT_EVENTS_STREAM to event.eventType
            }

            else -> null
        }
    }
}
