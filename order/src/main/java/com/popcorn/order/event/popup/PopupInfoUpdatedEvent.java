package com.popcorn.order.event.popup;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 팝업 정보 업데이트 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 팝업 정보가 업데이트되었을 때
 * - Redis Stream을 통해 Query 서비스로 전송
 *
 * [이벤트 수신자]
 * - Query 모듈: 팝업 정보 캐시 업데이트
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - popupId: 팝업 ID
 * - updatedAt: 업데이트 시간
 */
@Getter
@ToString(callSuper = true)
public class PopupInfoUpdatedEvent extends BaseEvent {

    /** 팝업 ID */
    private final UUID popupId;

    /** 업데이트 시간 */
    private final LocalDateTime updatedAt;

    private PopupInfoUpdatedEvent(String eventId, UUID popupId, LocalDateTime updatedAt) {
        super(eventId, "popup-info-updated");
        this.popupId = popupId;
        this.updatedAt = updatedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static PopupInfoUpdatedEvent create(UUID popupId) {
        String eventId = UUID.randomUUID().toString();
        return new PopupInfoUpdatedEvent(eventId, popupId, LocalDateTime.now());
    }

    public static PopupInfoUpdatedEvent create(String eventId, UUID popupId, LocalDateTime updatedAt) {
        return new PopupInfoUpdatedEvent(eventId, popupId, updatedAt);
    }
}