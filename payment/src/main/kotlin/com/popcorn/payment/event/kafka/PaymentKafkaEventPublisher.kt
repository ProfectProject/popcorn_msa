package com.popcorn.payment.event.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import com.popcorn.payment.event.domain.payment.*
import com.popcorn.payment.event.domain.cancellation.*
import com.popcorn.payment.event.domain.legacy.PaymentCompletedEvent
import com.popcorn.payment.event.integration.request.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Payment 서비스의 Kafka 이벤트 발행자
 * - Redis Stream Publisher와 동일한 인터페이스 유지
 * - 점진적 전환을 위한 이중 발행 지원
 */
@Component
@ConditionalOnProperty(value = ["kafka.enabled"], havingValue = "true")
class PaymentKafkaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(PaymentKafkaEventPublisher::class.java)

    /**
     * 이벤트를 Kafka로 발행
     * - 동기 발행 (실패 시 예외 발생)
     */
    fun publish(event: Any) {
        val (topicName, eventType, partitionKey) = resolveTopicAndKey(event) ?: return

        try {
            val eventData = buildEventData(event, eventType)

            // 동기 발행 (topic, key, value 직접 전달)
            val future = kafkaTemplate.send(topicName, partitionKey, eventData)
            val result = future.get()

            log.info(
                "💳 [PAYMENT] Kafka 이벤트 발행 완료: topic={} partition={} offset={} eventType={} key={} eventId={}",
                topicName,
                result.recordMetadata.partition(),
                result.recordMetadata.offset(),
                eventType,
                partitionKey,
                if (event is BasePaymentEvent) event.eventId else "N/A"
            )
        } catch (e: Exception) {
            log.error("❌ [PAYMENT] Kafka 이벤트 발행 실패: topic={} eventType={} key={} error={}",
                topicName, eventType, partitionKey, e.message, e)
            throw RuntimeException("Kafka 이벤트 발행 실패: $eventType", e)
        }
    }

    /**
     * 이벤트를 Kafka로 비동기 발행
     * - Fire and Forget 방식
     */
    fun publishAsync(event: Any) {
        val (topicName, eventType, partitionKey) = resolveTopicAndKey(event) ?: return

        try {
            val eventData = buildEventData(event, eventType)

            // 비동기 발행 (topic, key, value 직접 전달)
            val future = kafkaTemplate.send(topicName, partitionKey, eventData)

            future.whenComplete { result, ex ->
                if (ex == null && result != null) {
                    log.info(
                        "💳 [PAYMENT] Kafka 비동기 이벤트 발행 완료: topic={} partition={} offset={} eventType={} key={}",
                        topicName,
                        result.recordMetadata.partition(),
                        result.recordMetadata.offset(),
                        eventType,
                        partitionKey
                    )
                } else {
                    log.error("❌ [PAYMENT] Kafka 비동기 이벤트 발행 실패: topic={} eventType={} key={} error={}",
                        topicName, eventType, partitionKey, ex?.message, ex)
                }
            }

        } catch (e: Exception) {
            log.error("❌ [PAYMENT] Kafka 비동기 이벤트 발행 준비 실패: eventType={} error={}", eventType, e.message, e)
        }
    }

    /**
     * 이벤트 데이터 구성
     */
    private fun buildEventData(event: Any, eventType: String): Map<String, Any?> {
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
                "eventTime" to java.time.LocalDateTime.now().toString()
            ))

            // 이벤트 객체를 Map으로 변환하여 추가
            val eventJson = objectMapper.writeValueAsString(event)
            @Suppress("UNCHECKED_CAST")
            val eventMap = objectMapper.readValue(eventJson, Map::class.java) as Map<String, Any?>
            eventData.putAll(eventMap)
        }

        return eventData
    }

    /**
     * 이벤트 타입에 따른 토픽명과 파티션 키 결정
     * - Redis Stream과 동일한 매핑 유지
     */
    private fun resolveTopicAndKey(event: Any): Triple<String, String, String>? {
        return when (event) {
            // 기존 Payment Events
            is PaymentCreatedEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), event.orderId?.toString())
            )
            is PaymentApprovedEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), event.orderId?.toString())
            )
            is PaymentFailedEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), event.orderId?.toString())
            )
            is PaymentCancelledEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), event.orderId?.toString())
            )
            is PaymentCancelFailedEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), null)
            )
            is PaymentExpiredEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), null)
            )
            is PaymentSuccessEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), event.orderId?.toString())
            )

            // 새로운 Payment Events (사용자 요청)
            is PaymentUserCancelledEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), null)
            )
            is PaymentCancelSucceededEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId.toString(), null)
            )

            // 새로운 Payment Requests (사용자 요청)
            is PaymentCreateRequestedEvent -> Triple(
                EventConstants.Streams.PAYMENT_REQUESTS,
                event.eventType,
                extractPartitionKey(null, event.orderId?.toString())
            )
            is PaymentCancelRequestedEvent -> Triple(
                EventConstants.Streams.PAYMENT_REQUESTS,
                event.eventType,
                extractPartitionKey(event.paymentId?.toString(), null)
            )

            // 기존 외부 도메인 이벤트들
            is PaymentCompletedEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                EventConstants.EventTypes.PaymentDomain.PAYMENT_SUCCESS,
                "completed:${System.currentTimeMillis()}"
            )
            is QrCodeGenerationRequestedEvent -> Triple(
                EventConstants.Streams.CHECKIN_REQUESTS,
                event.eventType,
                extractPartitionKey(null, event.orderId?.toString())
            )
            is QrCodeInvalidationRequestedEvent -> Triple(
                EventConstants.Streams.CHECKIN_REQUESTS,
                event.eventType,
                extractPartitionKey(null, event.orderId?.toString())
            )
            is InventoryConfirmationRequestedEvent -> Triple(
                EventConstants.Streams.STORE_REQUESTS,
                event.eventType,
                extractPartitionKey(null, event.orderId?.toString())
            )
            is OrderStatusUpdateRequestedEvent -> Triple(
                EventConstants.Streams.ORDER_EVENTS,
                event.eventType,
                extractPartitionKey(null, event.orderId?.toString())
            )
            is PaymentCancelRetryEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId?.toString(), null)
            )
            is PaymentCancelFinalFailureEvent -> Triple(
                EventConstants.Streams.PAYMENT_EVENTS,
                event.eventType,
                extractPartitionKey(event.paymentId?.toString(), null)
            )

            // BasePaymentEvent 기반 이벤트 처리 (제네릭)
            is BasePaymentEvent -> {
                val topic = when (event.eventType) {
                    EventConstants.EventTypes.PaymentRequest.PAYMENT_CREATE_REQUESTED,
                    EventConstants.EventTypes.PaymentRequest.PAYMENT_CANCEL_REQUESTED -> EventConstants.Streams.PAYMENT_REQUESTS
                    EventConstants.EventTypes.Integration.ORDER_INFO_REQUEST -> EventConstants.Streams.ORDER_INFO_REQUESTS
                    else -> EventConstants.Streams.PAYMENT_EVENTS
                }
                Triple(
                    topic,
                    event.eventType,
                    extractPartitionKey(event.paymentId.toString(), null)
                )
            }

            else -> {
                log.warn("⚠️  [PAYMENT] 알 수 없는 이벤트 타입: {}", event::class.java.simpleName)
                null
            }
        }
    }

    /**
     * 파티션 키 추출
     * - 우선순위: orderId > paymentId > 기본값
     * - orderId 기준으로 파티셔닝하여 순서 보장
     */
    private fun extractPartitionKey(paymentId: String?, orderId: String?): String {
        return when {
            !orderId.isNullOrBlank() -> "order:$orderId"
            !paymentId.isNullOrBlank() -> "payment:$paymentId"
            else -> "default:${System.currentTimeMillis()}"
        }
    }
}
