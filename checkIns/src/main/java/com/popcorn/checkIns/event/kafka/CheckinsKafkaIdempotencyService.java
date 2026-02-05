package com.popcorn.checkIns.event.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * CheckIn 도메인 Kafka 멱등성 보장 서비스
 *
 * Redis를 사용하여 이벤트 중복 처리를 방지합니다.
 * - eventId 기반 중복 감지
 * - 처리 완료 기록 및 TTL 관리 (24시간)
 * - Redis 장애 시 graceful handling
 */
@Service
@ConditionalOnProperty(value = "kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CheckinsKafkaIdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis Key 접두사
    private static final String PROCESSED_EVENT_PREFIX = "checkin:kafka:processed:";
    private static final String PROCESSING_INFO_PREFIX = "checkin:kafka:processing:";

    // TTL 설정
    private static final Duration EVENT_TTL = Duration.ofHours(24);

    /**
     * 중복 이벤트 여부 확인
     *
     * @param eventId 이벤트 고유 ID
     * @return true: 중복 이벤트 (이미 처리됨), false: 새로운 이벤트
     */
    public boolean isDuplicateEvent(String eventId) {
        try {
            String key = PROCESSED_EVENT_PREFIX + eventId;
            Boolean exists = redisTemplate.hasKey(key);

            if (Boolean.TRUE.equals(exists)) {
                log.debug("🔍 [IDEMPOTENCY] 중복 이벤트 감지: eventId={}", eventId);
                return true;
            }

            log.debug("🆕 [IDEMPOTENCY] 새로운 이벤트: eventId={}", eventId);
            return false;

        } catch (Exception e) {
            log.error("❌ [IDEMPOTENCY] 중복 체크 실패 (가용성 우선으로 false 반환): eventId={} error={}",
                    eventId, e.getMessage());
            // Redis 장애 시 가용성 우선 - 중복 처리 허용
            return false;
        }
    }

    /**
     * 이벤트 처리 완료 기록
     *
     * @param eventId 이벤트 고유 ID
     * @param eventType 이벤트 타입
     * @param topic Kafka 토픽
     * @param partition 파티션 번호
     * @param offset 오프셋
     * @param processingTimeMs 처리 시간 (밀리초)
     */
    public void recordProcessedEvent(String eventId, String eventType, String topic,
                                   Integer partition, Long offset, Long processingTimeMs) {
        try {
            String processedKey = PROCESSED_EVENT_PREFIX + eventId;
            String processingKey = PROCESSING_INFO_PREFIX + eventId;

            // 처리 완료 마킹 (간단한 값)
            redisTemplate.opsForValue().set(processedKey, "1", EVENT_TTL);

            // 처리 상세 정보 저장
            ProcessingInfo info = new ProcessingInfo(
                    eventId, eventType, topic, partition, offset,
                    processingTimeMs, LocalDateTime.now()
            );

            redisTemplate.opsForValue().set(processingKey, info, EVENT_TTL);

            log.debug("✅ [IDEMPOTENCY] 처리 완료 기록: eventId={} eventType={} processingTime={}ms",
                    eventId, eventType, processingTimeMs);

        } catch (Exception e) {
            log.warn("⚠️ [IDEMPOTENCY] 처리 완료 기록 실패: eventId={} error={}",
                    eventId, e.getMessage());
            // Redis 장애 시에도 메인 로직은 계속 진행
        }
    }

    /**
     * 이벤트 처리 정보 조회
     *
     * @param eventId 이벤트 고유 ID
     * @return 처리 정보 (없으면 null)
     */
    public ProcessingInfo getProcessingInfo(String eventId) {
        try {
            String key = PROCESSING_INFO_PREFIX + eventId;
            Object info = redisTemplate.opsForValue().get(key);

            if (info instanceof ProcessingInfo) {
                return (ProcessingInfo) info;
            }

            return null;

        } catch (Exception e) {
            log.error("❌ [IDEMPOTENCY] 처리 정보 조회 실패: eventId={} error={}",
                    eventId, e.getMessage());
            return null;
        }
    }

    /**
     * 처리 통계 조회 (모니터링용)
     *
     * @return 처리된 이벤트 수 (근사치)
     */
    public long getProcessedEventCount() {
        try {
            String pattern = PROCESSED_EVENT_PREFIX + "*";
            var keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("⚠️ [IDEMPOTENCY] 통계 조회 실패: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 특정 이벤트 처리 기록 삭제 (테스트용)
     */
    public void clearProcessedEvent(String eventId) {
        try {
            redisTemplate.delete(PROCESSED_EVENT_PREFIX + eventId);
            redisTemplate.delete(PROCESSING_INFO_PREFIX + eventId);
            log.info("🗑️ [IDEMPOTENCY] 처리 기록 삭제: eventId={}", eventId);
        } catch (Exception e) {
            log.warn("⚠️ [IDEMPOTENCY] 기록 삭제 실패: eventId={} error={}",
                    eventId, e.getMessage());
        }
    }

    /**
     * 이벤트 처리 정보
     */
    public record ProcessingInfo(
            String eventId,
            String eventType,
            String topic,
            Integer partition,
            Long offset,
            Long processingTimeMs,
            LocalDateTime processedAt
    ) {
        public String getProcessingDescription() {
            return String.format("📊 Event[%s] %s processed in %dms at %s (topic=%s, partition=%d, offset=%d)",
                    eventId, eventType, processingTimeMs, processedAt, topic, partition, offset);
        }
    }
}