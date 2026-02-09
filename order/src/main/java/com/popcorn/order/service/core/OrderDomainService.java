package com.popcorn.order.service.core;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderItem;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.entity.ItemType;

import lombok.extern.slf4j.Slf4j;

/**
 * 주문 도메인 서비스 - 순수한 비즈니스 로직만 담당
 *
 * [초보자를 위한 설명]
 * 도메인 서비스란? 비즈니스 규칙과 로직만 담당하는 서비스예요.
 * 데이터베이스나 외부 API 같은 기술적인 것들은 신경 쓰지 않아요.
 * 오직 "주문이 어떻게 동작해야 하는가?"라는 비즈니스 규칙만 관리해요.
 *
 * 예를 들어:
 * - "주문은 언제 취소할 수 있는가?"
 * - "어떤 상태에서 어떤 상태로 바꿀 수 있는가?"
 * - "총 금액은 어떻게 계산하는가?"
 *
 * 이런 것들이 도메인 서비스의 역할이에요.
 *
 * 왜 따로 만들까?
 * - 비즈니스 규칙이 한 곳에 모여 있어서 찾기 쉬워요
 * - 규칙이 바뀔 때 여기만 수정하면 돼요
 * - 테스트하기 쉬워요 (데이터베이스 없이도 테스트 가능)
 */
@Service
@Slf4j
public class OrderDomainService {

    // 상수들 - 비즈니스 규칙을 한 곳에 모아두기
    private static final int RESERVATION_CANCEL_MINUTES = 5;  // 예약형: 5분 후까지 취소 가능
    private static final int PURCHASE_CANCEL_MINUTES = 5;     // 구매형: 5분 후까지 취소 가능

    /**
     * 주문 상태 전환 규칙 정의
     *
     * [초보자를 위한 설명]
     * 주문 상태는 아무렇게나 바뀔 수 없어요. 정해진 순서가 있어야 해요.
     * 예를 들어 "주문 요청됨"에서 바로 "주문 완료"로 갈 수는 없죠.
     * 이 맵(Map)은 각 상태에서 어떤 상태로 갈 수 있는지를 정의해요.
     */
    private static final Map<OrderStatus, EnumSet<OrderStatus>> STATUS_TRANSITIONS = buildStatusTransitions();

    /**
     * 상태 전환 규칙을 만드는 메서드
     */
    private static Map<OrderStatus, EnumSet<OrderStatus>> buildStatusTransitions() {
        Map<OrderStatus, EnumSet<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);

        // 주문 요청됨 → 다음으로 갈 수 있는 상태들
        transitions.put(OrderStatus.REQUESTED, EnumSet.of(
                OrderStatus.ACCEPTED,        // 수락됨
                OrderStatus.PAYMENT_PENDING, // 결제 대기
                OrderStatus.PAID,            // 결제 완료 (결제 이벤트 직접 수신 시)
                OrderStatus.RESERVED,        // 예약 확정
                OrderStatus.REJECTED,        // 거절됨
                OrderStatus.CANCELLED        // 취소됨
        ));

        // 수락됨 → 다음으로 갈 수 있는 상태들
        transitions.put(OrderStatus.ACCEPTED, EnumSet.of(
                OrderStatus.RESERVED,        // 예약 확정
                OrderStatus.CANCELLED        // 취소
        ));

        // 예약 확정됨 → 다음으로 갈 수 있는 상태들
        transitions.put(OrderStatus.RESERVED, EnumSet.of(
                OrderStatus.PAYMENT_PENDING, // 결제 대기
                OrderStatus.PAID,            // 결제 완료
                OrderStatus.COMPLETED,       // 완료 (재고 차감 성공 시)
                OrderStatus.CANCELLED        // 취소
        ));

