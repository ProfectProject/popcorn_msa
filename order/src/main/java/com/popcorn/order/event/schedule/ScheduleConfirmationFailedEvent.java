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
 * 스케줄 확정 실패 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 스케줄 확정이 실패했을 때 (예약 → 확정 과정에서 실패)
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 스케줄 확정 실패 처리 및 주문 취소 또는 보상 트랜잭션
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - reason: 실패 사유
 * - retryable: 재시도 가능 여부
 * - failedAt: 실패 시간
 */
@Getter
@ToString(callSuper = true)
public class ScheduleConfirmationFailedEvent extends BaseOrderEvent {

    /** 실패 사유 */
    private final String reason;

    /** 재시도 가능 여부 */
    private final Boolean retryable;

    /** 실패 시간 */
    private final LocalDateTime failedAt;

    private ScheduleConfirmationFailedEvent(UUID orderId, String reason, Boolean retryable,
                                          LocalDateTime failedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.SCHEDULE_CONFIRMATION_FAILED, userId);
        this.reason = reason;
        this.retryable = retryable;
        this.failedAt = failedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        payload.put("retryable", retryable);
        payload.put("failedAt", failedAt != null ? failedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ScheduleConfirmationFailedEvent create(UUID orderId, String reason, Boolean retryable) {
        return create(orderId, reason, retryable, null);
    }

    public static ScheduleConfirmationFailedEvent create(UUID orderId, String reason, Boolean retryable, Long userId) {
        return new ScheduleConfirmationFailedEvent(orderId, reason, retryable, LocalDateTime.now(), userId);
    }

    /**
     * 재시도 가능한 실패 이벤트 생성
     */
    public static ScheduleConfirmationFailedEvent createRetryable(UUID orderId, String reason) {
        return create(orderId, reason, true, null);
    }

    /**
     * 재시도 불가능한 실패 이벤트 생성
     */
    public static ScheduleConfirmationFailedEvent createNonRetryable(UUID orderId, String reason) {
        return create(orderId, reason, false, null);
    }
}
