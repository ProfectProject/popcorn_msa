package com.popcorn.order.event.publisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.popcorn.order.entity.Order;
import com.popcorn.order.event.order.OrderPaidEvent;
import com.popcorn.order.kafka.producer.PaymentRequestsProducer;
import com.popcorn.order.kafka.producer.StoreRequestsProducer;
import com.popcorn.order.event.order.OrderCancelledEvent;
import com.popcorn.order.event.order.OrderCompletedEvent;
import com.popcorn.order.repository.OrderItemRepository;
import com.popcorn.order.repository.OrderRepository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 이벤트 발행자
 *
 * [역할]
 * - 주문 관련 이벤트를 내부 및 외부 시스템으로 발행
 * - 내부: Spring Events (동일 서비스 내)
 * - 외부: RabbitMQ (다른 마이크로서비스)
 *
 * [이벤트 종류]
 * - OrderPaidEvent: 결제 완료 시 재고 차감 요청
 * - OrderCancelledEvent: 주문 취소 시 재고 복원 요청
 * - OrderCompletedEvent: 주문 완료 시 알림 발송
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRequestsProducer paymentRequestsProducer;
    private final StoreRequestsProducer storeRequestsProducer;

    /**
     * 주문 결제 완료 이벤트 발행
     *
     * @param order 결제 완료된 주문
     */
    public void publishOrderPaidEvent(Order order) {
        try {
            // 1. 이벤트 객체 생성
            OrderPaidEvent event = OrderPaidEvent.createEvent(order);

            log.info("주문 결제 완료 이벤트 발행 - orderId: {}, orderNo: {}, amount: {}",
                    event.getOrderId(), event.getOrderNo(), event.getTotalAmount());

            // 2. 내부 이벤트 발행 (동일 서비스 내 처리)
            applicationEventPublisher.publishEvent(event);

        } catch (Exception e) {
            log.error("주문 결제 완료 이벤트 발행 실패 - orderId: {}", order.getId(), e);
            // 이벤트 발행 실패는 주문 프로세스를 중단시키지 않음 (최종 일관성)
        }
    }


    /**
     * 굿즈 예약 취소 요청 이벤트 발행
     */
    public void publishGoodsReservationCancelRequestedEvent(
            Order order,
            java.util.UUID goodsId,
            Integer quantity,
            String reason
    ) {
        try {
            String eventId = java.util.UUID.randomUUID().toString();

            log.info("굿즈 예약 취소 요청 이벤트 발행 - orderId: {}, goodsId: {}",
                    order.getId(), goodsId);

            /* kafka 굿즈 예약 취소 요청 */
            storeRequestsProducer.publishGoodsReservationCancelRequested(order, goodsId, quantity, reason);

        } catch (Exception e) {
            log.error("굿즈 예약 취소 요청 이벤트 발행 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 결제 생성 요청 이벤트 발행
     *
     * @param order 주문 정보
     * @param paymentMethod 결제 수단
     */
    public void publishPaymentCreateRequestedEvent(Order order, String paymentMethod) {
        try {
            String eventId = java.util.UUID.randomUUID().toString();

            log.info("결제 생성 요청 이벤트 발행 - orderId: {}, paymentMethod: {}",
                    order.getId(), paymentMethod);

            String paymentKey = "order:" + order.getId();

            /* kafka payment-created request 발행 */
            paymentRequestsProducer.publishPaymentCreateRequested(order,paymentMethod,paymentKey);

        } catch (Exception e) {
            log.error("결제 생성 요청 이벤트 발행 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 결제 취소 요청 이벤트 발행
     */
    public void publishPaymentCancelRequestedEvent(Order order, String paymentId, String reason) {
        try {
            String eventId = java.util.UUID.randomUUID().toString();

            log.info("결제 취소 요청 이벤트 발행 - orderId: {}, paymentId: {}",
                    order.getId(), paymentId);

            /* kafka payment-cancel-requested 발행 */
            paymentRequestsProducer.publishPaymentCancelRequested(order, paymentId, reason);

        } catch (Exception e) {
            log.error("결제 취소 요청 이벤트 발행 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 주문 취소 이벤트 발행
     *
     * @param order 취소된 주문
     * @param reason 취소 사유
     */
    public void publishOrderCancelledEvent(Order order, String reason) {
        try {
            OrderCancelledEvent event = OrderCancelledEvent.builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .customerId(order.getCustomerId())
                    .popupId(order.getPopupId())
                    .reason(reason)
                    .cancelledAt(java.time.LocalDateTime.now())
                    .eventTime(java.time.LocalDateTime.now())
                    .build();

            log.info("주문 취소 이벤트 발행 - orderId: {}, reason: {}",
                    event.getOrderId(), event.getReason());

            // 내부 이벤트 발행
            applicationEventPublisher.publishEvent(event);

        } catch (Exception e) {
            log.error("주문 취소 이벤트 발행 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 재고 차감 실패 이벤트 발행 (통합 이벤트 사용)
     *
     * @param orderId 주문 ID
     * @param reason 실패 사유
     */
    public void publishStockDeductionFailedEvent(java.util.UUID orderId, String reason) {
        try {
            // 주문 정보 조회
            com.popcorn.order.entity.Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

            // 통합 이벤트 제거: 개별 StockDeductionFailedEvent 발행으로 변경 예정
            // TODO: StockDeductionFailedEvent 발행 로직 추가
            log.warn("재고 차감 실패 이벤트가 개별 이벤트로 변경되어야 합니다 - orderId: {}, reason: {}",
                    orderId, reason);

        } catch (Exception e) {
            log.error("[UNIFIED] 재고 차감 실패 이벤트 발행 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }


    /**
     * 주문 완료 이벤트 발행 (알림 발송용)
     *
     * @param order 완료된 주문
     */
    public void publishOrderCompletedEvent(Order order) {
        try {
            Integer itemCount = orderItemRepository.getTotalQuantityByOrderId(order.getId());
            int safeItemCount = itemCount != null ? itemCount : 0;
            OrderCompletedEvent event = OrderCompletedEvent.builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .customerId(order.getCustomerId())
                    .popupId(order.getPopupId())
                    .orderDate(order.getCreatedAt())
                    .finalAmount(order.getTotalAmount())
                    .itemCount(safeItemCount)
                    .completedAt(java.time.LocalDateTime.now())
                    .eventTime(java.time.LocalDateTime.now())
                    .build();

            log.info("주문 완료 이벤트 발행 - orderId: {}", event.getOrderId());

            // 내부 이벤트 발행
            applicationEventPublisher.publishEvent(event);

        } catch (Exception e) {
            log.error("주문 완료 이벤트 발행 실패 - orderId: {}", order.getId(), e);
        }
    }



    // ================ 스케줄 예약 관련 이벤트 발행 메소드들 ================


    /**
     * 스케줄 확정 요청 이벤트 발행 (결제 완료 후)
     * 예약 상태에서 확정 상태로 변경 요청
     */
    public void publishScheduleConfirmationRequestedEvent(
            Order order,
            java.util.UUID orderId,
            String orderNo,
            java.util.UUID popupId,
            java.util.List<com.popcorn.order.service.core.OrderCommandService.ScheduleConfirmationItem> confirmationItems) {

        try {
            String eventId = java.util.UUID.randomUUID().toString();

            log.info("[ORDER] 스케줄 확정 요청 이벤트 발행 시작 - orderId: {}, 스케줄 수: {}",
                    orderId, confirmationItems.size());

            /* kafka SCHEDULE_CONFIRMATION_REQUESTED 발행 */
            storeRequestsProducer.publishScheduleConfirmationRequested(order,confirmationItems);

            log.info("[ORDER] 스케줄 확정 요청 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, eventId);

        } catch (Exception e) {
            log.error("[ORDER] 스케줄 확정 요청 이벤트 발행 실패 - orderId: {}", orderId, e);
            // 스케줄 확정 요청 실패해도 주문 상태에는 영향 없음 (로그만 남김)
        }
    }

    /**
     * 스케줄 예약 취소 요청 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param popupId 팝업 ID
     * @param sessionOptionId 세션 옵션 ID
     * @param quantity 취소할 수량
     * @param reason 취소 사유
     */
    public void publishScheduleReservationCancelRequestedEvent(java.util.UUID orderId, String orderNo,
                                                              java.util.UUID popupId, java.util.UUID sessionOptionId,
                                                              Integer quantity, String reason) {
        try {
            String eventId = java.util.UUID.randomUUID().toString();

            log.info("[ORDER] 스케줄 예약 취소 요청 이벤트 발행 - orderId: {}, sessionOptionId: {}, reason: {}",
                    orderId, sessionOptionId, reason);

            // ScheduleReservationCancelRequestedEvent 생성 및 발행
            com.popcorn.order.event.schedule.ScheduleReservationCancelRequestedEvent event =
                    com.popcorn.order.event.schedule.ScheduleReservationCancelRequestedEvent.create(
                            orderId,
                            orderNo,
                            popupId,
                            sessionOptionId,
                            quantity,
                            reason
                    );

            // 내부 이벤트 발행
            applicationEventPublisher.publishEvent(event);

            log.info("[ORDER] 스케줄 예약 취소 요청 이벤트 발행 완료 - orderId: {}, eventId: {}, sessionOptionId: {}",
                    orderId, eventId, sessionOptionId);

        } catch (Exception e) {
            log.error("[ORDER] 스케줄 예약 취소 요청 이벤트 발행 실패 - orderId: {}, sessionOptionId: {}, error: {}",
                    orderId, sessionOptionId, e.getMessage(), e);
            // 예약 취소 요청 실패해도 주문 상태에는 영향 없음 (로그만 남김)
        }
    }

    // ================ 개별 이벤트 발행 메소드들 ================

    /**
     * 재고 차감 요청 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param popupId 팝업 ID
     * @param deductionItems 차감할 항목들
     */
    public void publishStockDeductionRequestedEvent(java.util.UUID orderId, String orderNo,
                                                   java.util.UUID popupId,
                                                   java.util.List<com.popcorn.order.event.stock.StockDeductionRequestedEvent.DeductionItem> deductionItems) {
        try {
            log.info("[ORDER] 재고 차감 요청 이벤트 발행 - orderId: {}, 항목수: {}", orderId, deductionItems.size());

            // StockDeductionRequestedEvent 생성 및 발행
            com.popcorn.order.event.stock.StockDeductionRequestedEvent event =
                    com.popcorn.order.event.stock.StockDeductionRequestedEvent.create(orderId, orderNo, popupId, deductionItems);

            // Redis Stream 제거: 내부 이벤트만 발행
            applicationEventPublisher.publishEvent(event);

            log.info("[ORDER] 재고 차감 요청 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, event.getEventId());

        } catch (Exception e) {
            log.error("[ORDER] 재고 차감 요청 이벤트 발행 실패 - orderId: {}, error: {}",
                    orderId, e.getMessage(), e);
        }
    }

    /**
     * 굿즈 예약 요청 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param popupId 팝업 ID
     * @param goodsId 굿즈 ID
     * @param quantity 수량
     */
    public void publishGoodsReservationRequestedEvent(java.util.UUID orderId, String orderNo,
                                                     java.util.UUID popupId, java.util.UUID goodsId, Integer quantity) {
        try {
            log.info("[ORDER] 굿즈 예약 요청 이벤트 발행 - orderId: {}, goodsId: {}, quantity: {}",
                    orderId, goodsId, quantity);

            // GoodsReservationRequestedEvent 생성 및 발행
            com.popcorn.order.event.goods.GoodsReservationRequestedEvent event =
                    com.popcorn.order.event.goods.GoodsReservationRequestedEvent.create(orderId, orderNo, popupId, goodsId, quantity);

            // Redis Stream 제거: 내부 이벤트만 발행
            applicationEventPublisher.publishEvent(event);

            log.info("[ORDER] 굿즈 예약 요청 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, event.getEventId());

        } catch (Exception e) {
            log.error("[ORDER] 굿즈 예약 요청 이벤트 발행 실패 - orderId: {}, error: {}",
                    orderId, e.getMessage(), e);
        }
    }

    /**
     * 스케줄 예약 요청 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param popupId 팝업 ID
     * @param reservedSessions 예약할 세션들
     */
    public void publishScheduleReservationRequestedEvent(java.util.UUID orderId, String orderNo,
                                                        java.util.UUID popupId,
                                                        java.util.List<com.popcorn.order.event.schedule.ScheduleReservationRequestedEvent.ReservedSession> reservedSessions) {
        try {
            log.info("[ORDER] 스케줄 예약 요청 이벤트 발행 - orderId: {}, 세션수: {}",
                    orderId, reservedSessions.size());

            // ScheduleReservationRequestedEvent 생성 및 발행
            com.popcorn.order.event.schedule.ScheduleReservationRequestedEvent event =
                    com.popcorn.order.event.schedule.ScheduleReservationRequestedEvent.create(orderId, orderNo, popupId, reservedSessions);

            // Redis Stream 제거: 내부 이벤트만 발행
            applicationEventPublisher.publishEvent(event);

            log.info("[ORDER] 스케줄 예약 요청 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, event.getEventId());

        } catch (Exception e) {
            log.error("[ORDER] 스케줄 예약 요청 이벤트 발행 실패 - orderId: {}, error: {}",
                    orderId, e.getMessage(), e);
        }
    }

}
