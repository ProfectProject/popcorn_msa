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
 * 팝업 상태 변경 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 팝업 상태가 변경되었을 때
 * - Redis Stream을 통해 Query 서비스로 전송
 *
 * [이벤트 수신자]
 * - Query 모듈: 팝업 상태 캐시 업데이트
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - popupId: 팝업 ID
 * - fromStatus: 이전 상태
 * - toStatus: 변경된 상태
 * - updatedAt: 변경 시간
 */
@Getter
@ToString(callSuper = true)
public class PopupStatusUpdatedEvent extends BasePopupEvent {

    /** 팝업 ID */
    private final UUID popupId;

    /** 이전 상태 */
    private final String fromStatus;

    /** 변경된 상태 */
    private final String toStatus;

    /** 변경 시간 */
    private final LocalDateTime updatedAt;

    private PopupStatusUpdatedEvent(UUID popupId, String fromStatus,
                                  String toStatus, LocalDateTime updatedAt) {
        super(popupId, EventConstants.EventTypes.POPUP_STATUS_UPDATED);
        this.popupId = popupId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.updatedAt = updatedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("fromStatus", fromStatus);
        payload.put("toStatus", toStatus);
        payload.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static PopupStatusUpdatedEvent create(UUID popupId, String fromStatus, String toStatus) {
        return new PopupStatusUpdatedEvent(popupId, fromStatus, toStatus, LocalDateTime.now());
    }

    public static PopupStatusUpdatedEvent create(UUID popupId, String fromStatus,
                                               String toStatus, LocalDateTime updatedAt) {
        return new PopupStatusUpdatedEvent(popupId, fromStatus, toStatus, updatedAt);
    }
}
