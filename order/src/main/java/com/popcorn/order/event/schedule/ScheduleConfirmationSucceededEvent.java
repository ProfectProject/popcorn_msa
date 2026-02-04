package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 스케줄 확정 성공 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 스케줄 확정이 성공했을 때 (예약 → 확정)
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 스케줄 확정 성공 처리 및 주문 완료
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - scheduleDetails: 확정된 스케줄 상세 정보
 * - confirmedAt: 확정 시간
 */
@Getter
@ToString(callSuper = true)
public class ScheduleConfirmationSucceededEvent extends BaseOrderEvent {

    /** 확정된 스케줄 상세 정보 */
    private final List<ScheduleDetail> scheduleDetails;

    /** 확정 시간 */
    private final LocalDateTime confirmedAt;

    private ScheduleConfirmationSucceededEvent(UUID orderId, List<ScheduleDetail> scheduleDetails,
                                             LocalDateTime confirmedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.SCHEDULE_CONFIRMATION_SUCCEEDED, userId);
        this.scheduleDetails = scheduleDetails;
        this.confirmedAt = confirmedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scheduleDetailCount", scheduleDetails != null ? scheduleDetails.size() : 0);
        payload.put("confirmedAt", confirmedAt != null ? confirmedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ScheduleConfirmationSucceededEvent create(UUID orderId, List<ScheduleDetail> scheduleDetails,
                                                           LocalDateTime confirmedAt) {
        return create(orderId, scheduleDetails, confirmedAt, null);
    }

    public static ScheduleConfirmationSucceededEvent create(UUID orderId, List<ScheduleDetail> scheduleDetails,
                                                           LocalDateTime confirmedAt, Long userId) {
        return new ScheduleConfirmationSucceededEvent(orderId, scheduleDetails, confirmedAt, userId);
    }

    /**
     * 스케줄 상세 정보
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class ScheduleDetail {
        /** 세션 옵션 ID */
        private UUID sessionOptionId;

        /** 확정된 수량 */
        private Integer confirmedQuantity;

        /** 세션명 */
        private String sessionName;

        /** 세션 시간 */
        private LocalDateTime sessionTime;

        /** 확정 토큰 */
        private String confirmationToken;

        /** 확정 시간 */
        private LocalDateTime confirmedAt;

        public static ScheduleDetail create(UUID sessionOptionId, Integer confirmedQuantity,
                                          String sessionName, LocalDateTime sessionTime,
                                          String confirmationToken) {
            return ScheduleDetail.builder()
                    .sessionOptionId(sessionOptionId)
                    .confirmedQuantity(confirmedQuantity)
                    .sessionName(sessionName)
                    .sessionTime(sessionTime)
                    .confirmationToken(confirmationToken)
                    .confirmedAt(LocalDateTime.now())
                    .build();
        }
    }
}
