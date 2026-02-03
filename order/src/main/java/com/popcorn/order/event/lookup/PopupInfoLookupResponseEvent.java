package com.popcorn.order.event.lookup;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.common.event.BaseEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 팝업 정보 조회 응답 이벤트
 */
@Getter
@ToString(callSuper = true)
public class PopupInfoLookupResponseEvent extends BaseEvent {

    private final String requestCorrelationId;
    private final boolean success;
    private final String message;
    private final String popupName;
    private final String storeId;
    private final LocalDateTime respondedAt;

    /**
     * 생성자
     */
    public PopupInfoLookupResponseEvent(UUID popupId, String requestCorrelationId, boolean success, String message,
                                       String popupName, String storeId, LocalDateTime respondedAt) {
        super(popupId, "Popup", "popup-info-lookup-response", null);
        this.requestCorrelationId = requestCorrelationId;
        this.success = success;
        this.message = message;
        this.popupName = popupName;
        this.storeId = storeId;
        this.respondedAt = respondedAt;
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
        payload.put("success", success);
        payload.put("message", message);
        payload.put("popupName", popupName);
        payload.put("storeId", storeId);
        payload.put("respondedAt", respondedAt != null ? respondedAt.toString() : null);
        return payload;
    }

    /**
     * 성공 응답 팩토리 메서드
     */
    public static PopupInfoLookupResponseEvent success(UUID popupId, String correlationId,
                                                      String popupName, String storeId) {
        return new PopupInfoLookupResponseEvent(popupId, correlationId, true, "성공",
                popupName, storeId, LocalDateTime.now());
    }

    /**
     * 실패 응답 팩토리 메서드
     */
    public static PopupInfoLookupResponseEvent failure(UUID popupId, String correlationId, String errorMessage) {
        return new PopupInfoLookupResponseEvent(popupId, correlationId, false, errorMessage,
                null, null, LocalDateTime.now());
    }
}