        // 결제 대기 → 다음으로 갈 수 있는 상태들
        transitions.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(
                OrderStatus.PAID,            // 결제 완료
                OrderStatus.COMPLETED,       // 완료 (재고 차감 성공 시)
                OrderStatus.CANCELLED        // 취소
        ));

        // 결제 완료 → 다음으로 갈 수 있는 상태들
        transitions.put(OrderStatus.PAID, EnumSet.of(
                OrderStatus.COMPLETED,       // 완료
                OrderStatus.CANCELLED        // 취소 (5분 이내만 허용)
        ));

        // 더 이상 변경 불가능한 상태들
        transitions.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));

        return transitions;
    }

    // ================ 주문 생성 관련 메서드들 ================

    /**
     * 주문 생성 전 검증하기
     *
     * [초보자를 위한 설명]
     * 주문을 만들기 전에 모든 조건이 맞는지 확인해요.
     * 뭔가 잘못되면 예외를 던져서 주문 생성을 막아요.
     */
    public void validateOrderCreation(Long customerId, UUID popupId, List<OrderItem> orderItems) {
        log.debug("주문 생성 검증 시작 - 고객: {}, 팝업: {}", customerId, popupId);

        // 1. 고객 정보 확인
        validateCustomerInfo(customerId);

        // 2. 팝업 정보 확인
        validatePopupInfo(popupId);

        // 3. 주문 항목들 확인
        validateOrderItems(orderItems);

        log.debug("주문 생성 검증 완료 - 모든 조건 만족!");
    }

    /**
     * 고객 정보 검증
     */
    private void validateCustomerInfo(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("올바른 고객 정보가 필요해요!");
        }
    }

    /**
     * 스토어 정보 검증
     */
    /**
     * 팝업 정보 검증
     */
    private void validatePopupInfo(UUID popupId) {
        if (popupId == null) {
            throw new IllegalArgumentException("팝업 정보가 필요해요!");
        }
    }

    /**
     * 주문 항목들 검증
     */
    private void validateOrderItems(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품을 최소 1개는 선택해주세요!");
        }

        // 각 항목별로 자세히 확인
        for (OrderItem item : orderItems) {
            validateSingleOrderItem(item);
        }

        // 🚨 핵심 비즈니스 룰 검증 추가
        validateOrderItemsBusinessRules(orderItems);
    }

    /**
     * 개별 주문 항목 검증
     */
    private void validateSingleOrderItem(OrderItem item) {
        // 수량 확인
        if (item.getQty() == null || item.getQty() <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 해요!");
        }

        // 단가 확인
        if (item.getUnitPrice() == null || item.getUnitPrice() <= 0) {
            throw new IllegalArgumentException("상품 가격이 올바르지 않아요!");
        }
    }

    /**
     * 주문 항목들 간의 비즈니스 룰 검증
     *
     * [수정된 비즈니스 룰]
     * 1. 스케줄만 → 허용 (스케줄 예약)
     * 2. 굿즈만 → 허용 (굿즈 예약)
     * 3. 스케줄 + 굿즈 → 허용 (복합 주문)
     * 4. 둘다 없으면 → 주문 실패
     */
    private void validateOrderItemsBusinessRules(List<OrderItem> orderItems) {
        log.debug("주문 항목 비즈니스 룰 검증 시작");

        boolean hasGoods = orderItems.stream()
                .anyMatch(item -> ItemType.GOODS.equals(item.getOrderItemType()));

        boolean hasReservation = orderItems.stream()
                .anyMatch(item -> ItemType.RESERVATION.equals(item.getOrderItemType()));

        // 둘다 없으면 주문할 게 없음
        if (!hasGoods && !hasReservation) {
            throw new IllegalArgumentException(
                "예약 또는 굿즈 중 최소 하나는 선택해야 합니다."
            );
        }

        // ✅ 허용되는 케이스들
        if (hasReservation && !hasGoods) {
            log.debug("비즈니스 룰 검증 통과: 스케줄 예약만");
        } else if (!hasReservation && hasGoods) {
            log.debug("비즈니스 룰 검증 통과: 굿즈만");
        } else if (hasReservation && hasGoods) {
            log.debug("비즈니스 룰 검증 통과: 스케줄 + 굿즈 복합형");
        }

        log.debug("주문 항목 비즈니스 룰 검증 완료");
    }

    // ================ 주문 생성 로직 ================

    /**
     * 새로운 주문 만들기 (팩토리 메서드)
     *
     * [초보자를 위한 설명]
     * 팩토리 메서드란? 객체를 만드는 전용 메서드예요.
     * 복잡한 생성 로직을 여기에 모아두면 실수를 줄일 수 있어요.
     */
    public Order createOrder(Long customerId, UUID popupId,
                           ItemType orderType, List<OrderItem> orderItems) {

        log.info("새 주문 만들기 시작 - 고객: {}, 타입: {}", customerId, orderType);

        // 1. 먼저 검증부터
        validateOrderCreation(customerId, popupId, orderItems);

        // 2. 비즈니스 규칙 적용해서 값들 계산
        LocalDateTime cancelableUntil = calculateCancelableUntil(orderType);
        int totalAmount = calculateTotalAmount(orderItems);

        // 3. 주문 엔티티 생성
        Order order = Order.builder()
                .orderNo(Order.generateOrderNo())        // 주문 번호 자동 생성
                .customerId(customerId)
                .popupId(popupId)
                .orderType(orderType)
                .status(OrderStatus.REQUESTED)           // 처음엔 항상 "요청됨" 상태
                .totalAmount(totalAmount)
                .cancelableUntil(cancelableUntil)
                .build();

        // 4. 주문과 주문 항목들 연결
        order.addOrderItems(new ArrayList<>(orderItems));

        log.info("새 주문 만들기 완료 - 주문번호: {}, 금액: {}원", order.getOrderNo(), totalAmount);
        return order;
    }

    // ================ 계산 메서드들 ================

    /**
     * 취소 가능 시간 계산하기
     *
     * [초보자를 위한 설명]
     * 주문 타입에 따라 취소할 수 있는 시간이 달라요.
     * 지금은 모두 5분으로 같지만, 나중에 다르게 할 수도 있어요.
     */
    public LocalDateTime calculateCancelableUntil(ItemType orderType) {
        LocalDateTime now = LocalDateTime.now();

        return switch (orderType) {
            case RESERVATION -> now.plusMinutes(RESERVATION_CANCEL_MINUTES);
            case GOODS -> now.plusMinutes(PURCHASE_CANCEL_MINUTES);
            case MIXED -> now.plusMinutes(PURCHASE_CANCEL_MINUTES); // 혼합형은 구매형과 동일한 정책
        };
    }

    /**
     * 총 주문 금액 계산하기
     *
     * [초보자를 위한 설명]
     * 각 상품의 라인 금액을 가져와서 `long`으로 누적한 다음,
     * Integer 범위를 넘으면 예외를 던져요.
     */
    public int calculateTotalAmount(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return 0;
        }

        long total = 0L;
        for (OrderItem item : orderItems) {
            if (item.getLineAmount() == null) {
                throw new IllegalArgumentException("주문 항목 금액이 누락되었습니다.");
            }

            total += item.getLineAmount();
            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("총 주문 금액이 허용 범위를 초과했습니다.");
            }
        }

        return (int) total;
    }

    // ================ 상태 변경 관련 메서드들 ================

    /**
     * 상태 변경이 가능한지 확인하기
     *
     * [초보자를 위한 설명]
     * 아무 상태에서 아무 상태로나 바꿀 수는 없어요.
     * 미리 정해진 규칙에 따라서만 바꿀 수 있어요.
     */
    public boolean canChangeStatus(OrderStatus currentStatus, OrderStatus newStatus) {
        // 같은 상태로는 변경할 필요가 없어요
        if (currentStatus == newStatus) {
            return false;
        }

        // 허용된 전환 목록에서 확인
        EnumSet<OrderStatus> allowedStatuses = STATUS_TRANSITIONS.get(currentStatus);
        boolean canChange = allowedStatuses != null && allowedStatuses.contains(newStatus);

        log.debug("상태 변경 가능성 확인: {} → {} = {}", currentStatus, newStatus, canChange);
        return canChange;
    }

    /**
     * 주문 취소가 가능한지 확인하기
     *
     * [초보자를 위한 설명]
     * 취소하려면 두 가지 조건을 모두 만족해야 해요:
     * 1. 취소 가능 시간 내에 있어야 해요
     * 2. 현재 상태에서 CANCELLED로 바꿀 수 있어야 해요
     */
    public boolean canCancelOrder(Order order) {
        // 시간 조건 확인
        boolean withinTimeLimit = order.isCancelable();

        // 상태 전환 조건 확인
        boolean statusAllowsCancellation = canChangeStatus(order.getStatus(), OrderStatus.CANCELLED);

        boolean canCancel = withinTimeLimit && statusAllowsCancellation;

        log.debug("주문 취소 가능성 확인 - 주문번호: {}, 시간조건: {}, 상태조건: {}, 결과: {}",
                order.getOrderNo(), withinTimeLimit, statusAllowsCancellation, canCancel);

        return canCancel;
    }

    /**
     * 결제 후 취소가 가능한지 확인 (특별한 경우)
     */
    public boolean canCancelPaidOrder(Order order) {
        return order.getStatus() == OrderStatus.PAID && order.isCancelable();
    }

    // ================ 유틸리티 메서드들 ================

    /**
     * 주문이 완료 상태인지 확인
     */
    public boolean isOrderCompleted(Order order) {
        return order.getStatus() == OrderStatus.COMPLETED;
    }

    /**
     * 주문이 진행 중인지 확인 (취소/거절/완료가 아닌 경우)
     */
    public boolean isOrderInProgress(Order order) {
        OrderStatus status = order.getStatus();
        return status != OrderStatus.CANCELLED
                && status != OrderStatus.REJECTED
                && status != OrderStatus.COMPLETED;
    }

    /**
     * 주문이 결제 관련 상태인지 확인
     */
    public boolean isPaymentRelatedStatus(OrderStatus status) {
        return status == OrderStatus.PAYMENT_PENDING || status == OrderStatus.PAID;
    }

}
