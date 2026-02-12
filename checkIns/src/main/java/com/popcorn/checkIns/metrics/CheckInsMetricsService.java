package com.popcorn.checkIns.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CheckIns 서비스 메트릭 수집 및 모니터링 서비스
 *
 * Micrometer/Prometheus 기반 메트릭 수집으로
 * 운영 환경에서 실시간 모니터링 지원
 */
@Service
@Slf4j
public class CheckInsMetricsService {

    private final MeterRegistry meterRegistry;

    // === QR 코드 관련 메트릭 ===
    private final Counter qrGenerationCounter;
    private final Counter qrValidationCounter;
    private final Counter qrInvalidationCounter;
    private final Timer qrGenerationTimer;
    private final Timer qrValidationTimer;

    // === CheckIn 관련 메트릭 ===
    private final Counter checkinSuccessCounter;
    private final Counter checkinFailureCounter;
    private final Timer checkinProcessingTimer;

    // === Kafka 메시지 처리 메트릭 ===
    private final Counter kafkaMessageProcessedCounter;
    private final Counter kafkaMessageErrorCounter;
    private final Timer kafkaProcessingTimer;

    // === Redis 이벤트 메트릭 ===
    private final Counter redisEventPublishedCounter;
    private final Counter redisEventProcessedCounter;
    private final Timer redisOperationTimer;

    // === 실시간 상태 게이지 ===
    private final AtomicLong activeQrCodes = new AtomicLong(0);
    private final AtomicLong totalCheckins = new AtomicLong(0);
    private final AtomicLong activeKafkaMessages = new AtomicLong(0);
    private final AtomicLong lastActivityTime = new AtomicLong(0);

    public CheckInsMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // QR 코드 메트릭 초기화
        this.qrGenerationCounter = Counter.builder("checkins.qr.generation.total")
                .description("Total QR codes generated")
                .register(meterRegistry);

        this.qrValidationCounter = Counter.builder("checkins.qr.validation.total")
                .description("Total QR code validations")
                .register(meterRegistry);

        this.qrInvalidationCounter = Counter.builder("checkins.qr.invalidation.total")
                .description("Total QR code invalidations")
                .register(meterRegistry);

        this.qrGenerationTimer = Timer.builder("checkins.qr.generation.duration")
                .description("QR code generation processing time")
                .register(meterRegistry);

        this.qrValidationTimer = Timer.builder("checkins.qr.validation.duration")
                .description("QR code validation processing time")
                .register(meterRegistry);

        // CheckIn 메트릭 초기화
        this.checkinSuccessCounter = Counter.builder("checkins.checkin.success")
                .description("Successful check-ins")
                .register(meterRegistry);

        this.checkinFailureCounter = Counter.builder("checkins.checkin.failure")
                .description("Failed check-ins")
                .register(meterRegistry);

        this.checkinProcessingTimer = Timer.builder("checkins.checkin.processing.duration")
                .description("Check-in processing time")
                .register(meterRegistry);

        // Kafka 메트릭 초기화
        this.kafkaMessageProcessedCounter = Counter.builder("checkins.kafka.messages.processed")
                .description("Total Kafka messages processed by topic and event type")
                .register(meterRegistry);

        this.kafkaMessageErrorCounter = Counter.builder("checkins.kafka.messages.errors")
                .description("Kafka message processing errors")
                .register(meterRegistry);

        this.kafkaProcessingTimer = Timer.builder("checkins.kafka.processing.duration")
                .description("Kafka message processing time")
                .register(meterRegistry);

        // Redis 메트릭 초기화
        this.redisEventPublishedCounter = Counter.builder("checkins.redis.events.published")
                .description("Redis events published by type")
                .register(meterRegistry);

        this.redisEventProcessedCounter = Counter.builder("checkins.redis.events.processed")
                .description("Redis events processed by type")
                .register(meterRegistry);

        this.redisOperationTimer = Timer.builder("checkins.redis.operation.duration")
                .description("Redis operation processing time")
                .register(meterRegistry);

