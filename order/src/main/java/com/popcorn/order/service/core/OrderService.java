package com.popcorn.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.popcorn.order.dto.command.CreateOrderCommand;
import com.popcorn.order.dto.response.OrderCreateResponse;
import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.repository.OrderRepository;
import com.popcorn.order.repository.OrderStatusHistoryRepository;
import com.popcorn.order.event.publisher.OrderEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 처리 서비스 - 초보자도 이해하기 쉽게 작성
 *
 * [초보자를 위한 설명]
 * 이 클래스는 주문과 관련된 모든 업무를 처리합니다.
 * 예를 들어: 주문 생성, 상태 변경, 주문 조회 등
 *
 * 왜 Service 클래스가 필요한가?
 * - Controller: 사용자 요청 받기
 * - Service: 실제 업무 처리 (비즈니스 로직)
 * - Repository: 데이터베이스와 대화
 *
 * 이렇게 역할을 나누면 코드가 깔끔해지고 관리하기 쉬워집니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    // 데이터베이스 작업을 위한 도구들
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    // 이벤트 발행을 위한 도구
    private final OrderEventPublisher orderEventPublisher;

    // 주문 관련 상수들 - 한 곳에서 관리하면 나중에 바꾸기 쉬워요
    private static final int DEFAULT_CANCEL_MINUTES = 30;  // 기본 취소 가능 시간 (30분)
    private static final int DEFAULT_ITEM_PRICE = 10000;   // 임시 기본 가격

    /**
     * 주문 생성하기 - 메인 메서드
     *
     * [초보자를 위한 설명]
     * 이 메서드는 새로운 주문을 만드는 일을 합니다.
     * 단계별로 차근차근 진행해서 실수가 없도록 해요.
     *
     * @param command 주문 만들기에 필요한 정보
     * @return 만들어진 주문 정보
     */
    @Transactional  // 데이터베이스 작업이 안전하게 처리되도록 보장
    public OrderCreateResponse createOrder(CreateOrderCommand command) {
        // 로그 찍기 - 어떤 일이 일어나는지 기록해둬요
        log.info("새 주문 만들기 시작! 사용자: {}, 팝업: {}",
                command.getUserId(), command.getPopupId());

        try {
            // 단계 1: 주문 정보가 올바른지 확인하기
            checkOrderInfo(command);

            // 단계 2: 주문 만들기
            Order newOrder = createOrderEntity(command);

            // 단계 3: 데이터베이스에 저장하기
            Order savedOrder = saveOrderToDatabase(newOrder);

            // 단계 4: 응답 만들어서 돌려주기
            OrderCreateResponse response = createOrderResponse(savedOrder);

            log.info("주문 만들기 성공! 주문번호: {}", response.getOrderNo());
            return response;

        } catch (Exception error) {
            // 뭔가 잘못됐을 때는 로그에 남기고 에러를 던져요
            log.error("주문 만들기 실패 - 사용자: {}, 에러: {}",
                     command.getUserId(), error.getMessage());
            throw new RuntimeException("주문을 만드는 중에 문제가 생겼어요: " + error.getMessage());
        }
    }

    /**
     * 주문 정보가 올바른지 확인하기
     *
     * [초보자를 위한 설명]
     * 사용자가 보낸 주문 정보에 빠진 것이 없는지,
     * 잘못된 것이 없는지 하나씩 체크해봐요.
     * 문제가 있으면 에러를 던져서 알려줘요.
     */
    private void checkOrderInfo(CreateOrderCommand command) {
        // 아예 정보가 없으면 안돼요
        if (command == null) {
            throw new IllegalArgumentException("주문 정보를 보내주세요!");
        }

        // 누가 주문하는지 알아야 해요
        if (command.getUserId() == null) {
            throw new IllegalArgumentException("사용자 정보가 필요해요!");
        }

        // 어떤 팝업에서 주문하는지 알아야 해요
        if (command.getPopupId() == null) {
            throw new IllegalArgumentException("팝업 정보가 필요해요!");
        }

        // 예약인지 구매인지 알아야 해요
        if (isEmptyString(command.getOrderType())) {
            throw new IllegalArgumentException("주문 타입을 선택해주세요!");
        }

        // 뭘 주문할지 알아야 해요
        if (isEmptyList(command.getItems())) {
            throw new IllegalArgumentException("주문할 상품을 최소 1개는 선택해주세요!");
        }

        log.debug("주문 정보 확인 완료 - 모든 정보가 올바릅니다!");
    }

    /**
     * 문자열이 비어있는지 확인하는 헬퍼 메서드
     */
    private boolean isEmptyString(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * 리스트가 비어있는지 확인하는 헬퍼 메서드
     */
    private boolean isEmptyList(java.util.List<?> list) {
        return list == null || list.isEmpty();
    }

    /**
     * 주문 엔티티 생성
     *
     * @param command 주문 생성 명령
     * @return 생성된 주문 엔티티
     *
     * [초보자 가이드]
     * DTO → Entity 변환:
     * - Command 객체에서 필요한 정보를 추출
     * - 비즈니스 로직 적용 (주문번호 생성, 상태 설정 등)
     * - Entity 객체로 변환
     */
    private Order createOrderEntity(CreateOrderCommand command) {
        // 주문 번호 자동 생성
        String orderNo = Order.generateOrderNo();

        // 주문 타입 변환
        ItemType orderType = ItemType.valueOf(command.getOrderType());

        // 총 금액 계산 (임시로 고정값 사용)
        Integer totalAmount = calculateTotalAmount(command);

        // 취소 가능 시간 설정 (30분 후)
        LocalDateTime cancelableUntil = LocalDateTime.now().plusMinutes(30);

        return Order.builder()
                .orderNo(orderNo)
                .customerId(command.getUserId())
                .popupId(command.getPopupId())
                .orderType(orderType)
                .status(OrderStatus.REQUESTED)
                .totalAmount(totalAmount)
                .cancelableUntil(cancelableUntil)
                .build();
    }

    /**
     * 총 주문 금액 계산
     *
     * @param command 주문 생성 명령
     * @return 계산된 총 금액
     *
     * [초보자 가이드]
     * 실제로는 각 항목의 단가와 수량을 곱해서 합산해야 하지만,
     * 지금은 간단히 고정값으로 구현
     */
    private Integer calculateTotalAmount(CreateOrderCommand command) {
        // TODO: 실제 가격 계산 로직 구현
        // - 각 항목의 단가 조회
        // - 수량과 곱셈
        // - 할인 적용
        // - 배송비 추가 등

        return 10000; // 임시 고정값
    }

    /**
     * 주문을 데이터베이스에 저장
     *
     * @param order 저장할 주문 엔티티
     * @return 저장된 주문 엔티티 (ID가 생성됨)
     */
    private Order saveOrderToDatabase(Order order) {
        log.debug("주문 데이터베이스 저장 시작 - 주문번호: {}", order.getOrderNo());

        try {
            Order savedOrder = orderRepository.save(order);
            log.info("주문 저장 완료 - ID: {}, 주문번호: {}", savedOrder.getId(), savedOrder.getOrderNo());
            return savedOrder;
        } catch (Exception e) {
            log.error("주문 저장 실패 - 주문번호: {}", order.getOrderNo(), e);
            throw new RuntimeException("주문 저장 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 주문 엔티티를 응답 DTO로 변환
     *
     * @param order 변환할 주문 엔티티
     * @return 주문 생성 응답 DTO
     */
    private OrderCreateResponse createOrderResponse(Order order) {
        log.debug("주문 응답 생성 - 주문번호: {}", order.getOrderNo());

        return OrderCreateResponse.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .popupId(order.getPopupId())
                .orderType(order.getOrderType().name())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .cancelableUntil(order.getCancelableUntil())
                .createdAt(order.getCreatedAt())
                .items(List.of()) // TODO: 실제 주문 항목 구현 후 수정
                .build();
    }

    /**
     * 주문 상태 업데이트
     *
     * @param orderId 주문 ID
     * @param status 새로운 상태
     * @param reason 변경 사유
     *
     * [초보자 가이드]
     * 상태 변경 시 고려사항:
     * - 현재 상태에서 새 상태로 변경 가능한지 검증
     * - 상태 변경 이력 기록
     * - 관련 이벤트 발생
     */
    @Transactional
    public void updateOrderStatus(UUID orderId, String status, String reason) {
        log.info("주문 상태 변경 - ID: {}, 상태: {}, 사유: {}",
                orderId, status, reason);

        try {
            // TODO: 실제 구현
            // 1. 주문 조회
            // 2. 상태 변경 가능 여부 검증
            // 3. 상태 업데이트
            // 4. 이력 기록

            log.info("주문 상태 변경 완료 - ID: {}", orderId);

        } catch (Exception e) {
            log.error("주문 상태 변경 실패 - ID: {}", orderId, e);
            throw new RuntimeException("주문 상태 변경 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 결제 완료 처리
     * Payment 모듈에서 결제가 완료되면 호출되는 메서드
     *
     * @param orderId 주문 ID
     */
    @Transactional
    public void handlePaymentCompleted(UUID orderId) {
        try {
            log.info("결제 완료 처리 시작 - orderId: {}", orderId);

            // 1. 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

            // 2. 주문 상태가 PAYMENT_PENDING인지 확인
            if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
                log.warn("잘못된 주문 상태 - 현재 상태: {}, 주문ID: {}",
                        order.getOrderStatus(), orderId);
                throw new IllegalStateException("결제 처리가 가능한 주문 상태가 아닙니다.");
            }

            // 3. 주문 상태를 PAID로 변경
            OrderStatus oldStatus = order.getOrderStatus();
            order.markAsPaid();

            Order savedOrder = orderRepository.save(order);

            // 4. 주문 상태 변경 이력 기록
            saveOrderStatusHistory(order, oldStatus, "결제 완료");

            // 5. OrderPaidEvent 발행 (재고 차감 요청)
            orderEventPublisher.publishOrderPaidEvent(savedOrder);

            log.info("결제 완료 처리 성공 - orderId: {}, 상태: {} -> {}",
                    orderId, oldStatus, OrderStatus.PAID);

        } catch (Exception e) {
            log.error("결제 완료 처리 실패 - orderId: {}", orderId, e);
            throw new RuntimeException("결제 완료 처리 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 주문 취소 처리 (보상 트랜잭션)
     * 재고 차감 실패 등의 이유로 주문을 취소해야 할 때 호출
     *
     * @param orderId 주문 ID
     * @param reason 취소 사유
     */
    @Transactional
    public void handleOrderCancellation(UUID orderId, String reason) {
        try {
            log.info("주문 취소 처리 시작 - orderId: {}, reason: {}", orderId, reason);

            // 1. 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

            // 2. 이미 취소된 주문인지 확인
            if (order.isCancelled()) {
                log.warn("이미 취소된 주문입니다 - orderId: {}", orderId);
                return;
            }

            // 3. 주문 취소 처리
            OrderStatus oldStatus = order.getOrderStatus();
            order.markAsCancelled(reason);

            Order savedOrder = orderRepository.save(order);

            // 4. 주문 상태 변경 이력 기록
            saveOrderStatusHistory(order, oldStatus, reason);

            // 5. OrderCancelledEvent 발행 (Payment 모듈에게 환불 요청)
            orderEventPublisher.publishOrderCancelledEvent(savedOrder, reason);

            log.info("주문 취소 처리 성공 - orderId: {}, 상태: {} -> {}, 사유: {}",
                    orderId, oldStatus, OrderStatus.CANCELLED, reason);

        } catch (Exception e) {
            log.error("주문 취소 처리 실패 - orderId: {}, reason: {}", orderId, reason, e);
            throw new RuntimeException("주문 취소 처리 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 주문 확정 처리
     * 재고 차감이 성공하면 주문을 확정 상태로 변경
     *
     * @param orderId 주문 ID
     */
    @Transactional
    public void handleOrderConfirmation(UUID orderId) {
        try {
            log.info("주문 확정 처리 시작 - orderId: {}", orderId);

            // 1. 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

            // 2. 주문이 PAID 상태인지 확인
            if (!order.isPaid()) {
                log.warn("주문 확정 불가 - 현재 상태: {}, 주문ID: {}",
                        order.getOrderStatus(), orderId);
                throw new IllegalStateException("결제가 완료되지 않은 주문은 확정할 수 없습니다.");
            }

            // 3. 주문 상태를 CONFIRMED로 변경
            OrderStatus oldStatus = order.getOrderStatus();
            order.markAsConfirmed();

            Order savedOrder = orderRepository.save(order);

            // 4. 주문 상태 변경 이력 기록
            saveOrderStatusHistory(order, oldStatus, "재고 차감 성공 - 주문 확정");

            // 5. OrderCompletedEvent 발행 (알림 발송)
            orderEventPublisher.publishOrderCompletedEvent(savedOrder);

            log.info("주문 확정 처리 성공 - orderId: {}, 상태: {} -> {}",
                    orderId, oldStatus, OrderStatus.COMPLETED);

        } catch (Exception e) {
            log.error("주문 확정 처리 실패 - orderId: {}", orderId, e);
            throw new RuntimeException("주문 확정 처리 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 주문 상태 변경 이력 저장
     */
    private void saveOrderStatusHistory(Order order, OrderStatus oldStatus, String reason) {
        try {
            orderStatusHistoryRepository.save(order.toHistory(oldStatus, reason));
        } catch (Exception e) {
            log.error("주문 상태 이력 저장 실패 - orderId: {}", order.getId(), e);
            // 이력 저장 실패는 주요 로직을 중단시키지 않음
        }
    }

}
