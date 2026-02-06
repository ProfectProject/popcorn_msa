package com.popcorn.common.kafka;

import com.popcorn.common.util.IdempotencyKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 기반 Kafka 멱등성 서비스.
 *
 * 키 포맷: kafka:processed:event:{eventId}
 */
@Service
@ConditionalOnProperty(value = "kafka.enabled", havingValue = "true")
public class RedisKafkaIdempotencyService implements KafkaIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(RedisKafkaIdempotencyService.class);

    private static final String PROCESSED_EVENT_PREFIX = "kafka:processed:event:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration ttl;

    public RedisKafkaIdempotencyService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${kafka.idempotency.ttl-hours:24}") long ttlHours
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    @Override
    public boolean isDuplicateEvent(String eventId) {
        try {
            String key = eventKey(eventId);
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                log.debug("⏭️  중복 이벤트 감지: eventId={}", eventId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("⚠️ Redis 중복 체크 실패, 안전하게 false 반환: eventId={}, error={}", eventId, e.getMessage());
            return false;
        }
    }

    @Override
    public void recordProcessedEvent(String eventId,
                                     String eventType,
                                     String topic,
                                     Integer partition,
                                     Long offset,
                                     Long processingTimeMs) {
        try {
            String key = eventKey(eventId);
            Map<String, Object> eventInfo = new HashMap<>();
            eventInfo.put("eventId", eventId);
            eventInfo.put("eventType", eventType);
            eventInfo.put("topic", topic);
            eventInfo.put("partition", partition);
            eventInfo.put("offset", offset);
            eventInfo.put("processingTimeMs", processingTimeMs);
            eventInfo.put("processedAt", System.currentTimeMillis());

            redisTemplate.opsForValue().set(key, eventInfo, ttl);
            log.debug("✅ 이벤트 처리 기록: eventId={}, ttl={}h", eventId, ttl.toHours());
        } catch (Exception e) {
            log.warn("⚠️ Redis 처리 기록 실패: eventId={}, error={}", eventId, e.getMessage());
        }
    }

    private String eventKey(String eventId) {
        return PROCESSED_EVENT_PREFIX + eventId;
    }
}
