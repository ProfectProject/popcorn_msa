package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 스케줄 해제 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 스케줄이 해제되었을 때 (주문 취소, 환불 등)
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 스케줄 해제 완료 처리
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - releasedAt: 해제 시간
 */
@Getter
@ToString(callSuper = true)
public class ScheduleReleasedEvent extends BaseOrderEvent {

    /** 해제 시간 */
    private final LocalDateTime releasedAt;

    private ScheduleReleasedEvent(UUID orderId, LocalDateTime releasedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.SCHEDULE_RELEASED, userId);
        this.releasedAt = releasedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("releasedAt", releasedAt != null ? releasedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ScheduleReleasedEvent create(UUID orderId, LocalDateTime releasedAt) {
        return create(orderId, releasedAt, null);
    }

    public static ScheduleReleasedEvent create(UUID orderId, LocalDateTime releasedAt, Long userId) {
        return new ScheduleReleasedEvent(orderId, releasedAt, userId);
    }

    /**
     * 현재 시간으로 해제 이벤트 생성
     */
    public static ScheduleReleasedEvent createNow(UUID orderId) {
        return create(orderId, LocalDateTime.now(), null);
    }
}
