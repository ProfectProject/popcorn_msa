package com.popcorn.checkIns.event.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.checkIns.event.domain.BaseCheckinEvent;
import com.popcorn.checkIns.event.domain.CheckinEvents.*;
import com.popcorn.checkIns.constants.EventConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CheckIn 도메인 Kafka 이벤트 발행자
 *
 * 점진적 마이그레이션을 위해 Redis와 병행하여 사용됩니다.
 * - checkin-requests: QR 생성 요청 수신 (Payment → CheckIn)
 * - checkin-events: QR 생성 완료, 체크인 완료 발행 (CheckIn → Order, User, Query)
 *
 * 주요 기능:
 * - 동기 발행 (publish): 확실한 전송 보장
 * - 비동기 발행 (publishAsync): 성능 우선
 * - 이벤트ID 기반 중복 처리 및 파티션 키 순서 보장
 */
@Component
@ConditionalOnProperty(value = "kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CheckinsKafkaEventPublisher implements CheckinEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Topic 상수 (common-lib의 KafkaConfig에서 정의된 것과 동일)
    private static final String CHECKIN_EVENTS_TOPIC = "checkin-events";
    private static final String CHECKIN_REQUESTS_TOPIC = "checkin-requests";

    /**
     * 동기 이벤트 발행 (확실한 전송 보장)
     * 실패 시 예외 발생하여 상위 로직에서 처리 가능
     */
    @Override
    public void publish(BaseCheckinEvent event) {
        try {
            TopicAndKey topicAndKey = resolveTopicAndKey(event);
            String topic = topicAndKey.topic();
            String key = topicAndKey.key();
            Map<String, Object> eventData = buildEventData(event);

            SendResult<String, Object> result = kafkaTemplate.send(topic, key, eventData)
                    .get(5, TimeUnit.SECONDS); // 5초 타임아웃

            log.info("📱 [KAFKA] CheckIn 이벤트 발행 완료: topic={} eventType={} partition={} offset={} eventId={}",
                    topic, event.getEventType(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.getEventId());

        } catch (Exception e) {
            log.error("❌ [KAFKA] CheckIn 이벤트 발행 실패: eventType={} error={}",
                    event.getEventType(), e.getMessage(), e);
            throw new RuntimeException("Kafka 이벤트 발행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 비동기 이벤트 발행 (Fire & Forget)
     * 메인 로직에 영향을 주지 않고 성능 우선
     */
    public void publishAsync(BaseCheckinEvent event) {
        try {
            TopicAndKey topicAndKey = resolveTopicAndKey(event);
            String topic = topicAndKey.topic();
            String key = topicAndKey.key();
            Map<String, Object> eventData = buildEventData(event);

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, eventData);

            future.thenAccept(result -> {
                log.info("📱 [KAFKA-ASYNC] CheckIn 이벤트 발행 완료: topic={} eventType={} partition={} offset={} eventId={}",
                        topic, event.getEventType(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.getEventId());
            }).exceptionally(ex -> {
                log.error("❌ [KAFKA-ASYNC] CheckIn 이벤트 발행 실패: eventType={} error={}",
                        event.getEventType(), ex.getMessage(), ex);
                return null;
            });

        } catch (Exception e) {
            log.error("❌ [KAFKA-ASYNC] CheckIn 이벤트 발행 준비 실패: eventType={} error={}",
                    event.getEventType(), e.getMessage(), e);
        }
    }

    @Override
    public void publishQrGenerationRequested(QrGenerationRequestedEvent event) {
        log.info("🔔 [KAFKA] QR 생성 요청 이벤트 발행 - orderId: {}, paymentId: {}",
                event.getOrderId(), event.getPaymentId());
        publish(event);
    }

    @Override
    public void publishQrGenerated(QrGeneratedEvent event) {
        log.info("✅ [KAFKA] QR 생성 완료 이벤트 발행 - orderId: {}, qrId: {}",
                event.getOrderId(), event.getQrId());
        publish(event);
    }

    @Override
    public void publishCheckinCreated(CheckinCreatedEvent event) {
        log.info("🎯 [KAFKA] 체크인 생성 이벤트 발행 - orderId: {}, checkinId: {}",
                event.getOrderId(), event.getCheckinIdValue());
        publish(event);
    }

    /**
     * 이벤트 타입에 따라 Topic과 Partition Key 결정
     *
     * 파티션 키 전략:
     * - 기본: orderId (주문별 순서 보장)
     * - QR 관련: qr:{orderId}
     * - 체크인 관련: checkin:{orderId}
     */
    private record TopicAndKey(String topic, String key) {}

    private TopicAndKey resolveTopicAndKey(BaseCheckinEvent event) {
        String topic = switch (event.getEventType()) {
            case EventConstants.EventTypes.QR_GENERATION_REQUESTED -> CHECKIN_REQUESTS_TOPIC;
            case EventConstants.EventTypes.QR_GENERATED,
                 EventConstants.EventTypes.CHECKIN_CREATED -> CHECKIN_EVENTS_TOPIC;
            default -> throw new IllegalArgumentException("지원되지 않는 CheckIn 이벤트 타입: " + event.getEventType());
        };

        String key = buildPartitionKey(event);
        return new TopicAndKey(topic, key);
    }

    /**
     * 파티션 키 생성 (순서 보장을 위해 orderId 기반)
     */
    private String buildPartitionKey(BaseCheckinEvent event) {
        return switch (event.getEventType()) {
            case EventConstants.EventTypes.QR_GENERATION_REQUESTED -> {
                if (event instanceof QrGenerationRequestedEvent qrEvent) {
                    yield "qr:" + qrEvent.getOrderId();
                }
                yield "qr:" + event.getAggregateId();
            }
            case EventConstants.EventTypes.QR_GENERATED -> {
                if (event instanceof QrGeneratedEvent qrEvent) {
                    yield "order:" + qrEvent.getOrderId();
                }
                yield "order:" + event.getAggregateId();
            }
            case EventConstants.EventTypes.CHECKIN_CREATED -> {
                if (event instanceof CheckinCreatedEvent checkinEvent) {
                    yield "order:" + checkinEvent.getOrderId();
                }
                yield "checkin:" + event.getAggregateId();
            }
            default -> "default:" + event.getAggregateId();
        };
    }

    /**
     * BaseCheckinEvent를 Kafka 메시지로 변환
     * Redis와 동일한 구조 유지 (호환성)
     */
    private Map<String, Object> buildEventData(BaseCheckinEvent event) {
        Map<String, Object> eventData = new java.util.HashMap<>();

        // BaseEvent 메타데이터
        eventData.put(EventConstants.MetadataKeys.EVENT_TYPE, event.getEventType());
        eventData.put(EventConstants.MetadataKeys.EVENT_ID, event.getEventId().toString());
        eventData.put(EventConstants.MetadataKeys.AGGREGATE_ID, event.getAggregateId().toString());
        eventData.put(EventConstants.MetadataKeys.AGGREGATE_TYPE, event.getAggregateType());
        eventData.put(EventConstants.MetadataKeys.TIMESTAMP, event.getTimestamp().toString());
        eventData.put(EventConstants.MetadataKeys.EVENT_VERSION, event.getEventVersion());
        eventData.put(EventConstants.MetadataKeys.CORRELATION_ID, event.getCorrelationId().toString());

        if (event.getUserId() != null) {
            eventData.put(EventConstants.MetadataKeys.USER_ID, event.getUserId().toString());
        }

        // 이벤트별 비즈니스 데이터
        Map<String, Object> payload = event.getEventPayload();
        eventData.putAll(payload);

        // 메타데이터 추가
        if (!event.getMetadata().isEmpty()) {
            try {
                eventData.put("metadata", objectMapper.writeValueAsString(event.getMetadata()));
            } catch (JsonProcessingException e) {
                log.warn("⚠️ [KAFKA] 메타데이터 직렬화 실패: {}", e.getMessage());
            }
        }

        return eventData;
    }
}
