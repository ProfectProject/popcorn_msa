package com.popcorn.order.event.popup;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.popup.BasePopupEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 팝업 생성 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 새로운 팝업이 생성되었을 때
 * - Redis Stream을 통해 Query 서비스로 전송
 *
 * [이벤트 수신자]
 * - Query 모듈: 팝업 정보 캐시 업데이트
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - popupId: 팝업 ID
 * - storeId: 스토어 ID
 */
@Getter
@ToString(callSuper = true)
public class PopupCreatedEvent extends BasePopupEvent {

    /** 팝업 ID */
    private final UUID popupId;

    /** 스토어 ID */
    private final UUID storeId;

    /** 생성 시간 */
    private final LocalDateTime createdAt;

    private PopupCreatedEvent(UUID popupId, UUID storeId, LocalDateTime createdAt) {
        super(popupId, EventConstants.EventTypes.POPUP_CREATED);
        this.popupId = popupId;
        this.storeId = storeId;
        this.createdAt = createdAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("storeId", storeId != null ? storeId.toString() : null);
        payload.put("createdAt", createdAt != null ? createdAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static PopupCreatedEvent create(UUID popupId, UUID storeId) {
        return new PopupCreatedEvent(popupId, storeId, LocalDateTime.now());
    }

    public static PopupCreatedEvent create(UUID popupId, UUID storeId, LocalDateTime createdAt) {
        return new PopupCreatedEvent(popupId, storeId, createdAt);
    }
}
