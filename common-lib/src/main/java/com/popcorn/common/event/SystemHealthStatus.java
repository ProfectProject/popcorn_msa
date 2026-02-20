package com.popcorn.common.event;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 🩺 시스템 건강성 상태
 */
@Getter
@Builder
@AllArgsConstructor
public class SystemHealthStatus {

    private final HealthLevel healthLevel;
    private final long totalPendingEvents;
    private final LocalDateTime lastCheckTime;
    private final boolean isHealthy;
    private final boolean needsAttention;
    private final String errorMessage;

    @Builder.Default
    private final java.util.Map<String, Long> aggregateEventCounts = new java.util.HashMap<>();

    /**
     * 실패한 건강성 체크 결과
     */
    public static SystemHealthStatus failed(String errorMessage) {
        return SystemHealthStatus.builder()
            .healthLevel(HealthLevel.UNKNOWN)
            .errorMessage(errorMessage)
            .lastCheckTime(LocalDateTime.now())
            .isHealthy(false)
            .needsAttention(true)
            .totalPendingEvents(-1)
            .build();
    }

    public enum HealthLevel {
        HEALTHY,    // 정상
        WARNING,    // 주의
        CRITICAL,   // 심각
        UNKNOWN     // 알 수 없음
    }
}