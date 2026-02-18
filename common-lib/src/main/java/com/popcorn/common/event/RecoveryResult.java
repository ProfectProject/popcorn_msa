package com.popcorn.common.event;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 🚨 복구 작업 결과
 */
@Getter
@Builder
@AllArgsConstructor
public class RecoveryResult {

    private final String aggregateType;
    private final String aggregateId;
    private final int recoveredEventCount;
    private final long beforeCurrentSequence;
    private final long beforeLastProcessed;
    private final long afterLastProcessed;
    private final LocalDateTime recoveryTime;
    private final boolean success;
    private final String errorMessage;

    /**
     * 실패한 복구 결과 생성
     */
    public static RecoveryResult failed(String aggregateType, String aggregateId, String errorMessage) {
        return RecoveryResult.builder()
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .success(false)
            .errorMessage(errorMessage)
            .recoveryTime(LocalDateTime.now())
            .recoveredEventCount(0)
            .build();
    }

    /**
     * 복구 효과 계산
     */
    public long getRecoveryGap() {
        return afterLastProcessed - beforeLastProcessed;
    }

    /**
     * 복구 결과 요약
     */
    public String getSummary() {
        return String.format("Recovery[%s:%s, recovered=%d, gap=%d→%d, success=%s]",
            aggregateType, aggregateId, recoveredEventCount,
            beforeCurrentSequence - beforeLastProcessed,
            beforeCurrentSequence - afterLastProcessed,
            success);
    }
}