        // 게이지 메트릭 등록
        initializeGaugeMetrics();
    }

    private void initializeGaugeMetrics() {
        Gauge.builder("checkins.qr.active.count", activeQrCodes, AtomicLong::get)
                .description("Current number of active QR codes")
                .register(meterRegistry);

        Gauge.builder("checkins.checkin.total.count", totalCheckins, AtomicLong::get)
                .description("Total number of check-ins processed")
                .register(meterRegistry);

        Gauge.builder("checkins.kafka.active.messages", activeKafkaMessages, AtomicLong::get)
                .description("Current number of active Kafka messages being processed")
                .register(meterRegistry);

        Gauge.builder("checkins.last.activity.timestamp", lastActivityTime, AtomicLong::get)
                .description("Timestamp of last activity")
                .register(meterRegistry);
    }

    // === QR 코드 메트릭 기록 ===

    /**
     * QR 코드 생성 메트릭 기록
     */
    public void recordQrGeneration(String orderNo, long processingTimeMs) {
        try {
            qrGenerationCounter.increment();
            meterRegistry.counter("checkins.qr.generation.total",
                    "order_no", orderNo != null ? orderNo : "unknown")
                .increment();
            qrGenerationTimer.record(Duration.ofMillis(processingTimeMs));
            activeQrCodes.incrementAndGet();
            updateLastActivity();

            log.debug("📊 [METRICS] QR 생성: orderNo={} processingTime={}ms", orderNo, processingTimeMs);
        } catch (Exception e) {
            log.error("❌ [METRICS] QR 생성 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * QR 코드 검증 메트릭 기록
     */
    public void recordQrValidation(String qrToken, boolean isValid, long processingTimeMs) {
        try {
            qrValidationCounter.increment();
            meterRegistry.counter("checkins.qr.validation.total",
                    "valid", String.valueOf(isValid),
                    "qr_token", qrToken != null ? "present" : "missing")
                .increment();
            qrValidationTimer.record(Duration.ofMillis(processingTimeMs));
            updateLastActivity();

            log.debug("📊 [METRICS] QR 검증: valid={} processingTime={}ms", isValid, processingTimeMs);
        } catch (Exception e) {
            log.error("❌ [METRICS] QR 검증 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * QR 코드 무효화 메트릭 기록
     */
    public void recordQrInvalidation(String reason) {
        try {
            qrInvalidationCounter.increment();
            meterRegistry.counter("checkins.qr.invalidation.total",
                    "reason", reason != null ? reason : "unknown")
                .increment();
            activeQrCodes.decrementAndGet();
            updateLastActivity();

            log.debug("📊 [METRICS] QR 무효화: reason={}", reason);
        } catch (Exception e) {
            log.error("❌ [METRICS] QR 무효화 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    // === CheckIn 메트릭 기록 ===

    /**
     * CheckIn 성공 메트릭 기록
     */
    public void recordCheckinSuccess(String qrToken, long processingTimeMs) {
        try {
            checkinSuccessCounter.increment();
            meterRegistry.counter("checkins.checkin.success",
                    "qr_token", qrToken != null ? "present" : "missing")
                .increment();
            checkinProcessingTimer.record(Duration.ofMillis(processingTimeMs));
            totalCheckins.incrementAndGet();
            updateLastActivity();

            log.debug("📊 [METRICS] CheckIn 성공: processingTime={}ms", processingTimeMs);
        } catch (Exception e) {
            log.error("❌ [METRICS] CheckIn 성공 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * CheckIn 실패 메트릭 기록
     */
    public void recordCheckinFailure(String reason, String errorType) {
        try {
            checkinFailureCounter.increment();
            meterRegistry.counter("checkins.checkin.failure",
                    "reason", reason != null ? reason : "unknown",
                    "error_type", errorType != null ? errorType : "unknown")
                .increment();
            updateLastActivity();

            log.warn("📊 [METRICS] CheckIn 실패: reason={} errorType={}", reason, errorType);
        } catch (Exception e) {
            log.error("❌ [METRICS] CheckIn 실패 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    // === Kafka 메시지 처리 메트릭 ===

    /**
     * Kafka 메시지 처리 메트릭 기록
     */
    public void recordKafkaMessageProcessed(String topic, String eventType, long processingTimeMs) {
        try {
            kafkaMessageProcessedCounter.increment();
            meterRegistry.counter("checkins.kafka.messages.processed",
                    "topic", topic != null ? topic : "unknown",
                    "event_type", eventType != null ? eventType : "unknown")
                .increment();
            kafkaProcessingTimer.record(Duration.ofMillis(processingTimeMs));
            updateLastActivity();

            log.debug("📊 [METRICS] Kafka 메시지 처리: topic={} eventType={} processingTime={}ms",
                    topic, eventType, processingTimeMs);
        } catch (Exception e) {
            log.error("❌ [METRICS] Kafka 메시지 처리 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Kafka 메시지 처리 에러 메트릭 기록
     */
    public void recordKafkaMessageError(String topic, String eventType, String errorType) {
        try {
            kafkaMessageErrorCounter.increment();
            meterRegistry.counter("checkins.kafka.messages.errors",
                    "topic", topic != null ? topic : "unknown",
                    "event_type", eventType != null ? eventType : "unknown",
                    "error_type", errorType != null ? errorType : "unknown")
                .increment();

            log.warn("📊 [METRICS] Kafka 메시지 에러: topic={} eventType={} errorType={}",
                    topic, eventType, errorType);
        } catch (Exception e) {
            log.error("❌ [METRICS] Kafka 에러 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Kafka 메시지 처리 시작
     */
    public void recordKafkaMessageStarted() {
        activeKafkaMessages.incrementAndGet();
    }

    /**
     * Kafka 메시지 처리 완료
     */
    public void recordKafkaMessageCompleted() {
        activeKafkaMessages.decrementAndGet();
    }

    // === Redis 이벤트 메트릭 ===

    /**
     * Redis 이벤트 발행 메트릭 기록
     */
    public void recordRedisEventPublished(String eventType, String streamKey) {
        try {
            redisEventPublishedCounter.increment();
            meterRegistry.counter("checkins.redis.events.published",
                    "event_type", eventType != null ? eventType : "unknown",
                    "stream_key", streamKey != null ? streamKey : "unknown")
                .increment();

            log.debug("📊 [METRICS] Redis 이벤트 발행: eventType={} streamKey={}", eventType, streamKey);
        } catch (Exception e) {
            log.error("❌ [METRICS] Redis 이벤트 발행 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Redis 이벤트 처리 메트릭 기록
     */
    public void recordRedisEventProcessed(String eventType, long processingTimeMs) {
        try {
            redisEventProcessedCounter.increment();
            meterRegistry.counter("checkins.redis.events.processed",
                    "event_type", eventType != null ? eventType : "unknown")
                .increment();
            redisOperationTimer.record(Duration.ofMillis(processingTimeMs));

            log.debug("📊 [METRICS] Redis 이벤트 처리: eventType={} processingTime={}ms",
                    eventType, processingTimeMs);
        } catch (Exception e) {
            log.error("❌ [METRICS] Redis 이벤트 처리 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    // === 유틸리티 메소드 ===

    /**
     * 마지막 활동 시간 업데이트
     */
    private void updateLastActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }

    /**
     * 메트릭 상태 요약 조회 (헬스체크용)
     */
    public java.util.Map<String, Object> getMetricsSummary() {
        return java.util.Map.of(
                "activeQrCodes", activeQrCodes.get(),
                "totalCheckins", totalCheckins.get(),
                "activeKafkaMessages", activeKafkaMessages.get(),
                "lastActivityTime", lastActivityTime.get(),
                "qrGenerationsTotal", qrGenerationCounter.count(),
                "qrValidationsTotal", qrValidationCounter.count(),
                "checkinSuccessTotal", checkinSuccessCounter.count(),
                "checkinFailureTotal", checkinFailureCounter.count(),
                "kafkaMessagesProcessedTotal", kafkaMessageProcessedCounter.count(),
                "kafkaMessageErrorsTotal", kafkaMessageErrorCounter.count()
        );
    }
}
