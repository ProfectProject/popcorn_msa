package com.popcorn.order.event.publisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.popcorn.order.entity.Order;
import com.popcorn.order.event.order.OrderPaidEvent;
import com.popcorn.order.event.order.OrderCancelledEvent;
import com.popcorn.order.event.order.OrderCompletedEvent;
import com.popcorn.order.event.store.StoreRequestEvent;
import com.popcorn.order.event.stock.StockDeductionResultEvent;
import com.popcorn.order.event.stock.StockDeductionRequestedEvent;
import com.popcorn.order.event.schedule.ScheduleReservationRequestedEvent;
import com.popcorn.order.event.schedule.ScheduleReservationCancelRequestedEvent;
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
    private final RedisEventPublisher redisEventPublisher;
    private final OrderRepository orderRepository;

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

            // 3. Redis 이벤트 발행 (Store 서비스 연동)
            redisEventPublisher.publishOrderPaidEvent(event);


        } catch (Exception e) {
            log.error("주문 결제 완료 이벤트 발행 실패 - orderId: {}", order.getId(), e);
            // 이벤트 발행 실패는 주문 프로세스를 중단시키지 않음 (최종 일관성)
        }
    }


    /**
     * 굿즈 예약 취소 요청 이벤트 발행
     */
    public void publishGoodsReservationCancelRequestedEvent(Order order, java.util.UUID goodsId, Integer quantity) {
        try {
            String eventId = java.util.UUID.randomUUID().toString();

            log.info("굿즈 예약 취소 요청 이벤트 발행 - orderId: {}, goodsId: {}",
                    order.getId(), goodsId);

            redisEventPublisher.publishGoodsReservationCancelRequestedEvent(
                    eventId,
                    order.getId(),
                    order.getPopupId(),
                    goodsId,
                    quantity
            );

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

            redisEventPublisher.publishPaymentCreateRequestedEvent(
                    eventId,
                    order.getId(),
                    order.getOrderNo(),
                    order.getTotalAmount(),
                    paymentMethod,
                    order.getCustomerId(),
                    paymentKey
            );

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

            redisEventPublisher.publishPaymentCancelRequestedEvent(
                    eventId,
                    order.getId(),
                    order.getOrderNo(),
                    paymentId,
                    reason,
                    order.getCustomerId()
            );

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

            // 통합된 재고 차감 실패 이벤트 발행
            StockDeductionResultEvent event = StockDeductionResultEvent.failure(
                    orderId,
                    order.getCustomerId(),
                    Optional.of(order.getOrderNo()),
                    order.getPopupId(),
                    reason,
                    List.of() // failedItems는 빈 리스트
            );

            log.warn("💥 [UNIFIED] 재고 차감 실패 이벤트 발행 - orderId: {}, orderNo: {}, reason: {}",
                    orderId, order.getOrderNo(), reason);

            // 내부 이벤트 발행
            applicationEventPublisher.publishEvent(event);

        } catch (Exception e) {
            log.error("❌ [UNIFIED] 재고 차감 실패 이벤트 발행 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }


    /**
     * 주문 완료 이벤트 발행 (알림 발송용)
     *
     * @param order 완료된 주문
     */
    public void publishOrderCompletedEvent(Order order) {
        try {
            OrderCompletedEvent event = OrderCompletedEvent.builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .customerId(order.getCustomerId())
                    .popupId(order.getPopupId())
                    .orderDate(order.getCreatedAt())
                    .finalAmount(order.getTotalAmount())
                    .itemCount(order.getTotalQuantity())
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



    /**
     * 재고 차감 요청 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param orderNo 주문 번호
     * @param popupId 팝업 ID
     * @param deductionItems 차감 항목들
     */
    public void publishStockDeductionRequestedEvent(java.util.UUID orderId, String orderNo,
                                                   java.util.UUID popupId,
                                                   java.util.List<StockDeductionRequestedEvent.StockDeductionItem> deductionItems) {
        try {
            log.info("📦 [ORDER] 재고 차감 요청 이벤트 발행 시작 - orderId: {}, 항목 수: {}", orderId, deductionItems.size());

            StockDeductionRequestedEvent event = StockDeductionRequestedEvent.create(
                    orderId, orderNo, popupId, deductionItems
            );

            // 1. 내부 이벤트 발행 (Spring Events)
            applicationEventPublisher.publishEvent(event);

            // 2. Redis Stream 이벤트 발행 (Store 서비스로 전송)
            redisEventPublisher.publishStockDeductionRequestedEvent(event);

            log.info("📦✅ [ORDER] 재고 차감 요청 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, event.getEventId());

        } catch (Exception e) {
            log.error("📦❌ [ORDER] 재고 차감 요청 이벤트 발행 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }

    // ================ 📅 스케줄 예약 관련 이벤트 발행 메소드들 ================

    /**
     * 스케줄 예약 요청 이벤트 발행
     *
     * @param order 주문 정보
     * @param reservationItems 예약 요청 항목들
     */
    public void publishScheduleReservationRequestedEvent(Order order,
                                                        java.util.List<ScheduleReservationRequestedEvent.ReservationItem> reservationItems) {
        try {
            ScheduleReservationRequestedEvent event = ScheduleReservationRequestedEvent.create(
                    order.getId(),
                    order.getOrderNo(),
                    order.getPopupId(),
                    reservationItems
            );

            log.info("📅 [ORDER] 스케줄 예약 요청 이벤트 발행 - orderId: {}, 세션수: {}",
                    event.getOrderId(), event.getReservationItemCount());

            // 내부 이벤트 발행
            applicationEventPublisher.publishEvent(event);

            // Redis 이벤트 발행 (Store 서비스로 전송)
            redisEventPublisher.publishScheduleReservationRequestedEvent(event);

            log.info("📅✅ [ORDER] 스케줄 예약 요청 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

        } catch (Exception e) {
            log.error("📅❌ [ORDER] 스케줄 예약 요청 이벤트 발행 실패 - orderId: {}", order.getId(), e);
        }
    }

    /**
     * 스케줄 예약 취소 요청 이벤트 발행
     */
    public void publishScheduleReservationCancelRequestedEvent(Order order,
                                                              String reservationToken,
                                                              java.util.List<ScheduleReservationCancelRequestedEvent.CancelItem> cancelItems,
                                                              String cancelReason) {
        try {
            ScheduleReservationCancelRequestedEvent event = ScheduleReservationCancelRequestedEvent.create(
                    order.getId(),
                    order.getOrderNo(),
                    order.getPopupId(),
                    reservationToken,
                    cancelItems,
                    cancelReason
            );

            log.info("📅 [ORDER] 스케줄 예약 취소 요청 이벤트 발행 - orderId: {}, 세션수: {}, 사유: {}",
                    event.getOrderId(), event.getCancelItemCount(), event.getCancelReason());

            // 내부 이벤트 발행
            applicationEventPublisher.publishEvent(event);

            // Redis 이벤트 발행 (Store 서비스로 전송)
            redisEventPublisher.publishScheduleReservationCancelRequestedEvent(event);

            log.info("📅✅ [ORDER] 스케줄 예약 취소 요청 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

        } catch (Exception e) {
            log.error("📅❌ [ORDER] 스케줄 예약 취소 요청 이벤트 발행 실패 - orderId: {}", order.getId(), e);
        }
    }


    /**
     * 스케줄 확정 요청 이벤트 발행 (결제 완료 후)
     * 예약 상태에서 확정 상태로 변경 요청
     */
    public void publishScheduleConfirmationRequestedEvent(
            java.util.UUID orderId,
            String orderNo,
            java.util.UUID popupId,
            java.util.List<com.popcorn.order.service.core.OrderCommandService.ScheduleConfirmationItem> confirmationItems) {

        try {
            String eventId = java.util.UUID.randomUUID().toString();

            log.info("📅🔒 [ORDER] 스케줄 확정 요청 이벤트 발행 시작 - orderId: {}, 스케줄 수: {}",
                    orderId, confirmationItems.size());

            // Redis Stream을 통해 Store 서비스로 스케줄 확정 요청 전송
            redisEventPublisher.publishScheduleConfirmationRequestedEvent(
                    eventId,
                    orderId,
                    orderNo,
                    popupId,
                    confirmationItems
            );

            log.info("✅ [ORDER] 스케줄 확정 요청 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, eventId);

        } catch (Exception e) {
            log.error("❌ [ORDER] 스케줄 확정 요청 이벤트 발행 실패 - orderId: {}", orderId, e);
            // 스케줄 확정 요청 실패해도 주문 상태에는 영향 없음 (로그만 남김)
        }
    }

    /**
     * 🏪 통합 Store 요청 이벤트 발행
     *
     * @param storeRequestEvent Store 서비스 요청 이벤트
     */
    public void publishStoreRequestEvent(StoreRequestEvent storeRequestEvent) {
        try {
            log.info("🏪 [UNIFIED-STORE] Store 요청 이벤트 발행 시작 - orderId: {}, requestType: {}, items: {}",
                    storeRequestEvent.getOrderId(), storeRequestEvent.getRequestType(),
                    storeRequestEvent.getRequestItems().size());

            // 내부 이벤트 발행 (Spring Events)
            applicationEventPublisher.publishEvent(storeRequestEvent);

            // Redis 이벤트 발행 (Store 서비스로 전송)
            redisEventPublisher.publishStoreRequestEvent(storeRequestEvent);

            log.info("✅ [UNIFIED-STORE] Store 요청 이벤트 발행 완료 - orderId: {}, requestType: {}, eventId: {}",
                    storeRequestEvent.getOrderId(), storeRequestEvent.getRequestType(), storeRequestEvent.getEventId());

        } catch (Exception e) {
            log.error("❌ [UNIFIED-STORE] Store 요청 이벤트 발행 실패 - orderId: {}, requestType: {}, error: {}",
                    storeRequestEvent.getOrderId(), storeRequestEvent.getRequestType(), e.getMessage(), e);
        }
    }
}
