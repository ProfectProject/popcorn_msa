package com.popcorn.common.kafka;

/**
 * Kafka 이벤트 멱등성 서비스 (eventId 기반)
 *
 * - Kafka 리스너에서 중복 처리를 방지하기 위해 사용
 * - 구현체는 Redis 기반으로 공유 상태를 유지해야 함
 */
public interface KafkaIdempotencyService {

    /**
     * @return true if eventId already processed
     */
    boolean isDuplicateEvent(String eventId);

    /**
     * Record a successfully processed event.
     */
    void recordProcessedEvent(String eventId,
                              String eventType,
                              String topic,
                              Integer partition,
                              Long offset,
                              Long processingTimeMs);
}
