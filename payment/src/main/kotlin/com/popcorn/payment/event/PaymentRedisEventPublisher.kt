package com.popcorn.payment.event

import com.fasterxml.jackson.databind.ObjectMapper
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
        private const val PAYMENT_EVENTS_STREAM = "payment-events"
        private const val PAYMENT_REQUESTS_STREAM = "payment-requests"
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
                    "eventType" to event.eventType,
                    "eventId" to event.eventId.toString(),
                    "aggregateId" to event.aggregateId.toString(),
                    "aggregateType" to event.aggregateType,
                    "timestamp" to event.timestamp.toString(),
                    "eventVersion" to event.eventVersion,
                    "correlationId" to event.correlationId.toString(),
                    "userId" to event.userId?.toString(),
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
                    "eventType" to eventType,
                    "eventId" to java.util.UUID.randomUUID().toString(),
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
            is PaymentCompletedEvent -> PAYMENT_EVENTS_STREAM to "payment-completed"
            is QrCodeGenerationRequestedEvent -> QR_EVENTS_STREAM to "qr-generation-requested"
            is QrCodeInvalidationRequestedEvent -> QR_EVENTS_STREAM to "qr-invalidation-requested"
            is InventoryConfirmationRequestedEvent -> INVENTORY_EVENTS_STREAM to "inventory-confirmation-requested"
            is OrderStatusUpdateRequestedEvent -> ORDER_EVENTS_STREAM to "order-status-update-requested"
            is PaymentCancelRetryEvent -> PAYMENT_EVENTS_STREAM to "payment-cancel-retry"
            is PaymentCancelFinalFailureEvent -> PAYMENT_EVENTS_STREAM to "payment-cancel-final-failure"

            // BasePaymentEvent 기반 이벤트 처리 (제네릭)
            is BasePaymentEvent -> when (event.eventType) {
                "payment-create-requested", "payment-cancel-requested" -> PAYMENT_REQUESTS_STREAM to event.eventType
                else -> PAYMENT_EVENTS_STREAM to event.eventType
            }

            else -> null
        }
    }
}
