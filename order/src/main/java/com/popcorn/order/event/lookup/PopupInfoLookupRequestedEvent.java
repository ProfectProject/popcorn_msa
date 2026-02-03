package com.popcorn.order.event.lookup;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.common.event.BaseEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 팝업 정보 조회 요청 이벤트
 */
@Getter
@ToString(callSuper = true)
public class PopupInfoLookupRequestedEvent extends BaseEvent {

    private final String requestCorrelationId;
    private final LocalDateTime requestedAt;

    /**
     * 생성자
     */
    public PopupInfoLookupRequestedEvent(UUID popupId, String requestCorrelationId, LocalDateTime requestedAt) {
        super(popupId, "Popup", "popup-info-lookup-requested", null);
        this.requestCorrelationId = requestCorrelationId;
        this.requestedAt = requestedAt;
    }

    /**
     * 팝업 ID 조회
     */
    public UUID getPopupId() {
        return getAggregateId();
    }

    /**
     * 요청 상관관계 ID 조회 (BaseEvent의 correlationId와 구분)
     */
    public String getRequestCorrelationId() {
        return requestCorrelationId;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("popupId", getPopupId() != null ? getPopupId().toString() : null);
        payload.put("requestCorrelationId", requestCorrelationId);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static PopupInfoLookupRequestedEvent create(UUID popupId, String correlationId) {
        return new PopupInfoLookupRequestedEvent(popupId, correlationId, LocalDateTime.now());
    }
}