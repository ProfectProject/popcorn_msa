package com.popcorn.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 📋 순서 보장 이벤트 모델
 * - 모든 이벤트가 가져야 하는 순서 관련 메타데이터
 * - 시퀀스 번호와 처리 상태 정보 포함
 * - JSON 직렬화/역직렬화 지원
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderedEvent {

    /**
     * 이벤트 고유 ID
     */
    private UUID eventId;

    /**
     * 애그리게이트 타입 (ORDER, PAYMENT, STORE, COUPON, CHECKIN)
     */
    private String aggregateType;

    /**
     * 애그리게이트 ID (주문 ID, 결제 ID 등)
     */
    private String aggregateId;

    /**
     * 🔢 이벤트 시퀀스 번호 (동일 애그리게이트 내에서 순서 보장)
     */
    private long sequence;

    /**
     * 이벤트 타입 (ORDER_CREATED, PAYMENT_APPROVED 등)
     */
    private String eventType;

    /**
     * Kafka 토픽명
     */
    private String topic;

    /**
     * 파티션 키 (Kafka 파티셔닝용)
     */
    private String partitionKey;

    /**
     * 스키마 버전
     */
    private int schemaVersion;

    /**
     * 이벤트 데이터 (실제 비즈니스 데이터)
     */
    private Map<String, Object> eventData;

    /**
     * 이벤트 헤더 정보
     */
    private Map<String, Object> headers;

    /**
     * 이벤트 발생 시각
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime occurredAt;

    /**
     * 이벤트 생성 시각
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 🎯 처리 우선순위 (낮을수록 높은 우선순위)
     */
    @Builder.Default
    private int priority = 100;

    /**
     * 📊 재시도 횟수
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * 🚨 최대 재시도 횟수
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * ⏰ 마지막 처리 시도 시각
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAttemptAt;

    /**
     * 💾 처리 상태
     */
    @Builder.Default
    private ProcessingStatus status = ProcessingStatus.PENDING;

    /**
     * ❌ 에러 메시지 (처리 실패 시)
     */
    private String errorMessage;

    /**
     * 🏷️ 이벤트에 대한 사용자 정의 태그
     */
    private Map<String, String> tags;

    // === 🔧 Business Logic Methods ===

    /**
     * 🎯 이벤트 처리 시작
     */
    public OrderedEvent startProcessing() {
        return this.toBuilder()
            .status(ProcessingStatus.PROCESSING)
            .lastAttemptAt(LocalDateTime.now())
            .build();
    }

    /**
     * ✅ 이벤트 처리 성공
     */
    public OrderedEvent markAsProcessed() {
        return this.toBuilder()
            .status(ProcessingStatus.PROCESSED)
            .lastAttemptAt(LocalDateTime.now())
            .build();
    }

    /**
     * ❌ 이벤트 처리 실패
     */
    public OrderedEvent markAsFailed(String errorMessage) {
        int newRetryCount = this.retryCount + 1;
        ProcessingStatus newStatus = newRetryCount >= maxRetries ?
            ProcessingStatus.DEAD_LETTER : ProcessingStatus.FAILED;

        return this.toBuilder()
            .status(newStatus)
            .retryCount(newRetryCount)
            .errorMessage(errorMessage)
            .lastAttemptAt(LocalDateTime.now())
            .build();
    }

    /**
     * 🔄 재처리 준비
     */
    public OrderedEvent prepareForRetry() {
        if (this.retryCount >= this.maxRetries) {
            throw new IllegalStateException("Maximum retry count exceeded for event: " + this.eventId);
        }

        return this.toBuilder()
            .status(ProcessingStatus.PENDING)
            .lastAttemptAt(null)
            .build();
    }

    /**
     * 🎯 이벤트가 처리 가능한 상태인지 확인
     */
    public boolean isProcessable() {
        return status == ProcessingStatus.PENDING;
    }

    /**
     * ⏰ 이벤트가 처리 시간을 초과했는지 확인
     */
    public boolean isProcessingTimeout(int timeoutMinutes) {
        if (status != ProcessingStatus.PROCESSING || lastAttemptAt == null) {
            return false;
        }
        return lastAttemptAt.isBefore(LocalDateTime.now().minusMinutes(timeoutMinutes));
    }

    /**
     * 🏷️ 태그 추가
     */
    public OrderedEvent addTag(String key, String value) {
        if (this.tags == null) {
            this.tags = Map.of(key, value);
        } else {
            Map<String, String> newTags = Map.of(key, value);
            newTags.putAll(this.tags);
            this.tags = newTags;
        }
        return this;
    }

    /**
     * 🔍 특정 태그 값 조회
     */
    public String getTag(String key) {
        return tags != null ? tags.get(key) : null;
    }

    /**
     * 📊 이벤트 요약 정보 (로깅용)
     */
    public String getSummary() {
        return String.format("Event[id=%s, type=%s, aggregate=%s:%s, seq=%d, status=%s]",
            eventId, eventType, aggregateType, aggregateId, sequence, status);
    }

    /**
     * ⚖️ 같은 애그리게이트의 다른 이벤트와 순서 비교
     */
    public int compareSequence(OrderedEvent other) {
        if (!this.aggregateType.equals(other.aggregateType) ||
            !this.aggregateId.equals(other.aggregateId)) {
            throw new IllegalArgumentException("Cannot compare events from different aggregates");
        }
        return Long.compare(this.sequence, other.sequence);
    }

    // === 🔧 Factory Methods ===

    /**
     * 🏭 기존 Outbox Event에서 OrderedEvent 생성
     */
    public static OrderedEvent fromOutboxEvent(UUID eventId, String aggregateType, String aggregateId,
                                             long sequence, String eventType, String topic,
                                             String partitionKey, int schemaVersion,
                                             Map<String, Object> eventData, Map<String, Object> headers,
                                             LocalDateTime occurredAt) {
        return OrderedEvent.builder()
            .eventId(eventId)
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .sequence(sequence)
            .eventType(eventType)
            .topic(topic)
            .partitionKey(partitionKey)
            .schemaVersion(schemaVersion)
            .eventData(eventData)
            .headers(headers)
            .occurredAt(occurredAt)
            .createdAt(LocalDateTime.now())
            .status(ProcessingStatus.PENDING)
            .build();
    }

    /**
     * 🎯 높은 우선순위 이벤트 생성
     */
    public static OrderedEvent createHighPriority(UUID eventId, String aggregateType,
                                                 String aggregateId, long sequence,
                                                 String eventType, Map<String, Object> eventData) {
        return OrderedEvent.builder()
            .eventId(eventId)
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .sequence(sequence)
            .eventType(eventType)
            .eventData(eventData)
            .priority(1) // 높은 우선순위
            .occurredAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .status(ProcessingStatus.PENDING)
            .build();
    }

    // === 📊 Enums ===

    public enum ProcessingStatus {
        PENDING,        // 처리 대기 중
        PROCESSING,     // 처리 중
        PROCESSED,      // 처리 완료
        FAILED,         // 처리 실패 (재시도 가능)
        DEAD_LETTER,    // 최종 실패 (Dead Letter Queue로 이동)
        SKIPPED         // 건너뛰어짐 (중복 등)
    }
}