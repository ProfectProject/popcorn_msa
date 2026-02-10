package com.popcorn.order.service.core;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.popcorn.common.annotation.Idempotent;
import com.popcorn.order.util.PerformanceLogger;

import com.popcorn.order.dto.command.CreateOrderCommand;
import com.popcorn.order.dto.response.OrderCreateResponse;
import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderItem;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.entity.OrderStatusHistory;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.event.order.OrderCreatedEvent;
import com.popcorn.order.event.order.OrderStatusChangedEvent;
//import com.popcorn.order.event.TestProducer;
import com.popcorn.order.event.order.OrderCancelledEvent;
import com.popcorn.order.event.publisher.OrderEventPublisher;
import com.popcorn.order.kafka.producer.OrderEventProducer;
import com.popcorn.order.kafka.producer.StoreRequestsProducer;
import com.popcorn.order.repository.OrderRepository;
import com.popcorn.order.repository.OrderItemRepository;
import com.popcorn.order.repository.OrderStatusHistoryRepository;
import com.popcorn.order.dto.payment.CreatePaymentRequest;
import com.popcorn.order.dto.payment.CreatePaymentResponse;
import com.popcorn.order.dto.payment.PaymentUrlResponse;
import com.popcorn.common.cache.IdempotencyService;
import com.popcorn.order.service.cache.OrderCacheService;
import com.popcorn.order.service.cache.PaymentCacheService;
import com.popcorn.order.service.util.OrderReservationAwaiter;
import com.popcorn.order.service.monitor.OrderPerformanceMonitor;
import com.popcorn.order.service.monitor.PaymentPerformanceMonitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.popcorn.order.util.PaymentTokenUtil;

