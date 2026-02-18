package com.popcorn.common.event;

import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 📊 이벤트 처리 결과
 * - 개별 이벤트 처리 결과 정보
 * - 성공/실패/연기/스킵 상태 관리
 * - 처리 시간 및 성능 메트릭
 */
@Getter
@Builder
@AllArgsConstructor
public class EventProcessingResult {

    /**
     * 처리된 이벤트 ID
     */
    private final UUID eventId;

    /**
     * 처리 상태
     */
    @Builder.Default
    private final ProcessingStatus status = ProcessingStatus.SUCCESS;

    /**
     * 에러 메시지 (실패 시)
     */
    private final String errorMessage;

    /**
     * 처리 시작 시각
     */
    @Builder.Default
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 처리 완료 시각
     */
    private final LocalDateTime endTime;

    /**
     * 처리 소요 시간
     */
    private final Duration processingDuration;

    /**
     * 재시도 횟수
     */
    @Builder.Default
    private final int retryCount = 0;

    /**
     * 처리 결과 메타데이터
     */
    private final java.util.Map<String, Object> metadata;

    // === 🔧 Business Logic Methods ===

    /**
     * 처리 성공 여부 확인
     */
    public boolean isSuccess() {
        return status == ProcessingStatus.SUCCESS;
    }

    /**
     * 처리 실패 여부 확인
     */
    public boolean isFailed() {
        return status == ProcessingStatus.FAILED;
    }

    /**
     * 처리 연기 여부 확인
     */
    public boolean isDeferred() {
        return status == ProcessingStatus.DEFERRED;
    }

    /**
     * 처리 스킵 여부 확인
     */
    public boolean isSkipped() {
        return status == ProcessingStatus.SKIPPED;
    }

    /**
     * 처리 시간 계산 (null-safe)
     */
    public Duration getActualDuration() {
        if (processingDuration != null) {
            return processingDuration;
        }
        if (endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return Duration.between(startTime, LocalDateTime.now());
    }

    /**
     * 성능이 느린 처리인지 확인
     */
    public boolean isSlowProcessing(Duration threshold) {
        return getActualDuration().compareTo(threshold) > 0;
    }

    /**
     * 결과 요약 정보 (로깅용)
     */
    public String getSummary() {
        return String.format("EventResult[eventId=%s, status=%s, duration=%dms, retries=%d%s]",
            eventId, status, getActualDuration().toMillis(), retryCount,
            errorMessage != null ? ", error=" + errorMessage : "");
    }

    // === 🏭 Factory Methods ===

    /**
     * ✅ 성공 결과 생성
     */
    public static EventProcessingResult success(UUID eventId) {
        return EventProcessingResult.builder()
            .eventId(eventId)
            .status(ProcessingStatus.SUCCESS)
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * ✅ 성공 결과 생성 (메타데이터 포함)
     */
    public static EventProcessingResult success(UUID eventId, java.util.Map<String, Object> metadata) {
        return EventProcessingResult.builder()
            .eventId(eventId)
            .status(ProcessingStatus.SUCCESS)
            .endTime(LocalDateTime.now())
            .metadata(metadata)
            .build();
    }

    /**
     * ❌ 실패 결과 생성
     */
    public static EventProcessingResult failed(String errorMessage) {
        return EventProcessingResult.builder()
            .status(ProcessingStatus.FAILED)
            .errorMessage(errorMessage)
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * ❌ 실패 결과 생성 (이벤트 ID 포함)
     */
    public static EventProcessingResult failed(UUID eventId, String errorMessage) {
        return EventProcessingResult.builder()
            .eventId(eventId)
            .status(ProcessingStatus.FAILED)
            .errorMessage(errorMessage)
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * ❌ 실패 결과 생성 (재시도 횟수 포함)
     */
    public static EventProcessingResult failed(UUID eventId, String errorMessage, int retryCount) {
        return EventProcessingResult.builder()
            .eventId(eventId)
            .status(ProcessingStatus.FAILED)
            .errorMessage(errorMessage)
            .retryCount(retryCount)
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * 🔄 연기 결과 생성
     */
    public static EventProcessingResult deferred(String reason) {
        return EventProcessingResult.builder()
            .status(ProcessingStatus.DEFERRED)
            .errorMessage(reason)
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * 🔄 연기 결과 생성 (이벤트 ID 포함)
     */
    public static EventProcessingResult deferred(UUID eventId, String reason) {
        return EventProcessingResult.builder()
            .eventId(eventId)
            .status(ProcessingStatus.DEFERRED)
            .errorMessage(reason)
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * ⏭️ 스킵 결과 생성
     */
    public static EventProcessingResult skipped(String reason) {
        return EventProcessingResult.builder()
            .status(ProcessingStatus.SKIPPED)
            .errorMessage(reason)
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * ⏭️ 스킵 결과 생성 (이벤트 ID 포함)
     */
    public static EventProcessingResult skipped(UUID eventId, String reason) {
        return EventProcessingResult.builder()
            .eventId(eventId)
            .status(ProcessingStatus.SKIPPED)
            .errorMessage(reason)
            .endTime(LocalDateTime.now())
            .build();
    }

    // === 📊 Enums ===

    public enum ProcessingStatus {
        SUCCESS,    // 처리 성공
        FAILED,     // 처리 실패
        DEFERRED,   // 처리 연기 (순서 대기)
        SKIPPED     // 처리 스킵 (중복, 무시 등)
    }
}