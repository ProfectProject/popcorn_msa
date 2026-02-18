package com.popcorn.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 🔢 시퀀스 지원 Outbox 이벤트 발행자
 * - 기존 Outbox 패턴에 순서 보장 기능 추가
 * - 이벤트 시퀀스 번호 자동 생성
 * - 트랜잭션 내에서 안전한 이벤트 발행
 * - 다양한 서비스에서 재사용 가능
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SequencedOutboxEventPublisher {

    private final EventSequenceManager sequenceManager;
    private final ObjectMapper objectMapper;

    /**
     * 🎯 순서 보장 이벤트 발행
     *
     * @param aggregateType 애그리게이트 타입 (ORDER, PAYMENT, STORE 등)
     * @param aggregateId 애그리게이트 ID
     * @param eventType 이벤트 타입 (ORDER_CREATED, PAYMENT_APPROVED 등)
     * @param eventData 이벤트 데이터
     * @return 생성된 OrderedEvent
     */
    @Transactional
    public OrderedEvent publishEvent(String aggregateType, String aggregateId,
                                   String eventType, Map<String, Object> eventData) {
        return publishEvent(aggregateType, aggregateId, eventType, eventData, null, null);
    }

    /**
     * 🎯 순서 보장 이벤트 발행 (상세 옵션)
     *
     * @param aggregateType 애그리게이트 타입
     * @param aggregateId 애그리게이트 ID
     * @param eventType 이벤트 타입
     * @param eventData 이벤트 데이터
     * @param headers 이벤트 헤더 (선택적)
     * @param topic Kafka 토픽 (선택적, 기본값 사용 가능)
     * @return 생성된 OrderedEvent
     */
    @Transactional
    public OrderedEvent publishEvent(String aggregateType, String aggregateId,
                                   String eventType, Map<String, Object> eventData,
                                   Map<String, Object> headers, String topic) {
        // 입력 검증
        validateInput(aggregateType, aggregateId, eventType, eventData);

        try {
            // 1. 시퀀스 번호 생성
            long sequence = sequenceManager.generateSequence(aggregateType, aggregateId);
            if (sequence < 0) {
                throw new EventPublishingException("시퀀스 번호 생성 실패: " + aggregateType + ":" + aggregateId);
            }

            // 2. 이벤트 ID 생성
            UUID eventId = UUID.randomUUID();

            // 3. 헤더 정보 준비
            Map<String, Object> enrichedHeaders = prepareHeaders(headers, aggregateType, eventType, sequence);

            // 4. 이벤트 데이터 보강
            Map<String, Object> enrichedEventData = enrichEventData(eventData, eventId, sequence, aggregateType, aggregateId);

            // 5. OrderedEvent 생성
            OrderedEvent orderedEvent = OrderedEvent.builder()
                .eventId(eventId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .sequence(sequence)
                .eventType(eventType)
                .topic(topic != null ? topic : deriveDefaultTopic(aggregateType))
                .partitionKey(aggregateId) // 동일 애그리게이트는 동일 파티션
                .schemaVersion(1)
                .eventData(enrichedEventData)
                .headers(enrichedHeaders)
                .occurredAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .priority(derivePriority(eventType))
                .build();

            log.info("📤 [이벤트 발행] 순서 보장 이벤트 생성 완료 - {}", orderedEvent.getSummary());

            return orderedEvent;

        } catch (Exception e) {
            log.error("❌ [이벤트 발행] 실패 - aggregateType: {}, aggregateId: {}, eventType: {}",
                aggregateType, aggregateId, eventType, e);
            throw new EventPublishingException("이벤트 발행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 🎯 높은 우선순위 이벤트 발행 (긴급 이벤트용)
     */
    @Transactional
    public OrderedEvent publishHighPriorityEvent(String aggregateType, String aggregateId,
                                                String eventType, Map<String, Object> eventData) {
        OrderedEvent event = publishEvent(aggregateType, aggregateId, eventType, eventData);

        // 높은 우선순위로 변경
        OrderedEvent highPriorityEvent = event.toBuilder()
            .priority(1) // 최고 우선순위
            .build();

        log.warn("🚨 [높은 우선순위] 긴급 이벤트 발행 - {}", highPriorityEvent.getSummary());
        return highPriorityEvent;
    }

    /**
     * 📦 배치 이벤트 발행 (트랜잭션 내에서 여러 이벤트 동시 발행)
     */
    @Transactional
    public java.util.List<OrderedEvent> publishBatch(java.util.List<EventPublishRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            log.debug("📦 [배치 발행] 발행할 이벤트 없음");
            return java.util.List.of();
        }

        log.info("📦 [배치 발행] {} 개 이벤트 배치 발행 시작", requests.size());

        java.util.List<OrderedEvent> publishedEvents = new java.util.ArrayList<>();

        try {
            for (EventPublishRequest request : requests) {
                OrderedEvent event = publishEvent(
                    request.getAggregateType(),
                    request.getAggregateId(),
                    request.getEventType(),
                    request.getEventData(),
                    request.getHeaders(),
                    request.getTopic()
                );
                publishedEvents.add(event);
            }

            log.info("📦 [배치 발행] 완료 - {} 개 이벤트 발행 성공", publishedEvents.size());
            return publishedEvents;

        } catch (Exception e) {
            log.error("❌ [배치 발행] 실패 - 발행 중단, 롤백됨", e);
            throw new EventPublishingException("배치 이벤트 발행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 🔄 이벤트 재발행 (복구 시나리오용)
     */
    @Transactional
    public OrderedEvent republishEvent(String aggregateType, String aggregateId,
                                     long originalSequence, String eventType,
                                     Map<String, Object> eventData) {
        log.warn("🔄 [이벤트 재발행] 시작 - {}:{} sequence: {}, eventType: {}",
            aggregateType, aggregateId, originalSequence, eventType);

        try {
            // 재발행 이벤트는 현재 시퀀스가 아닌 원본 시퀀스 사용
            UUID eventId = UUID.randomUUID();

            Map<String, Object> headers = new HashMap<>();
            headers.put("republished", true);
            headers.put("originalSequence", originalSequence);
            headers.put("republishedAt", LocalDateTime.now().toString());

            OrderedEvent event = OrderedEvent.builder()
                .eventId(eventId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .sequence(originalSequence) // 원본 시퀀스 사용
                .eventType(eventType)
                .topic(deriveDefaultTopic(aggregateType))
                .partitionKey(aggregateId)
                .schemaVersion(1)
                .eventData(eventData)
                .headers(headers)
                .occurredAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .priority(50) // 중간 우선순위
                .build();

            log.warn("🔄 [이벤트 재발행] 완료 - {}", event.getSummary());
            return event;

        } catch (Exception e) {
            log.error("❌ [이벤트 재발행] 실패 - {}:{} sequence: {}",
                aggregateType, aggregateId, originalSequence, e);
            throw new EventPublishingException("이벤트 재발행 실패: " + e.getMessage(), e);
        }
    }

    // === 🔧 Private Helper Methods ===

    private void validateInput(String aggregateType, String aggregateId,
                             String eventType, Map<String, Object> eventData) {
        if (aggregateType == null || aggregateType.trim().isEmpty()) {
            throw new IllegalArgumentException("aggregateType은 필수입니다");
        }
        if (aggregateId == null || aggregateId.trim().isEmpty()) {
            throw new IllegalArgumentException("aggregateId는 필수입니다");
        }
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("eventType은 필수입니다");
        }
        if (eventData == null) {
            throw new IllegalArgumentException("eventData는 필수입니다 (빈 Map이라도 전달해야 함)");
        }
    }

    private Map<String, Object> prepareHeaders(Map<String, Object> originalHeaders,
                                             String aggregateType, String eventType, long sequence) {
        Map<String, Object> headers = new HashMap<>();

        // 기존 헤더 복사
        if (originalHeaders != null) {
            headers.putAll(originalHeaders);
        }

        // 시스템 헤더 추가
        headers.put("producer", "sequenced-outbox-publisher");
        headers.put("aggregateType", aggregateType);
        headers.put("eventType", eventType);
        headers.put("sequence", sequence);
        headers.put("publishedAt", LocalDateTime.now().toString());
        headers.put("schemaVersion", "1.0");

        return headers;
    }

    private Map<String, Object> enrichEventData(Map<String, Object> originalData, UUID eventId,
                                              long sequence, String aggregateType, String aggregateId) {
        Map<String, Object> enrichedData = new HashMap<>(originalData);

        // 메타데이터 보강
        enrichedData.put("eventId", eventId.toString());
        enrichedData.put("sequence", sequence);
        enrichedData.put("aggregateType", aggregateType);
        enrichedData.put("aggregateId", aggregateId);
        enrichedData.put("occurredAt", LocalDateTime.now().toString());

        // 기존 데이터의 eventId가 있다면 원본으로 유지
        if (originalData.containsKey("eventId")) {
            enrichedData.put("eventId", originalData.get("eventId"));
        }

        return enrichedData;
    }

    private String deriveDefaultTopic(String aggregateType) {
        return switch (aggregateType.toUpperCase()) {
            case "ORDER" -> "order.events";
            case "PAYMENT" -> "payment.events";
            case "STORE" -> "store.events";
            case "COUPON" -> "coupon.events";
            case "CHECKIN" -> "checkin.events";
            default -> "generic.events";
        };
    }

    private int derivePriority(String eventType) {
        if (eventType == null) return 100;

        String upperType = eventType.toUpperCase();

        // 높은 우선순위 이벤트 타입들
        if (upperType.contains("PAYMENT_APPROVED") || upperType.contains("PAYMENT_FAILED")) {
            return 10; // 결제 관련은 높은 우선순위
        }
        if (upperType.contains("ORDER_CANCELLED") || upperType.contains("ORDER_REFUNDED")) {
            return 20; // 취소/환불 관련
        }
        if (upperType.contains("CREATED") || upperType.contains("UPDATED")) {
            return 50; // 생성/수정 관련
        }

        return 100; // 기본 우선순위
    }

    // === 📦 Helper Classes ===

    /**
     * 이벤트 발행 요청 정보
     */
    @lombok.Data
    @lombok.Builder
    public static class EventPublishRequest {
        private final String aggregateType;
        private final String aggregateId;
        private final String eventType;
        private final Map<String, Object> eventData;
        private final Map<String, Object> headers;
        private final String topic;
    }

    /**
     * 이벤트 발행 예외
     */
    public static class EventPublishingException extends RuntimeException {
        public EventPublishingException(String message) {
            super(message);
        }

        public EventPublishingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}