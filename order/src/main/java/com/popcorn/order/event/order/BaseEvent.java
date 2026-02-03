package com.popcorn.order.event.order;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

/**
 * 기본 이벤트 클래스
 *
 * 모든 이벤트의 공통 속성을 정의합니다.
 * - 이벤트 ID
 * - 이벤트 타입
 * - 이벤트 발생 시간
 * - 이벤트 페이로드 (하위 클래스에서 구현)
 */
@Getter
@ToString
public abstract class BaseEvent {

    /** 이벤트 고유 ID */
    private final String eventId;

    /** 이벤트 타입 (kebab-case) */
    private final String eventType;

    /** 이벤트 발생 시간 */
    private final LocalDateTime eventTime;

    protected BaseEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventTime = LocalDateTime.now();
    }

    /**
     * 이벤트별 고유한 페이로드 데이터를 반환합니다.
     * 하위 클래스에서 구현해야 합니다.
     *
     * @return 이벤트 페이로드 맵
     */
    public abstract Map<String, Object> getEventPayload();

    /**
     * 전체 이벤트 데이터를 포함한 맵을 반환합니다.
     * 공통 필드 + 페이로드를 포함합니다.
     *
     * @return 전체 이벤트 데이터
     */
    public final Map<String, Object> toEventData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("eventId", eventId);
        data.put("eventType", eventType);
        data.put("eventTime", eventTime.toString());

        // 페이로드 데이터 추가
        Map<String, Object> payload = getEventPayload();
        if (payload != null) {
            data.putAll(payload);
        }

        return data;
    }

    /**
     * 이벤트 타입이 유효한지 검증합니다.
     * kebab-case 형식인지 확인합니다.
     */
    protected final void validateEventType(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("이벤트 타입은 필수입니다.");
        }

        if (!eventType.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            throw new IllegalArgumentException("이벤트 타입은 kebab-case 형식이어야 합니다: " + eventType);
        }
    }
}