/**
 * 주문 명령(Command) 처리 서비스
 *
 * CQRS 패턴의 Command 쪽 담당 - 데이터 변경 작업만 처리
 * 주문 생성, 상태 변경, 취소 등 데이터를 바꾸는 모든 작업을 여기서 담당
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCommandService {

    private final OrderDomainService orderDomainService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderEventPublisher orderEventPublisher;
    private final PaymentTokenUtil paymentTokenUtil;
    private final OrderCacheService orderCacheService;
    private final TransactionTemplate transactionTemplate;
    private final OrderReservationAwaiter orderReservationAwaiter;
    private final IdempotencyService idempotencyService;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final OrderEventProducer orderEventProducer;
    private final StoreRequestsProducer storeRequestsProducer;



    // 🚀 극한 성능 최적화 서비스들 (목표: 1초 미만)
    private final OrderPerformanceMonitor performanceMonitor;

    // 💳 결제 극한 성능 최적화 서비스들 (목표: 800ms 미만)
    private final PaymentCacheService paymentCacheService;
    private final PaymentPerformanceMonitor paymentPerformanceMonitor;

    @org.springframework.beans.factory.annotation.Value("${frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @org.springframework.beans.factory.annotation.Value("${order.reservation.wait-timeout-ms:0}")
    private long reservationWaitTimeoutMs;

    @org.springframework.beans.factory.annotation.Value("${order.reservation.fail-on-timeout:false}")
    private boolean reservationFailOnTimeout;


    // 성능 통계 카운터
    private final AtomicLong totalOrders = new AtomicLong(0);

    /**
     * 새로운 주문 생성하기 (멱등성 처리)
     * keyExpression: 사용자ID + 팝업ID로 고유 키 생성
     * ttlSeconds: 5분간 멱등성 보장 (실수로 빠르게 연속 클릭해도 안전)
     */
    @Idempotent(
        keyExpression = "#command.userId + ':' + #command.popupId",
        keyPrefix = "order:create",
        ttlSeconds = 60,   // 1분 (중복 방지 최적화)
        responseType = OrderCreateResponse.class
    )
    public OrderCreateResponse createOrder(CreateOrderCommand command) {
        return PerformanceLogger.logDetailedTime(
            "주문 생성",
            String.format("사용자: %s, 팝업: %s", command.getUserId(), command.getPopupId()),
            () -> {
                log.info("주문 생성 시작 - 사용자: {}, 팝업: {}", command.getUserId(), command.getPopupId());

                try {
                    return executeOrderCreation(command);
                } catch (com.popcorn.order.exception.OrderReservationFailedException
                         | com.popcorn.order.exception.OrderReservationTimeoutException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("주문 생성 실패 - 사용자: {}, 에러: {}", command.getUserId(), e.getMessage(), e);
                    throw new RuntimeException("주문 생성 중 문제가 발생했어요: " + e.getMessage(), e);
                }
            }
        );
    }

    /**
     * 실제 주문 생성 로직 실행 (극한 성능 최적화 버전 v2)
     *
     * 🚀 극한 성능 개선 사항:
     * - 재고 예약 타임아웃: 5000ms → 800ms (84% 단축)
     * - Redis 타임아웃: 2000ms → 300ms (85% 단축)
     * - 성능 모니터링 추가
     *
     * 🎯 목표 성능: < 600ms
     */
    private OrderCreateResponse executeOrderCreation(CreateOrderCommand command) {
        // 성능 모니터링 시작
        OrderPerformanceMonitor.PerformanceTracker tracker =
            performanceMonitor.startOrderTracking("ORDER-" + System.currentTimeMillis());

        long startTime = System.currentTimeMillis();

        OrderCreationResult result = createOrderInTransactionOptimized(command);
        if (result == null || result.getOrder() == null) {
            throw new RuntimeException("주문 생성에 실패했습니다.");
        }

        Order savedOrder = result.getOrder();
        boolean hasGoodsItems = result.hasGoodsItems();
        boolean hasReservationItems = result.hasReservationItems();

        /*!!!!kafka : 주문 생성 이벤트 발행 !!!*/
        //testProducer.sendMessage("eventType: order_created");
        // 
        /*orderEventProducer.publishOrderCreated(
                    savedOrder.getId(),
                    savedOrder.getOrderNo(),
                    savedOrder.getCustomerId(),
                    savedOrder.getOrderType().name(),
                    savedOrder.getPopupId(),
                    savedOrder.getTotalAmount(),
                    null);*/
        
        log.info("⚡ 주문 DB 생성 완료 - 주문번호: {}, 처리시간: {}ms",
                savedOrder.getOrderNo(), System.currentTimeMillis() - startTime);

        // 예약/재고 응답 대기 준비
        orderReservationAwaiter.register(savedOrder.getId());

        // 📅🛍️ 스케줄 예약 및 굿즈 재고 예약 처리 (트랜잭션 커밋 후 바로 발행)
        if (hasReservationItems && hasGoodsItems) {
            log.info("복합 주문 처리 시작 - 주문번호: {} (스케줄 + 굿즈)", savedOrder.getOrderNo());
            reserveScheduleForOrder(savedOrder);
            log.info("복합 주문 - 스케줄 예약 요청 완료, 굿즈 재고는 스케줄 성공 후 진행 - 주문번호: {}",
                    savedOrder.getOrderNo());
        } else if (hasReservationItems) {
            log.info("스케줄 전용 주문 처리 시작 - 주문번호: {}", savedOrder.getOrderNo());
            reserveScheduleForOrder(savedOrder);
            log.info("스케줄 예약 요청 완료 - 주문번호: {} (응답 이벤트 대기)", savedOrder.getOrderNo());
        } else if (hasGoodsItems) {
            log.info("굿즈 전용 주문 처리 시작 - 주문번호: {}", savedOrder.getOrderNo());
            reserveStockForOrder(savedOrder);
            log.info("굿즈 재고 예약 요청 완료 - 주문번호: {} (응답 이벤트 대기)", savedOrder.getOrderNo());
        } else {
            log.error("🚨 비즈니스 룰 위반: 예약도 굿즈도 없는 주문! - 주문번호: {}", savedOrder.getOrderNo());
            throw new IllegalStateException("예약 또는 굿즈 중 최소 하나는 필요합니다.");
        }

        // 🚀 즉시 응답 처리 (성능 최적화)
        tracker.markEventStart();
        log.info("🚀 [INSTANT-RESPONSE] 예약은 백그라운드에서 처리, 즉시 응답 - orderId: {}", savedOrder.getId());

        // 타임아웃 처리 제거 (즉시 응답이므로 불필요)
        tracker.markEventEnd();

        // 즉시 응답: 예약과 동시에 결제 URL 생성
        Order latestOrder = orderRepository.findById(savedOrder.getId()).orElse(savedOrder);
        List<OrderItem> latestOrderItems = orderItemRepository.findByOrderId(latestOrder.getId());
        latestOrder.setOrderItems(latestOrderItems);
        //latestOrder.setOrderItems(orderItemRepository.findByOrderId(latestOrder.getId()););
        String paymentMethod = determinePaymentMethod(latestOrder);

        // 즉시 결제 URL 생성
        com.popcorn.order.dto.payment.PaymentUrlResponse paymentUrlResponse = generatePaymentUrlAfterReservation(latestOrder);

        String paymentStatus = "PAYMENT_PENDING";
        String paymentMessage = paymentUrlResponse != null ? "결제 링크가 발급되었습니다." : "결제 링크 생성 중입니다.";

        OrderCreateResponse response = OrderCreateResponse.fromOrderWithPayment(
                latestOrder,
                null,
                paymentStatus,
                paymentMethod,
                paymentUrlResponse != null ? paymentUrlResponse.getPaymentUrl() : null,
                paymentUrlResponse != null ? paymentUrlResponse.getExpiresAt() : null,
                paymentMessage
        );

        long totalElapsed = System.currentTimeMillis() - startTime;
        boolean isUltraFast = totalElapsed < 1000;
        String performanceEmoji = isUltraFast ? "🚀" : totalElapsed < 2000 ? "⚡" : "🐌";

        boolean isNewUltraFast = totalElapsed < 600; // 새로운 목표: 600ms
        String newPerformanceEmoji = isNewUltraFast ? "🚀" : totalElapsed < 1000 ? "⚡" : "🐌";

        log.info("{} 주문 생성 완료 (극한 성능 최적화 v2) - 주문번호: {}, 상태: {}, 총 처리시간: {}ms (목표: <600ms) {}",
                newPerformanceEmoji, response.getOrderNo(), response.getStatus(), totalElapsed,
                isNewUltraFast ? "✅ ULTRA-FAST v2 달성!" : totalElapsed < 1000 ? "⚠️ 목표 미달성" : "❌ 성능 문제");

        // 주문 생성 이벤트 발행 (중복 제거 완료)
        eventPublisher.publishEvent(new OrderCreatedEvent(latestOrder, null));
        
        /*
        * kafka 이벤트 발행
        */
        orderEventProducer.publishOrderCreated(latestOrder,latestOrderItems,hasGoodsItems,hasReservationItems);

        orderCacheService.evictMyOrdersCache(latestOrder.getCustomerId());

        // 성능 모니터링 완료
        tracker.finish();

        // 성능 통계 주기적 로깅
        if (totalOrders.incrementAndGet() % 10 == 0) {
            performanceMonitor.logPerformanceStats();
            paymentPerformanceMonitor.logPerformanceStats(); // 결제 성능 통계도 함께 출력
        }

        return response;
    }

    private void invalidateCreateOrderIdempotencyKey(CreateOrderCommand command, String reason) {
        try {
            String key = "order:create:" + command.getUserId() + ":" + command.getPopupId();
            idempotencyService.invalidateKey(key);
            log.info("멱등성 키 무효화 완료 - key: {}, reason: {}", key, reason);
        } catch (Exception e) {
            log.warn("멱등성 키 무효화 실패 - userId: {}, popupId: {}, error: {}",
                    command.getUserId(), command.getPopupId(), e.getMessage());
        }
    }

    @Transactional
    protected OrderCreationResult createOrderInTransaction(CreateOrderCommand command) {
        return createOrderInTransactionSequential(command);
    }

    /**
     * 🚀 최적화된 주문 생성 (목표: 300ms 이내)
     */
    @Transactional
    protected OrderCreationResult createOrderInTransactionOptimized(CreateOrderCommand command) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 [OPTIMIZED] 주문 생성 시작 - userId: {}", command.getUserId());

        // 1단계: 주문 엔티티 생성 (DB 조회 최소화)
        List<OrderItem> orderItems = convertToOrderItems(command.getItems());
        ItemType orderType = ItemType.valueOf(command.getOrderType());

        long step1Time = System.currentTimeMillis();
        Order order = orderDomainService.createOrder(
                command.getUserId(),
                command.getPopupId(),
                orderType,
                orderItems
        );
        log.debug("⚡ [STEP1] 주문 엔티티 생성 완료: {}ms", System.currentTimeMillis() - step1Time);

        // 2단계: 주문 저장 (최적화된 저장)
        long step2Time = System.currentTimeMillis();
        Order savedOrder = orderRepository.save(order);
        log.debug("⚡ [STEP2] 주문 저장 완료: {}ms", System.currentTimeMillis() - step2Time);

        // 3단계: 주문 아이템 저장 (배치 처리)
        long step3Time = System.currentTimeMillis();
        savedOrder.getOrderItems().forEach(item -> item.setOrderId(savedOrder.getId()));
        orderItemRepository.saveAll(savedOrder.getOrderItems());
        log.debug("⚡ [STEP3] 주문 아이템 저장 완료: {}ms", System.currentTimeMillis() - step3Time);

        // 4단계: 상태 히스토리 저장 (단순화)
        long step4Time = System.currentTimeMillis();
        OrderStatusHistory createdHistory = OrderStatusHistory.builder()
                .orderId(savedOrder.getId())
                .fromStatus(null)
                .toStatus(savedOrder.getStatus())
                .reason("주문 생성")
                .changedAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(createdHistory);
        log.debug("⚡ [STEP4] 상태 히스토리 저장 완료: {}ms", System.currentTimeMillis() - step4Time);

        // 5단계: 결과 구성
        boolean hasGoodsItems = savedOrder.getOrderItems().stream()
                .anyMatch(item -> ItemType.GOODS.equals(item.getOrderItemType()));
        boolean hasReservationItems = savedOrder.getOrderItems().stream()
                .anyMatch(item -> ItemType.RESERVATION.equals(item.getOrderItemType()));

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🚀 [OPTIMIZED] 주문 생성 완료 - userId: {}, 총 시간: {}ms", command.getUserId(), totalTime);

        return new OrderCreationResult(savedOrder, hasReservationItems, hasGoodsItems);
    }

    /**
     * 주문 생성 - 기존 순차 처리 버전 (fallback)
     */
    @Transactional
    protected OrderCreationResult createOrderInTransactionSequential(CreateOrderCommand command) {
        log.info("🔄 [SEQUENTIAL] 주문 생성 시작 - userId: {}", command.getUserId());

        List<OrderItem> orderItems = convertToOrderItems(command.getItems());
        ItemType orderType = ItemType.valueOf(command.getOrderType());

        Order order = orderDomainService.createOrder(
                command.getUserId(),
                command.getPopupId(),
                orderType,
                orderItems
        );

        Order savedOrder = orderRepository.save(order);

        savedOrder.getOrderItems().forEach(item -> item.setOrderId(savedOrder.getId()));
        orderItemRepository.saveAll(savedOrder.getOrderItems());

        OrderStatusHistory createdHistory = OrderStatusHistory.builder()
                .orderId(savedOrder.getId())
                .fromStatus(null)
                .toStatus(savedOrder.getStatus())
                .reason("주문 생성")
                .changedAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(createdHistory);

        boolean hasGoodsItems = savedOrder.getOrderItems().stream()
                .anyMatch(item -> ItemType.GOODS.equals(item.getOrderItemType()));

        boolean hasReservationItems = savedOrder.getOrderItems().stream()
                .anyMatch(item -> ItemType.RESERVATION.equals(item.getOrderItemType()));

        return new OrderCreationResult(savedOrder, hasReservationItems, hasGoodsItems);
    }

    private void cancelOrderSafely(UUID orderId, String reason) {
        try {
            updateOrderStatus(orderId, OrderStatus.CANCELLED.name(), reason);
        } catch (Exception e) {
            log.warn("주문 취소 처리 실패 - orderId: {}, reason: {}", orderId, reason, e);
        }
    }

    private static class OrderCreationResult {
        private final Order order;
        private final boolean hasReservationItems;
        private final boolean hasGoodsItems;

        private OrderCreationResult(Order order, boolean hasReservationItems, boolean hasGoodsItems) {
            this.order = order;
            this.hasReservationItems = hasReservationItems;
            this.hasGoodsItems = hasGoodsItems;
        }

        public Order getOrder() {
            return order;
        }

        public boolean hasReservationItems() {
            return hasReservationItems;
        }

        public boolean hasGoodsItems() {
            return hasGoodsItems;
        }
    }

    /**
     * 주문 상태 변경하기 (멱등성 처리)
     *
     * 같은 주문을 같은 상태로 여러 번 변경해도 한 번만 처리됩니다.
     */
    @Transactional
    @Idempotent(
        keyExpression = "#orderId + ':' + #status",
        keyPrefix = "order:status",
        ttlSeconds = 60,  // 1분 (중복 방지 최적화)
        responseType = Order.class
    )
    public Order updateOrderStatus(UUID orderId, String status, String reason) {
        log.info("주문 상태 변경 - 주문ID: {}, 새상태: {}, 이유: {}", orderId, status, reason);

        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        // 2. 이미 취소된 주문은 변경 불가
        if (currentStatus == OrderStatus.CANCELLED) {
            throw new RuntimeException("이미 취소된 주문은 상태를 변경할 수 없어요");
        }

        // 3. 새 상태 검증
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 주문 상태예요: " + status);
        }

        // 동일 상태 요청은 멱등 처리 (no-op)
        if (currentStatus == newStatus) {
            log.info("주문 상태 변경 멱등 처리 - 주문ID: {}, 상태: {}", orderId, newStatus);
            return order;
        }

        // 4. 도메인 규칙 검증
        if (!orderDomainService.canChangeStatus(currentStatus, newStatus)) {
            throw new RuntimeException(String.format("상태 변경이 불가능해요: %s → %s", currentStatus, newStatus));
        }

        // 5. 상태 변경 및 저장
        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);
        
        boolean hasGoodsItems = savedOrder.getOrderItems().stream()
            .anyMatch(item -> ItemType.GOODS.equals(item.getOrderItemType()));
        boolean hasReservationItems = savedOrder.getOrderItems().stream()
            .anyMatch(item -> ItemType.RESERVATION.equals(item.getOrderItemType()));


        // 6. 상태 변경 이력 저장
        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .orderId(savedOrder.getId())
                .fromStatus(currentStatus)
                .toStatus(newStatus)
                .reason(reason)
                .changedAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(statusHistory);

        /*
        *  kafka order_status_update 발행
        */
        orderEventProducer.publishOrderStatusUpdate(savedOrder,currentStatus,newStatus,hasGoodsItems,hasReservationItems);

        // 7. 이벤트 발행
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                currentStatus,
                newStatus,
                reason,
                "SYSTEM"
        ));

        if (newStatus == OrderStatus.PAID) {
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(savedOrder.getId());
            savedOrder.setOrderItems(orderItems);
            orderEventPublisher.publishOrderPaidEvent(savedOrder);

            /*
            * kafka : order-paid 발행
            * boolean hasGoodsItems = savedOrder.getOrderItems().stream()
             */
            orderEventProducer.publishOrderPaid(savedOrder,hasGoodsItems,hasReservationItems);

        }

        // 8. 특별한 상태 변경시 추가 이벤트
        if (newStatus == OrderStatus.CANCELLED) {
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(savedOrder.getId());
            eventPublisher.publishEvent(new OrderCancelledEvent(
                    savedOrder.getId(),
                    savedOrder.getCustomerId(),
                    currentStatus,
                    reason,
                    "SYSTEM",
                    null // 환불 금액은 별도 계산 필요
            ));

            /*
            * kafka order_cancelled 발행
            */
            orderEventProducer.publishOrderCancelled(savedOrder,orderItems,hasGoodsItems,hasReservationItems,LocalDateTime.now());
        }

        log.info("주문 상태 변경 완료 - 주문ID: {}, {} → {}", orderId, currentStatus, newStatus);
        return savedOrder;
    }

    /**
     * 주문 취소하기 (멱등성 처리)
     *
     * [초보자 가이드]
     * 같은 주문을 같은 이유로 여러 번 취소해도 한 번만 처리됩니다.
     */
    @Transactional
    @Idempotent(
        keyExpression = "#orderId + ':cancel:' + #reason",
        keyPrefix = "order:cancel",
        ttlSeconds = 60,  // 1분 (중복 방지 최적화)
        responseType = Order.class
    )
    public Order cancelOrder(UUID orderId, String reason) {
        log.info("주문 취소 요청 - 주문ID: {}, 이유: {}", orderId, reason);

        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

        // 2. 취소 가능 여부 확인
        if (!orderDomainService.canCancelOrder(order)) {
            throw new RuntimeException("취소할 수 없는 주문이에요. 시간이 지났거나 이미 처리된 상태예요.");
        }

        // 3. 취소 상태로 변경
        return updateOrderStatus(orderId, OrderStatus.CANCELLED.name(), reason);
    }

    // ================ 헬퍼 메서드들 ================

    /**
     * 명령 객체를 주문 항목 엔티티로 변환
     */
    private List<OrderItem> convertToOrderItems(List<CreateOrderCommand.OrderItemCommand> itemCommands) {
        return itemCommands.stream()
                .map(this::convertToOrderItem)
                .toList();
    }

    /**
     * 개별 주문 항목 변환
     */
    private OrderItem convertToOrderItem(CreateOrderCommand.OrderItemCommand itemCommand) {
        Integer unitPrice = itemCommand.getUnitPrice() != null ? itemCommand.getUnitPrice() : 0;
        Integer lineAmount = unitPrice * itemCommand.getQty();

        return OrderItem.builder()
                .orderItemType(itemCommand.getOrderItemType())
                .qty(itemCommand.getQty())
                .unitPrice(unitPrice)
                .lineAmount(lineAmount)
                .sessionOptionId(itemCommand.getSessionId())
                .goodsId(itemCommand.getGoodsId())
                .build();
    }

    /**
     * 결제 방법 결정 - Toss Payment로 고정
     */
    private String determinePaymentMethod(Order order) {
        // 🚀 Toss Payment로 고정 (카드, 계좌이체, 가상계좌, 휴대폰 결제 모두 지원)
        return "TOSS_PAYMENT";
    }

    /**
     * 고객에게 결제 URL 알림 발송
     * 결제 대기 상태일 때 고객이 결제를 완료할 수 있도록 안내
     */
    private void sendPaymentUrlNotificationToCustomer(CreatePaymentResponse paymentResponse) {
        try {
            log.info("고객 결제 URL 알림 발송: 주문ID={}, 결제ID={}, URL={}",
                    paymentResponse.getOrderId(),
                    paymentResponse.getPaymentId(),
                    paymentResponse.getPaymentUrl());

            // TODO: 실제 고객 알림 서비스 연동
            // - 푸시 알림, SMS, 카카오톡 등을 통한 결제 링크 전송
            // - NotificationClient.sendPaymentUrl(customerId, paymentUrl, orderNo)

            // 현재는 로깅만 수행
        } catch (Exception e) {
            log.error("고객 결제 URL 알림 발송 실패: 주문ID={}",
                    paymentResponse.getOrderId(), e);
        }
    }

    /**
     * 관리자에게 결제 시스템 장애 알림 발송
     * 결제 시스템 장애로 주문이 수동 처리가 필요한 경우 관리자에게 즉시 알림
     */
    private void sendPaymentErrorNotificationToAdmin(UUID orderId, Throwable error) {
        try {
            log.warn("관리자 결제 장애 알림 발송: 주문ID={}, 에러={}",
                    orderId, error.getMessage());

            // TODO: 실제 관리자 알림 시스템 연동
            // - Slack, Teams, Email 등을 통한 장애 알림
            // - AdminNotificationClient.sendPaymentError(orderId, error, urgency=HIGH)

            // 현재는 경고 로깅만 수행 (모니터링 시스템에서 수집 가능)
        } catch (Exception e) {
            log.error("관리자 결제 장애 알림 발송 실패: 주문ID={}", orderId, e);
        }
    }

    /**
     * 극한 성능 최적화 결제 URL 생성 (🚀 목표: 800ms → 200ms, 75% 단축)
     *
     * [극한 최적화 결제 플로우]
     * - 1단계: 캐시에서 결제 정보 확인 (5ms)
     * - 2단계: 토큰 생성 및 캐싱 (50ms)
     * - 3단계: URL 생성 및 캐싱 (10ms)
     * - 성능 모니터링 및 통계 수집
     */
    private CreatePaymentResponse requestPaymentUrlWithCircuitBreaker(Order order, String paymentMethod) {
        CircuitBreaker paymentCircuitBreaker = circuitBreakerFactory.create("payment-service");

        return paymentCircuitBreaker.run(
            () -> requestPaymentUrlInternal(order, paymentMethod),
            throwable -> buildPaymentErrorResponse(order, paymentMethod, throwable)
        );
    }

    private CreatePaymentResponse requestPaymentUrlInternal(Order order, String paymentMethod) {
        // 🚀 결제 성능 모니터링 시작
        PaymentPerformanceMonitor.PaymentPerformanceTracker paymentTracker =
            paymentPerformanceMonitor.startPaymentTracking(order.getOrderNo());

        try {
            log.info("🚀 극한 최적화 결제 생성 시작 - 주문번호: {}, 금액: {}원, 결제방법: {}",
                    order.getOrderNo(), order.getTotalAmount(), paymentMethod);

            // 1단계: 캐시에서 기존 결제 토큰 확인 (목표: 5ms)
            paymentTracker.markTokenGenerationStart();
            String cachedToken = paymentCacheService.getCachedPaymentToken(order.getId());
            boolean tokenFromCache = cachedToken != null;

            String paymentToken;
            if (tokenFromCache) {
                paymentToken = cachedToken;
                log.debug("⚡ 결제 토큰 캐시 히트 - 주문번호: {}", order.getOrderNo());
            } else {
                // 토큰 생성 (목표: 50ms)
            Long customerId = order.getCustomerId();
            String customerKey = customerId != null
                    ? "customer_" + customerId.toString().replace("-", "")
                    : "guest";
            String orderName = generateOrderName(order);
            String orderNo = order.getOrderNo() != null ? order.getOrderNo() : order.getId().toString();
            Integer totalAmount = order.getTotalAmount();
            int amount = totalAmount != null ? totalAmount : 0;

            paymentToken = paymentTokenUtil.generatePaymentToken(
                    order.getId(),
                    orderNo,
                    amount,
                    orderName,
                    customerKey,
                    paymentMethod
            );

                // 토큰 캐싱 (30분 TTL)
                paymentCacheService.cachePaymentToken(order.getId(), paymentToken);
                log.debug("💾 새 결제 토큰 생성 및 캐시 저장 - 주문번호: {}", order.getOrderNo());
            }
            paymentTracker.markTokenGenerationEnd(tokenFromCache);

            // 2단계: URL 생성 및 캐싱 (목표: 10ms)
            paymentTracker.markUrlGenerationStart();
            String paymentUrl = String.format("%s/auto-payment?token=%s", frontendBaseUrl, paymentToken);
            paymentTracker.markUrlGenerationEnd(false); // URL은 항상 새로 생성

            // 3단계: 응답 생성 및 캐싱 (목표: 5ms)
            CreatePaymentResponse finalResponse = CreatePaymentResponse.builder()
                    .paymentId(null) // 비동기 요청이므로 즉시 결제 ID 미확정
                    .orderId(order.getId())
                    .amount(order.getTotalAmount())
                    .status("READY") // 결제 준비 상태
                    .paymentMethod(paymentMethod)
                    .paymentUrl(paymentUrl)
                    .expiresAt(LocalDateTime.now().plusMinutes(30))
                    .createdAt(LocalDateTime.now())
                    .build();

            // 결제 상태 캐싱 (5분 TTL)
            paymentCacheService.cachePaymentStatus(order.getId(), "READY");

            log.info("🚀 극한 최적화 결제 생성 완료 - 주문번호: {}, 토큰캐시: {}, 토큰길이: {}자",
                    order.getOrderNo(), tokenFromCache ? "HIT" : "MISS", paymentToken.length());

            return finalResponse;

        } catch (Exception e) {
            log.error("💥 극한 최적화 결제 생성 실패 - 주문번호: {}, 에러: {}",
                    order.getOrderNo(), e.getMessage(), e);
            throw e;
        } finally {
            // 성능 모니터링 완료
            paymentTracker.finish();
        }
    }

    private CreatePaymentResponse buildPaymentErrorResponse(Order order, String paymentMethod, Throwable error) {
        String errorUrl = frontendBaseUrl + "/payments/fail?reason=payment-creation-failed&orderId=" + order.getId();
        log.warn("🚨 결제 생성 실패로 프론트엔드 에러 페이지 반환: {} (reason: {})",
                errorUrl, error != null ? error.getMessage() : "unknown");

        return CreatePaymentResponse.builder()
                .paymentId(null)
                .orderId(order.getId())
                .amount(order.getTotalAmount())
                .status("ERROR")
                .paymentMethod(paymentMethod)
                .paymentUrl(errorUrl)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 극한 최적화 결제 URL 생성 (예약 성공 후) - 🚀 목표: 300ms 미만
     */
    @Transactional
    public PaymentUrlResponse generatePaymentUrlAfterReservation(Order order) {
        if (order == null) {
            return null;
        }

        // 성능 추적 시작
        PaymentPerformanceMonitor.PaymentPerformanceTracker tracker =
            paymentPerformanceMonitor.startPaymentTracking("URL-" + order.getOrderNo());

        try {
            // 캐시에서 기존 URL 확인 (목표: 5ms)
            PaymentUrlResponse cachedResponse = paymentCacheService.getCachedPaymentUrl(order.getId());
            if (cachedResponse != null && cachedResponse.getExpiresAt().isAfter(LocalDateTime.now())) {
                log.debug("⚡ 결제 URL 캐시 히트 - 주문번호: {}", order.getOrderNo());
                tracker.finish();
                return cachedResponse;
            }

            // 새로운 결제 URL 생성 (목표: 200ms)
            String paymentMethod = determinePaymentMethod(order);
            CreatePaymentResponse paymentResponse = requestPaymentUrlWithCircuitBreaker(order, paymentMethod);

            String paymentUrl = paymentResponse != null ? paymentResponse.getPaymentUrl() : null;
            if (paymentUrl == null) {
                tracker.finish();
                return null;
            }

            String token = extractToken(paymentUrl);
            LocalDateTime createdAt = paymentResponse.getCreatedAt() != null
                    ? paymentResponse.getCreatedAt() : LocalDateTime.now();
            LocalDateTime expiresAt = paymentResponse.getExpiresAt() != null
                    ? paymentResponse.getExpiresAt() : LocalDateTime.now().plusMinutes(30);

            PaymentUrlResponse urlResponse = PaymentUrlResponse.builder()
                    .paymentUrl(paymentUrl)
                    .token(token)
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .amount(order.getTotalAmount() != null ? order.getTotalAmount().longValue() : null)
                    .paymentMethod(paymentMethod)
                    .createdAt(createdAt)
                    .expiresAt(expiresAt)
                    .expiresInMinutes((int) java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes())
                    .build();

            // 캐싱 (30분 TTL)
            paymentCacheService.cachePaymentUrl(order.getId(), urlResponse);
            orderCacheService.storePaymentUrl(order.getId(), urlResponse);

            // 비동기 알림 (성능에 영향 없음)
            CompletableFuture.runAsync(() -> sendPaymentUrlNotificationToCustomer(paymentResponse));

            log.info("🚀 극한 최적화 결제 URL 생성 완료 - 주문번호: {}", order.getOrderNo());
            return urlResponse;

        } finally {
            tracker.finish();
        }
    }

    private String extractToken(String paymentUrl) {
        if (paymentUrl == null) {
            return null;
        }
        int idx = paymentUrl.indexOf("token=");
        if (idx < 0) {
            return null;
        }
        return paymentUrl.substring(idx + "token=".length());
    }

    /**
     * 주문명 생성 (결제 화면에 표시될 이름)
     */
    private String generateOrderName(Order order) {
        try {
            List<OrderItem> items = order.getOrderItems();
            if (items.isEmpty()) {
                return "팝콘 주문";
            }

            OrderItem firstItem = items.get(0);
            String itemName;

            if (ItemType.RESERVATION.equals(firstItem.getOrderItemType())) {
                itemName = "팝업 예약";
            } else if (ItemType.GOODS.equals(firstItem.getOrderItemType())) {
                itemName = "굿즈 구매";
            } else {
                itemName = "팝콘 상품";
            }

            if (items.size() == 1) {
                return itemName;
            } else {
                return itemName + " 외 " + (items.size() - 1) + "건";
            }

        } catch (Exception e) {
            log.warn("주문명 생성 실패, 기본명 사용: orderId={}", order.getId(), e);
            return "팝콘 주문";
        }
    }

    /**
     * 주문에 포함된 굿즈 항목들의 재고를 예약합니다.
     *
     * @param order 재고 예약할 주문
     * @throws RuntimeException 재고 부족 또는 예약 실패 시
     */
    private void reserveStockForOrder(Order order) {
        log.info("주문 재고 예약 요청 시작(이벤트) - 주문번호: {}", order.getOrderNo());

        // 굿즈 항목만 필터링 (예약형 상품은 재고 예약 불필요)
        List<OrderItem> goodsItems = order.getOrderItems().stream()
                .filter(item -> ItemType.GOODS.equals(item.getOrderItemType()))
                .toList();

        if (goodsItems.isEmpty()) {
            log.info("굿즈 항목이 없어 재고 예약을 건너뜁니다 - 주문번호: {}", order.getOrderNo());
            return;
        }

        // 개별 굿즈 예약 요청 이벤트 발행
        for (OrderItem item : goodsItems) {
            if (item.getGoodsId() != null) {
                publishAfterCommit(() -> orderEventPublisher.publishGoodsReservationRequestedEvent(
                        order.getId(),
                        order.getOrderNo(),
                        order.getPopupId(),
                        item.getGoodsId(),
                        item.getQty()
                ));
            } else {
                log.warn("굿즈 변형 ID가 없어 재고 예약 요청을 건너뜁니다 - 주문번호: {}, 항목ID: {}",
                        order.getOrderNo(), item.getId());
            }
        }

        log.info("🏪 [ORDER] 굿즈 재고 예약 요청 이벤트 발행 완료 - 주문번호: {}, 굿즈수: {}",
                order.getOrderNo(), goodsItems.size());
    }

    /**
     * 주문에 포함된 스케줄 항목들을 예약합니다.
     *
     * @param order 스케줄 예약할 주문
     * @throws RuntimeException 스케줄 예약 실패 시
     */
    private void reserveScheduleForOrder(Order order) {
        log.info("📅 주문 스케줄 예약 요청 시작(이벤트) - 주문번호: {}", order.getOrderNo());

        // 예약 항목만 필터링 (굿즈 항목은 스케줄 예약 불필요)
        List<OrderItem> reservationItems = order.getOrderItems().stream()
                .filter(item -> ItemType.RESERVATION.equals(item.getOrderItemType()))
                .toList();

        if (reservationItems.isEmpty()) {
            log.info("📅 예약 항목이 없어 스케줄 예약을 건너뜁니다 - 주문번호: {}", order.getOrderNo());
            return;
        }

        // 개별 스케줄 예약 요청 이벤트 발행
        List<com.popcorn.order.event.schedule.ScheduleReservationRequestedEvent.ReservedSession> reservedSessions =
                reservationItems.stream()
                        .filter(item -> item.getSessionOptionId() != null)
                        .map(item -> com.popcorn.order.event.schedule.ScheduleReservationRequestedEvent.ReservedSession.create(
                                item.getSessionOptionId(),
                                item.getQty(),
                                generateSessionName(item),
                                null // sessionTime은 Store 서비스에서 조회
                        ))
                        .toList();

        if (!reservedSessions.isEmpty()) {
            publishAfterCommit(() -> orderEventPublisher.publishScheduleReservationRequestedEvent(
                    order.getId(),
                    order.getOrderNo(),
                    order.getPopupId(),
                    reservedSessions
            ));
        }

        log.info("📅 [ORDER] 스케줄 예약 요청 이벤트 발행 완료 - 주문번호: {}, 세션수: {}",
                order.getOrderNo(), reservedSessions.size());
    }

    /**
     * 세션 이름 생성 (임시)
     */
    private String generateSessionName(OrderItem item) {
        return "팝업 세션"; // 실제로는 Store 서비스에서 조회해야 함
    }

    private void publishAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /* 결제 요청 이벤트 발행  */
    public void publishPaymentCreateRequestedEvent(UUID orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

            String paymentMethod = determinePaymentMethod(order);
            
            orderEventPublisher.publishPaymentCreateRequestedEvent(order, paymentMethod);
            /* kafka 결제 생성 요청 이벤트 발행 */
            
            
        } catch (Exception e) {
            log.error("결제 생성 요청 이벤트 발행 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 재고 예약 실패 시 이전에 예약한 항목들을 롤백합니다.
     *
     * @param order 주문
     * @param goodsItems 모든 굿즈 항목들
     * @param failedItem 실패한 항목 (이 항목 이전까지만 롤백)
     *
     */
    private void rollbackStockReservations(Order order, List<OrderItem> goodsItems, OrderItem failedItem) {
        log.info("재고 예약 롤백 시작 - 주문번호: {}", order.getOrderNo());

        for (OrderItem item : goodsItems) {
            // 실패한 항목에 도달하면 중단
            if (item.equals(failedItem)) {
                break;
            }

            if (item.getGoodsId() == null) {
                continue;
            }

            try {
                log.info("굿즈 재고 예약 취소 시도 - 주문번호: {}, 굿즈변형ID: {}, 수량: {}",
                        order.getOrderNo(), item.getGoodsId(), item.getQty());

                orderEventPublisher.publishGoodsReservationCancelRequestedEvent(
                        order,
                        item.getGoodsId(),
                        item.getQty()
                        //reason
                );

                log.info("굿즈 재고 예약 취소 성공 - 주문번호: {}, 굿즈변형ID: {}",
                        order.getOrderNo(), item.getGoodsId());

            } catch (Exception e) {
                log.error("굿즈 재고 예약 취소 실패 - 주문번호: {}, 굿즈변형ID: {}, 에러: {}",
                        order.getOrderNo(), item.getGoodsId(), e.getMessage(), e);
                // 롤백 실패는 로그만 남기고 계속 진행
            }
        }

        log.info("재고 예약 롤백 완료 - 주문번호: {}", order.getOrderNo());
    }

    /**
     * OrderItem에서 제품명 생성
     * OrderItem 엔티티에 제품명 필드가 없으므로 타입에 따라 임시 이름 생성
     */
    private String generateProductName(OrderItem item) {
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

    // 통합 이벤트 제거: publishStockReservedEvent 삭제됨

    // 통합 이벤트 제거: publishStockReservationFailedEvent 삭제됨
    // TODO: 개별 StockReservationFailedEvent 발행으로 변경 예정


    /**
     * 주문 완료 이벤트 발행 (결제 완료 시 호출)
     */
    public void publishOrderCompletedEvent(UUID orderId) {
        try {
            log.info("주문 완료 이벤트 발행 시작 - orderId: {}", orderId);

            // 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));
            boolean hasGoods = order.isGoodsType() || order.isMixedType();
            boolean hasReservation = order.isReservationType() || order.isMixedType();

            // 주문 완료 이벤트 발행
            orderEventPublisher.publishOrderCompletedEvent(order);

            /* kafka 주문완료 이벤트 발행 */
            orderEventProducer.publishOrderCompleted(order,hasGoods,hasReservation);

            log.info("✅ 주문 완료 이벤트 발행 완료 - orderId: {}, orderNo: {}", orderId, order.getOrderNo());

        } catch (Exception e) {
            log.error("❌ 주문 완료 이벤트 발행 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
            // 이벤트 발행 실패는 주문 처리에 영향을 주지 않음 (로그만 남김)
        }
    }

    /**
     * 주문의 재고 예약 취소 (결제 실패 시 호출) --> paid 전에
     */
    public void cancelStockReservationsForOrder(UUID orderId) {
        try {
            log.info("주문 재고 예약 취소 시작 - orderId: {}", orderId);

            // 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

            // 주문 항목들 조회
            List<com.popcorn.order.entity.OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

            // 굿즈 항목만 필터링
            List<com.popcorn.order.entity.OrderItem> goodsItems = orderItems.stream()
                    .filter(item -> ItemType.GOODS.equals(item.getOrderItemType()))
                    .toList();

            if (goodsItems.isEmpty()) {
                log.info("굿즈 항목이 없어 재고 예약 취소를 건너뜁니다 - orderId: {}", orderId);
                return;
            }

            // 각 굿즈 항목에 대해 재고 예약 취소
            for (com.popcorn.order.entity.OrderItem item : goodsItems) {
                if (item.getGoodsId() == null) {
                    log.warn("굿즈 변형 ID가 없어 재고 예약 취소를 건너뜁니다 - orderId: {}, 항목ID: {}",
                            orderId, item.getId());
                    continue;
                }

                try {
                    log.info("굿즈 재고 예약 취소 시도 - orderId: {}, 굿즈변형ID: {}, 수량: {}",
                            orderId, item.getGoodsId(), item.getQty());

                    orderEventPublisher.publishGoodsReservationCancelRequestedEvent(
                            order,
                            item.getGoodsId(),
                            item.getQty()
                    );

                    log.info("✅ 굿즈 재고 예약 취소 완료 - orderId: {}, 굿즈변형ID: {}",
                            orderId, item.getGoodsId());

                } catch (Exception e) {
                    log.error("❌ 굿즈 재고 예약 취소 실패 - orderId: {}, 굿즈변형ID: {}, error: {}",
                            orderId, item.getGoodsId(), e.getMessage(), e);
                    // 개별 항목 취소 실패는 전체 처리를 중단시키지 않음
                }
            }

            log.info("✅ 주문 재고 예약 취소 완료 - orderId: {}, 처리된 굿즈 수: {}",
                    orderId, goodsItems.size());

        } catch (Exception e) {
            log.error("❌ 주문 재고 예약 취소 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
            // 재고 예약 취소 실패도 주문 상태 변경에 영향을 주지 않음 (로그만 남김)
        }
    }

    /**
     * 주문의 스케줄 예약 취소 (결제 실패/취소 시 호출)
     */
    public void cancelScheduleReservationsCfororder(UUID orderId) {
        try {
            log.info("주문 스케줄 예약 해제 검증 시작 - orderId: {}", orderId);

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

            List<OrderItem> scheduleItems = orderItemRepository.findByOrderId(orderId).stream()
                    .filter(item -> ItemType.RESERVATION.equals(item.getOrderItemType()))
                    .filter(item -> item.getSessionOptionId() != null)
                    .toList();

            if (scheduleItems.isEmpty()) {
                log.info("스케줄 항목이 없어 해제를 건너뜁니다 - orderId: {}", orderId);
                return;
            }

            for (OrderItem item : scheduleItems) {
            UUID scheduleId = item.getSessionOptionId();

            storeRequestsProducer.publishScheduleReservationCancelRequested(
                    order,
                    scheduleId
            );

            log.info("📅 스케줄 해제 요청 발행 - orderId: {}, scheduleId: {}",
                    orderId, scheduleId);
        }

            log.info("✅ 스케줄 예약 해제 요청 완료 - orderId: {}, 해제 세션 수: {}",
                    orderId);
        } catch (Exception e) {
            log.error("❌ 주문 스케줄 예약 해제 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 주문의 결제 취소 (결제 실패 시 호출)
     */
    public void cancelPaymentForOrder(UUID orderId, String paymentId, String reason) {
        try {
            log.info("주문 결제 취소 시작 - orderId: {}, paymentId: {}, reason: {}", orderId, paymentId, reason);

            // 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

            // 결제 취소 요청 이벤트 발행 (Payment 서비스가 처리)
            orderEventPublisher.publishPaymentCancelRequestedEvent(
                    order,
                    paymentId != null ? paymentId : "",
                    reason != null ? reason : "주문 결제 실패로 인한 자동 취소"
            );

            log.info("✅ 결제 취소 요청 이벤트 발행 완료 - orderId: {}, paymentId: {}", orderId, paymentId);

        } catch (Exception e) {
            log.error("❌ 주문 결제 취소 실패 - orderId: {}, paymentId: {}, error: {}",
                    orderId, paymentId, e.getMessage(), e);
            // 결제 취소 실패도 주문 상태 변경에 영향을 주지 않음 (로그만 남김)
        }
    }

    /**
     * 주문의 재고 차감 요청 (결제 완료 시 호출)
     */
    public void requestStockDeduction(UUID orderId) {
        try {
            log.info("주문 재고 차감 요청 시작 - orderId: {}", orderId);

            // 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

            // 주문 항목들 조회
            List<com.popcorn.order.entity.OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

            // 굿즈 항목만 필터링 (예약형은 재고 차감 불필요)
            List<com.popcorn.order.entity.OrderItem> goodsItems = orderItems.stream()
                    .filter(item -> ItemType.GOODS.equals(item.getOrderItemType()))
                    .filter(item -> item.getGoodsId() != null)
                    .toList();

            if (goodsItems.isEmpty()) {
                log.info("굿즈 항목이 없어 재고 차감을 건너뜁니다 - orderId: {}", orderId);
                return;
            }

            // 개별 재고 차감 요청 이벤트 발행
            List<com.popcorn.order.event.stock.StockDeductionRequestedEvent.DeductionItem> deductionItems =
                    goodsItems.stream()
                            .map(item -> com.popcorn.order.event.stock.StockDeductionRequestedEvent.DeductionItem.create(
                                    item.getGoodsId(),
                                    item.getQty(),
                                    generateProductName(item),
                                    item.getUnitPrice()
                            ))
                            .toList();

            if (!deductionItems.isEmpty()) {
                orderEventPublisher.publishStockDeductionRequestedEvent(
                        orderId,
                        order.getOrderNo(),
                        order.getPopupId(),
                        deductionItems
                );

                /* kafka 재고차감 이벤트 발행 */
                storeRequestsProducer.publishStockDeductionRequested(order,deductionItems);
            }

            log.info("✅ [ORDER] 재고 차감 요청 이벤트 발행 완료 - orderId: {}, 굿즈 항목 수: {}",
                    orderId, deductionItems.size());

        } catch (Exception e) {
            log.error("❌ 주문 재고 차감 요청 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
            // 재고 차감 요청 실패도 주문 상태 변경에 영향을 주지 않음 (로그만 남김)
        }
    }

    /**
     * 주문의 스케줄 확정 요청 (결제 완료 시 호출)
     * 예약 상태에서 확정 상태로 변경하여 취소 불가능하게 만듦
     */
    public void requestScheduleConfirmation(UUID orderId) {
        try {
            log.info("📅 주문 스케줄 확정 요청 시작 - orderId: {}", orderId);

            // 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

            // 주문 항목들 조회
            List<com.popcorn.order.entity.OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

            // 스케줄 항목만 필터링 (굿즈는 스케줄 확정 불필요)
            List<com.popcorn.order.entity.OrderItem> scheduleItems = orderItems.stream()
                    .filter(item -> ItemType.RESERVATION.equals(item.getOrderItemType()))
                    .filter(item -> item.getSessionOptionId() != null)
                    .toList();

            if (scheduleItems.isEmpty()) {
                log.info("스케줄 항목이 없어 스케줄 확정을 건너뜁니다 - orderId: {}", orderId);
                return;
            }

            // 스케줄 확정 항목 리스트 생성
            List<ScheduleConfirmationItem> confirmationItems = scheduleItems.stream()
                    .map(item -> ScheduleConfirmationItem.builder()
                            .scheduleId(item.getSessionOptionId())
                            .quantity(item.getQty())
                            .sessionName(generateProductName(item))
                            .sessionTime(generateSessionTimeInfo(item))
                            .build())
                    .collect(java.util.stream.Collectors.toList());

            // 스케줄 확정 요청 이벤트 발행
            orderEventPublisher.publishScheduleConfirmationRequestedEvent(
                    order,
                    orderId,
                    order.getOrderNo(),
                    order.getPopupId(),
                    confirmationItems
            );

            log.info("✅ 주문 스케줄 확정 요청 완료 - orderId: {}, 스케줄 항목 수: {}",
                    orderId, confirmationItems.size());

        } catch (Exception e) {
            log.error("❌ 주문 스케줄 확정 요청 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
            // 스케줄 확정 요청 실패도 주문 상태 변경에 영향을 주지 않음 (로그만 남김)
        }
    }

    /**
     * 스케줄 예약 해제 (결제 실패/취소 등 보상 트랜잭션에서 호출)
     * - 해당 orderId에 RESERVATION(스케줄)이 있는지 확인
     * - 주문 상태가 PAID인지 확인 (요구사항)
     */
    public void releaseScheduleReservationsForOrder(UUID orderId) {
        try {
            log.info("스케줄 예약 해제 검증 시작 - orderId: {}", orderId);

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

            /*/ 1) 상태 검증: PAID만 허용
            if (order.getStatus() != OrderStatus.PAID) {
                log.info("스케줄 예약 해제 스킵 - 상태가 PAID 아님 - orderId: {}, status: {}",
                        orderId, order.getStatus());
                return;
            }*/

            // 2) RESERVATION 필터링 (scheduleId = sessionOptionId)
            List<OrderItem> scheduleItems = orderItemRepository.findByOrderId(orderId).stream()
                    .filter(item -> ItemType.RESERVATION.equals(item.getOrderItemType()))
                    .filter(item -> item.getSessionOptionId() != null)
                    .toList();

            if (scheduleItems.isEmpty()) {
                log.info("스케줄 항목이 없어 해제를 건너뜁니다 - orderId: {}", orderId);
                return;
            }

            // 3) 각 스케줄에 대해 해제 요청 발행
            List<Map<String, Object>> releaseItems = scheduleItems.stream()
                    .map(item -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("sessionId", item.getSessionOptionId());
                        map.put("qty", item.getQty());
                        return map;
                    })
                    .toList();

            // 4) Kafka 발행
            storeRequestsProducer.publishScheduleReleaseRequested(
                    order,
                    releaseItems
            );

            log.info("✅ 스케줄 예약 해제 요청 완료 - orderId: {}, 해제 세션 수: {}",
                    orderId, scheduleItems.size());

        } catch (Exception e) {
            log.error("❌ 스케줄 예약 해제 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 굿즈 재고 예약 해제 (결제 실패/취소 등 보상 트랜잭션에서 호출)
     * - 해당 orderId에 GOODS가 있는지 확인
     * - 주문 상태가 PAID인지 확인 (요구사항)
     */
    public void realeaseGoodsReservationsForOrder(UUID orderId) {
        try {
            log.info("굿즈 예약 해제 검증 시작 - orderId: {}", orderId);

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없어요: " + orderId));

            /*/ 1) 상태 검증: PAID만 허용
            if (order.getStatus() != OrderStatus.PAID) {
                log.info("굿즈 예약 해제 스킵 - 상태가 PAID 아님 - orderId: {}, status: {}",
                        orderId, order.getStatus());
                return;
            }*/

            // 2) GOODS 필터링
            List<OrderItem> goodsItems = orderItemRepository.findByOrderId(orderId).stream()
                    .filter(item -> ItemType.GOODS.equals(item.getOrderItemType()))
                    .filter(item -> item.getGoodsId() != null)
                    .toList();

            if (goodsItems.isEmpty()) {
                log.info("굿즈 항목이 없어 해제를 건너뜁니다 - orderId: {}", orderId);
                return;
            }

            // 3) 각 굿즈 아이템에 대해 해제 요청 발행
            List<Map<String, Object>> releaseItems = goodsItems.stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("goodsId", item.getGoodsId());
                    map.put("qty", item.getQty());
                    return map;
                })
                .toList();

            // 4) Kafka 발행
            storeRequestsProducer.publishStockReleaseRequested(
                    order,
                    releaseItems
            );

            log.info("✅ 굿즈 예약 해제 요청 완료 - orderId: {}, 해제 굿즈 수: {}",
                    orderId, goodsItems.size());

        } catch (Exception e) {
            log.error("❌ 굿즈 예약 해제 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }





    /**
     * OrderItem에서 세션 시간 정보 생성
     */
    private String generateSessionTimeInfo(OrderItem item) {
        if (item == null || item.getSessionOptionId() == null) {
            return "시간 정보 없음";
        }

        // TODO: 실제 세션 시간 정보 조회 로직 구현 필요
        // 현재는 임시 정보만 반환
        return "세션 " + item.getSessionOptionId().toString().substring(0, 8);
    }

    /**
     * 스케줄 확정 항목 DTO
     */
    @lombok.Builder
    public static class ScheduleConfirmationItem {
        private java.util.UUID scheduleId;
        private Integer quantity;
        private String sessionName;
        private String sessionTime;

        public java.util.UUID getScheduleId() { return scheduleId; }
        public Integer getQuantity() { return quantity; }
        public String getSessionName() { return sessionName; }
        public String getSessionTime() { return sessionTime; }
    }

    /**
     * OrderItem에서 상품 변형명 생성
     */
    private String generateProductVariantName(OrderItem item) {
        if (item == null || item.getOrderItemType() == null) {
            return "기본";
        }

        switch (item.getOrderItemType()) {
            case RESERVATION:
                return "예약형";
            case GOODS:
                return "굿즈";
            default:
                return "기본";
        }
    }

}
