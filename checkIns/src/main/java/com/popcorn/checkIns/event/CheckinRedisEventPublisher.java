package com.popcorn.checkIns.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.checkIns.event.CheckinEvents.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * CheckIn 도메인 Redis Stream 이벤트 발행자 (BaseEvent 기반)
 *
 * BaseCheckinEvent를 활용한 일관된 이벤트 발행을 제공합니다.
 * - checkin-requests: QR 생성 요청 등 (Payment → CheckIn)
 * - checkin-events: QR 생성 완료, 체크인 완료 등 (CheckIn → 다른 도메인)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckinRedisEventPublisher implements CheckinEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Stream 이름 상수
    private static final String CHECKIN_EVENTS_STREAM = "checkin-events";
    private static final String CHECKIN_REQUESTS_STREAM = "checkin-requests";

    @Override
    public void publish(BaseCheckinEvent event) {
        String streamName = resolveStreamName(event);
        if (streamName == null) {
            log.warn("⚠️ [CHECKIN] 지원되지 않는 이벤트 타입 - eventType: {}", event.getEventType());
            return;
        }

        try {
            Map<String, Object> eventData = buildEventData(event);

            // Map<String, Object>를 Map<String, String>으로 변환
            Map<String, String> stringEventData = eventData.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue() != null ? entry.getValue().toString() : "null"
                    ));

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(streamName);
            String recordId = redisTemplate.opsForStream().add(record).getValue();

            log.info("📱 [CHECKIN] Redis Stream 이벤트 발행 완료: stream={} eventType={} recordId={} eventId={}",
                    streamName, event.getEventType(), recordId, event.getEventId());

        } catch (Exception e) {
            log.error("❌ [CHECKIN] Redis Stream 이벤트 발행 실패: eventType={} error={}",
                    event.getEventType(), e.getMessage(), e);
        }
    }

    @Override
    public void publishQrGenerationRequested(QrGenerationRequestedEvent event) {
        log.info("🔔 [CHECKIN] QR 생성 요청 이벤트 발행 - orderId: {}, paymentId: {}",
                event.getOrderId(), event.getPaymentId());
        publish(event);
    }

    @Override
    public void publishQrGenerated(QrGeneratedEvent event) {
        log.info("✅ [CHECKIN] QR 생성 완료 이벤트 발행 - orderId: {}, qrId: {}",
                event.getOrderId(), event.getQrId());
        publish(event);
    }

    @Override
    public void publishCheckinCreated(CheckinCreatedEvent event) {
        log.info("🎯 [CHECKIN] 체크인 생성 이벤트 발행 - orderId: {}, checkinId: {}",
                event.getOrderId(), event.getCheckinIdValue());
        publish(event);
    }

    /**
     * 이벤트 타입에 따라 적절한 스트림 이름을 결정
     */
    private String resolveStreamName(BaseCheckinEvent event) {
        return switch (event.getEventType()) {
            case "qr-generation-requested" -> CHECKIN_REQUESTS_STREAM;
            case "qr-generated", "checkin-created" -> CHECKIN_EVENTS_STREAM;
            default -> null;
        };
    }

    /**
     * BaseCheckinEvent를 활용하여 이벤트 데이터 구성
     */
    private Map<String, Object> buildEventData(BaseCheckinEvent event) {
        Map<String, Object> eventData = new java.util.HashMap<>();

        // BaseEvent 메타데이터 활용
        eventData.put("eventType", event.getEventType());
        eventData.put("eventId", event.getEventId().toString());
        eventData.put("aggregateId", event.getAggregateId().toString());
        eventData.put("aggregateType", event.getAggregateType());
        eventData.put("timestamp", event.getTimestamp().toString());
        eventData.put("eventVersion", event.getEventVersion());
        eventData.put("correlationId", event.getCorrelationId().toString());

        if (event.getUserId() != null) {
            eventData.put("userId", event.getUserId().toString());
        }

        // 이벤트별 비즈니스 데이터 (getEventPayload에서 제공)
        eventData.putAll(event.getEventPayload());

        // metadata가 있다면 추가
        if (!event.getMetadata().isEmpty()) {
            try {
                eventData.put("metadata", objectMapper.writeValueAsString(event.getMetadata()));
            } catch (Exception e) {
                log.warn("메타데이터 직렬화 실패: {}", e.getMessage());
            }
        }

        return eventData;
    }
}