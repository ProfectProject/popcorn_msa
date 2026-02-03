package com.popcorn.order.event.order;

import java.util.Map;
import java.util.UUID;

import com.popcorn.order.entity.OrderStatus;

import lombok.Getter;

/**
 * 주문 상태 변경 이벤트 - 주문 상태가 바뀔 때 발생!
 *
 * [초보자를 위한 설명]
 * 주문 상태가 언제 바뀌나요?
 * - "주문 요청됨" → "주문 수락됨"
 * - "주문 수락됨" → "결제 대기"
 * - "결제 대기" → "결제 완료"
 * - "결제 완료" → "주문 완료"
 * 등등 주문이 진행되면서 상태가 계속 바껴요.
 *
 * 이 이벤트를 듣고 있는 서비스들이 할 일:
 * - 알림 서비스: "고객에게 상태 변경 알림을 보내자!"
 * - 로그 서비스: "상태 변경 기록을 남기자!"
 * - 대시보드: "실시간 현황을 업데이트하자!"
 */
@Getter
public class OrderStatusChangedEvent extends BaseOrderEvent {

    // 상태 변경에 대한 정보들
    private final OrderStatus fromStatus;      // 변경 전 상태
    private final OrderStatus toStatus;        // 변경 후 상태
    private final String changeReason;         // 왜 바뀌었는지 이유
    private final String changedBy;            // 누가 바꿨는지 (SYSTEM, ADMIN, CUSTOMER 등)

    /**
     * 주문 상태 변경 이벤트 만들기
     *
     * @param orderId 상태가 바뀐 주문 ID
     * @param userId 주문한 고객 ID
     * @param fromStatus 변경 전 상태
     * @param toStatus 변경 후 상태
     * @param changeReason 변경 이유
     * @param changedBy 변경 주체
     */
    public OrderStatusChangedEvent(UUID orderId, Long userId, OrderStatus fromStatus,
                                 OrderStatus toStatus, String changeReason, String changedBy) {
        // 부모 클래스에 기본 정보 전달
        super(
            orderId,
            "order_status_changed",
            userId,
            createEventMetadata(fromStatus, toStatus, changeReason, changedBy)
        );

        // 상태 변경 정보 저장
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changeReason = changeReason;
        this.changedBy = changedBy;
    }

    /**
     * 상태 변경 메타데이터 생성
     */
    private static Map<String, Object> createEventMetadata(OrderStatus fromStatus, OrderStatus toStatus,
                                                          String changeReason, String changedBy) {
        return Map.of(
            "fromStatus", fromStatus.name(),
            "toStatus", toStatus.name(),
            "changeReason", changeReason != null ? changeReason : "이유 없음",
            "changedBy", changedBy != null ? changedBy : "SYSTEM"
        );
    }

    // ================ 이벤트 전용 메서드들 ================

    @Override
    public Map<String, Object> getEventPayload() {
        return Map.of(
            "orderId", getOrderId(),
            "userId", getUserId(),
            "fromStatus", fromStatus.name(),
            "toStatus", toStatus.name(),
            "changeReason", changeReason,
            "changedBy", changedBy,
            "timestamp", getTimestamp()
        );
    }

    /**
     * 상태 변경 설명 (사람이 읽기 쉬운 형태)
     */
    public String getDetailedDescription() {
        return String.format(
            "주문 상태가 변경되었습니다! [주문ID=%s, %s → %s, 이유=%s, 변경자=%s]",
            getOrderId(), fromStatus.name(), toStatus.name(), changeReason, changedBy
        );
    }

    /**
     * 긍정적인 변화인지 확인 (주문이 진전되고 있는지)
     */
    public boolean isPositiveChange() {
        // 취소나 거절이 아닌 경우는 긍정적인 변화로 봄
        return toStatus != OrderStatus.CANCELLED && toStatus != OrderStatus.REJECTED;
    }

    /**
     * 주문 완료 단계로의 변화인지 확인
     */
    public boolean isCompletionChange() {
        return toStatus == OrderStatus.COMPLETED || toStatus == OrderStatus.PAID;
    }

    /**
     * 취소 관련 변화인지 확인
     */
    public boolean isCancellationChange() {
        return toStatus == OrderStatus.CANCELLED || toStatus == OrderStatus.REJECTED;
    }

    /**
     * 자동 시스템에 의한 변경인지 확인
     */
    public boolean isSystemChange() {
        return "SYSTEM".equals(changedBy);
    }

}