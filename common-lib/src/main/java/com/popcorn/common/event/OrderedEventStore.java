package com.popcorn.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 🗂️ 순서 보장 이벤트 저장소
 * - Out-of-order 이벤트를 임시 저장
 * - 순서에 맞는 이벤트가 올 때까지 대기
 * - 재처리 및 재정렬 지원
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderedEventStore {

    private static final String PENDING_EVENTS_KEY_PREFIX = "event:pending:";
    private static final String EVENT_WAIT_KEY_PREFIX = "event:wait:";
    private static final long PENDING_EVENT_EXPIRE_MINUTES = 30; // 30분 후 만료

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 🔒 순서가 맞지 않는 이벤트를 임시 저장
     *
     * @param aggregateType 애그리게이트 타입
     * @param aggregateId 애그리게이트 ID
     * @param event 저장할 이벤트
     */
    public void storePendingEvent(String aggregateType, String aggregateId, OrderedEvent event) {
        String pendingKey = buildPendingKey(aggregateType, aggregateId);

        try {
            // 이벤트를 JSON으로 직렬화
            String eventJson = objectMapper.writeValueAsString(event);

            // Redis Sorted Set에 시퀀스 번호를 score로 사용해 저장
            redisTemplate.opsForZSet().add(pendingKey, eventJson, event.getSequence());

            // TTL 설정
            redisTemplate.expire(pendingKey, PENDING_EVENT_EXPIRE_MINUTES, TimeUnit.MINUTES);

            log.info("📦 [대기 저장] 이벤트 저장 완료 - {}:{} sequence: {}, eventId: {}",
                aggregateType, aggregateId, event.getSequence(), event.getEventId());

        } catch (JsonProcessingException e) {
            log.error("❌ [대기 저장] 직렬화 실패 - {}:{} eventId: {}",
                aggregateType, aggregateId, event.getEventId(), e);
        } catch (Exception e) {
            log.error("❌ [대기 저장] 저장 실패 - {}:{} eventId: {}",
                aggregateType, aggregateId, event.getEventId(), e);
        }
    }

    /**
     * 📤 처리 가능한 이벤트들을 순서대로 조회 (제거하지 않고 조회만)
     *
     * @param aggregateType 애그리게이트 타입
     * @param aggregateId 애그리게이트 ID
     * @param expectedNextSequence 다음으로 처리해야 할 시퀀스 번호
     * @return 처리 가능한 이벤트 목록 (순서대로 정렬됨)
     */
    public List<OrderedEvent> getProcessableEvents(String aggregateType, String aggregateId,
                                                  long expectedNextSequence) {
        String pendingKey = buildPendingKey(aggregateType, aggregateId);
        List<OrderedEvent> processableEvents = new ArrayList<>();

        try {
            // 대기 중인 이벤트들을 순서대로 조회
            Set<String> eventJsons = redisTemplate.opsForZSet()
                .rangeByScore(pendingKey, expectedNextSequence, Double.POSITIVE_INFINITY);

            if (eventJsons == null || eventJsons.isEmpty()) {
                return processableEvents;
            }

            long currentExpected = expectedNextSequence;

            // 연속된 시퀀스 번호의 이벤트들만 처리 가능 (제거하지 않음)
            for (String eventJson : eventJsons) {
                try {
                    OrderedEvent event = objectMapper.readValue(eventJson, OrderedEvent.class);

                    if (event.getSequence() == currentExpected) {
                        processableEvents.add(event);
                        currentExpected++;

                        log.debug("✅ [처리 가능] 이벤트 준비 완료 - {}:{} sequence: {}, eventId: {}",
                            aggregateType, aggregateId, event.getSequence(), event.getEventId());
                    } else {
                        // 연속되지 않는 시퀀스가 나오면 중단
                        break;
                    }

                } catch (JsonProcessingException e) {
                    log.error("❌ [처리 가능] 역직렬화 실패 - eventJson: {}", eventJson, e);
                }
            }

            if (!processableEvents.isEmpty()) {
                log.info("🎯 [처리 가능] {} 개 이벤트 준비 완료 - {}:{} sequences: {}-{}",
                    processableEvents.size(), aggregateType, aggregateId,
                    processableEvents.get(0).getSequence(),
                    processableEvents.get(processableEvents.size() - 1).getSequence());
            }

        } catch (Exception e) {
            log.error("❌ [처리 가능] 조회 실패 - {}:{}", aggregateType, aggregateId, e);
        }

        return processableEvents;
    }

    /**
     * 🗑️ 처리 완료된 이벤트 정리 (시퀀스 기반)
     */
    public boolean removeProcessedEvent(String aggregateType, String aggregateId, long sequence) {
        String pendingKey = buildPendingKey(aggregateType, aggregateId);

        try {
            // 해당 시퀀스의 모든 이벤트를 조회하여 제거
            Set<String> eventJsons = redisTemplate.opsForZSet()
                .rangeByScore(pendingKey, sequence, sequence);

            if (eventJsons != null && !eventJsons.isEmpty()) {
                for (String eventJson : eventJsons) {
                    Long removed = redisTemplate.opsForZSet().remove(pendingKey, eventJson);
                    if (removed != null && removed > 0) {
                        log.debug("🗑️ [정리 완료] 대기 이벤트 제거 - {}:{} sequence: {}",
                            aggregateType, aggregateId, sequence);
                        return true;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            log.warn("⚠️ [정리 실패] 대기 이벤트 제거 실패 - {}:{} sequence: {}",
                aggregateType, aggregateId, sequence, e);
            return false;
        }
    }

    /**
     * 🗑️ 처리 완료된 이벤트 정리 (이벤트 객체 기반)
     */
    public void removePendingEvent(String aggregateType, String aggregateId, OrderedEvent event) {
        removeProcessedEvent(aggregateType, aggregateId, event.getSequence());
    }

    /**
     * 📊 대기 중인 이벤트 수 조회
     */
    public long getPendingEventCount(String aggregateType, String aggregateId) {
        String pendingKey = buildPendingKey(aggregateType, aggregateId);

        try {
            Long count = redisTemplate.opsForZSet().count(pendingKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("⚠️ [대기 수] 조회 실패 - {}:{}", aggregateType, aggregateId, e);
            return 0;
        }
    }

    /**
     * 🔍 대기 중인 이벤트 상세 정보 조회 (모니터링용)
     */
    public List<PendingEventInfo> getPendingEventDetails(String aggregateType, String aggregateId) {
        String pendingKey = buildPendingKey(aggregateType, aggregateId);
        List<PendingEventInfo> details = new ArrayList<>();

        try {
            Set<String> eventJsons = redisTemplate.opsForZSet()
                .rangeByScore(pendingKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

            if (eventJsons != null) {
                for (String eventJson : eventJsons) {
                    try {
                        OrderedEvent event = objectMapper.readValue(eventJson, OrderedEvent.class);
                        Double score = redisTemplate.opsForZSet().score(pendingKey, eventJson);

                        details.add(PendingEventInfo.builder()
                            .eventId(event.getEventId())
                            .sequence(event.getSequence())
                            .eventType(event.getEventType())
                            .aggregateType(aggregateType)
                            .aggregateId(aggregateId)
                            .waitingTime(Duration.between(event.getCreatedAt(), LocalDateTime.now()))
                            .score(score != null ? score.longValue() : -1)
                            .build());

                    } catch (JsonProcessingException e) {
                        log.warn("⚠️ [상세 조회] 이벤트 파싱 실패 - eventJson: {}", eventJson);
                    }
                }
            }

            // 시퀀스 번호 순으로 정렬
            details.sort(Comparator.comparingLong(PendingEventInfo::getSequence));

        } catch (Exception e) {
            log.error("❌ [상세 조회] 실패 - {}:{}", aggregateType, aggregateId, e);
        }

        return details;
    }

    /**
     * 🧹 만료된 대기 이벤트 정리
     */
    public int cleanupExpiredEvents(String aggregateType, String aggregateId) {
        String pendingKey = buildPendingKey(aggregateType, aggregateId);
        int cleanedCount = 0;

        try {
            Set<String> eventJsons = redisTemplate.opsForZSet()
                .rangeByScore(pendingKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

            if (eventJsons != null) {
                LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(PENDING_EVENT_EXPIRE_MINUTES);

                for (String eventJson : eventJsons) {
                    try {
                        OrderedEvent event = objectMapper.readValue(eventJson, OrderedEvent.class);

                        if (event.getCreatedAt().isBefore(cutoffTime)) {
                            redisTemplate.opsForZSet().remove(pendingKey, eventJson);
                            cleanedCount++;

                            log.warn("🧹 [만료 정리] 대기 시간 초과 이벤트 제거 - {}:{} sequence: {}, eventId: {}, age: {}분",
                                aggregateType, aggregateId, event.getSequence(), event.getEventId(),
                                Duration.between(event.getCreatedAt(), LocalDateTime.now()).toMinutes());
                        }

                    } catch (JsonProcessingException e) {
                        log.warn("⚠️ [만료 정리] 파싱 실패로 제거 - eventJson: {}", eventJson);
                        redisTemplate.opsForZSet().remove(pendingKey, eventJson);
                        cleanedCount++;
                    }
                }
            }

            if (cleanedCount > 0) {
                log.info("🧹 [만료 정리] 완료 - {}:{} 제거된 이벤트: {} 개",
                    aggregateType, aggregateId, cleanedCount);
            }

        } catch (Exception e) {
            log.error("❌ [만료 정리] 실패 - {}:{}", aggregateType, aggregateId, e);
        }

        return cleanedCount;
    }

    /**
     * 🎯 전체 시스템의 대기 중인 이벤트 현황 조회
     */
    public Map<String, Long> getSystemWidePendingEventCounts() {
        Map<String, Long> counts = new HashMap<>();

        try {
            Set<String> keys = redisTemplate.keys(PENDING_EVENTS_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    Long count = redisTemplate.opsForZSet().count(key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
                    if (count != null && count > 0) {
                        // Key에서 aggregate 정보 추출
                        String aggregateInfo = key.substring(PENDING_EVENTS_KEY_PREFIX.length());
                        counts.put(aggregateInfo, count);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ [시스템 현황] 조회 실패", e);
        }

        return counts;
    }

    // === 🔧 Private Helper Methods ===

    private String buildPendingKey(String aggregateType, String aggregateId) {
        return PENDING_EVENTS_KEY_PREFIX + aggregateType.toLowerCase() + ":" + aggregateId;
    }

    // === 📊 Result Classes ===

    @lombok.Data
    @lombok.Builder
    public static class PendingEventInfo {
        private final UUID eventId;
        private final long sequence;
        private final String eventType;
        private final String aggregateType;
        private final String aggregateId;
        private final Duration waitingTime;
        private final long score;
    }
}