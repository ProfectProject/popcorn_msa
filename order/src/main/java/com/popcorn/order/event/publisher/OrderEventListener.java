package com.popcorn.order.event.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// TODO: Kafka Listener로 대체 예정
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.entity.OrderItem;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.entity.OrderStatusHistory;
import com.popcorn.order.repository.OrderRepository;
import com.popcorn.order.repository.OrderItemRepository;
import com.popcorn.order.repository.OrderStatusHistoryRepository;
import com.popcorn.order.event.payment.PaymentCompletedEvent;
import com.popcorn.order.event.order.OrderPaidEvent;
import com.popcorn.order.event.order.OrderCancelledEvent;
import com.popcorn.order.enums.ProductType;
// 통합 이벤트 제거: ScheduleReservationResultEvent import 삭제됨
import com.popcorn.order.service.core.OrderCommandService;
import com.popcorn.order.service.util.OrderReservationAwaiter;
import com.popcorn.order.dto.payment.PaymentUrlResponse;
import com.popcorn.order.event.compensation.PaymentCompensationRequestedEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

/**
 * 주문 이벤트 리스너
 *
 * [역할]
 * - 내부 이벤트 및 외부 이벤트(RabbitMQ) 수신 처리
 * - Store 모듈의 재고 차감 결과 처리
 * - Payment 모듈의 결제 상태 변경 처리
 * - 보상 트랜잭션 처리
 *
 * [이벤트 수신 소스]
 * - Store 모듈: 재고 차감 성공/실패
 * - Payment 모듈: 결제 완료/취소
 * - 내부: 주문 상태 변경
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisEventPublisher redisEventPublisher;
    private final ObjectMapper objectMapper;
    private final OrderCommandService orderCommandService;
    private final OrderReservationAwaiter orderReservationAwaiter;



    /**
     * Payment 모듈의 결제 완료 이벤트 수신 (내부 이벤트)
     *
     * @param event 결제 완료 이벤트
     */
    @EventListener
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            log.info("결제 완료 이벤트 수신 - orderId: {}", event.getOrderId());

            // 주문 상태를 PAID로 변경 (내부 로직에서 주문 아이템 로드 + 이벤트 발행)
            orderCommandService.updateOrderStatus(
                    event.getOrderId(),
                    OrderStatus.PAID.name(),
                    "결제 완료"
            );

            log.info("주문 상태 PAID로 변경 및 재고 차감 요청 이벤트 발행 완료 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("🚨 결제 완료 이벤트 처리 중 오류 - orderId: {}", event.getOrderId(), e);

            // 🔥 이벤트 처리 실패 시 반드시 예외 throw + 보상 트랜잭션
            handleEventProcessingFailure(event.getOrderId(), "결제 완료 이벤트 처리", e);
            throw new RuntimeException("결제 완료 이벤트 처리 실패", e);
        }
    }

    /**
     * 주문 결제 완료 이벤트 수신 -> 재고 차감 처리
     */
    @EventListener
    @Transactional
    public void handleOrderPaid(OrderPaidEvent event) {
        try {
            log.info("주문 결제 완료 이벤트 수신 - orderId: {}", event.getOrderId());

            if (event.getOrderItems() == null || event.getOrderItems().isEmpty()) {
                log.warn("재고 차감할 주문 항목이 없습니다 - orderId: {}", event.getOrderId());
                return;
            }

            // 통합 이벤트 제거로 인해 재고 차감 로직은 개별 이벤트로 처리됩니다.
            // TODO: 개별 StockDeductionRequestedEvent 발행으로 변경 예정

            log.info("주문 결제 완료 이벤트 처리 완료 (통합 이벤트 제거됨) - orderId: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("🚨 재고 차감 처리 실패 - orderId: {}, error: {}",
                    event.getOrderId(), e.getMessage(), e);

            // 재고 차감 실패 이벤트 발행
            orderEventPublisher.publishStockDeductionFailedEvent(
                    event.getOrderId(),
                    "재고 차감 실패: " + e.getMessage()
            );

            // 🔥 보상 트랜잭션 실행
            handleEventProcessingFailure(event.getOrderId(), "주문 결제 완료 후 재고 차감 처리", e);
            throw new RuntimeException("재고 차감 처리 실패", e);
        }
    }

    /**
     * 재고 차감 실패로 인한 주문 취소 처리 (보상 트랜잭션)
     *
     * @param event 재고 차감 결과 이벤트 (실패)
     */
    // 통합 이벤트 제거: handleOrderCancellationForStockFailure 삭제됨
    /*private void handleOrderCancellationForStockFailure(StoreResultEvent event) {
        try {
            UUID orderId = event.getOrderId();

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

            // 주문이 이미 취소된 경우 무시
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                log.warn("이미 취소된 주문입니다 - orderId: {}", orderId);
                return;
            }

            // 주문 취소 처리
            String cancellationReason = String.format("재고 차감 실패 - %s",
                    event.getFailureReason());

            order.updateStatus(OrderStatus.CANCELLED);
            order.setCancellationReason(cancellationReason);
            orderRepository.save(order);

            // 주문 취소 이벤트 발행 (Payment 모듈에게 결제 취소 요청)
            orderEventPublisher.publishOrderCancelledEvent(order, cancellationReason);

            log.info("재고 차감 실패로 인한 주문 취소 처리 완료 - orderId: {}, reason: {}",
                    orderId, cancellationReason);

        } catch (Exception e) {
            log.error("재고 차감 실패로 인한 주문 취소 처리 중 오류 - orderId: {}",
                    event.getOrderId(), e);
            throw new RuntimeException("주문 취소 처리 실패", e);
        }
    }

    /**
     * 재고 차감 성공으로 인한 주문 확정 처리
     *
     * @param orderId 주문 ID
     */
    private void handleOrderConfirmation(UUID orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

            // 주문 상태를 COMPLETED로 변경
            order.updateStatus(OrderStatus.COMPLETED);
            orderRepository.save(order);

            // 주문 확정 완료 이벤트 발행
            orderEventPublisher.publishOrderCompletedEvent(order);

            log.info("재고 차감 성공으로 인한 주문 확정 처리 완료 - orderId: {}", orderId);

        } catch (Exception e) {
            log.error("주문 확정 처리 중 오류 - orderId: {}", orderId, e);
            throw new RuntimeException("주문 확정 처리 실패", e);
        }
    }

    // 통합 이벤트 제거: handleInternalStockDeductionFailed 삭제됨
    /*
     * 내부 재고 차감 실패 이벤트 수신 (동일 서비스 내, 통합 이벤트 사용)
     * 통합 이벤트 제거로 인해 삭제됨
     */

    /**
     * 주문 취소 이벤트 수신 → 재고 예약 취소 처리
     *
     * @param event 주문 취소 이벤트
     */
    @EventListener
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        try {
            log.info("주문 취소 이벤트 수신 - orderId: {}, reason: {}",
                    event.getOrderId(), event.getCancelReason());

            // 주문 정보 조회
            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.getOrderId()));

            // 요청/예약 단계에서 취소된 경우에도 보상 요청 전송
            if (OrderStatus.REQUESTED.equals(event.getPreviousStatus())
                    || OrderStatus.RESERVED.equals(event.getPreviousStatus())) {
                cancelStockReservationsForOrder(order);
                cancelScheduleReservations(order, "주문 취소로 인한 예약 해제");
                log.info("재고/스케줄 예약 취소 완료 - orderId: {}", event.getOrderId());
            } else {
                log.info("재고/스케줄 예약 취소 불필요 - orderId: {}, previousStatus: {}",
                        event.getOrderId(), event.getPreviousStatus());
            }

        } catch (Exception e) {
            log.error("🚨 주문 취소 처리 중 오류 - orderId: {}, error: {}",
                    event.getOrderId(), e.getMessage(), e);

            // 🔥 주문 취소 처리 실패 시 보상 트랜잭션
            handleEventProcessingFailure(event.getOrderId(), "주문 취소 이벤트 처리", e);
            // 주문 취소는 중요한 프로세스이므로 예외도 throw
            throw new RuntimeException("주문 취소 처리 실패", e);
        }
    }

    /**
     * 주문의 모든 굿즈 항목에 대한 재고 예약을 취소합니다.
     *
     * @param order 재고 예약을 취소할 주문
     */
    private void cancelStockReservationsForOrder(Order order) {
        log.info("주문 재고 예약 취소 시작 - 주문번호: {}", order.getOrderNo());

        // 주문 항목들 조회 (order 객체에 포함되어 있지 않을 수 있음)
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        // 굿즈 항목만 필터링
        List<OrderItem> goodsItems = orderItems.stream()
                .filter(item -> ItemType.GOODS.equals(item.getOrderItemType()))
                .toList();

        if (goodsItems.isEmpty()) {
            log.info("굿즈 항목이 없어 재고 예약 취소를 건너뜁니다 - 주문번호: {}", order.getOrderNo());
            return;
        }

        // 각 굿즈 항목에 대해 재고 예약 취소
        for (OrderItem item : goodsItems) {
            if (item.getGoodsId() == null) {
                log.warn("굿즈 변형 ID가 없어 재고 예약 취소를 건너뜁니다 - 주문번호: {}, 항목ID: {}",
                        order.getOrderNo(), item.getId());
                continue;
            }

            try {
                log.info("굿즈 재고 예약 취소 시도 - 주문번호: {}, 굿즈변형ID: {}, 수량: {}",
                        order.getOrderNo(), item.getGoodsId(), item.getQty());

                orderEventPublisher.publishGoodsReservationCancelRequestedEvent(
                        order,
                        item.getGoodsId(),
                        item.getQty()
                );

                log.info("굿즈 재고 예약 취소 성공 - 주문번호: {}, 굿즈변형ID: {}",
                        order.getOrderNo(), item.getGoodsId());

            } catch (Exception e) {
                log.error("굿즈 재고 예약 취소 실패 - 주문번호: {}, 굿즈변형ID: {}, 에러: {}",
                        order.getOrderNo(), item.getGoodsId(), e.getMessage(), e);
                // 개별 항목 실패는 로그만 남기고 계속 진행
            }
        }

        log.info("주문 재고 예약 취소 완료 - 주문번호: {}", order.getOrderNo());
    }

    // ================ 🚨 이벤트 실패 처리 및 보상 트랜잭션 ================

    /**
     * 이벤트 처리 실패 시 공통 처리 로직
     *
     * 🔥 핵심: 이벤트 실패 시 반드시 에러가 떠야 하고, 보상 트랜잭션이 실행되어야 함
     */
    private void handleEventProcessingFailure(UUID orderId, String eventType, Exception error) {
        try {
            log.error("🚨🔥 [CRITICAL] 이벤트 처리 실패 감지 - orderId: {}, eventType: {}, error: {}",
                    orderId, eventType, error.getMessage());

            // 1. 주문 상태를 ERROR로 변경 (복구 가능하도록)
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

            OrderStatus previousStatus = order.getStatus();
            order.updateStatus(OrderStatus.CANCELLED); // ERROR 상태가 없으므로 CANCELLED 사용
            order.setCancellationReason(String.format("이벤트 처리 실패: %s - %s", eventType, error.getMessage()));
            orderRepository.save(order);

            // 2. 이벤트 실패 이력 저장
            OrderStatusHistory failureHistory = OrderStatusHistory.builder()
                    .orderId(orderId)
                    .fromStatus(previousStatus)
                    .toStatus(OrderStatus.CANCELLED)
                    .reason(String.format("🚨 이벤트 처리 실패: %s", eventType))
                    .changedAt(LocalDateTime.now())
                    .build();
            orderStatusHistoryRepository.save(failureHistory);

            // 3. 🔥 보상 트랜잭션 실행 (Saga 패턴)
            executeCompensationTransaction(order, eventType, error);

            // 4. 🚨 관리자 알림 (모니터링)
            sendCriticalErrorNotificationToAdmin(orderId, eventType, error);

            log.error("🚨✅ 이벤트 처리 실패 보상 트랜잭션 완료 - orderId: {}", orderId);

        } catch (Exception compensationError) {
            // 보상 트랜잭션마저 실패한 경우 - 매우 심각한 상황
            log.error("🚨💥 [DISASTER] 보상 트랜잭션 실패 - orderId: {}, originalError: {}, compensationError: {}",
                    orderId, error.getMessage(), compensationError.getMessage(), compensationError);

            // 데드레터큐나 수동 처리 큐로 전송 필요
            sendToDeadLetterQueue(orderId, eventType, error, compensationError);
        }
    }

    /**
     * 🔄 보상 트랜잭션 실행 (Saga 패턴)
     * 실패한 이벤트 타입에 따라 적절한 롤백 처리
     */
    private void executeCompensationTransaction(Order order, String eventType, Exception error) {
        log.info("🔄 보상 트랜잭션 시작 - orderId: {}, eventType: {}", order.getId(), eventType);

        try {
            // 이벤트 타입별 보상 처리
            switch (eventType) {
                case "재고 차감 성공 이벤트 처리":
                    compensateStockDeductionSuccess(order);
                    break;

                case "스케줄 예약 성공 이벤트 처리":
                    compensateScheduleReservationSuccess(order);
                    break;

                case "결제 완료 이벤트 처리":
                    compensatePaymentCompleted(order);
                    break;

                default:
                    // 알 수 없는 이벤트 타입 - 전체 보상
                    compensateFullOrder(order);
                    break;
            }

            log.info("🔄✅ 보상 트랜잭션 완료 - orderId: {}, eventType: {}", order.getId(), eventType);

        } catch (Exception e) {
            log.error("🔄💥 보상 트랜잭션 실행 실패 - orderId: {}, eventType: {}", order.getId(), eventType, e);
            throw new RuntimeException("보상 트랜잭션 실행 실패", e);
        }
    }

    /**
     * 재고 차감 성공 처리 실패 시 보상
     */
    private void compensateStockDeductionSuccess(Order order) {
        log.info("🔄 재고 차감 성공 보상 트랜잭션 - orderId: {}", order.getId());

        // 1. 이미 차감된 재고를 복원 요청
        publishStockRestoreRequestedEvent(order, "재고 차감 성공 처리 실패 보상");

        // 2. 스케줄 예약이 있었다면 취소
        if (hasReservationItems(order)) {
            cancelScheduleReservations(order, "재고 처리 실패 보상");
        }

        // 3. 결제 취소 요청
        publishPaymentCancelCompensationEvent(order, "재고 처리 실패");
    }

    /**
     * 스케줄 예약 성공 처리 실패 시 보상
     */
    private void compensateScheduleReservationSuccess(Order order) {
        log.info("🔄 스케줄 예약 성공 보상 트랜잭션 - orderId: {}", order.getId());

        // 1. 예약된 스케줄 취소 요청
        cancelScheduleReservations(order, "스케줄 예약 성공 처리 실패 보상");

        // 2. 굿즈 재고 예약이 있었다면 취소
        if (hasGoodsItems(order)) {
            cancelGoodsReservations(order, "스케줄 처리 실패 보상");
        }

        // 3. 결제 취소 요청
        publishPaymentCancelCompensationEvent(order, "스케줄 처리 실패");
    }

    /**
     * 결제 완료 처리 실패 시 보상
     */
    private void compensatePaymentCompleted(Order order) {
        log.info("🔄 결제 완료 보상 트랜잭션 - orderId: {}", order.getId());

        // 1. 결제 취소 요청 (우선순위 높음)
        publishPaymentCancelCompensationEvent(order, "결제 완료 처리 실패");

        // 2. 스케줄 예약 취소
        if (hasReservationItems(order)) {
            cancelScheduleReservations(order, "결제 처리 실패 보상");
        }

        // 3. 굿즈 재고 예약 취소
        if (hasGoodsItems(order)) {
            cancelGoodsReservations(order, "결제 처리 실패 보상");
        }
    }

    /**
     * 전체 주문 보상 (알 수 없는 실패)
     */
    private void compensateFullOrder(Order order) {
        log.info("🔄 전체 주문 보상 트랜잭션 - orderId: {}", order.getId());

        // 모든 리소스 해제
        compensatePaymentCompleted(order);
    }

    /**
     * 스케줄 예약 취소
     */
    private void cancelScheduleReservations(Order order, String reason) {
        try {
            List<OrderItem> reservationItems = order.getOrderItems().stream()
                    .filter(item -> ItemType.RESERVATION.equals(item.getOrderItemType()))
                    .toList();

            if (reservationItems.isEmpty()) {
                log.info("취소할 스케줄 예약이 없습니다 - orderId: {}", order.getId());
                return;
            }

            // 개별 ScheduleReservationCancelRequestedEvent 발행
            for (OrderItem item : reservationItems) {
                if (item.getSessionOptionId() != null) {
                    try {
                        log.info("🔄 스케줄 예약 취소 이벤트 발행 - orderId: {}, sessionOptionId: {}, reason: {}",
                                order.getId(), item.getSessionOptionId(), reason);

                        orderEventPublisher.publishScheduleReservationCancelRequestedEvent(
                                order.getId(),
                                order.getOrderNo(),
                                order.getPopupId(),
                                item.getSessionOptionId(),
                                item.getQty(),
                                reason
                        );

                        log.info("✅ 스케줄 예약 취소 이벤트 발행 완료 - orderId: {}, sessionOptionId: {}",
                                order.getId(), item.getSessionOptionId());

                    } catch (Exception e) {
                        log.error("❌ 스케줄 예약 취소 이벤트 발행 실패 - orderId: {}, sessionOptionId: {}, error: {}",
                                order.getId(), item.getSessionOptionId(), e.getMessage(), e);
                        // 개별 항목 실패는 로그만 남기고 계속 진행
                    }
                } else {
                    log.warn("세션 옵션 ID가 없어 스케줄 예약 취소를 건너뜁니다 - orderId: {}, itemId: {}",
                            order.getId(), item.getId());
                }
            }

        } catch (Exception e) {
            log.error("🔄💥 스케줄 예약 취소 보상 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 굿즈 재고 예약 취소
     */
    private void cancelGoodsReservations(Order order, String reason) {
        try {
            List<OrderItem> goodsItems = order.getOrderItems().stream()
                    .filter(item -> ItemType.GOODS.equals(item.getOrderItemType()))
                    .toList();

            if (goodsItems.isEmpty()) {
                log.info("취소할 굿즈 재고가 없습니다 - orderId: {}", order.getId());
                return;
            }

            for (OrderItem item : goodsItems) {
                if (item.getGoodsId() != null) {
                    orderEventPublisher.publishGoodsReservationCancelRequestedEvent(
                            order,
                            item.getGoodsId(),
                            item.getQty()
                    );
                }
            }

            log.info("🔄 굿즈 재고 예약 취소 보상 요청 완료 - orderId: {}", order.getId());

        } catch (Exception e) {
            log.error("🔄💥 굿즈 재고 예약 취소 보상 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 재고 복원 요청 이벤트 발행
     */
    private void publishStockRestoreRequestedEvent(Order order, String reason) {
        try {
            // TODO: StockRestoreRequestedEvent 구현 필요
            log.info("🔄 재고 복원 요청 (TODO: 구현 필요) - orderId: {}, reason: {}", order.getId(), reason);
        } catch (Exception e) {
            log.error("🔄💥 재고 복원 요청 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 결제 취소 보상 이벤트 발행
     */
    private void publishPaymentCancelCompensationEvent(Order order, String reason) {
        try {
            orderEventPublisher.publishPaymentCancelRequestedEvent(
                    order,
                    null, // paymentId는 조회 필요
                    String.format("보상 트랜잭션: %s", reason)
            );
            log.info("🔄 결제 취소 보상 요청 완료 - orderId: {}, reason: {}", order.getId(), reason);
        } catch (Exception e) {
            log.error("🔄💥 결제 취소 보상 요청 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 주문에 예약 항목이 있는지 확인
     */
    private boolean hasReservationItems(Order order) {
        return order.getOrderItems().stream()
                .anyMatch(item -> ItemType.RESERVATION.equals(item.getOrderItemType()));
    }

    /**
     * 주문에 굿즈 항목이 있는지 확인
     */
    private boolean hasGoodsItems(Order order) {
        return order.getOrderItems().stream()
                .anyMatch(item -> ItemType.GOODS.equals(item.getOrderItemType()));
    }

    /**
     * 🚨 관리자에게 긴급 알림 발송
     */
    private void sendCriticalErrorNotificationToAdmin(UUID orderId, String eventType, Exception error) {
        try {
            log.error("🚨📢 [ADMIN-ALERT] 이벤트 처리 실패 관리자 알림 - orderId: {}, eventType: {}, error: {}",
                    orderId, eventType, error.getMessage());

            // TODO: Slack, Teams, Email 등으로 관리자 긴급 알림 발송
            // AdminNotificationService.sendCriticalAlert(orderId, eventType, error);

        } catch (Exception e) {
            log.error("🚨💥 관리자 알림 발송 실패 - orderId: {}", orderId, e);
        }
    }

    /**
     * 💀 데드레터큐로 전송 (최후의 수단)
     */
    private void sendToDeadLetterQueue(UUID orderId, String eventType, Exception originalError, Exception compensationError) {
        try {
            log.error("💀 [DEAD-LETTER-QUEUE] 복구 불가능한 실패 - orderId: {}, eventType: {}", orderId, eventType);

            // TODO: 데드레터큐 또는 수동 처리 큐로 전송
            // DeadLetterQueueService.send(orderId, eventType, originalError, compensationError);

        } catch (Exception e) {
            // 이것마저 실패하면... 🤯
            log.error("💀💥 데드레터큐 전송도 실패 - orderId: {}, 수동 처리 필요!", orderId, e);
        }
    }

    // ================ 🔄 Payment 보상 트랜잭션 이벤트 리스너 ================

    /**
     * Payment 서비스로부터 보상 요청 이벤트 수신
     *
     * 결제 검증 실패나 결제 처리 실패 시 Payment 서비스가 보상 처리를 요청하는 이벤트입니다.
     * Order 서비스는 이 요청을 받아 예약 취소, 재고 해제, 주문 상태 업데이트 등의 보상 액션을 수행합니다.
     */
    @EventListener
    @Transactional
    public void handlePaymentCompensationRequested(PaymentCompensationRequestedEvent event) {
        try {
            log.info("🔄 Payment 보상 요청 이벤트 수신 - compensationId: {}, orderId: {}, type: {}, reason: {}",
                    event.getCompensationId(), event.getOrderId(), event.getCompensationType(), event.getCompensationReason());

            // 주문 정보 조회
            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.getOrderId()));

            // 이미 취소된 주문인지 확인
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                log.warn("이미 취소된 주문에 대한 보상 요청 - orderId: {}, 현재 상태: {}",
                        event.getOrderId(), order.getOrderStatus());
                // 보상 완료 이벤트 발행 (이미 처리됨)
                publishCompensationCompletedEvent(event, "SUCCESS",
                        List.of("ORDER_ALREADY_CANCELLED"), "주문이 이미 취소된 상태입니다");
                return;
            }

            List<String> completedActions = new ArrayList<>();
            List<String> failedActions = new ArrayList<>();

            // 요청된 보상 액션들을 순차적으로 실행
            for (PaymentCompensationRequestedEvent.CompensationAction action : event.getRequestedActions()) {
                try {
                    boolean success = executeCompensationAction(order, action, event);
                    if (success) {
                        completedActions.add(action.getActionType());
                        log.info("✅ 보상 액션 실행 성공 - orderId: {}, action: {}",
                                event.getOrderId(), action.getActionType());
                    } else {
                        failedActions.add(action.getActionType());
                        log.error("❌ 보상 액션 실행 실패 - orderId: {}, action: {}",
                                event.getOrderId(), action.getActionType());
                    }
                } catch (Exception e) {
                    failedActions.add(action.getActionType());
                    log.error("💥 보상 액션 실행 중 예외 - orderId: {}, action: {}, error: {}",
                            event.getOrderId(), action.getActionType(), e.getMessage(), e);
                }
            }

            // 보상 결과에 따른 응답 이벤트 발행
            String result;
            String notes;
            if (failedActions.isEmpty()) {
                result = "SUCCESS";
                notes = "모든 보상 액션이 성공적으로 완료되었습니다";
            } else if (completedActions.isEmpty()) {
                result = "FAILED";
                notes = String.format("모든 보상 액션이 실패했습니다. 실패한 액션: %s", String.join(", ", failedActions));
            } else {
                result = "PARTIAL_SUCCESS";
                notes = String.format("일부 보상 액션만 성공했습니다. 성공: %s, 실패: %s",
                        String.join(", ", completedActions), String.join(", ", failedActions));
            }

            // Payment 서비스로 보상 완료 결과 전송
            publishCompensationCompletedEvent(event, result, completedActions, notes);

            log.info("🔄✅ Payment 보상 요청 처리 완료 - compensationId: {}, orderId: {}, result: {}",
                    event.getCompensationId(), event.getOrderId(), result);

        } catch (Exception e) {
            log.error("🔄💥 Payment 보상 요청 처리 실패 - compensationId: {}, orderId: {}, error: {}",
                    event.getCompensationId(), event.getOrderId(), e.getMessage(), e);

            // 보상 실패 이벤트 발행
            publishCompensationFailedEvent(event, e.getMessage());
            throw new RuntimeException("Payment 보상 요청 처리 실패", e);
        }
    }

    /**
     * 개별 보상 액션 실행
     */
    private boolean executeCompensationAction(Order order, PaymentCompensationRequestedEvent.CompensationAction action,
                                             PaymentCompensationRequestedEvent event) {
        try {
            // 액션 타입 정리 및 검증
            String cleanedActionType = action.getActionType();
            if (cleanedActionType != null) {
                cleanedActionType = cleanedActionType.trim().replaceAll("[\\[\\]\"']", "");
            }

            log.debug("🔍 [ACTION] 보상 액션 실행 - orderId: {}, 원본 액션타입: '{}', 정리된 액션타입: '{}'",
                    order.getId(), action.getActionType(), cleanedActionType);

            // 강건한 문자열 매칭
            if (cleanedActionType == null || cleanedActionType.isBlank()) {
                log.warn("⚠️ 빈 액션 타입 - orderId: {}", order.getId());
                return false;
            }

            switch (cleanedActionType.toUpperCase()) {
                case "CANCEL_RESERVATION":
                    return executeCancelReservationAction(order, action, event);

                case "UPDATE_ORDER_STATUS":
                    return executeUpdateOrderStatusAction(order, action, event);

                case "RELEASE_STOCK":
                    return executeReleaseStockAction(order, action, event);

                default:
                    log.warn("알 수 없는 보상 액션 타입 - orderId: {}, 원본: '{}', 정리됨: '{}', 전체 액션: {}",
                            order.getId(), action.getActionType(), cleanedActionType, action);
                    // 알 수 없는 액션 타입이지만 전체 보상 프로세스를 실패시키지는 않음
                    log.info("⚠️ 알 수 없는 액션 타입이지만 다른 액션 계속 진행 - orderId: {}", order.getId());
                    return true;
            }
        } catch (Exception e) {
            log.error("보상 액션 실행 중 예외 발생 - orderId: {}, actionType: {}, error: {}",
                    order.getId(), action.getActionType(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 예약 취소 액션 실행
     */
    private boolean executeCancelReservationAction(Order order, PaymentCompensationRequestedEvent.CompensationAction action,
                                                  PaymentCompensationRequestedEvent event) {
        try {
            log.info("🔄 예약 취소 액션 실행 - orderId: {}, reason: {}",
                    order.getId(), action.getParameters().get("reason"));

            // 스케줄 예약 취소
            cancelScheduleReservations(order, "Payment compensation: " + event.getCompensationReason());

            // 굿즈 재고 예약 취소
            cancelStockReservationsForOrder(order);

            return true;
        } catch (Exception e) {
            log.error("예약 취소 액션 실행 실패 - orderId: {}, error: {}", order.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 주문 상태 업데이트 액션 실행
     */
    private boolean executeUpdateOrderStatusAction(Order order, PaymentCompensationRequestedEvent.CompensationAction action,
                                                  PaymentCompensationRequestedEvent event) {
        try {
            String newStatus = (String) action.getParameters().get("newStatus");
            String reason = (String) action.getParameters().get("reason");

            log.info("🔄 주문 상태 업데이트 액션 실행 - orderId: {}, newStatus: {}, reason: {}",
                    order.getId(), newStatus, reason);

            // 주문 상태 업데이트
            OrderStatus orderStatus = OrderStatus.valueOf(newStatus);
            OrderStatus previousStatus = order.getStatus();
            order.updateStatus(orderStatus);

            if ("CANCELLED".equals(newStatus)) {
                order.setCancellationReason(String.format("Payment compensation: %s", reason));
            }

            orderRepository.save(order);

            // 주문 상태 이력 저장
            OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                    .orderId(order.getId())
                    .fromStatus(previousStatus)
                    .toStatus(orderStatus)
                    .reason(String.format("Payment compensation: %s", reason))
                    .changedAt(LocalDateTime.now())
                    .build();
            orderStatusHistoryRepository.save(statusHistory);

            return true;
        } catch (Exception e) {
            log.error("주문 상태 업데이트 액션 실행 실패 - orderId: {}, error: {}", order.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 재고 해제 액션 실행
     */
    private boolean executeReleaseStockAction(Order order, PaymentCompensationRequestedEvent.CompensationAction action,
                                             PaymentCompensationRequestedEvent event) {
        try {
            log.info("🔄 재고 해제 액션 실행 - orderId: {}, reason: {}",
                    order.getId(), action.getParameters().get("reason"));

            // 굿즈 재고 해제
            cancelStockReservationsForOrder(order);

            return true;
        } catch (Exception e) {
            log.error("재고 해제 액션 실행 실패 - orderId: {}, error: {}", order.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Payment 서비스로 보상 완료 이벤트 발행
     */
    private void publishCompensationCompletedEvent(PaymentCompensationRequestedEvent event, String result,
                                                  List<String> completedActions, String notes) {
        try {
            // TODO: Payment 서비스로 보상 완료 이벤트 발행
            // PaymentCompensationCompletedEvent 생성 및 발행
            log.info("📨 Payment 서비스로 보상 완료 이벤트 발행 - compensationId: {}, result: {}",
                    event.getCompensationId(), result);

            // Redis Stream이나 Kafka를 통해 Payment 서비스로 이벤트 전송
            // redisEventPublisher.publishCompensationCompleted(event, result, completedActions, notes);

        } catch (Exception e) {
            log.error("보상 완료 이벤트 발행 실패 - compensationId: {}, error: {}",
                    event.getCompensationId(), e.getMessage(), e);
        }
    }

    /**
     * Payment 서비스로 보상 실패 이벤트 발행
     */
    private void publishCompensationFailedEvent(PaymentCompensationRequestedEvent event, String failureReason) {
        try {
            // TODO: Payment 서비스로 보상 실패 이벤트 발행
            // PaymentCompensationFailedEvent 생성 및 발행
            log.error("📨 Payment 서비스로 보상 실패 이벤트 발행 - compensationId: {}, reason: {}",
                    event.getCompensationId(), failureReason);

            // Redis Stream이나 Kafka를 통해 Payment 서비스로 이벤트 전송
            // redisEventPublisher.publishCompensationFailed(event, failureReason);

        } catch (Exception e) {
            log.error("보상 실패 이벤트 발행 실패 - compensationId: {}, error: {}",
                    event.getCompensationId(), e.getMessage(), e);
        }
    }

    // ================ 📅 스케줄 예약 이벤트 리스너들 ================



    // ================ 📅 스케줄 예약 헬퍼 메소드들 ================

    /**
     * 스케줄 예약 후 굿즈 재고 예약 요청
     */
    private void requestGoodsReservationAfterSchedule(Order order) {
        try {
            log.info("📅→🛍️ 스케줄 예약 완료 후 굿즈 재고 예약 시작 - orderId: {}", order.getId());

            // 주문 항목들 조회
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
            order.setOrderItems(orderItems);

            // 굿즈 항목만 필터링
            List<OrderItem> goodsItems = orderItems.stream()
                    .filter(item -> ItemType.GOODS.equals(item.getOrderItemType()))
                    .toList();

            if (goodsItems.isEmpty()) {
                log.warn("📅→🛍️ 굿즈 항목이 없습니다 - orderId: {}", order.getId());
                return;
            }

            // 통합 이벤트 제거: 개별 GoodsReservationRequestedEvent 발행으로 변경 예정
            // TODO: GoodsReservationRequestedEvent 발행 로직 추가
            log.info("📅→🛍️ [NOTICE] 굿즈 재고 예약 로직이 개별 이벤트로 변경되어야 합니다 - orderId: {}, 굿즈수: {}",
                    order.getId(), goodsItems.size());

        } catch (Exception e) {
            log.error("📅→🛍️❌ 스케줄 예약 후 굿즈 재고 예약 요청 실패 - orderId: {}", order.getId(), e);

            // 굿즈 재고 예약 요청 실패 시 스케줄 예약 취소
            handleGoodsReservationRequestFailure(order, e.getMessage());
        }
    }

    /**
     * 결제 대기 상태로 전환
     */
    private PaymentUrlResponse moveToPaymentPending(Order order, String reason) {
        try {
            order.updateStatus(OrderStatus.PAYMENT_PENDING);
            orderRepository.save(order);

            // 상태 이력 저장
            OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                    .orderId(order.getId())
                    .fromStatus(OrderStatus.RESERVED)
                    .toStatus(OrderStatus.PAYMENT_PENDING)
                    .reason(reason)
                    .changedAt(LocalDateTime.now())
                    .build();
            orderStatusHistoryRepository.save(statusHistory);

            // 결제 생성 요청 - 직접 이벤트 발행
            String paymentMethod = determinePaymentMethod(order);
            orderEventPublisher.publishPaymentCreateRequestedEvent(order, paymentMethod);

            // 예약 성공 이후 결제 URL 생성
            PaymentUrlResponse paymentUrl = orderCommandService.generatePaymentUrlAfterReservation(order);

            log.info("💳 결제 대기 상태 전환 완료 - orderId: {}, 사유: {}", order.getId(), reason);
            return paymentUrl;
        } catch (Exception e) {
            log.error("💳❌ 결제 대기 상태 전환 실패 - orderId: {}", order.getId(), e);
            throw new RuntimeException("결제 대기 상태 전환 실패", e);
        }
    }


    /**
     * 굿즈 재고 예약 요청 실패 처리 (스케줄 예약 취소)
     */
    private void handleGoodsReservationRequestFailure(Order order, String failureReason) {
        try {
            log.info("📅→🛍️❌ 굿즈 재고 예약 요청 실패로 스케줄 예약 취소 - orderId: {}, 사유: {}",
                    order.getId(), failureReason);

            // 통합 이벤트 제거: 개별 ScheduleReservationCancelRequestedEvent 발행으로 변경 예정
            // TODO: ScheduleReservationCancelRequestedEvent 발행 로직 추가
            log.info("스케줄 예약 취소 로직이 개별 이벤트로 변경되어야 합니다 - orderId: {}", order.getId());

            // 주문 취소 처리
            String cancellationReason = String.format("굿즈 재고 예약 요청 실패 - %s", failureReason);
            order.updateStatus(OrderStatus.CANCELLED);
            order.setCancellationReason(cancellationReason);
            orderRepository.save(order);

            log.info("📅→🛍️❌ 굿즈 재고 예약 요청 실패 처리 완료 - orderId: {}", order.getId());

        } catch (Exception e) {
            log.error("📅→🛍️❌ 굿즈 재고 예약 요청 실패 처리 중 오류 - orderId: {}", order.getId(), e);
        }
    }

    // 통합 이벤트 제거: createScheduleCancelItems 삭제됨

    /**
     * 결제 방법 결정 - Toss Payment로 고정
     */
    private String determinePaymentMethod(Order order) {
        // 🚀 Toss Payment로 고정 (카드, 계좌이체, 가상계좌, 휴대폰 결제 모두 지원)
        return "TOSS_PAYMENT";
    }

    // ================ 📋 헬퍼 메서드들 ================

    // 통합 이벤트 제거 완료 - 개별 이벤트로 전환 완료

    /**
     * OrderItem에서 제품명 생성 (헬퍼 메서드)
     */
    private String getProductName(OrderItem item) {
        if (item == null || item.getOrderItemType() == null) {
            return "알 수 없는 상품";
        }

        switch (item.getOrderItemType()) {
            case RESERVATION:
                return "팝업 예약";
            case GOODS:
                return "굿즈 상품";
            default:
                return "상품";
        }
    }
}
