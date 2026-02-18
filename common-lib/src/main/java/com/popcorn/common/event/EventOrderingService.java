package com.popcorn.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.annotation.Transactional;

/**
 * 🎯 이벤트 순서 보장 핵심 서비스
 * - 이벤트 순서 검증 및 처리
 * - Out-of-order 이벤트 재정렬
 * - 배치 처리 및 성능 최적화
 * - 모니터링 및 알림
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventOrderingService {

    private final EventSequenceManager sequenceManager;
    private final OrderedEventStore eventStore;
    private final EventProcessor eventProcessor;

    /**
     * 🎯 이벤트 처리 - 순서 보장
     *
     * @param event 처리할 이벤트
     * @return 처리 결과
     */
    @Transactional
    public EventProcessingResult processEvent(OrderedEvent event) {
        if (event == null) {
            log.warn("⚠️ [순서 처리] NULL 이벤트 무시");
            return EventProcessingResult.skipped("NULL 이벤트");
        }

        log.debug("📥 [순서 처리] 이벤트 수신 - {}", event.getSummary());

        try {
            // 1. 시퀀스 검증
            EventSequenceManager.SequenceValidationResult validation = sequenceManager.validateSequence(
                event.getAggregateType(), event.getAggregateId(), event.getSequence());

            switch (validation) {
                case IN_ORDER -> {
                    // 정상 순서 - 즉시 처리
                    return processInOrderEvent(event);
                }
                case OUT_OF_ORDER -> {
                    // 순서가 앞선 이벤트 - 대기 저장소에 저장
                    return handleOutOfOrderEvent(event);
                }
                case DUPLICATE_OR_LATE -> {
                    // 중복이거나 늦은 이벤트 - 스킵
                    return EventProcessingResult.skipped("중복 또는 늦은 이벤트");
                }
                default -> {
                    log.error("❌ [순서 처리] 알 수 없는 검증 결과: {}", validation);
                    return EventProcessingResult.failed("알 수 없는 검증 결과");
                }
            }

        } catch (Exception e) {
            log.error("❌ [순서 처리] 처리 실패 - eventId: {}, error: {}",
                event.getEventId(), e.getMessage(), e);
            return EventProcessingResult.failed("처리 중 예외 발생: " + e.getMessage());
        }
    }

    /**
     * ✅ 정상 순서 이벤트 처리
     */
    private EventProcessingResult processInOrderEvent(OrderedEvent event) {
        return processInOrderEvent(event, true);
    }

    /**
     * ✅ 정상 순서 이벤트 처리 (재귀 제어)
     */
    @Transactional
    private EventProcessingResult processInOrderEvent(OrderedEvent event, boolean processWaiting) {
        try {
            // 1. 이벤트 처리
            OrderedEvent processingEvent = event.startProcessing();
            EventProcessingResult result = eventProcessor.process(processingEvent);

            if (result.isSuccess()) {
                // 2. 처리 성공 - 시퀀스 업데이트
                sequenceManager.updateLastProcessedSequence(
                    event.getAggregateType(), event.getAggregateId(),
                    event.getSequence(), event.getEventId());

                // 3. 대기 중인 다음 이벤트들 처리 시도 (재귀 방지)
                if (processWaiting) {
                    processWaitingEventsIterative(event.getAggregateType(), event.getAggregateId());
                }

                log.info("✅ [정상 처리] 이벤트 처리 완료 - {}", event.getSummary());
                return result;
            } else {
                // 처리 실패
                log.error("❌ [정상 처리] 이벤트 처리 실패 - {}, error: {}",
                    event.getSummary(), result.getErrorMessage());
                return result;
            }

        } catch (Exception e) {
            log.error("❌ [정상 처리] 예외 발생 - {}", event.getSummary(), e);
            return EventProcessingResult.failed("정상 처리 중 예외: " + e.getMessage());
        }
    }

    /**
     * 🔄 순서가 맞지 않는 이벤트 처리
     */
    private EventProcessingResult handleOutOfOrderEvent(OrderedEvent event) {
        try {
            // 대기 저장소에 저장
            eventStore.storePendingEvent(event.getAggregateType(), event.getAggregateId(), event);

            log.info("📦 [순서 대기] 이벤트 저장 완료 - {} (다음 예상 시퀀스: {})",
                event.getSummary(),
                sequenceManager.getLastProcessedSequence(event.getAggregateType(), event.getAggregateId()) + 1);

            return EventProcessingResult.deferred("순서 대기 저장 완료");

        } catch (Exception e) {
            log.error("❌ [순서 대기] 저장 실패 - {}", event.getSummary(), e);
            return EventProcessingResult.failed("대기 저장 실패: " + e.getMessage());
        }
    }

    /**
     * 🎯 대기 중인 이벤트들 처리 (반복적 처리 - 재귀 방지)
     */
    private void processWaitingEventsIterative(String aggregateType, String aggregateId) {
        try {
            int totalProcessedCount = 0;
            int maxIterations = 100; // 무한 루프 방지
            int iterations = 0;

            while (iterations < maxIterations) {
                iterations++;

                long expectedNext = sequenceManager.getLastProcessedSequence(aggregateType, aggregateId) + 1;
                List<OrderedEvent> processableEvents = eventStore.getProcessableEvents(
                    aggregateType, aggregateId, expectedNext);

                if (processableEvents.isEmpty()) {
                    log.debug("📭 [대기 처리] 처리 가능한 대기 이벤트 없음 - {}:{} (iteration: {})",
                        aggregateType, aggregateId, iterations);
                    break;
                }

                log.info("🎯 [대기 처리] {} 개 대기 이벤트 처리 시작 - {}:{}, sequences: {}-{} (iteration: {})",
                    processableEvents.size(), aggregateType, aggregateId,
                    processableEvents.get(0).getSequence(),
                    processableEvents.get(processableEvents.size() - 1).getSequence(), iterations);

                int batchProcessedCount = 0;
                boolean continueBatch = true;

                for (OrderedEvent waitingEvent : processableEvents) {
                    if (!continueBatch) break;

                    try {
                        // 대기 이벤트를 정상 순서로 처리 (재귀 방지)
                        EventProcessingResult result = processInOrderEvent(waitingEvent, false);

                        if (result.isSuccess()) {
                            // 처리 성공 후 대기 목록에서 제거
                            eventStore.removeProcessedEvent(
                                waitingEvent.getAggregateType(),
                                waitingEvent.getAggregateId(),
                                waitingEvent.getSequence());

                            batchProcessedCount++;
                            totalProcessedCount++;
                            log.debug("✅ [대기 처리] 이벤트 처리 완료 - {}", waitingEvent.getSummary());
                        } else {
                            log.error("❌ [대기 처리] 이벤트 처리 실패 - {}, error: {}",
                                waitingEvent.getSummary(), result.getErrorMessage());
                            continueBatch = false; // 실패 시 현재 배치 중단
                        }

                    } catch (Exception e) {
                        log.error("❌ [대기 처리] 예외 발생 - {}", waitingEvent.getSummary(), e);
                        continueBatch = false; // 예외 시 현재 배치 중단
                    }
                }

                log.info("🎯 [대기 처리] 배치 완료 - {}:{}, 배치 처리: {}/{} 개 (iteration: {})",
                    aggregateType, aggregateId, batchProcessedCount, processableEvents.size(), iterations);

                // 처리 가능한 이벤트가 없거나 실패한 경우 중단
                if (batchProcessedCount == 0) {
                    break;
                }
            }

            if (iterations >= maxIterations) {
                log.warn("⚠️ [대기 처리] 최대 반복 횟수 도달 - {}:{}, 총 처리: {} 개",
                    aggregateType, aggregateId, totalProcessedCount);
            } else if (totalProcessedCount > 0) {
                log.info("🎯 [대기 처리] 전체 완료 - {}:{}, 총 처리: {} 개",
                    aggregateType, aggregateId, totalProcessedCount);
            }

        } catch (Exception e) {
            log.error("❌ [대기 처리] 전체 처리 실패 - {}:{}", aggregateType, aggregateId, e);
        }
    }

    /**
     * 📊 배치 이벤트 처리 (성능 최적화)
     *
     * @param events 처리할 이벤트 목록
     * @return 배치 처리 결과
     */
    public BatchProcessingResult processBatch(List<OrderedEvent> events) {
        if (events == null || events.isEmpty()) {
            return BatchProcessingResult.empty();
        }

        log.info("📦 [배치 처리] {} 개 이벤트 배치 처리 시작", events.size());

        // 애그리게이트별로 그룹화
        Map<String, List<OrderedEvent>> groupedEvents = groupEventsByAggregate(events);

        Map<UUID, EventProcessingResult> individualResults = new HashMap<>();
        int totalProcessed = 0;
        int totalFailed = 0;
        int totalDeferred = 0;
        int totalSkipped = 0;

        for (Map.Entry<String, List<OrderedEvent>> entry : groupedEvents.entrySet()) {
            String aggregateKey = entry.getKey();
            List<OrderedEvent> aggregateEvents = entry.getValue();

            log.debug("🎯 [배치 처리] {} 개 이벤트 처리 시작 - aggregate: {}",
                aggregateEvents.size(), aggregateKey);

            // 시퀀스 순으로 정렬
            aggregateEvents.sort(Comparator.comparingLong(OrderedEvent::getSequence));

            for (OrderedEvent event : aggregateEvents) {
                EventProcessingResult result = processEvent(event);

                switch (result.getStatus()) {
                    case SUCCESS -> totalProcessed++;
                    case FAILED -> totalFailed++;
                    case DEFERRED -> totalDeferred++;
                    case SKIPPED -> totalSkipped++;
                }

                individualResults.put(event.getEventId(), result);
            }
        }

        BatchProcessingResult batchResult = BatchProcessingResult.builder()
            .totalEvents(events.size())
            .processedCount(totalProcessed)
            .failedCount(totalFailed)
            .deferredCount(totalDeferred)
            .skippedCount(totalSkipped)
            .individualResults(individualResults)
            .build();

        log.info("📦 [배치 처리] 완료 - 총: {}, 성공: {}, 실패: {}, 대기: {}, 스킵: {}",
            events.size(), totalProcessed, totalFailed, totalDeferred, totalSkipped);

        return batchResult;
    }

    /**
     * 🧹 만료된 대기 이벤트 정리
     */
    public CleanupResult cleanupExpiredEvents() {
        log.info("🧹 [정리 작업] 만료된 대기 이벤트 정리 시작");

        Map<String, Integer> cleanedByAggregate = new HashMap<>();
        int totalCleaned = 0;

        try {
            // 전체 시스템의 대기 이벤트 현황 조회
            Map<String, Long> pendingEventCounts = eventStore.getSystemWidePendingEventCounts();

            for (String aggregateKey : pendingEventCounts.keySet()) {
                String[] parts = aggregateKey.split(":", 2);
                if (parts.length != 2) continue;

                String aggregateType = parts[0];
                String aggregateId = parts[1];

                int cleanedCount = eventStore.cleanupExpiredEvents(aggregateType, aggregateId);
                totalCleaned += cleanedCount;

                if (cleanedCount > 0) {
                    cleanedByAggregate.put(aggregateKey, cleanedCount);
                }
            }

            CleanupResult result = CleanupResult.builder()
                .totalCleaned(totalCleaned)
                .cleanupTime(LocalDateTime.now())
                .success(true)
                .cleanedByAggregate(cleanedByAggregate)
                .build();

            log.info("🧹 [정리 작업] 완료 - 총 {} 개 만료 이벤트 정리", totalCleaned);
            return result;

        } catch (Exception e) {
            log.error("❌ [정리 작업] 실패", e);
            return CleanupResult.failed(e.getMessage());
        }
    }

    /**
     * 📊 시스템 상태 조회 (모니터링용)
     */
    public SystemHealthStatus getSystemHealth() {
        try {
            Map<String, Long> pendingEventCounts = eventStore.getSystemWidePendingEventCounts();
            long totalPendingEvents = pendingEventCounts.values().stream().mapToLong(Long::longValue).sum();

            // 건강성 판단 기준
            boolean isHealthy = totalPendingEvents < 100; // 100개 미만이면 정상
            boolean needsAttention = totalPendingEvents > 500; // 500개 이상이면 주의 필요

            SystemHealthStatus.HealthLevel healthLevel;
            if (needsAttention) {
                healthLevel = SystemHealthStatus.HealthLevel.CRITICAL;
            } else if (!isHealthy) {
                healthLevel = SystemHealthStatus.HealthLevel.WARNING;
            } else {
                healthLevel = SystemHealthStatus.HealthLevel.HEALTHY;
            }

            return SystemHealthStatus.builder()
                .healthLevel(healthLevel)
                .totalPendingEvents(totalPendingEvents)
                .aggregateEventCounts(pendingEventCounts)
                .lastCheckTime(LocalDateTime.now())
                .isHealthy(isHealthy)
                .needsAttention(needsAttention)
                .build();

        } catch (Exception e) {
            log.error("❌ [건강성 체크] 실패", e);
            return SystemHealthStatus.failed(e.getMessage());
        }
    }

    /**
     * 🚨 특정 애그리게이트의 이벤트 순서 강제 복구
     */
    public RecoveryResult forceRecovery(String aggregateType, String aggregateId) {
        log.warn("🚨 [강제 복구] 시작 - {}:{}", aggregateType, aggregateId);

        try {
            // 1. 현재 상태 조회
            long currentSequence = sequenceManager.getCurrentSequence(aggregateType, aggregateId);
            long lastProcessed = sequenceManager.getLastProcessedSequence(aggregateType, aggregateId);
            long pendingCount = eventStore.getPendingEventCount(aggregateType, aggregateId);

            log.info("🔍 [강제 복구] 현재 상태 - current: {}, lastProcessed: {}, pending: {}",
                currentSequence, lastProcessed, pendingCount);

            // 2. 대기 중인 이벤트들 강제 처리
            int processedCount = 0;
            for (long seq = lastProcessed + 1; seq <= currentSequence; seq++) {
                List<OrderedEvent> events = eventStore.getProcessableEvents(aggregateType, aggregateId, seq);
                for (OrderedEvent event : events) {
                    EventProcessingResult result = eventProcessor.process(event);
                    if (result.isSuccess()) {
                        sequenceManager.updateLastProcessedSequence(aggregateType, aggregateId,
                            event.getSequence(), event.getEventId());
                        processedCount++;
                    }
                }
            }

            // 3. 복구 결과
            RecoveryResult result = RecoveryResult.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .recoveredEventCount(processedCount)
                .beforeCurrentSequence(currentSequence)
                .beforeLastProcessed(lastProcessed)
                .afterLastProcessed(sequenceManager.getLastProcessedSequence(aggregateType, aggregateId))
                .recoveryTime(LocalDateTime.now())
                .success(true)
                .build();

            log.warn("🚨 [강제 복구] 완료 - {}:{}, 복구된 이벤트: {} 개",
                aggregateType, aggregateId, processedCount);

            return result;

        } catch (Exception e) {
            log.error("❌ [강제 복구] 실패 - {}:{}", aggregateType, aggregateId, e);
            return RecoveryResult.failed(aggregateType, aggregateId, e.getMessage());
        }
    }

    // === 🔧 Private Helper Methods ===

    private Map<String, List<OrderedEvent>> groupEventsByAggregate(List<OrderedEvent> events) {
        Map<String, List<OrderedEvent>> grouped = new HashMap<>();

        for (OrderedEvent event : events) {
            String key = event.getAggregateType() + ":" + event.getAggregateId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }

        return grouped;
    }

    // === 📊 Inner Interfaces ===

    /**
     * 이벤트 처리기 인터페이스
     */
    public interface EventProcessor {
        EventProcessingResult process(OrderedEvent event);
    }
}