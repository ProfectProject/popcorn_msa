package com.popcorn.store.event.standard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 표준 팝업 정보 변경 이벤트
 * 팝업의 기본 정보(제목/위치/예약 오픈 시간 등)가 수정되었을 때 발행됩니다.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StandardPopupInfoUpdatedEvent extends StandardBaseEvent {

    private String title;
    private LocalDateTime reservationOpenAt;
    private String addressRoad;
    private String addressDetail;
    private LocalDateTime updatedAt;

    @Override
    public java.util.Map<String, String> toStreamMap() {
        java.util.Map<String, String> map = super.toStreamMap();
        if (title != null) map.put("title", title);
        if (reservationOpenAt != null) map.put("reservationOpenAt", reservationOpenAt.toString());
        if (addressRoad != null) map.put("addressRoad", addressRoad);
        if (addressDetail != null) map.put("addressDetail", addressDetail);
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());
        return map;
    }

    @Override
    public java.util.Map<String, Object> toOutboxMap() {
        java.util.Map<String, Object> map = super.toOutboxMap();
        if (title != null) map.put("title", title);
        if (reservationOpenAt != null) map.put("reservationOpenAt", reservationOpenAt.toString());
        if (addressRoad != null) map.put("addressRoad", addressRoad);
        if (addressDetail != null) map.put("addressDetail", addressDetail);
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
