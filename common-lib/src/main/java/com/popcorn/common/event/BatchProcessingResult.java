package com.popcorn.common.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 🎯 배치 처리 결과
 */
@Getter
@Builder
@AllArgsConstructor
public class BatchProcessingResult {

    private final int totalEvents;
    private final int processedCount;
    private final int failedCount;
    private final int deferredCount;
    private final int skippedCount;

    @Builder.Default
    private final LocalDateTime processingTime = LocalDateTime.now();

    @Builder.Default
    private final java.util.Map<UUID, EventProcessingResult> individualResults = new java.util.HashMap<>();

    /**
     * 배치 처리 성공률
     */
    public double getSuccessRate() {
        return totalEvents > 0 ? (double) processedCount / totalEvents : 0.0;
    }

    /**
     * 개별 결과 추가
     */
    public void addResult(UUID eventId, EventProcessingResult result) {
        individualResults.put(eventId, result);
    }

    /**
     * 빈 배치 결과 생성
     */
    public static BatchProcessingResult empty() {
        return BatchProcessingResult.builder()
            .totalEvents(0)
            .processedCount(0)
            .failedCount(0)
            .deferredCount(0)
            .skippedCount(0)
            .build();
    }

    /**
     * 배치 처리가 성공적인지 확인
     */
    public boolean isSuccessful() {
        return failedCount == 0 && totalEvents > 0;
    }

    /**
     * 배치 결과 요약
     */
    public String getSummary() {
        return String.format("Batch[total=%d, success=%d, failed=%d, deferred=%d, skipped=%d, rate=%.1f%%]",
            totalEvents, processedCount, failedCount, deferredCount, skippedCount,
            getSuccessRate() * 100);
    }

}