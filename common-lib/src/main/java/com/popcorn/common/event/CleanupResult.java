package com.popcorn.common.event;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 🧹 정리 작업 결과
 */
@Getter
@Builder
@AllArgsConstructor
public class CleanupResult {

    private final int totalCleaned;
    private final LocalDateTime cleanupTime;
    private final boolean success;
    private final String errorMessage;

    @Builder.Default
    private final java.util.Map<String, Integer> cleanedByAggregate = new java.util.HashMap<>();

    /**
     * 정리 결과 추가
     */
    public void addCleanedAggregate(String aggregateKey, int count) {
        cleanedByAggregate.put(aggregateKey, count);
    }

    /**
     * 실패한 정리 결과 생성
     */
    public static CleanupResult failed(String errorMessage) {
        return CleanupResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .cleanupTime(LocalDateTime.now())
            .totalCleaned(0)
            .build();
    }

}