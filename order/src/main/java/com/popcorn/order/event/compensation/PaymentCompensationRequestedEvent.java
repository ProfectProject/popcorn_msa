package com.popcorn.order.event.compensation;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment 서비스로부터 받은 보상 요청 이벤트
 *
 * Payment 서비스에서 검증 실패나 결제 실패가 발생했을 때
 * Order 서비스에 보상 처리를 요청하는 이벤트입니다.
 */
@Slf4j
@Getter
@Builder
public class PaymentCompensationRequestedEvent {

    private final UUID compensationId;
    private final UUID paymentId;
    private final UUID orderId;
    private final String orderNo;
    private final Long customerId;
    private final String compensationReason;
    private final String compensationType; // "VALIDATION_FAILURE", "PAYMENT_FAILURE"
    private final LocalDateTime failedAt;
    private final List<CompensationAction> requestedActions;
    private final String priority;
    private final String correlationId;
    private final Long userId;

    /**
     * 보상 액션 정의
     */
    @Getter
    @Builder
    public static class CompensationAction {
        private final String actionType; // "CANCEL_RESERVATION", "UPDATE_ORDER_STATUS", "RELEASE_STOCK"
        private final String targetResource; // 대상 리소스 ID
        private final Map<String, Object> parameters;
    }

    /**
     * 검증 실패로 인한 보상 요청 이벤트인지 확인
     */
    public boolean isValidationFailureCompensation() {
        return "VALIDATION_FAILURE".equals(compensationType);
    }

    /**
     * 결제 실패로 인한 보상 요청 이벤트인지 확인
     */
    public boolean isPaymentFailureCompensation() {
        return "PAYMENT_FAILURE".equals(compensationType);
    }

    /**
     * 예약 취소 액션이 포함되어 있는지 확인
     */
    public boolean hasReservationCancellationAction() {
        return requestedActions.stream()
                .anyMatch(action -> "CANCEL_RESERVATION".equals(action.getActionType()));
    }

    /**
     * 주문 상태 업데이트 액션이 포함되어 있는지 확인
     */
    public boolean hasOrderStatusUpdateAction() {
        return requestedActions.stream()
                .anyMatch(action -> "UPDATE_ORDER_STATUS".equals(action.getActionType()));
    }

    /**
     * 재고 해제 액션이 포함되어 있는지 확인
     */
    public boolean hasStockReleaseAction() {
        return requestedActions.stream()
                .anyMatch(action -> "RELEASE_STOCK".equals(action.getActionType()));
    }

    /**
     * 높은 우선순위인지 확인
     */
    public boolean isHighPriority() {
        return "HIGH".equals(priority);
    }

    /**
     * 로그용 설명 생성
     */
    public String getCompensationDescription() {
        String actionTypes = requestedActions.stream()
                .map(CompensationAction::getActionType)
                .reduce((a, b) -> a + ", " + b)
                .orElse("NONE");
        return String.format("📨 [ORDER-COMPENSATION] Compensation requested from Payment service - orderId: %s, type: %s, actions: [%s]",
                orderId, compensationType, actionTypes);
    }

    /**
     * 검증 실패로 인한 보상 요청 이벤트 생성
     */
    public static PaymentCompensationRequestedEvent forValidationFailure(
            UUID orderId,
            String orderNo,
            Long customerId,
            String validationFailureReason,
            UUID paymentId,
            Long userId) {

        return PaymentCompensationRequestedEvent.builder()
                .compensationId(UUID.randomUUID())
                .paymentId(paymentId)
                .orderId(orderId)
                .orderNo(orderNo)
                .customerId(customerId)
                .compensationReason("Payment validation failed: " + validationFailureReason)
                .compensationType("VALIDATION_FAILURE")
                .failedAt(LocalDateTime.now())
                .requestedActions(List.of(
                        CompensationAction.builder()
                                .actionType("CANCEL_RESERVATION")
                                .targetResource(orderId.toString())
                                .parameters(Map.of("reason", "validation_failure"))
                                .build(),
                        CompensationAction.builder()
                                .actionType("UPDATE_ORDER_STATUS")
                                .targetResource(orderId.toString())
                                .parameters(Map.of(
                                        "newStatus", "CANCELLED",
                                        "reason", validationFailureReason
                                ))
                                .build()
                ))
                .priority("HIGH")
                .correlationId(UUID.randomUUID().toString())
                .userId(userId)
                .build();
    }

    /**
     * 결제 실패로 인한 보상 요청 이벤트 생성
     */
    public static PaymentCompensationRequestedEvent forPaymentFailure(
            UUID paymentId,
            UUID orderId,
            String orderNo,
            Long customerId,
            String paymentFailureReason,
            Long userId) {

        return PaymentCompensationRequestedEvent.builder()
                .compensationId(UUID.randomUUID())
                .paymentId(paymentId)
                .orderId(orderId)
                .orderNo(orderNo)
                .customerId(customerId)
                .compensationReason("Payment processing failed: " + paymentFailureReason)
                .compensationType("PAYMENT_FAILURE")
                .failedAt(LocalDateTime.now())
                .requestedActions(List.of(
                        CompensationAction.builder()
                                .actionType("CANCEL_RESERVATION")
                                .targetResource(orderId.toString())
                                .parameters(Map.of("reason", "payment_failure"))
                                .build(),
                        CompensationAction.builder()
                                .actionType("RELEASE_STOCK")
                                .targetResource(orderId.toString())
                                .parameters(Map.of("reason", "payment_failure"))
                                .build(),
                        CompensationAction.builder()
                                .actionType("UPDATE_ORDER_STATUS")
                                .targetResource(orderId.toString())
                                .parameters(Map.of(
                                        "newStatus", "PAYMENT_FAILED",
                                        "reason", paymentFailureReason
                                ))
                                .build()
                ))
                .priority("HIGH")
                .correlationId(UUID.randomUUID().toString())
                .userId(userId)
                .build();
    }
}