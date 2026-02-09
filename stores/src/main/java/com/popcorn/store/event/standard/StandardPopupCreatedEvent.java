package com.popcorn.store.event.standard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 표준 팝업 생성 이벤트
 * 새로운 팝업이 생성되었을 때 발행되는 이벤트
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StandardPopupCreatedEvent extends StandardBaseEvent {

    /**
     * 팝업 제목
     */
    private String title;

    /**
     * 팝업 상태
     */
    private String status;

    /**
     * 예약 오픈 시간
     */
    private LocalDateTime reservationOpenAt;

    /**
     * 주소 (도로명)
     */
    private String addressRoad;

    /**
     * 상세 주소
     */
    private String addressDetail;

    /**
     * 팝업 생성 시간
     */
    private LocalDateTime createdAt;

    /**
     * 정적 팩토리 메서드 - Popup 도메인 객체로부터 생성
     */
    public static StandardPopupCreatedEvent create(Object popup, UUID storeId) {
        StandardPopupCreatedEvent event = StandardPopupCreatedEvent.builder()
                .eventType(StandardEventType.POPUP_CREATED)
                .producer("store-service")
                .storeId(storeId)
                .status("DRAFT")
                .createdAt(LocalDateTime.now())
                .build();

        // TODO: Popup 엔티티에서 실제 값 매핑
        event.setDefaults();
        return event;
    }

    /**
     * Redis Stream 발행용 Map 변환 (Popup 전용 필드 추가)
     */
    @Override
    public java.util.Map<String, String> toStreamMap() {
        java.util.Map<String, String> map = super.toStreamMap();

        // Popup 전용 필드 추가
        if (title != null) map.put("title", title);
        if (status != null) map.put("status", status);
        if (reservationOpenAt != null) map.put("reservationOpenAt", reservationOpenAt.toString());
        if (addressRoad != null) map.put("addressRoad", addressRoad);
        if (addressDetail != null) map.put("addressDetail", addressDetail);
        if (createdAt != null) map.put("createdAt", createdAt.toString());

        return map;
    }

    @Override
    public java.util.Map<String, Object> toOutboxMap() {
        java.util.Map<String, Object> map = super.toOutboxMap();
        if (title != null) map.put("title", title);
        if (status != null) map.put("status", status);
        if (reservationOpenAt != null) map.put("reservationOpenAt", reservationOpenAt.toString());
        if (addressRoad != null) map.put("addressRoad", addressRoad);
        if (addressDetail != null) map.put("addressDetail", addressDetail);
        if (createdAt != null) map.put("createdAt", createdAt.toString());
        return map;
    }
}
