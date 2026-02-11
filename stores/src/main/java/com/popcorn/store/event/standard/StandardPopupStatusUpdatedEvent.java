package com.popcorn.store.event.standard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 표준 팝업 상태 변경 이벤트
 * 팝업 상태가 변경되었을 때 발행되는 이벤트
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StandardPopupStatusUpdatedEvent extends StandardBaseEvent {

    /**
     * 변경 전 상태
     */
    private String fromStatus;

    /**
     * 변경 후 상태
     */
    private String toStatus;

    /**
     * 마지막 업데이트 시각
     */
    private LocalDateTime updatedAt;

    /**
     * 정적 팩토리 메서드
     */
    public static StandardPopupStatusUpdatedEvent create(UUID popupId, UUID storeId, String fromStatus, String toStatus) {
        StandardPopupStatusUpdatedEvent event = StandardPopupStatusUpdatedEvent.builder()
                .eventType(StandardEventType.POPUP_STATUS_UPDATED)
                .producer("store-service")
                .popupId(popupId)
                .storeId(storeId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .updatedAt(LocalDateTime.now())
                .build();

        event.setDefaults();
        return event;
    }

    /**
     * Redis Stream 발행용 Map 변환 (Popup 상태 변경 전용 필드 추가)
     */
    @Override
    public java.util.Map<String, String> toStreamMap() {
        java.util.Map<String, String> map = super.toStreamMap();

        // Popup 상태 변경 전용 필드 추가
        if (fromStatus != null) map.put("fromStatus", fromStatus);
        if (toStatus != null) map.put("toStatus", toStatus);
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());

        return map;
    }

    @Override
    public java.util.Map<String, Object> toOutboxMap() {
        java.util.Map<String, Object> map = super.toOutboxMap();
        if (fromStatus != null) map.put("fromStatus", fromStatus);
        if (toStatus != null) map.put("toStatus", toStatus);
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
