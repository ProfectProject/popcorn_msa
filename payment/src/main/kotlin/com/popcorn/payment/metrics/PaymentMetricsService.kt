package com.popcorn.payment.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Payment 서비스 메트릭 수집 및 모니터링 서비스
 *
 * Micrometer/Prometheus 기반 메트릭 수집으로
 * 운영 환경에서 실시간 모니터링 지원
 */
@Service
class PaymentMetricsService(
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(PaymentMetricsService::class.java)

    // === DLQ 메트릭 카운터 ===
    private val dlqMessageCounter = Counter.builder("payment.dlq.messages")
        .description("Total DLQ messages received by event type")
        .register(meterRegistry)

    private val dlqErrorTypeCounter = Counter.builder("payment.dlq.errors")
        .description("DLQ messages by error type")
        .register(meterRegistry)

    private val criticalFailureCounter = Counter.builder("payment.dlq.critical.failures")
        .description("Critical payment failures requiring immediate attention")
        .register(meterRegistry)

    private val generalFailureCounter = Counter.builder("payment.dlq.general.failures")
        .description("General payment failures")
        .register(meterRegistry)

    // === Kafka 메시지 처리 메트릭 ===
    private val kafkaMessageProcessedCounter = Counter.builder("payment.kafka.messages.processed")
        .description("Total Kafka messages processed by topic and event type")
        .register(meterRegistry)

    private val kafkaMessageErrorCounter = Counter.builder("payment.kafka.messages.errors")
        .description("Kafka message processing errors")
        .register(meterRegistry)

    private val kafkaProcessingTimer = Timer.builder("payment.kafka.processing.duration")
        .description("Kafka message processing time")
        .register(meterRegistry)

    // === Payment 이벤트 메트릭 ===
    private val paymentEventPublishedCounter = Counter.builder("payment.events.published")
        .description("Payment events published by type")
        .register(meterRegistry)

    private val paymentEventProcessedCounter = Counter.builder("payment.events.processed")
        .description("Payment events processed by type")
        .register(meterRegistry)

    // === 실시간 상태 게이지 ===
    private val activeDlqMessages = AtomicLong(0)
    private val activeRetryAttempts = AtomicLong(0)
    private val lastDlqMessageTime = AtomicLong(0)

    init {
        // 게이지 메트릭 등록
        Gauge.builder("payment.dlq.active.messages", activeDlqMessages) { it.get().toDouble() }
            .description("Current number of active DLQ messages")
            .register(meterRegistry)

        Gauge.builder("payment.retry.active.attempts", activeRetryAttempts) { it.get().toDouble() }
            .description("Current number of active retry attempts")
            .register(meterRegistry)

        Gauge.builder("payment.dlq.last.message.timestamp", lastDlqMessageTime) { it.get().toDouble() }
            .description("Timestamp of last DLQ message received")
            .register(meterRegistry)
    }

    /**
     * DLQ 메시지 메트릭 기록
     */
    fun recordDlqMessage(eventType: String?, errorType: String?, retryAttempt: Int) {
        val safeEventType = eventType ?: "unknown"
        val safeErrorType = errorType ?: "unknown"

        try {
            dlqMessageCounter.increment()
            meterRegistry.counter(
                "payment.dlq.messages",
                "event_type", safeEventType,
                "retry_attempt", retryAttempt.toString()
            ).increment()

            dlqErrorTypeCounter.increment()
            meterRegistry.counter(
                "payment.dlq.errors",
                "event_type", safeEventType,
                "error_type", safeErrorType
            ).increment()

            activeDlqMessages.incrementAndGet()
            lastDlqMessageTime.set(System.currentTimeMillis())

            log.info("📊 [METRICS] DLQ 메트릭 기록: eventType={} errorType={} retryAttempt={}",
                safeEventType, safeErrorType, retryAttempt)
        } catch (e: Exception) {
            log.error("❌ [METRICS] DLQ 메트릭 기록 실패: {}", e.message, e)
        }
    }

    /**
     * 중요 실패 메트릭 기록
     */
    fun recordCriticalFailure(eventType: String?, paymentId: String?, orderId: String?) {
        val safeEventType = eventType ?: "unknown"

        try {
            criticalFailureCounter.increment()
            meterRegistry.counter(
                "payment.dlq.critical.failures",
                "event_type", safeEventType,
                "has_payment_id", (paymentId != null).toString(),
                "has_order_id", (orderId != null).toString()
            ).increment()

            log.error("🆘 [METRICS] 중요 실패 메트릭: eventType={} paymentId={} orderId={}",
                safeEventType, paymentId, orderId)

            // 중요 실패 시 추가 알림 로그 (실제 운영시 Slack/Teams 연동)
            if (eventType in listOf(
                    com.popcorn.payment.constants.EventConstants.EventTypes.PaymentDomain.PAYMENT_APPROVED,
                    com.popcorn.payment.constants.EventConstants.EventTypes.PaymentDomain.PAYMENT_FAILED,
                    com.popcorn.payment.constants.EventConstants.EventTypes.PaymentDomain.PAYMENT_CANCELLED
                )) {
                log.error("🚨 [CRITICAL-ALERT] 결제 핵심 이벤트 실패 - 즉시 확인 필요: eventType={}", eventType)
            }
        } catch (e: Exception) {
            log.error("❌ [METRICS] 중요 실패 메트릭 기록 실패: {}", e.message, e)
        }
    }

    /**
     * 일반 실패 메트릭 기록
     */
    fun recordGeneralFailure(eventType: String?) {
        val safeEventType = eventType ?: "unknown"

        try {
            generalFailureCounter.increment()
            meterRegistry.counter(
                "payment.dlq.general.failures",
                "event_type", safeEventType
            ).increment()
            log.info("📊 [METRICS] 일반 실패 메트릭: eventType={}", safeEventType)
        } catch (e: Exception) {
            log.error("❌ [METRICS] 일반 실패 메트릭 기록 실패: {}", e.message, e)
        }
    }

    /**
     * Kafka 메시지 처리 메트릭 기록
     */
    fun recordKafkaMessageProcessed(topic: String, eventType: String?, processingTimeMs: Long) {
        val safeEventType = eventType ?: "unknown"

        try {
            kafkaMessageProcessedCounter.increment()
            meterRegistry.counter(
                "payment.kafka.messages.processed",
                "topic", topic,
                "event_type", safeEventType
            ).increment()

            kafkaProcessingTimer.record(Duration.ofMillis(processingTimeMs))

            log.debug("📊 [METRICS] Kafka 메시지 처리: topic={} eventType={} processingTime={}ms",
                topic, safeEventType, processingTimeMs)
        } catch (e: Exception) {
            log.error("❌ [METRICS] Kafka 처리 메트릭 기록 실패: {}", e.message, e)
        }
    }

    /**
     * Kafka 메시지 처리 에러 메트릭 기록
     */
    fun recordKafkaMessageError(topic: String, eventType: String?, errorType: String) {
        val safeEventType = eventType ?: "unknown"

        try {
            kafkaMessageErrorCounter.increment()
            meterRegistry.counter(
                "payment.kafka.messages.errors",
                "topic", topic,
                "event_type", safeEventType,
                "error_type", errorType
            ).increment()

            log.warn("📊 [METRICS] Kafka 메시지 에러: topic={} eventType={} errorType={}",
                topic, safeEventType, errorType)
        } catch (e: Exception) {
            log.error("❌ [METRICS] Kafka 에러 메트릭 기록 실패: {}", e.message, e)
        }
    }

    /**
     * Payment 이벤트 발행 메트릭 기록
     */
    fun recordPaymentEventPublished(eventType: String, topic: String) {
        try {
            paymentEventPublishedCounter.increment()
            meterRegistry.counter(
                "payment.events.published",
                "event_type", eventType,
                "topic", topic
            ).increment()

            log.debug("📊 [METRICS] Payment 이벤트 발행: eventType={} topic={}", eventType, topic)
        } catch (e: Exception) {
            log.error("❌ [METRICS] Payment 이벤트 발행 메트릭 기록 실패: {}", e.message, e)
        }
    }

    /**
     * Payment 이벤트 처리 메트릭 기록
     */
    fun recordPaymentEventProcessed(eventType: String) {
        try {
            paymentEventProcessedCounter.increment()
            meterRegistry.counter(
                "payment.events.processed",
                "event_type", eventType
            ).increment()
            log.debug("📊 [METRICS] Payment 이벤트 처리: eventType={}", eventType)
        } catch (e: Exception) {
            log.error("❌ [METRICS] Payment 이벤트 처리 메트릭 기록 실패: {}", e.message, e)
        }
    }

    /**
     * 재시도 시작 메트릭 기록
     */
    fun recordRetryAttemptStarted() {
        activeRetryAttempts.incrementAndGet()
    }

    /**
     * 재시도 완료 메트릭 기록
     */
    fun recordRetryAttemptCompleted() {
        activeRetryAttempts.decrementAndGet()
    }

    /**
     * DLQ 메시지 처리 완료 메트릭 기록
     */
    fun recordDlqMessageCompleted() {
        activeDlqMessages.decrementAndGet()
    }

    /**
     * 메트릭 상태 요약 조회 (헬스체크용)
     */
    fun getMetricsSummary(): Map<String, Any> {
        return mapOf(
            "activeDlqMessages" to activeDlqMessages.get(),
            "activeRetryAttempts" to activeRetryAttempts.get(),
            "lastDlqMessageTime" to lastDlqMessageTime.get(),
            "dlqMessagesTotal" to dlqMessageCounter.count(),
            "criticalFailuresTotal" to criticalFailureCounter.count(),
            "generalFailuresTotal" to generalFailureCounter.count(),
            "kafkaMessagesProcessedTotal" to kafkaMessageProcessedCounter.count(),
            "kafkaMessageErrorsTotal" to kafkaMessageErrorCounter.count()
        )
    }
}
