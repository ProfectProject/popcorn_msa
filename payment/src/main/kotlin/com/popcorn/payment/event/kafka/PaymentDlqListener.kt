package com.popcorn.payment.event.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.metrics.PaymentMetricsService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * Payment DLQ(Dead Letter Queue) 전용 리스너
 *
 * DLQ로 전송된 실패 메시지들을 모니터링하고 수동 처리
 * - 관리자 알림
 * - 실패 패턴 분석
 * - 수동 재처리 지원
 */
@Component
@ConditionalOnProperty(value = ["kafka.enabled"], havingValue = "true")
class PaymentDlqListener(
    private val objectMapper: ObjectMapper,
    private val metricsService: PaymentMetricsService
) {
    private val log = LoggerFactory.getLogger(PaymentDlqListener::class.java)

    /**
     * Payment Events DLQ 토픽 모니터링
     */
    @KafkaListener(
        topics = [EventConstants.Streams.PAYMENT_EVENTS_DLQ],
        groupId = EventConstants.ConsumerGroups.PAYMENT_DLQ_GROUP,
        concurrency = "1" // DLQ는 단일 스레드로 순차 처리
    )
    fun handlePaymentEventsDlq(
        @Payload dlqMessage: Map<String, Any>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        try {
            val originalTopic = dlqMessage["originalTopic"] as? String
            val originalPartition = dlqMessage["partition"] as? Int
            val originalOffset = dlqMessage["offset"] as? Long
            val eventType = dlqMessage[EventConstants.MetadataKeys.EVENT_TYPE] as? String
            val eventId = dlqMessage[EventConstants.MetadataKeys.EVENT_ID] as? String
            val errorInfo = dlqMessage["error"] as? Map<String, Any>
            val timestamp = dlqMessage[EventConstants.MetadataKeys.TIMESTAMP] as? Long
            val retryAttempt = dlqMessage["retryAttempt"] as? Int ?: 0

            log.error("🚨 [DLQ] Payment Events DLQ 메시지 수신 - " +
                    "eventType={} eventId={} originalTopic={} originalPartition={} originalOffset={} " +
                    "retryAttempt={} error={}",
                eventType, eventId, originalTopic, originalPartition, originalOffset,
                retryAttempt, errorInfo?.get("message"))

            // 상세 로그 기록 (트러블슈팅용)
            log.debug("🔍 [DLQ] 상세 DLQ 메시지: {}", objectMapper.writeValueAsString(dlqMessage))

            // DLQ 메시지 통계 기록
            metricsService.recordDlqMessage(eventType, errorInfo?.get("type") as? String, retryAttempt)

            // 중요한 이벤트 타입별 특별 처리
            when (eventType) {
                EventConstants.EventTypes.PaymentDomain.PAYMENT_APPROVED -> handleCriticalPaymentFailure(dlqMessage)
                EventConstants.EventTypes.PaymentDomain.PAYMENT_FAILED -> handleCriticalPaymentFailure(dlqMessage)
                EventConstants.EventTypes.PaymentDomain.PAYMENT_CANCELLED -> handleCriticalPaymentFailure(dlqMessage)
                else -> handleGeneralDlqMessage(dlqMessage)
            }

            acknowledgment.acknowledge()
            completeDlqProcessing()

        } catch (e: Exception) {
            log.error("❌ [DLQ] DLQ 메시지 처리 중 오류 발생: error={}", e.message, e)
            acknowledgment.acknowledge() // DLQ 메시지는 재시도하지 않고 ACK
            completeDlqProcessing()
        }
    }

    /**
     * Payment Requests DLQ 토픽 모니터링
     */
    @KafkaListener(
        topics = [EventConstants.Streams.PAYMENT_REQUESTS_DLQ],
        groupId = EventConstants.ConsumerGroups.PAYMENT_DLQ_GROUP,
        concurrency = "1"
    )
    fun handlePaymentRequestsDlq(
        @Payload dlqMessage: Map<String, Any>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        try {
            val eventType = dlqMessage[EventConstants.MetadataKeys.EVENT_TYPE] as? String
            val eventId = dlqMessage[EventConstants.MetadataKeys.EVENT_ID] as? String
            val errorInfo = dlqMessage["error"] as? Map<String, Any>

            log.error("🚨 [DLQ] Payment Requests DLQ 메시지 수신 - " +
                    "eventType={} eventId={} error={}",
                eventType, eventId, errorInfo?.get("message"))

            // 요청 실패는 매우 중요하므로 별도 처리
            handleCriticalRequestFailure(dlqMessage)

            acknowledgment.acknowledge()
            completeDlqProcessing()

        } catch (e: Exception) {
            log.error("❌ [DLQ] Payment Requests DLQ 처리 오류: error={}", e.message, e)
            acknowledgment.acknowledge()
            completeDlqProcessing()
        }
    }

    /**
     * Order Requests DLQ 토픽 모니터링
     */
    @KafkaListener(
        topics = [EventConstants.Streams.ORDER_REQUESTS_DLQ],
        groupId = EventConstants.ConsumerGroups.PAYMENT_DLQ_GROUP,
        concurrency = "1"
    )
    fun handleOrderRequestsDlq(
        @Payload dlqMessage: Map<String, Any>,
        acknowledgment: Acknowledgment
    ) {
        try {
            val eventType = dlqMessage[EventConstants.MetadataKeys.EVENT_TYPE] as? String
            val eventId = dlqMessage[EventConstants.MetadataKeys.EVENT_ID] as? String

            log.warn("⚠️ [DLQ] Order Requests DLQ 메시지 수신 - eventType={} eventId={}", eventType, eventId)

            // Order 요청 실패 처리 (상대적으로 덜 중요)
            handleGeneralDlqMessage(dlqMessage)

            acknowledgment.acknowledge()
            completeDlqProcessing()

        } catch (e: Exception) {
            log.error("❌ [DLQ] Order Requests DLQ 처리 오류: error={}", e.message, e)
            acknowledgment.acknowledge()
            completeDlqProcessing()
        }
    }

    /**
     * 중요한 결제 이벤트 실패 처리
     * - 관리자 즉시 알림
     * - 데이터 정합성 체크
     */
    private fun handleCriticalPaymentFailure(dlqMessage: Map<String, Any>) {
        val eventType = dlqMessage[EventConstants.MetadataKeys.EVENT_TYPE] as? String
        val eventId = dlqMessage[EventConstants.MetadataKeys.EVENT_ID] as? String
        val originalMessage = dlqMessage["originalMessage"] as? Map<String, Any>
        val paymentId = originalMessage?.get("paymentId") as? String
        val orderId = originalMessage?.get("orderId") as? String

        log.error("🆘 [DLQ-CRITICAL] 중요 결제 이벤트 처리 실패 - " +
                "eventType={} eventId={} paymentId={} orderId={}",
            eventType, eventId, paymentId, orderId)

        // TODO: 실제 운영에서는 다음 작업 필요
        // 1. Slack/Teams 등으로 즉시 알림
        // 2. 결제 상태 데이터 정합성 체크
        // 3. 고객 서비스팀 알림 (필요시)
        // 4. 수동 복구 절차 가이드 제공

        // 중요 실패 메트릭 기록 (모니터링 및 알림용)
        metricsService.recordCriticalFailure(eventType, paymentId, orderId)
    }

    /**
     * 중요한 결제 요청 실패 처리
     */
    private fun handleCriticalRequestFailure(dlqMessage: Map<String, Any>) {
        val eventType = dlqMessage[EventConstants.MetadataKeys.EVENT_TYPE] as? String
        val originalMessage = dlqMessage["originalMessage"] as? Map<String, Any>
        val orderId = originalMessage?.get("orderId") as? String

        log.error("🆘 [DLQ-CRITICAL] 중요 결제 요청 실패 - eventType={} orderId={}", eventType, orderId)

        // TODO: 결제 요청 실패 대응
        // 1. 주문 상태 확인
        // 2. 고객 알림 (결제 재시도 안내)
        // 3. 관리자 알림

        metricsService.recordCriticalFailure(eventType, null, orderId)
    }

    /**
     * 일반 DLQ 메시지 처리
     */
    private fun handleGeneralDlqMessage(dlqMessage: Map<String, Any>) {
        val eventType = dlqMessage[EventConstants.MetadataKeys.EVENT_TYPE] as? String
        val eventId = dlqMessage[EventConstants.MetadataKeys.EVENT_ID] as? String

        log.warn("📊 [DLQ-GENERAL] 일반 DLQ 메시지 처리: eventType={} eventId={}", eventType, eventId)

        // 일반 실패 통계 기록
        metricsService.recordGeneralFailure(eventType)
    }

    /**
     * DLQ 메시지 처리 완료 후 메트릭 정리
     */
    private fun completeDlqProcessing() {
        metricsService.recordDlqMessageCompleted()
    }
}
