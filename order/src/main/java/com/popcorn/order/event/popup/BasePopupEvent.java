package com.popcorn.order.event.popup;

import com.popcorn.common.event.BaseEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 팝업 도메인 공통 이벤트 베이스
 * - aggregateType: "Popup"
 * - eventType: UPPER_SNAKE_CASE 검증
 */
public abstract class BasePopupEvent extends BaseEvent {

    protected BasePopupEvent(UUID popupId, String eventType) {
        super(popupId, "Popup", eventType, null, null);
        validateEventType(eventType);
    }

    public UUID getPopupId() {
        return getAggregateId();
    }

    public boolean isPopupEvent() {
        return "Popup".equals(getAggregateType());
    }

    @Override
    public abstract Map<String, Object> getEventPayload();
}
