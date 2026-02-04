package com.popcorn.checkIns.event;

import com.popcorn.common.event.BaseEvent;

import java.util.Map;
import java.util.UUID;

/**
 * CheckIn 도메인 전용 이벤트 기본 클래스
 *
 * common-lib의 BaseEvent를 상속하여 CheckIn 도메인에 특화된 기능을 제공합니다.
 *
 * 주요 기능:
 * - aggregateType을 "CheckIn"으로 고정
 * - CheckIn 도메인별 유틸리티 메서드
 * - QR 코드와 체크인 관련 공통 기능
 */
public abstract class BaseCheckinEvent extends BaseEvent {

    protected BaseCheckinEvent(UUID aggregateId, String eventType, Long userId, Map<String, Object> metadata) {
        super(aggregateId, "CheckIn", eventType, userId, metadata);
    }

    protected BaseCheckinEvent(UUID aggregateId, String eventType, Long userId) {
        super(aggregateId, "CheckIn", eventType, userId);
    }

    /**
     * CheckIn ID 접근자 (aggregateId의 별명)
     * @return CheckIn 관련 ID (qrId, checkinId 등)
     */
    public UUID getCheckinId() {
        return getAggregateId();
    }

    /**
     * CheckIn 이벤트인지 확인
     * @return 항상 true (CheckIn 도메인 전용)
     */
    public boolean isCheckinEvent() {
        return "CheckIn".equals(getAggregateType());
    }

    /**
     * CheckIn 이벤트용 로그 메시지 생성
     */
    public String getCheckinEventDescription() {
        return String.format("📱 [CHECKIN] %s - checkinId: %s, userId: %s",
                getEventType(), getCheckinId(), getUserId());
    }

    /**
     * CheckIn 관련 메타데이터 추가를 위한 헬퍼 메서드
     */
    protected Map<String, Object> createCheckinMetadata(Map<String, Object> additionalData) {
        Map<String, Object> baseMetadata = Map.of(
                "domain", "checkin",
                "aggregateType", "CheckIn"
        );

        if (additionalData != null) {
            return Map.of(
                    "domain", "checkin",
                    "aggregateType", "CheckIn",
                    "additionalData", additionalData
            );
        }

        return baseMetadata;
    }
}