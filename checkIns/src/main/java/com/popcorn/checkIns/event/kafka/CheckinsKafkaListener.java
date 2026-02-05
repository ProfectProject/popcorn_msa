package com.popcorn.checkIns.event.kafka;

import com.popcorn.checkIns.service.QrCodeService;
import com.popcorn.checkIns.constants.EventConstants;
import com.popcorn.checkIns.metrics.CheckInsMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * CheckIn 도메인 Kafka 이벤트 수신자
 *
 * 점진적 마이그레이션을 위해 Redis Stream과 병행하여 운영됩니다.
 *
 * 수신하는 이벤트:
 * - checkin-requests: Payment 서비스의 QR 생성 요청
 * - order-events: Order 서비스 이벤트 (필요시)
 *
 * 주요 기능:
 * - 멱등성 보장 (eventId 기반 중복 제거)
 * - 에러 처리 및 로깅
 * - 수동 acknowledge (at-least-once 보장)
 */
@Component
@ConditionalOnProperty(value = "kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CheckinsKafkaListener {

    private static final String ORDER_ID_KEY = "orderId";

    private final QrCodeService qrCodeService;
    private final CheckinsKafkaIdempotencyService idempotencyService;
    private final CheckInsMetricsService metricsService;

     /**
     * CheckIn 요청 이벤트 처리
     * Topic: checkin-requests
     * Consumer Group: checkin-service-group
     */
    @KafkaListener(
            topics = EventConstants.Streams.CHECKIN_REQUESTS,
            groupId = EventConstants.ConsumerGroups.CHECKIN_SERVICE_GROUP,
            concurrency = "3"
    )
    public void handleCheckinRequests(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment
    ) {
        long startTime = System.currentTimeMillis();
        String eventType = (String) eventData.get(EventConstants.MetadataKeys.EVENT_TYPE);
        String eventId = (String) eventData.get(EventConstants.MetadataKeys.EVENT_ID);

        // Kafka 메시지 처리 시작 메트릭
        metricsService.recordKafkaMessageStarted();

        try {
            log.info("📱 [KAFKA-IN] CheckIn 요청 수신: topic={} eventType={} partition={} offset={} eventId={}",
                    topic, eventType, partition, offset, eventId);

            // 멱등성 체크 (중복 처리 방지)
            if (eventId != null && idempotencyService.isDuplicateEvent(eventId)) {
                log.info("⏭️ [KAFKA-IN] 중복 이벤트 스킵: eventId={}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // 이벤트 타입별 처리
            if (EventConstants.EventTypes.QR_GENERATION_REQUESTED.equals(eventType)) {
                handleQrGenerationRequested(eventData);
            } else if ("QR_INVALIDATION_REQUESTED".equals(eventType)) {
                handleQrInvalidationRequested(eventData);
            } else {
                log.debug("🔍 [KAFKA-IN] 처리하지 않는 이벤트 타입: {}", eventType);
            }

            // 처리 완료 기록
            long processingTime = System.currentTimeMillis() - startTime;
            if (eventId != null) {
                idempotencyService.recordProcessedEvent(eventId, eventType, topic, partition, offset, processingTime);
            }

            acknowledgment.acknowledge();

            // Kafka 메시지 처리 성공 메트릭 기록
            metricsService.recordKafkaMessageProcessed(topic, eventType, processingTime);
            metricsService.recordKafkaMessageCompleted();

            log.info("✅ [KAFKA-IN] CheckIn 요청 처리 완료: eventType={} processingTime={}ms", eventType, processingTime);

        } catch (Exception e) {
            log.error("❌ [KAFKA-IN] CheckIn 요청 처리 실패: eventType={} eventId={} error={}",
                    eventType, eventId, e.getMessage(), e);

            // Kafka 메시지 처리 에러 메트릭 기록
            metricsService.recordKafkaMessageError(topic, eventType, e.getClass().getSimpleName());
            metricsService.recordKafkaMessageCompleted();

            // 에러 발생시에도 acknowledge (무한 재시도 방지)
            // 실제 운영에서는 DLQ로 보내거나 별도 에러 처리 로직 필요
            acknowledgment.acknowledge();
        }
    }

    /**
     * QR 코드 생성 요청 처리
     * Payment 서비스로부터 결제 완료 후 QR 생성 요청 수신
     */
    private void handleQrGenerationRequested(Map<String, Object> eventData) {
        long qrStartTime = System.currentTimeMillis();
        String orderNo = null;

        try {
            // 이벤트 데이터 추출
            UUID orderId = UUID.fromString((String) eventData.get(ORDER_ID_KEY));
            UUID paymentId = UUID.fromString((String) eventData.get("paymentId"));
            orderNo = (String) eventData.get("orderNo");

            log.info("🔔 [KAFKA-IN] QR 생성 요청 처리 시작: orderId={} paymentId={} orderNo={}",
                    orderId, paymentId, orderNo);

            // QR 코드 생성 서비스 호출 (Payment 직결 이벤트용)
            var qrCodeResponse = qrCodeService.issueFromPaymentEvent(orderId);

            // QR 생성 성공 메트릭 기록
            long qrProcessingTime = System.currentTimeMillis() - qrStartTime;
            metricsService.recordQrGeneration(orderNo, qrProcessingTime);

            log.info("✅ [KAFKA-IN] QR 생성 완료: orderId={} qrCode={} processingTime={}ms",
                    orderId, qrCodeResponse.getQrCode(), qrProcessingTime);

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ [KAFKA-IN] QR 생성 요청 데이터 오류: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ [KAFKA-IN] QR 생성 처리 실패: {}", e.getMessage(), e);
            throw e; // 상위에서 처리되도록 재발생
        }
    }

    /**
     * QR 코드 무효화 요청 처리
     * Payment 서비스로부터 결제 취소/실패 시 QR 무효화 요청 수신
     */
    private void handleQrInvalidationRequested(Map<String, Object> eventData) {
        String reason = null;

        try {
            // 이벤트 데이터 추출
            UUID orderId = UUID.fromString((String) eventData.get(ORDER_ID_KEY));
            UUID paymentId = UUID.fromString((String) eventData.get("paymentId"));
            reason = (String) eventData.get("reason");

            log.info("❌ [KAFKA-IN] QR 무효화 요청 처리 시작: orderId={} paymentId={} reason={}",
                    orderId, paymentId, reason);

            // QR 코드 무효화 처리
            qrCodeService.invalidateByOrderId(orderId, reason);

            // QR 무효화 메트릭 기록
            metricsService.recordQrInvalidation(reason);

            log.info("✅ [KAFKA-IN] QR 무효화 완료: orderId={} reason={}", orderId, reason);

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ [KAFKA-IN] QR 무효화 요청 데이터 오류: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ [KAFKA-IN] QR 무효화 처리 실패: {}", e.getMessage(), e);
            throw e; // 상위에서 처리되도록 재발생
        }
    }

     /**
     * Payment 이벤트 처리 (QR 무효화 등)
     * Topic: payment-events
     * Consumer Group: checkin-service-group
     */
    @KafkaListener(
            topics = EventConstants.Streams.PAYMENT_EVENTS,
            groupId = EventConstants.ConsumerGroups.CHECKIN_SERVICE_GROUP,
            concurrency = "3"
    )
    public void handlePaymentEvents(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment
    ) {
        long startTime = System.currentTimeMillis();
        String eventType = (String) eventData.get(EventConstants.MetadataKeys.EVENT_TYPE);
        String eventId = (String) eventData.get(EventConstants.MetadataKeys.EVENT_ID);

        try {
            log.debug("💳 [KAFKA-IN] Payment 이벤트 수신: topic={} eventType={} eventId={}",
                    topic, eventType, eventId);

            // 멱등성 체크
            if (eventId != null && idempotencyService.isDuplicateEvent(eventId)) {
                log.debug("⏭️ [KAFKA-IN] Payment 중복 이벤트 스킵: eventId={}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Payment 이벤트 타입별 처리
            if ("QR_INVALIDATION_REQUESTED".equals(eventType)) {
                handleQrInvalidationRequested(eventData);
            } else {
                log.debug("🔍 [KAFKA-IN] 처리하지 않는 Payment 이벤트: {}", eventType);
            }

            // 처리 완료 기록
            long processingTime = System.currentTimeMillis() - startTime;
            if (eventId != null) {
                idempotencyService.recordProcessedEvent(eventId, eventType, topic, partition, offset, processingTime);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ [KAFKA-IN] Payment 이벤트 처리 실패: eventType={} eventId={} error={}",
                    eventType, eventId, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

     /**
     * Order 이벤트 처리 (필요시 추가)
     * Topic: order-events
     * Consumer Group: checkin-service-group
     */
    @KafkaListener(
            topics = EventConstants.Streams.ORDER_EVENTS,
            groupId = EventConstants.ConsumerGroups.CHECKIN_SERVICE_GROUP,
            concurrency = "3"
    )
    public void handleOrderEvents(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment
    ) {
        long startTime = System.currentTimeMillis();
        String eventType = (String) eventData.get(EventConstants.MetadataKeys.EVENT_TYPE);
        String eventId = (String) eventData.get(EventConstants.MetadataKeys.EVENT_ID);

        try {
            log.debug("📦 [KAFKA-IN] Order 이벤트 수신: topic={} eventType={} eventId={}",
                    topic, eventType, eventId);

            // 멱등성 체크
            if (eventId != null && idempotencyService.isDuplicateEvent(eventId)) {
                log.debug("⏭️ [KAFKA-IN] Order 중복 이벤트 스킵: eventId={}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Order 이벤트 타입별 처리 (현재는 로깅만)
            if ("ORDER_PAID".equals(eventType)) {
                handleOrderPaid(eventData);
            } else if ("ORDER_CANCELLED".equals(eventType)) {
                handleOrderCancelled(eventData);
            } else {
                log.debug("🔍 [KAFKA-IN] 처리하지 않는 Order 이벤트: {}", eventType);
            }

            // 처리 완료 기록
            long processingTime = System.currentTimeMillis() - startTime;
            if (eventId != null) {
                idempotencyService.recordProcessedEvent(eventId, eventType, topic, partition, offset, processingTime);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ [KAFKA-IN] Order 이벤트 처리 실패: eventType={} eventId={} error={}",
                    eventType, eventId, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * 주문 결제 완료 이벤트 처리
     */
    private void handleOrderPaid(Map<String, Object> eventData) {
        UUID orderId = UUID.fromString((String) eventData.get(ORDER_ID_KEY));
        log.info("💳 [KAFKA-IN] 주문 결제 완료 알림: orderId={}", orderId);
        // 필요시 추가 로직 구현
    }

    /**
     * 주문 취소 이벤트 처리
     */
    private void handleOrderCancelled(Map<String, Object> eventData) {
        UUID orderId = UUID.fromString((String) eventData.get(ORDER_ID_KEY));
        log.info("❌ [KAFKA-IN] 주문 취소 알림: orderId={}", orderId);
        // 필요시 QR 코드 무효화 등 처리
    }
}
