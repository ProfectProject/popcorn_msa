package com.popcorn.order.event.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.repository.OrderRepository;
import com.popcorn.order.event.payment.PaymentCompletedEvent;
import com.popcorn.order.event.schedule.ScheduleReservationSucceededEvent;
import com.popcorn.order.event.schedule.ScheduleReservationFailedEvent;
import com.popcorn.order.service.core.OrderCommandService;
import com.popcorn.order.service.util.OrderInfoResponseService;
import com.popcorn.order.service.cache.PaymentCacheService;
import com.popcorn.order.service.cache.OrderCacheService;
import com.popcorn.order.service.lookup.OrderPopupLookupService;
import com.popcorn.order.service.util.OrderReservationAwaiter;
import com.popcorn.order.service.util.OrderIdempotencyService;
import com.popcorn.order.dto.payment.PaymentUrlResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Order 서비스 Redis Stream 이벤트 리스너
 *
 * 다른 마이크로서비스로부터 오는 응답 이벤트를 수신하고 처리
 */
@Component
@RequiredArgsConstructor
public class OrderRedisStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(OrderRedisStreamListener.class);

    private final OrderCommandService orderCommandService;
    private final OrderRepository orderRepository;
    private final OrderPopupLookupService orderPopupLookupService;
    private final OrderReservationAwaiter orderReservationAwaiter;
    private final OrderInfoResponseService orderInfoResponseService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCacheService paymentCacheService;
    private final OrderCacheService orderCacheService;
    private final OrderIdempotencyService orderIdempotencyService;
    private final ScheduledExecutorService retryExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "order-redis-retry");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            String streamName = record.getStream();
            String recordId = record.getId().getValue();

            Map<String, Object> values = new HashMap<>(record.getValue());

            // eventType 필수 검증
            String eventType = validateEventType(values, streamName, recordId);

            log.info("🔔 [ORDER] Stream 메시지 수신 - stream: {}, recordId: {}, eventType: {}",
                    streamName, recordId, eventType);

            if ("order:query:stream".equals(streamName)) {
                log.info("🔍 [ORDER] 주문 조회 요청 이벤트 수신");
                handleOrderQueryRequest(values);
                log.debug("✅ [ORDER] 주문 조회 요청 처리 완료 - recordId: {}", recordId);
                return;
            }

            if ("order:update:stream".equals(streamName)) {
                log.info("🔄 [ORDER] 주문 상태 업데이트 요청 이벤트 수신");
                handleOrderStatusUpdateRequest(values);
                log.debug("✅ [ORDER] 주문 상태 업데이트 요청 처리 완료 - recordId: {}", recordId);
                return;
            }

            if ("order-info-requests".equals(streamName)) {
                log.info("📨 [ORDER] Order 정보 요청 이벤트 수신");
                orderInfoResponseService.handleOrderInfoRequest(values);
                log.debug("✅ [ORDER] Order 정보 요청 처리 완료 - recordId: {}", recordId);
                return;
            }

            handleStreamEvent(eventType, values);

            log.debug("✅ [ORDER] 메시지 처리 완료 - stream: {}, recordId: {}", streamName, recordId);

        } catch (Exception e) {
            log.error("🚨 [ORDER] Stream 메시지 처리 실패 - record: {}, error: {}",
                    record, e.getMessage(), e);

            // 실패한 메시지를 DLQ로 이동
            sendToDeadLetterQueue(record, e);
        }
    }

    private void handleOrderQueryRequest(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String correlationId = (String) values.get("correlationId");

            if (orderIdStr == null || orderIdStr.isBlank()) {
                log.warn("🔍 [ORDER] 주문 조회 요청 필수 데이터 누락 - values: {}", values);
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            orderRepository.findById(orderId).ifPresentOrElse(order -> {
                try {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", order.getId());
                    response.put("orderNo", order.getOrderNo());
                    response.put("customerId", order.getCustomerId());
                    response.put("totalAmount", order.getTotalAmount() != null ? order.getTotalAmount() : 0);
                    response.put("status", order.getStatus() != null ? order.getStatus().name() : "UNKNOWN");
                    response.put("orderType", order.getOrderType() != null ? order.getOrderType().name() : "UNKNOWN");
                    response.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");

                    String cacheKey = "order:cache:" + orderId;
                    String json = objectMapper.writeValueAsString(response);
                    redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(30));

                    log.info("✅ [ORDER] 주문 조회 응답 캐시 저장 - orderId: {}, correlationId: {}",
                            orderId, correlationId);
                } catch (Exception e) {
                    log.error("🚨 [ORDER] 주문 조회 응답 캐시 저장 실패 - orderId: {}, error: {}",
                            orderId, e.getMessage(), e);
                }
            }, () -> log.warn("🔍 [ORDER] 주문 조회 요청 대상 없음 - orderId: {}", orderIdStr));
        } catch (Exception e) {
            log.error("🚨 [ORDER] 주문 조회 요청 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    private String normalizeUuidString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private void handleOrderStatusUpdateRequest(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String newStatus = (String) values.get("newStatus");
            String reason = (String) values.get("reason");
            String correlationId = (String) values.get("correlationId");

            if (orderIdStr == null || orderIdStr.isBlank() || newStatus == null) {
                log.warn("🔄 [ORDER] 주문 상태 업데이트 요청 필수 데이터 누락 - values: {}", values);
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            orderCommandService.updateOrderStatus(orderId, newStatus, reason != null ? reason : "");

            String responseKey = "order:update:response:" + correlationId;
            redisTemplate.opsForValue().set(responseKey, "OK", Duration.ofSeconds(30));

            log.info("✅ [ORDER] 주문 상태 업데이트 응답 저장 - orderId: {}, correlationId: {}",
                    orderId, correlationId);
        } catch (Exception e) {
            log.error("🚨 [ORDER] 주문 상태 업데이트 요청 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * Stream 이벤트 타입별 처리
     */
    private void handleStreamEvent(String eventType, Map<String, Object> values) {
        try {
            switch (eventType) {
                // 가격 조회, 사용자 주소 조회 응답 이벤트 처리 제거됨 (HTTP 동기 방식으로 변경)
                // 팝업 정보 조회 응답 처리 제거됨 (HTTP 동기 방식으로 변경)
                case EventConstants.EventTypes.PAYMENT_APPROVED:
                    log.info("💳 [ORDER] 결제 승인 이벤트 수신");
                    handlePaymentApproved(values);
                    break;
                case EventConstants.EventTypes.PAYMENT_FAILED:
                    log.info("❌ [ORDER] 결제 실패 이벤트 수신");
                    handlePaymentFailed(values);
                    break;
                case EventConstants.EventTypes.PAYMENT_CANCELLED:
                    log.info("↩️ [ORDER] 결제 취소 이벤트 수신");
                    handlePaymentCancelled(values);
                    break;
                case EventConstants.EventTypes.ORDER_PAID:
                    log.info("💳✅ [ORDER] 주문 결제 완료 이벤트 수신 - 재고 차감 시작");
                    handleOrderPaid(values);
                    break;
                case EventConstants.EventTypes.SCHEDULE_RESERVATION_SUCCEEDED:
                    log.info("📅✅ [ORDER] 스케줄 예약 성공 이벤트 수신");
                    handleScheduleReservationSuccess(values);
                    break;
                case EventConstants.EventTypes.SCHEDULE_RESERVATION_FAILED:
                    log.info("📅❌ [ORDER] 스케줄 예약 실패 이벤트 수신");
                    handleScheduleReservationFailed(values);
                    break;
                case EventConstants.EventTypes.STOCK_DEDUCTION_SUCCEEDED:
                    log.info("📦✅ [ORDER] 재고 차감 성공 이벤트 수신");
                    handleStockDeductionSuccess(values);
                    break;
                case EventConstants.EventTypes.STOCK_DEDUCTION_FAILED:
                    log.info("📦❌ [ORDER] 재고 차감 실패 이벤트 수신");
                    handleStockDeductionFailed(values);
                    break;
                case EventConstants.EventTypes.GOODS_RESERVATION_SUCCEEDED:
                    log.info("📦✅ [ORDER] 굿즈 예약 성공 이벤트 수신");
                    handleGoodsReserved(values);
                    break;
                case EventConstants.EventTypes.GOODS_RESERVATION_FAILED:
                    log.info("📦❌ [ORDER] 굿즈 예약 실패 이벤트 수신");
                    handleGoodsReservationFailed(values);
                    break;
                case EventConstants.EventTypes.MIXED_RESERVATION_SUCCEEDED:
                    log.info("🔗✅ [ORDER] 복합형 예약 성공 이벤트 수신");
                    handleMixedReservationSuccess(values);
                    break;
                case EventConstants.EventTypes.MIXED_RESERVATION_FAILED:
                    log.info("🔗❌ [ORDER] 복합형 예약 실패 이벤트 수신");
                    handleMixedReservationFailed(values);
                    break;
                default:
                    log.debug("🔔 [ORDER] 알 수 없는 이벤트 타입 - type: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("🚨 [ORDER] 이벤트 처리 실패 - eventType: {}, error: {}", eventType, e.getMessage(), e);
        }
    }

    /**
     * 스케줄 예약 성공 이벤트 처리
     */
    private void handleScheduleReservationSuccess(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");
            String token = (String) values.get("token");
            String expiresAt = (String) values.get("expiresAt");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");
            if (token != null) token = token.trim().replaceAll("^\"|\"$", "");
            if (expiresAt != null) expiresAt = expiresAt.trim().replaceAll("^\"|\"$", "");

            log.info("📅✅ [ORDER] 스케줄 예약 성공 처리 - orderId: {}, orderNo: {}, token: {}",
                    orderId, orderNo, token);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 주문 상태 업데이트
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.RESERVED.name(),
                    "스케줄 예약 완료 - 토큰: " + token);

                // 예약 대기자에게 성공 알림 (결제 URL 생성 포함)
                final PaymentUrlResponse[] paymentUrlHolder = new PaymentUrlResponse[1];
                try {
                    orderRepository.findById(orderUuid).ifPresent(order -> {
                        PaymentUrlResponse generated = orderCommandService.generatePaymentUrlAfterReservation(order);
                        paymentUrlHolder[0] = generated;
                        if (generated != null) {
                            log.info("💳✅ [ORDER] 결제 URL 생성 완료 - orderId: {}", orderUuid);
                        }
                    });
                } catch (Exception paymentError) {
                    log.warn("⚠️ [ORDER] 결제 URL 생성 실패 - orderId: {}, error: {}",
                            orderUuid, paymentError.getMessage());
                }
                // 결제 URL 생성 실패/주문 미조회여도 예약 성공은 완료 처리
                orderReservationAwaiter.completeSuccess(orderUuid, paymentUrlHolder[0]);

                // ScheduleReservationSucceededEvent 발행
                try {
                    ScheduleReservationSucceededEvent event = ScheduleReservationSucceededEvent.create(
                        orderUuid,
                        null,  // 예약된 세션 정보 (여기서는 단순화)
                        token,
                        expiresAt != null ? LocalDateTime.parse(expiresAt) : null
                    );

                    eventPublisher.publishEvent(event);
                    log.info("📨 [ORDER] ScheduleReservationSucceededEvent 발행 완료 - orderId: {}", orderId);
                } catch (Exception eventError) {
                    log.warn("⚠️ [ORDER] ScheduleReservationSucceededEvent 발행 실패 - orderId: {}, error: {}",
                            orderId, eventError.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 스케줄 예약 성공 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 스케줄 예약 실패 이벤트 처리
     */
    private void handleScheduleReservationFailed(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");
            String reason = (String) values.get("reason");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.warn("📅❌ [ORDER] 스케줄 예약 실패 처리 - orderId: {}, orderNo: {}, reason: {}",
                    orderId, orderNo, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 주문 상태를 CANCELLED로 업데이트
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                    "스케줄 예약 실패 - " + (reason != null ? reason : "알 수 없는 이유"));

                // 예약 대기자에게 실패 알림
                orderReservationAwaiter.completeFailure(orderUuid,
                        reason != null ? reason : "스케줄 예약 실패");

                // ScheduleReservationFailedEvent 발행
                try {
                    ScheduleReservationFailedEvent event = ScheduleReservationFailedEvent.create(
                        orderUuid,
                        null,  // 실패한 세션 정보 (여기서는 단순화)
                        reason != null ? reason : "스케줄 예약 실패"
                    );

                    eventPublisher.publishEvent(event);
                    log.info("📨 [ORDER] ScheduleReservationFailedEvent 발행 완료 - orderId: {}", orderId);
                } catch (Exception eventError) {
                    log.warn("⚠️ [ORDER] ScheduleReservationFailedEvent 발행 실패 - orderId: {}, error: {}",
                            orderId, eventError.getMessage());
                }

                // 보상 트랜잭션: 재고 예약 해제
                orderCommandService.cancelStockReservationsForOrder(orderUuid);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 스케줄 예약 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 결제 승인 이벤트 처리
     */
    private void handlePaymentApproved(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String paymentId = (String) values.get("paymentId");
            String amount = (String) values.get("amount");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (paymentId != null) paymentId = paymentId.trim().replaceAll("^\"|\"$", "");
            if (amount != null) amount = amount.trim().replaceAll("^\"|\"$", "");

            log.info("💳 [ORDER] 결제 승인 처리 - orderId: {}, paymentId: {}, amount: {}",
                    orderId, paymentId, amount);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 주문 상태를 PAID로 업데이트
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.PAID.name(),
                    "결제 승인 완료 - 결제ID: " + paymentId);

                // 1-1. 결제 관련 캐시 무효화 (결제 URL, 토큰 등)
                try {
                    paymentCacheService.evictPaymentCache(orderUuid);
                    orderCacheService.evictPaymentUrlCache(orderUuid);
                    log.info("🗑️ [ORDER] 결제 캐시 무효화 완료 - orderId: {} (PaymentCache + OrderCache)", orderId);
                } catch (Exception cacheEx) {
                    log.warn("⚠️ [ORDER] 결제 캐시 무효화 실패 - orderId: {}, error: {}", orderId, cacheEx.getMessage());
                }

                // 1-2. 멱등성 키 무효화 (주문 생성 중복 방지 키 해제)
                try {
                    var order = orderRepository.findById(orderUuid).orElse(null);
                    if (order != null && order.getCustomerId() != null && order.getPopupId() != null) {
                        String idempotencyKey = "order:create:" + order.getCustomerId() + ":" + order.getPopupId();
                        orderIdempotencyService.invalidateKey(idempotencyKey, "결제 승인 완료");
                        log.info("🔑 [ORDER] 멱등성 키 무효화 완료 - orderId: {}, key: {}", orderId, idempotencyKey);
                    }
                } catch (Exception idempEx) {
                    log.warn("⚠️ [ORDER] 멱등성 키 무효화 실패 - orderId: {}, error: {}", orderId, idempEx.getMessage());
                }

                // 2. 내부 PaymentCompletedEvent 발행하여 재고 차감 프로세스 시작
                try {
                    PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.create(
                            orderUuid,
                            null, // paymentId - 없으면 null
                            paymentId, // paymentKey
                            amount != null ? Integer.valueOf(amount) : null,
                            "TOSS_PAYMENT"
                    );

                    eventPublisher.publishEvent(paymentEvent);
                    log.info("✅ [ORDER] PaymentCompletedEvent 발행 완료 - 재고 차감 프로세스 시작 - orderId: {}", orderId);
                } catch (Exception eventEx) {
                    log.error("🚨 [ORDER] PaymentCompletedEvent 발행 실패 - orderId: {}", orderId, eventEx);
                }

                log.info("✅ [ORDER] 결제 승인 처리 완료 - orderId: {}, status: PAID", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 결제 승인 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 결제 완료 이벤트 처리 (비활성화됨 - handlePaymentApproved에서 처리)
     *
     * 중복 결제 방지를 위해 사용 중단.
     * 모든 결제 처리는 handlePaymentApproved에서 통합 처리됨.
     */
    @SuppressWarnings("unused")
    private void handlePaymentCompleted_DEPRECATED(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String paymentId = (String) values.get("paymentId");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (paymentId != null) paymentId = paymentId.trim().replaceAll("^\"|\"$", "");

            log.info("✅ [ORDER] 결제 완료 처리 - orderId: {}, paymentId: {}",
                    orderId, paymentId);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 재고 차감 요청 (Store 서비스에 재고 차감 요청 전송)
                orderCommandService.requestStockDeduction(orderUuid);

                // 2. 주문 상태를 COMPLETED로 업데이트
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.COMPLETED.name(),
                    "결제 완료 - 결제ID: " + paymentId);

                // 3. 주문 완료 이벤트 발행 (알림 등 후속 처리용)
                orderCommandService.publishOrderCompletedEvent(orderUuid);

                log.info("✅ [ORDER] 주문 완료 처리 완료 - orderId: {}, status: COMPLETED", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 결제 완료 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 주문 결제 완료 이벤트 처리 (Stores 서비스에서 발행)
     * - 재고 차감 요청 발행
     * - 스케줄 확정 처리
     */
    private void handleOrderPaid(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");
            String paymentId = (String) values.get("paymentId");
            String totalAmount = (String) values.get("totalAmount");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");
            if (paymentId != null) paymentId = paymentId.trim().replaceAll("^\"|\"$", "");
            if (totalAmount != null) totalAmount = totalAmount.trim().replaceAll("^\"|\"$", "");

            log.info("💳✅ [ORDER] 주문 결제 완료 처리 시작 - orderId: {}, orderNo: {}, amount: {}",
                    orderId, orderNo, totalAmount);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 주문 상태를 PAID로 업데이트
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.PAID.name(),
                    "결제 완료 - 재고 차감 및 스케줄 확정 진행");

                // 2. 재고 차감 요청 (Store 서비스에 재고 차감 요청 전송)
                log.info("📦 [ORDER] 재고 차감 요청 발행 - orderId: {}", orderId);
                orderCommandService.requestStockDeduction(orderUuid);

                // 3. 스케줄 확정 처리 (예약에서 확정으로 변경)
                log.info("📅 [ORDER] 스케줄 확정 요청 발행 - orderId: {}", orderId);
                orderCommandService.requestScheduleConfirmation(orderUuid);

                // 4. 내부 결제 완료 이벤트 발행 (다른 서비스 알림용)
                try {
                    PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.create(
                            orderUuid,
                            null, // paymentId - 없으면 null
                            paymentId, // paymentKey
                            totalAmount != null ? Integer.valueOf(totalAmount) : null,
                            "TOSS_PAYMENT"
                    );

                    eventPublisher.publishEvent(paymentEvent);
                    log.info("📨 [ORDER] 내부 결제 완료 이벤트 발행 완료 - orderId: {}", orderId);

                } catch (Exception eventError) {
                    log.warn("⚠️ [ORDER] 내부 결제 완료 이벤트 발행 실패 (재고/스케줄 처리는 계속) - error: {}",
                            eventError.getMessage());
                }

                log.info("✅ [ORDER] 주문 결제 완료 처리 완료 - orderId: {}, 재고차감+스케줄확정 요청 발행됨", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 주문 결제 완료 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 결제 실패 이벤트 처리
     */
    private void handlePaymentFailed(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String paymentId = (String) values.get("paymentId");
            String reason = (String) values.get("reason");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (paymentId != null) paymentId = paymentId.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.info("❌ [ORDER] 결제 실패 처리 - orderId: {}, paymentId: {}, reason: {}",
                    orderId, paymentId, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 결제 취소 처리 (결제 실패 시)
                if (paymentId != null && !paymentId.isEmpty()) {
                    orderCommandService.cancelPaymentForOrder(orderUuid, paymentId, reason);
                }

                // 2. 주문 상태를 CANCELLED로 업데이트 (결제 실패로 인한 취소)
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                    "결제 실패 - 결제ID: " + paymentId + ", 사유: " + reason);

                // 3. 재고 예약 해제
                orderCommandService.cancelStockReservationsForOrder(orderUuid);

                log.info("✅ [ORDER] 결제 실패 처리 완료 - orderId: {}, 결제취소: {}, status: CANCELLED, 재고 예약 해제됨",
                        orderId, paymentId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 결제 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 재고 차감 성공 이벤트 처리
     */
    private void handleStockDeductionSuccess(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");
            String stockDetails = (String) values.get("stockDetails");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");
            if (stockDetails != null) stockDetails = stockDetails.trim().replaceAll("^\"|\"$", "");

            log.info("📦✅ [ORDER] 재고 차감 성공 처리 - orderId: {}, orderNo: {}, stockDetails: {}",
                    orderId, orderNo, stockDetails);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 주문 상태를 COMPLETED로 업데이트 (재고 차감 성공 = 주문 완료)
                try {
                    orderCommandService.updateOrderStatus(orderUuid, OrderStatus.COMPLETED.name(),
                        "재고 차감 완료 - 주문 완료: " + stockDetails);
                } catch (Exception e) {
                    // 멱등성 처리나 이미 완료된 상태일 경우 로깅만 하고 넘어감
                    if (e.getMessage() != null && e.getMessage().contains("IdempotencyException")) {
                        log.warn("📦✅ [ORDER] 재고 차감 성공 멱등 처리 중복 - orderId: {}, reason: {}",
                                orderId, e.getMessage());
                        return;
                    }
                    // 다른 예외는 다시 던짐
                    throw e;
                }

                log.info("📦✅ [ORDER] 재고 차감 성공으로 주문 완료 상태 업데이트 완료 - orderId: {}", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 재고 차감 성공 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 재고 차감 실패 이벤트 처리
     */
    private void handleStockDeductionFailed(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");
            String reason = (String) values.get("reason");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.error("📦❌ [ORDER] 재고 차감 실패 처리 - orderId: {}, orderNo: {}, reason: {}",
                    orderId, orderNo, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 결제 취소 요청 (보상 트랜잭션)
                orderCommandService.cancelPaymentForOrder(orderUuid, null,
                    "재고 차감 실패로 인한 자동 환불: " + reason);

                // 2. 재고 예약 해제
                orderCommandService.cancelStockReservationsForOrder(orderUuid);

                // 3. 주문 상태를 CANCELLED로 업데이트 (재고 차감 실패)
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                    "재고 차감 실패로 인한 주문 취소 - " + reason);

                log.info("📦❌ [ORDER] 재고 차감 실패로 주문 취소 처리 완료 - orderId: {}", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 재고 차감 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 결제 취소 이벤트 처리
     */
    private void handlePaymentCancelled(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String reason = (String) values.get("cancelReason");

            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.info("↩️ [ORDER] 결제 취소 처리 - orderId: {}, reason: {}", orderId, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 결제 취소 시 캐시 무효화
                try {
                    paymentCacheService.evictPaymentCache(orderUuid);
                    orderCacheService.evictPaymentUrlCache(orderUuid);
                    log.info("🗑️ [ORDER] 결제 취소로 인한 캐시 무효화 완료 - orderId: {} (PaymentCache + OrderCache)", orderId);
                } catch (Exception cacheEx) {
                    log.warn("⚠️ [ORDER] 결제 취소 캐시 무효화 실패 - orderId: {}, error: {}", orderId, cacheEx.getMessage());
                }

                // 멱등성 키 무효화 (주문 생성 중복 방지 키 해제)
                try {
                    var order = orderRepository.findById(orderUuid).orElse(null);
                    if (order != null && order.getCustomerId() != null && order.getPopupId() != null) {
                        String idempotencyKey = "order:create:" + order.getCustomerId() + ":" + order.getPopupId();
                        orderIdempotencyService.invalidateKey(idempotencyKey, "결제 취소 - " + (reason != null ? reason : "사용자 요청"));
                        log.info("🔑 [ORDER] 멱등성 키 무효화 완료 - orderId: {}, key: {}, reason: {}", orderId, idempotencyKey, reason);
                    }
                } catch (Exception idempEx) {
                    log.warn("⚠️ [ORDER] 멱등성 키 무효화 실패 - orderId: {}, error: {}", orderId, idempEx.getMessage());
                }

                // 재고 예약 해제
                orderCommandService.cancelStockReservationsForOrder(orderUuid);

                // 주문 취소
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                        "결제 취소 - " + (reason != null ? reason : ""));
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 결제 취소 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 굿즈 예약 성공 이벤트 처리
     */
    private void handleGoodsReserved(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");

            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");

            if ((orderNo == null || orderNo.isBlank()) && orderId != null && !orderId.isBlank()) {
                try {
                    UUID orderUuid = UUID.fromString(orderId);
                    orderNo = orderRepository.findById(orderUuid)
                        .map(o -> o.getOrderNo())
                        .orElse(null);
                } catch (Exception ignored) {
                    // best-effort only
                }
            }

            log.info("📦✅ [ORDER] 굿즈 예약 성공 처리 - orderId: {}, orderNo: {}", orderId, orderNo);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);
                scheduleOrderStatusUpdate(orderUuid, 1);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 굿즈 예약 성공 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    private void scheduleOrderStatusUpdate(UUID orderId, int attempt) {
        int maxAttempts = 10;
        long delayMillis = 200L;

        retryExecutor.schedule(() -> {
            try {
                var orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isEmpty()) {
                    if (attempt < maxAttempts) {
                        scheduleOrderStatusUpdate(orderId, attempt + 1);
                    } else {
                        log.warn("📦✅ [ORDER] 굿즈 예약 성공 처리 재시도 실패 - orderId: {}", orderId);
                    }
                    return;
                }

                OrderStatus currentStatus = orderOpt.get().getStatus();
                if (currentStatus == OrderStatus.PAYMENT_PENDING) {
                    log.info("📦✅ [ORDER] 이미 PAYMENT_PENDING 상태 - orderId: {}", orderId);
                    return;
                }
                if (currentStatus == OrderStatus.PAID
                        || currentStatus == OrderStatus.COMPLETED
                        || currentStatus == OrderStatus.CANCELLED
                        || currentStatus == OrderStatus.REJECTED) {
                    log.warn("📦✅ [ORDER] 예약 성공 이벤트 무시 - 현재 상태: {}, orderId: {}",
                            currentStatus, orderId);
                    return;
                }

                orderCommandService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING.name(),
                        "재고 예약 완료 - 결제 진행");

                // 결제 생성 요청 이벤트 발행
                orderCommandService.publishPaymentCreateRequestedEvent(orderId);

                orderRepository.findById(orderId).ifPresent(order -> {
                    PaymentUrlResponse paymentUrl = orderCommandService.generatePaymentUrlAfterReservation(order);
                    orderReservationAwaiter.completeSuccess(orderId, paymentUrl);
                });
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : "";
                if (message.contains("주문을 찾을 수 없어요") && attempt < maxAttempts) {
                    scheduleOrderStatusUpdate(orderId, attempt + 1);
                    return;
                }
                log.error("🚨 [ORDER] 굿즈 예약 성공 처리 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 굿즈 예약 실패 이벤트 처리
     */
    private void handleGoodsReservationFailed(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String reason = (String) values.get("reason");

            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.warn("📦❌ [ORDER] 굿즈 예약 실패 처리 - orderId: {}, reason: {}", orderId, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                        "재고 부족 - 주문 실패: " + (reason != null ? reason : "재고 부족"));
                orderReservationAwaiter.completeFailure(orderUuid,
                        reason != null ? reason : "재고 부족");
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 굿즈 예약 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 복합형 예약 성공 이벤트 처리
     */
    private void handleMixedReservationSuccess(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String orderNo = (String) values.get("orderNo");
            String reservationToken = (String) values.get("reservationToken");

            log.info("🔗✅ [ORDER] 복합형 예약 성공 처리 - orderId: {}, orderNo: {}, token: {}",
                    orderIdStr, orderNo, reservationToken);

            if (orderIdStr != null && !orderIdStr.isEmpty()) {
                UUID orderId = UUID.fromString(orderIdStr);

                // 주문 상태를 RESERVED로 업데이트
                orderCommandService.updateOrderStatus(orderId, OrderStatus.RESERVED.name(),
                        "복합형 예약 성공 (스케줄+상품)");

                // 예약 대기자에게 성공 알림
                orderRepository.findById(orderId).ifPresent(order -> {
                    PaymentUrlResponse paymentUrlResponse = orderCommandService
                            .generatePaymentUrlAfterReservation(order);
                    orderReservationAwaiter.completeSuccess(orderId, paymentUrlResponse);
                });

                log.info("✅ [ORDER] 복합형 예약 성공 처리 완료 - orderId: {}", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 복합형 예약 성공 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 복합형 예약 실패 이벤트 처리
     */
    private void handleMixedReservationFailed(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String orderNo = (String) values.get("orderNo");
            String failureReason = (String) values.get("failureReason");

            log.warn("🔗❌ [ORDER] 복합형 예약 실패 처리 - orderId: {}, orderNo: {}, reason: {}",
                    orderIdStr, orderNo, failureReason);

            if (orderIdStr != null && !orderIdStr.isEmpty()) {
                UUID orderId = UUID.fromString(orderIdStr);

                // 주문 상태를 CANCELLED로 업데이트
                orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                        "복합형 예약 실패: " + (failureReason != null ? failureReason : "알 수 없는 이유"));

                // 예약 대기자에게 실패 알림
                orderReservationAwaiter.completeFailure(orderId,
                        failureReason != null ? failureReason : "복합형 예약 실패");

                log.warn("❌ [ORDER] 복합형 예약 실패 처리 완료 - orderId: {}", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 복합형 예약 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 실패한 메시지를 Dead Letter Queue로 전송
     */
    private void sendToDeadLetterQueue(MapRecord<String, String, String> record, Exception e) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("original_stream", record.getStream());
            dlqMessage.put("original_id", record.getId().getValue());
            dlqMessage.put("failed_at", System.currentTimeMillis());
            dlqMessage.put("error_message", e.getMessage());
            dlqMessage.put("error_class", e.getClass().getSimpleName());
            dlqMessage.put("original_data", new HashMap<>(record.getValue()));

            // DLQ Stream에 실패 메시지 저장
            redisTemplate.opsForStream().add("order-failed-events", dlqMessage);

            log.warn("📮 [ORDER] 실패한 메시지를 DLQ로 이동: stream={}, id={}, error={}",
                    record.getStream(), record.getId().getValue(), e.getMessage());

        } catch (Exception dlqError) {
            log.error("🚨 [ORDER] DLQ 전송 실패: {}", dlqError.getMessage(), dlqError);
        }
    }

    /**
     * 수신한 이벤트의 eventType 필수 검증
     */
    private String validateEventType(Map<String, Object> values, String streamName, String recordId) {
        Object eventTypeObj = values.get("eventType");

        if (eventTypeObj == null) {
            String errorMessage = String.format(
                "[CRITICAL] eventType이 누락된 이벤트 수신! stream=%s, recordId=%s, values=%s",
                streamName, recordId, values
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 필수 항목입니다: " + streamName);
        }

        String eventType = eventTypeObj.toString().trim();
        // 따옴표 제거
        eventType = eventType.replaceAll("^\"|\"$", "").trim();
        if (eventType.isEmpty()) {
            String errorMessage = String.format(
                "[CRITICAL] eventType이 비어있음! stream=%s, recordId=%s",
                streamName, recordId
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 필수 항목입니다: " + streamName);
        }

        // eventType 형식 검증 (UPPER_SNAKE_CASE)
        if (!eventType.matches("^[A-Z0-9]+(_[A-Z0-9]+)*$")) {
            String errorMessage = String.format(
                "[CRITICAL] eventType 형식 오류! eventType=%s, stream=%s, recordId=%s (UPPER_SNAKE_CASE 형식 필요)",
                eventType, streamName, recordId
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 UPPER_SNAKE_CASE 형식이어야 합니다: " + eventType);
        }

        log.debug("✅ [ORDER] eventType 검증 통과: {} (stream: {}, recordId: {})",
                eventType, streamName, recordId);

        return eventType;
    }
}
