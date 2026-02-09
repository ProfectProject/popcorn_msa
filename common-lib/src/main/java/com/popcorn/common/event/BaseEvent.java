package com.popcorn.common.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Builder;
import lombok.Getter;

/**
 * 모든 도메인 이벤트의 기본 클래스
 *
 * 기능:
 * - 이벤트 메타데이터 관리
 * - 이벤트 버전 관리
 * - 상관관계 추적 (Correlation ID)
 * - 이벤트 재생 및 디버깅 지원
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@Getter
public abstract class BaseEvent {

    private final UUID eventId;            // 이벤트 고유 ID
    private final UUID aggregateId;        // 애그리거트 ID (주문 ID, 결제 ID 등)
    private final String eventType;        // 이벤트 타입
    private final LocalDateTime timestamp;  // 이벤트 발생 시간
    private final String eventVersion;     // 이벤트 스키마 버전
    private final UUID correlationId;     // 상관관계 ID (요청 추적용)
    private final Long userId;             // 사용자 ID
    private final String aggregateType;    // 애그리거트 타입 (Order, Payment 등)
    private final Map<String, Object> metadata; // 추가 메타데이터

    protected BaseEvent(UUID aggregateId, String aggregateType, String eventType, Long userId, Map<String, Object> metadata) {
        validateEventType(eventType);
        this.eventId = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.eventVersion = "1.0"; // 현재는 고정, 향후 동적 관리 가능
        this.correlationId = generateCorrelationId();
        this.userId = userId;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    protected BaseEvent(UUID aggregateId, String aggregateType, String eventType, Long userId) {
        this(aggregateId, aggregateType, eventType, userId, null);
    }

    // ================ Event Context ================

    /**
     * 이벤트가 발생한 컨텍스트 정보 반환
     */
    public EventContext getEventContext() {
        return EventContext.builder()
                .eventId(eventId)
                .correlationId(correlationId)
                .timestamp(timestamp)
                .version(eventVersion)
                .build();
    }

    /**
     * 특정 메타데이터 값 조회
     */
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    /**
     * 메타데이터 존재 여부 확인
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    // ================ Helper Methods ================

    private UUID generateCorrelationId() {
        // 현재는 새로운 UUID 생성, 향후 MDC나 Request Context에서 가져올 수 있음
        return UUID.randomUUID();
    }

    /**
     * 이벤트 설명 생성 (로깅 및 디버깅용)
     */
    public String getEventDescription() {
        return String.format("%s[id=%s, aggregateId=%s, aggregateType=%s, userId=%s, timestamp=%s]",
                eventType, eventId, aggregateId, aggregateType, userId, timestamp);
    }

    /**
     * 이벤트 타입이 유효한지 검증합니다.
     * UPPER_SNAKE_CASE 형식인지 확인합니다.
     */
    protected final void validateEventType(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("이벤트 타입은 필수입니다.");
        }
        if (!eventType.matches("^[A-Z0-9]+(_[A-Z0-9]+)*$")) {
            throw new IllegalArgumentException("이벤트 타입은 UPPER_SNAKE_CASE 형식이어야 합니다: " + eventType);
        }
    }

    /**
     * 이벤트를 JSON 형태로 직렬화할 때 포함할 공통 필드들
     */
    public abstract Map<String, Object> getEventPayload();

    // ================ 내부 클래스 ================

    /**
     * 이벤트 컨텍스트 정보
     */
    @Getter
    public static class EventContext {
        private final UUID eventId;
        private final UUID correlationId;
        private final LocalDateTime timestamp;
        private final String version;

        private EventContext(Builder builder) {
            this.eventId = builder.eventId;
            this.correlationId = builder.correlationId;
            this.timestamp = builder.timestamp;
            this.version = builder.version;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private UUID eventId;
            private UUID correlationId;
            private LocalDateTime timestamp;
            private String version;

            public Builder eventId(UUID eventId) {
                this.eventId = eventId;
                return this;
            }

            public Builder correlationId(UUID correlationId) {
                this.correlationId = correlationId;
                return this;
            }

            public Builder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder version(String version) {
                this.version = version;
                return this;
            }

            public EventContext build() {
                return new EventContext(this);
            }
        }
    }

    @Override
    public String toString() {
        return getEventDescription();
    }
}
