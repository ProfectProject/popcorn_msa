package com.popcorn.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 🔢 이벤트 시퀀스 관리자
 * - 동일한 애그리게이트에 대한 이벤트 순서 번호 생성
 * - Redis를 이용한 분산 시퀀스 관리
 * - 이벤트 순서 보장을 위한 핵심 컴포넌트
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventSequenceManager {

    private static final String SEQUENCE_KEY_PREFIX = "event:sequence:";
    private static final String LAST_PROCESSED_KEY_PREFIX = "event:last_processed:";
    private static final long SEQUENCE_EXPIRE_HOURS = 24; // 24시간 후 만료

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 🎯 이벤트 시퀀스 번호 생성
     *
     * @param aggregateType 애그리게이트 타입 (ORDER, PAYMENT, STORE 등)
     * @param aggregateId 애그리게이트 ID
     * @return 순서 보장된 시퀀스 번호
     */
    public long generateSequence(String aggregateType, String aggregateId) {
        String sequenceKey = buildSequenceKey(aggregateType, aggregateId);

        try {
            // Redis INCR을 사용해 원자적 증가
            Long sequence = redisTemplate.opsForValue().increment(sequenceKey);
            if (sequence == null) {
                log.error("❌ [시퀀스] Redis INCR 실패 - aggregateType: {}, aggregateId: {}",
                    aggregateType, aggregateId);
                return -1;
            }

            // 시퀀스 키에 TTL 설정 (첫 번째 증가 시에만)
            if (sequence == 1) {
                redisTemplate.expire(sequenceKey, SEQUENCE_EXPIRE_HOURS, TimeUnit.HOURS);
            }

            log.debug("📈 [시퀀스] 생성 완료 - {}:{} → sequence: {}",
                aggregateType, aggregateId, sequence);

            return sequence;

        } catch (Exception e) {
            log.error("❌ [시퀀스] 생성 실패 - aggregateType: {}, aggregateId: {}",
                aggregateType, aggregateId, e);
            return -1;
        }
    }

    /**
     * 🔍 현재 시퀀스 번호 조회 (증가시키지 않음)
     */
    public long getCurrentSequence(String aggregateType, String aggregateId) {
        String sequenceKey = buildSequenceKey(aggregateType, aggregateId);

        try {
            String value = redisTemplate.opsForValue().get(sequenceKey);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            log.warn("⚠️ [시퀀스] 조회 실패 - {}:{}", aggregateType, aggregateId, e);
            return 0;
        }
    }

    /**
     * 📊 마지막 처리된 이벤트 시퀀스 업데이트
     *
     * @param aggregateType 애그리게이트 타입
     * @param aggregateId 애그리게이트 ID
     * @param sequence 처리된 시퀀스 번호
     * @param eventId 이벤트 ID (디버깅용)
     */
    public void updateLastProcessedSequence(String aggregateType, String aggregateId,
                                          long sequence, UUID eventId) {
        String lastProcessedKey = buildLastProcessedKey(aggregateType, aggregateId);

        try {
            // 현재 마지막 처리된 시퀀스와 비교
            String currentValue = redisTemplate.opsForValue().get(lastProcessedKey);
            long currentSequence = currentValue != null ? Long.parseLong(currentValue) : 0;

            // 시퀀스가 순차적으로 증가하는지 확인
            if (sequence <= currentSequence) {
                log.warn("⚠️ [순서 경고] 이전 또는 동일한 시퀀스 - {}:{} current: {} → new: {}, eventId: {}",
                    aggregateType, aggregateId, currentSequence, sequence, eventId);
            } else if (sequence != currentSequence + 1) {
                log.error("🚨 [순서 오류] 시퀀스 건너뜀 감지 - {}:{} expected: {} → actual: {}, eventId: {}",
                    aggregateType, aggregateId, currentSequence + 1, sequence, eventId);
            }

            // 마지막 처리된 시퀀스 업데이트
            redisTemplate.opsForValue().set(lastProcessedKey, String.valueOf(sequence),
                Duration.ofHours(SEQUENCE_EXPIRE_HOURS));

            log.debug("✅ [처리 완료] 시퀀스 업데이트 - {}:{} sequence: {}, eventId: {}",
                aggregateType, aggregateId, sequence, eventId);

        } catch (Exception e) {
            log.error("❌ [처리 업데이트] 실패 - {}:{} sequence: {}, eventId: {}",
                aggregateType, aggregateId, sequence, eventId, e);
        }
    }

    /**
     * 🔍 마지막 처리된 시퀀스 조회
     */
    public long getLastProcessedSequence(String aggregateType, String aggregateId) {
        String lastProcessedKey = buildLastProcessedKey(aggregateType, aggregateId);

        try {
            String value = redisTemplate.opsForValue().get(lastProcessedKey);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            log.warn("⚠️ [마지막 처리] 조회 실패 - {}:{}", aggregateType, aggregateId, e);
            return 0;
        }
    }

    /**
     * 🎯 이벤트가 순서에 맞는지 확인
     *
     * @param aggregateType 애그리게이트 타입
     * @param aggregateId 애그리게이트 ID
     * @param eventSequence 확인할 이벤트의 시퀀스
     * @return 순서 검증 결과
     */
    public SequenceValidationResult validateSequence(String aggregateType, String aggregateId,
                                                    long eventSequence) {
        long lastProcessed = getLastProcessedSequence(aggregateType, aggregateId);
        long expectedNext = lastProcessed + 1;

        if (eventSequence == expectedNext) {
            return SequenceValidationResult.IN_ORDER;
        } else if (eventSequence < expectedNext) {
            return SequenceValidationResult.DUPLICATE_OR_LATE;
        } else {
            return SequenceValidationResult.OUT_OF_ORDER;
        }
    }

    /**
     * 🧹 시퀀스 초기화 (테스트 및 복구용)
     */
    public void resetSequence(String aggregateType, String aggregateId) {
        String sequenceKey = buildSequenceKey(aggregateType, aggregateId);
        String lastProcessedKey = buildLastProcessedKey(aggregateType, aggregateId);

        redisTemplate.delete(sequenceKey);
        redisTemplate.delete(lastProcessedKey);

        log.info("🧹 [시퀀스 초기화] 완료 - {}:{}", aggregateType, aggregateId);
    }

    /**
     * 📊 시퀀스 상태 조회 (모니터링용)
     */
    public SequenceStatus getSequenceStatus(String aggregateType, String aggregateId) {
        long currentSequence = getCurrentSequence(aggregateType, aggregateId);
        long lastProcessed = getLastProcessedSequence(aggregateType, aggregateId);
        long gap = currentSequence - lastProcessed;

        return SequenceStatus.builder()
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .currentSequence(currentSequence)
            .lastProcessedSequence(lastProcessed)
            .pendingEvents(gap)
            .isHealthy(gap <= 10) // 10개 이상 밀리면 비정상으로 판단
            .build();
    }

    // === 🔧 Private Helper Methods ===

    private String buildSequenceKey(String aggregateType, String aggregateId) {
        return SEQUENCE_KEY_PREFIX + aggregateType.toLowerCase() + ":" + aggregateId;
    }

    private String buildLastProcessedKey(String aggregateType, String aggregateId) {
        return LAST_PROCESSED_KEY_PREFIX + aggregateType.toLowerCase() + ":" + aggregateId;
    }

    // === 🎯 Result Classes ===

    public enum SequenceValidationResult {
        IN_ORDER,           // 정상 순서
        OUT_OF_ORDER,       // 순서가 앞섬 (나중에 처리해야 함)
        DUPLICATE_OR_LATE   // 중복이거나 늦은 이벤트 (무시 가능)
    }

    @lombok.Data
    @lombok.Builder
    public static class SequenceStatus {
        private final String aggregateType;
        private final String aggregateId;
        private final long currentSequence;
        private final long lastProcessedSequence;
        private final long pendingEvents;
        private final boolean isHealthy;
    }
}