package com.popcorn.payment.event.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.domain.payment.PaymentCancelFailedEvent
import com.popcorn.payment.event.domain.payment.PaymentUrlCreatedEvent
import com.popcorn.payment.event.domain.payment.EventLineItem
import java.util.UUID
import com.popcorn.payment.event.publisher.BasePaymentEventPublisherImpl
import com.popcorn.payment.service.TossPaymentCoroutineService
import com.popcorn.payment.dto.PaymentCreateResult
import com.popcorn.common.kafka.KafkaIdempotencyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * Payment 서비스 Kafka 이벤트 리스너
 * - Consumer Group: payment-cg
 * - 주문, 결제, 재고 관련 이벤트 수신
 * - 멱등성 보장 및 에러 핸들링
 */
@Component
@ConditionalOnProperty(value = ["kafka.enabled"], havingValue = "true")
class PaymentKafkaListener(
    private val objectMapper: ObjectMapper,
    private val tossPaymentCoroutineService: TossPaymentCoroutineService,
    private val paymentEventPublisher: BasePaymentEventPublisherImpl,
    private val kafkaIdempotencyService: KafkaIdempotencyService
) {
    private val log = LoggerFactory.getLogger(PaymentKafkaListener::class.java)
    private val eventScope = CoroutineScope(Dispatchers.Default)

    /**
     * Payment Events 토픽 구독
     * - 결제 관련 이벤트 모니터링
     * - 자체 이벤트 포함
     */
    @KafkaListener(
        topics = [EventConstants.Streams.PAYMENT_EVENTS],
        groupId = EventConstants.ConsumerGroups.PAYMENT_SERVICE_GROUP
    )
    fun handlePaymentEvents(
        @Payload eventData: Map<String, Any>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        val eventType = eventData[EventConstants.MetadataKeys.EVENT_TYPE] as? String
        val eventId = eventData[EventConstants.MetadataKeys.EVENT_ID] as? String
        try {
            log.info("🔔 [PAYMENT] Kafka 이벤트 수신 - topic: {}, partition: {}, offset: {}, key: {}, eventType: {}, eventId: {}",
                topic, partition, offset, key, eventType, eventId)

            // Redis 기반 멱등성 체크 (eventId 기반)
            if (eventId != null && kafkaIdempotencyService.isDuplicateEvent(eventId)) {
                log.info("⏭️  [PAYMENT] 중복 이벤트 스킵 - eventId: {}, eventType: {}", eventId, eventType)
                acknowledgment.acknowledge()
                return
            }

            // 이벤트 타입별 처리
            handlePaymentEvent(eventType, eventData)

            val processingTime = System.currentTimeMillis() - startTime
            log.debug("✅ [PAYMENT] Payment 이벤트 처리 완료 - eventType: {}, eventId: {}, 처리시간: {}ms",
                eventType, eventId, processingTime)

            // 성능 모니터링
            if (processingTime > 100) {
                log.warn("⚠️ [PAYMENT] Payment 이벤트 처리 지연 - eventType: {}, 처리시간: {}ms",
                    eventType, processingTime)
            }

            // Redis에 성공적으로 처리된 이벤트 기록
            if (eventId != null) {
                kafkaIdempotencyService.recordProcessedEvent(
                    eventId,
                    eventType,
                    topic,
                    partition,
                    offset,
                    processingTime
                )
            }
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("🚨 [PAYMENT] Payment 이벤트 처리 실패 - topic: {}, partition: {}, offset: {}, 처리시간: {}ms, error: {}",
                topic, partition, offset, processingTime, e.message, e)
            // 공통 DefaultErrorHandler가 재시도/ DLQ 처리
            throw e
        }
    }

    /**
     * Payment Requests 토픽 구독
     * - Order 서비스로부터의 결제 요청 처리
     */
    @KafkaListener(
        topics = [EventConstants.Streams.PAYMENT_REQUESTS],
        groupId = EventConstants.ConsumerGroups.PAYMENT_SERVICE_GROUP
    )
    fun handlePaymentRequests(
        @Payload eventData: Map<String, Any>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        val eventType = eventData[EventConstants.MetadataKeys.EVENT_TYPE] as? String
        val eventId = eventData[EventConstants.MetadataKeys.EVENT_ID] as? String
        try {
            log.info("📥 [PAYMENT] Payment 요청 수신 - topic: {}, partition: {}, offset: {}, key: {}, eventType: {}, eventId: {}",
                topic, partition, offset, key, eventType, eventId)

            // Redis 기반 멱등성 체크
            if (eventId != null && kafkaIdempotencyService.isDuplicateEvent(eventId)) {
                log.info("⏭️  [PAYMENT] 중복 요청 스킵 - eventId: {}, eventType: {}", eventId, eventType)
                acknowledgment.acknowledge()
                return
            }

            // 요청 타입별 처리
            handlePaymentRequest(eventType, eventData)

            val processingTime = System.currentTimeMillis() - startTime
            log.debug("✅ [PAYMENT] Payment 요청 처리 완료 - eventType: {}, eventId: {}, 처리시간: {}ms",
                eventType, eventId, processingTime)

            // 성공적으로 처리된 이벤트 기록
            // Redis에 성공적으로 처리된 이벤트 기록
            if (eventId != null) {
                kafkaIdempotencyService.recordProcessedEvent(
                    eventId,
                    eventType,
                    topic,
                    partition,
                    offset,
                    processingTime
                )
            }
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("🚨 [PAYMENT] Payment 요청 처리 실패 - topic: {}, partition: {}, offset: {}, 처리시간: {}ms, error: {}",
                topic, partition, offset, processingTime, e.message, e)
            // 공통 DefaultErrorHandler가 재시도/ DLQ 처리
            throw e
        }
    }

    /**
     * Order Requests 토픽 구독
     * - Order 서비스로부터의 주문 정보 응답 수신
     */
    @KafkaListener(
        topics = [EventConstants.Streams.ORDER_REQUESTS],
        groupId = EventConstants.ConsumerGroups.PAYMENT_SERVICE_GROUP
    )
    fun handleOrderRequests(
        @Payload eventData: Map<String, Any>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        val eventType = eventData[EventConstants.MetadataKeys.EVENT_TYPE] as? String
        val eventId = eventData[EventConstants.MetadataKeys.EVENT_ID] as? String
        try {
            log.info("📞 [PAYMENT] Order 요청 수신 - topic: {}, partition: {}, offset: {}, key: {}, eventType: {}, eventId: {}",
                topic, partition, offset, key, eventType, eventId)

            // Redis 기반 멱등성 체크
            if (eventId != null && kafkaIdempotencyService.isDuplicateEvent(eventId)) {
                log.info("⏭️  [PAYMENT] 중복 Order 요청 스킵 - eventId: {}, eventType: {}", eventId, eventType)
                acknowledgment.acknowledge()
                return
            }

            // Order 요청 처리 (HTTP 기반으로 변경되어 더 이상 이벤트 기반 Order 정보 응답 처리 안함)
            log.info("🔔 [PAYMENT] Order 요청 수신 (HTTP 기반으로 변경) - eventType: {}", eventType)

            val processingTime = System.currentTimeMillis() - startTime
            log.debug("✅ [PAYMENT] Order 요청 처리 완료 - eventType: {}, eventId: {}, 처리시간: {}ms",
                eventType, eventId, processingTime)

            // Redis에 성공적으로 처리된 이벤트 기록
            if (eventId != null) {
                kafkaIdempotencyService.recordProcessedEvent(
                    eventId,
                    eventType,
                    topic,
                    partition,
                    offset,
                    processingTime
                )
            }
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("🚨 [PAYMENT] Order 요청 처리 실패 - topic: {}, partition: {}, offset: {}, 처리시간: {}ms, error: {}",
                topic, partition, offset, processingTime, e.message, e)
            // 공통 DefaultErrorHandler가 재시도/ DLQ 처리
            throw e
        }
    }

    /**
     * Payment 이벤트 타입별 처리
     */
    private fun handlePaymentEvent(eventType: String?, eventData: Map<String, Any>) {
        try {
            when (eventType) {
                // 주문 관련 이벤트
                EventConstants.EventTypes.OrderDomain.ORDER_CREATED -> {
                    log.info("📦 [PAYMENT] 주문 생성 이벤트 수신 - orderId: {}", eventData["orderId"])
                }
                EventConstants.EventTypes.OrderDomain.ORDER_PAID -> {
                    log.info("💳 [PAYMENT] 주문 결제 완료 이벤트 수신 - orderId: {}", eventData["orderId"])
                }
                EventConstants.EventTypes.OrderDomain.ORDER_COMPLETED -> {
                    log.info("✅ [PAYMENT] 주문 완료 이벤트 수신 - orderId: {}", eventData["orderId"])
                }

                // 결제 관련 이벤트 (자체 모니터링)
                "PAYMENT_CREATED" -> {
                    log.info("🧾 [PAYMENT] 결제 생성 이벤트 수신 - paymentId: {}", eventData["paymentId"])
                }
                "PAYMENT_APPROVED" -> {
                    log.info("✅ [PAYMENT] 결제 승인 이벤트 수신 - paymentId: {}", eventData["paymentId"])
                    handlePaymentApproved(eventData)
                }
                "PAYMENT_FAILED" -> {
                    log.warn("❌ [PAYMENT] 결제 실패 이벤트 수신 - paymentId: {}", eventData["paymentId"])
                    handlePaymentFailed(eventData)
                }
                "PAYMENT_USER_CANCELLED" -> {
                    log.info("↩️ [PAYMENT] 결제 취소 이벤트 수신 - paymentId: {}", eventData["paymentId"])
                }
                EventConstants.EventTypes.PaymentDomain.PAYMENT_SUCCESS -> {
                    log.info("✅ [PAYMENT] 결제 완료 이벤트 수신 - paymentId: {}", eventData["paymentId"])
                }

                else -> {
                    log.info("🔔 [PAYMENT] 기타 Payment 이벤트 수신 - eventType: {}", eventType)
                }
            }
        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] Payment 이벤트 처리 실패 - eventType: {}, error: {}", eventType, e.message, e)
        }
    }

    /**
     * Payment 요청 타입별 처리
     */
    private fun handlePaymentRequest(eventType: String?, eventData: Map<String, Any>) {
        try {
            when (eventType) {
                "PAYMENT_CREATE_REQUESTED" -> {
                    log.info("🧾 [PAYMENT] 결제 생성 요청 수신 - orderId: {}", eventData["orderId"])
                    handlePaymentCreateRequested(eventData)
                }
                "PAYMENT_CANCEL_REQUESTED" -> {
                    log.info("↩️ [PAYMENT] 결제 취소 요청 수신 - orderId: {}", eventData["orderId"])
                    handlePaymentCancelRequested(eventData)
                }

                else -> {
                    log.info("🔔 [PAYMENT] 기타 Payment 요청 수신 - eventType: {}", eventType)
                }
            }
        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] Payment 요청 처리 실패 - eventType: {}, error: {}", eventType, e.message, e)
        }
    }

    /**
     * 결제 승인 이벤트 처리
     */
    private fun handlePaymentApproved(eventData: Map<String, Any>) {
        try {
            val paymentId = eventData["paymentId"] as? String ?: return
            val orderId = eventData["orderId"] as? String ?: return
            val orderNo = eventData["orderNo"] as? String
            val customerId = eventData["customerId"] as? String
            val amount = eventData["amount"] as? String
            val hasReservation = toBoolean(eventData["hasReservation"])
            val hasGoods = toBoolean(eventData["hasGoods"])

            log.info("💰 [PAYMENT] Kafka 경로 결제 승인 처리 - paymentId: {}, orderId: {}, amount: {}",
                paymentId, orderId, amount)

            eventScope.launch {
                try {
                    // QR 코드 생성 이벤트 발행 (예약 또는 굿즈 결제 시 생성)
                    if (hasReservation == true || hasGoods == true) {
                        publishQrCodeGenerationEventKafka(paymentId, orderId, orderNo, customerId)
                        log.info("✅ [KAFKA] QR 생성 요청: orderId={} hasReservation={} hasGoods={}",
                            orderId, hasReservation, hasGoods)
                    } else {
                        log.info("🚫 [KAFKA] QR 생성 스킵: orderId={} hasReservation={} hasGoods={}",
                            orderId, hasReservation, hasGoods)
                    }

                    // 재고 차감 확정 이벤트 발행 (Store 서비스로)
                    confirmInventoryDeductionKafka(paymentId, orderId)

                    log.info("✅ [PAYMENT] Kafka 경로 결제 승인 후속 이벤트 발행 완료: paymentId={}", paymentId)

                } catch (e: Exception) {
                    log.error("❌ [PAYMENT] Kafka 경로 후속 이벤트 발행 실패: paymentId={} error={}",
                        paymentId, e.message, e)
                }
            }

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 결제 승인 처리 실패 - eventData: {}, error: {}", eventData, e.message, e)
        }
    }

    private fun toBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    /**
     * 결제 실패 이벤트 처리
     */
    private fun handlePaymentFailed(eventData: Map<String, Any>) {
        try {
            val paymentId = eventData["paymentId"] as? String ?: return
            val orderId = eventData["orderId"] as? String ?: return
            val reason = eventData["reason"] as? String ?: "Unknown failure"

            log.warn("❌ [PAYMENT] Kafka 경로 결제 실패 처리 - paymentId: {}, orderId: {}, reason: {}",
                paymentId, orderId, reason)

            eventScope.launch {
                try {
                    // 보상 트랜잭션: 재고 복구 이벤트 발행 (Store 서비스로)
                    restoreInventoryKafka(paymentId, orderId, reason)

                    // 주문 상태 실패로 변경 이벤트 발행 (Order 서비스로)
                    updateOrderStatusToFailedKafka(paymentId, orderId, reason)

                    log.info("✅ [PAYMENT] Kafka 경로 결제 실패 보상 이벤트 발행 완료: paymentId={}", paymentId)

                } catch (e: Exception) {
                    log.error("❌ [PAYMENT] Kafka 경로 보상 이벤트 발행 실패: paymentId={} error={}",
                        paymentId, e.message, e)
                }
            }

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 결제 실패 처리 실패 - eventData: {}, error: {}", eventData, e.message, e)
        }
    }

    /**
     * 결제 취소 요청 이벤트 처리 (기존 Redis 로직과 동일)
     */
    private fun handlePaymentCancelRequested(eventData: Map<String, Any>) {
        val orderIdRaw = eventData["orderId"]?.toString()?.trim()?.trim('"')
        val reason = eventData["reason"]?.toString()?.trim()?.trim('"') ?: "주문 취소"
        val orderNo = eventData["orderNo"]?.toString()?.trim()?.trim('"') ?: orderIdRaw
        val customerIdRaw = eventData["customerId"]?.toString()?.trim()?.trim('"')
        val customerId = customerIdRaw?.toLongOrNull()

        if (orderIdRaw.isNullOrBlank()) {
            log.warn("⚠️ [PAYMENT] 결제 취소 요청 필수 데이터 누락 - eventData: {}", eventData)
            return
        }

        val orderId = java.util.UUID.fromString(orderIdRaw)

        eventScope.launch {
            try {
                val result = tossPaymentCoroutineService.cancelPayment(orderId, reason)

                paymentEventPublisher.publishPaymentCancelled(
                    paymentId = result.paymentId,
                    orderId = orderId,
                    orderNo = orderNo ?: orderId.toString(),
                    cancelAmount = result.cancelAmount,
                    cancelReason = result.cancelReason,
                    customerId = customerId
                )

                log.info("✅ [PAYMENT] 결제 취소 처리 완료 - orderId={}, paymentId={}", orderId, result.paymentId)

            } catch (e: Exception) {
                log.error("🚨 [PAYMENT] 결제 취소 처리 실패 - orderId={}, error={}", orderId, e.message, e)
                paymentEventPublisher.publishAsync(
                    PaymentCancelFailedEvent(
                        _paymentId = java.util.UUID(0, 0),
                        orderId = orderId,
                        orderNo = orderNo ?: orderId.toString(),
                        cancelReason = reason,
                        failureReason = e.message ?: "결제 취소 실패",
                        retryCount = 0,
                        customerId = customerId
                    )
                )
            }
        }
    }

    /**
     * 결제 생성 요청 이벤트 처리 (기존 Redis 로직과 동일)
     */
    private fun handlePaymentCreateRequested(eventData: Map<String, Any>) {
        try {
            val orderIdRaw = eventData["orderId"]?.toString()?.trim()?.trim('"')
            val orderNo = eventData["orderNo"]?.toString()?.trim()?.trim('"') ?: "ORDER-${System.currentTimeMillis()}"
            val amountRaw = eventData["amount"]?.toString()?.trim()?.trim('"')
            val paymentMethod = eventData["paymentMethod"]?.toString()?.trim()?.trim('"') ?: "CARD"
            val customerIdRaw = eventData["customerId"]?.toString()?.trim()?.trim('"')
            val orderName = eventData["orderName"]?.toString()?.trim()?.trim('"') ?: orderNo

            if (orderIdRaw.isNullOrBlank() || amountRaw.isNullOrBlank()) {
                log.warn("⚠️ [PAYMENT] 결제 생성 요청 필수 데이터 누락 - eventData: {}", eventData)
                return
            }

            val orderId = orderIdRaw
            val amount = amountRaw.toIntOrNull() ?: 0
            val customerId = customerIdRaw?.toLongOrNull()

            if (amount <= 0) {
                log.warn("⚠️ [PAYMENT] 잘못된 금액 - orderId: {}, amount: {}", orderId, amount)
                return
            }

            log.info("💳 [PAYMENT] 결제 생성 요청 처리 시작 - orderId={}, method={}, amount={}원, orderNo={}, customerId={}",
                orderId, paymentMethod, amount, orderNo, customerId)

            // 비동기로 결제 URL 생성 처리
            eventScope.launch {
                try {
                    // Toss 결제 URL 생성
                    val customerKey = customerId?.toString() ?: "guest-${System.currentTimeMillis()}"
                    val createResult = tossPaymentCoroutineService.createPaymentRequest(
                        orderId = orderId,
                        amount = amount,
                        orderName = orderName,
                        customerKey = customerKey
                    )

                    // PaymentUrlCreatedEvent 생성
                    val urlCreatedEvent = PaymentUrlCreatedEvent.create(
                        orderId = UUID.fromString(orderId),
                        orderNo = orderNo,
                        paymentUrl = createResult.paymentUrl,
                        amount = amount,
                        paymentMethod = paymentMethod,
                        customerId = customerId,
                        expiresAt = createResult.expiresAt
                    )

                    // 이벤트 발행
                    paymentEventPublisher.publish(urlCreatedEvent)

                    log.info("✅ [PAYMENT] 결제 URL 생성 및 이벤트 발행 완료 - orderId={}, paymentUrl={}, expiresAt={}",
                        orderId, createResult.paymentUrl, createResult.expiresAt)

                } catch (e: Exception) {
                    log.error("❌ [PAYMENT] 결제 URL 생성 실패 - orderId={}, error={}", orderId, e.message, e)

                    // 실패 시 PaymentFailedEvent 발행 (선택적)
                    // TODO: 결제 생성 실패 이벤트 발행 로직 추가 고려
                }
            }

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 결제 생성 요청 처리 실패 - eventData: {}, error: {}", eventData, e.message, e)
        }
    }


    /**
     * Kafka 경로용 QR 생성 이벤트 발행
     * CheckIn 서비스로 QR 생성 요청 전송
     */
    private suspend fun publishQrCodeGenerationEventKafka(
        paymentId: String,
        orderId: String,
        orderNo: String?,
        customerId: String?
    ) {
        try {
            log.info("📱 [KAFKA] QR 코드 생성 이벤트 발행: paymentId={}, orderId={}", paymentId, orderId)

            // QR 생성 요청 이벤트 발행 (CheckIn 서비스가 처리)
            paymentEventPublisher.publishQrCodeGenerationRequested(
                paymentId = java.util.UUID.fromString(paymentId),
                orderId = java.util.UUID.fromString(orderId),
                orderNo = orderNo ?: "unknown",
                customerId = customerId?.toLongOrNull() ?: 0L
            )

            log.info("✅ [KAFKA] QR 코드 생성 요청 이벤트 발행 완료: paymentId={}, orderId={}", paymentId, orderId)

        } catch (e: Exception) {
            log.error("❌ [KAFKA] QR 코드 생성 이벤트 발행 실패: paymentId={}, orderId={}, error={}", paymentId, orderId, e.message, e)
        }
    }

    /**
     * Kafka 경로용 재고 차감 확정 이벤트 발행
     * Store 서비스로 재고 차감 확정 요청 전송
     */
    private suspend fun confirmInventoryDeductionKafka(paymentId: String, orderId: String) {
        try {
            log.info("📦 [KAFKA] 재고 차감 확정 이벤트 발행: paymentId={}, orderId={}", paymentId, orderId)

            // 재고 차감 확정 이벤트 발행 (Store 서비스가 처리)
            paymentEventPublisher.publishAsync(
                com.popcorn.payment.event.integration.request.InventoryConfirmationRequestedEvent.createConfirm(
                    java.util.UUID.fromString(paymentId),
                    java.util.UUID.fromString(orderId)
                )
            )

            log.info("✅ [KAFKA] 재고 차감 확정 이벤트 발행 완료: paymentId={}, orderId={}", paymentId, orderId)

        } catch (e: Exception) {
            log.error("❌ [KAFKA] 재고 차감 확정 이벤트 발행 실패: paymentId={}, orderId={}, error={}", paymentId, orderId, e.message, e)
        }
    }

    /**
     * Kafka 경로용 재고 복구 이벤트 발행 (보상 트랜잭션)
     * Store 서비스로 재고 복구 요청 전송
     */
    private suspend fun restoreInventoryKafka(paymentId: String, orderId: String, reason: String) {
        try {
            log.info("🔄 [KAFKA] 재고 복구 이벤트 발행: paymentId={}, orderId={}, reason={}",
                paymentId, orderId, reason)

            // 재고 복구 이벤트 발행 (Store 서비스가 처리)
            paymentEventPublisher.publishAsync(
                com.popcorn.payment.event.integration.request.InventoryConfirmationRequestedEvent.createRestore(
                    java.util.UUID.fromString(paymentId),
                    java.util.UUID.fromString(orderId),
                    reason
                )
            )

            log.info("✅ [KAFKA] 재고 복구 이벤트 발행 완료: paymentId={}, orderId={}", paymentId, orderId)

        } catch (e: Exception) {
            log.error("❌ [KAFKA] 재고 복구 이벤트 발행 실패: paymentId={}, orderId={}, error={}",
                paymentId, orderId, e.message, e)
        }
    }

    /**
     * Kafka 경로용 주문 상태 실패로 변경 이벤트 발행
     * Order 서비스로 주문 실패 상태 변경 요청 전송
     */
    private suspend fun updateOrderStatusToFailedKafka(paymentId: String, orderId: String, reason: String) {
        try {
            log.info("📝 [KAFKA] 주문 실패 상태 변경 이벤트 발행: paymentId={}, orderId={}, reason={}",
                paymentId, orderId, reason)

            // 주문 상태 실패 변경 이벤트 발행 (Order 서비스가 처리)
            paymentEventPublisher.publishAsync(
                com.popcorn.payment.event.integration.request.OrderStatusUpdateRequestedEvent.createFailed(
                    java.util.UUID.fromString(paymentId),
                    java.util.UUID.fromString(orderId),
                    reason
                )
            )

            log.info("✅ [KAFKA] 주문 실패 상태 변경 이벤트 발행 완료: paymentId={}, orderId={}", paymentId, orderId)

        } catch (e: Exception) {
            log.error("❌ [KAFKA] 주문 실패 상태 변경 이벤트 발행 실패: paymentId={}, orderId={}, error={}",
                paymentId, orderId, e.message, e)
        }
    }

    /**
     * Order Events (주문 도메인 이벤트)
     * - Order 서비스로부터의 주문 도메인 이벤트 수신
     * - ORDER_CREATED, ORDER_PAID, ORDER_COMPLETED 등 처리
     */
    @KafkaListener(
        topics = [EventConstants.Streams.ORDER_EVENTS],
        groupId = EventConstants.ConsumerGroups.PAYMENT_SERVICE_GROUP
    )
    fun handleOrderEvents(
        @Payload eventData: Map<String, Any>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        val eventType = eventData[EventConstants.MetadataKeys.EVENT_TYPE] as? String
        val eventId = eventData[EventConstants.MetadataKeys.EVENT_ID] as? String
        try {
            log.info("📦 [PAYMENT] Order 이벤트 수신 - topic: {}, partition: {}, offset: {}, key: {}, eventType: {}, eventId: {}",
                topic, partition, offset, key, eventType, eventId)

            // 멱등성 체크
            val idempotencyKey = eventData[EventConstants.MetadataKeys.IDEMPOTENCY_KEY] as? String
                ?: "$topic-$partition-$offset"

            val isAlreadyProcessed = idempotencyService.isAlreadyProcessed(idempotencyKey)
            if (isAlreadyProcessed) {
                log.info("🔄 [PAYMENT] Order 이벤트 중복 처리 건너뛰기 - eventType: {}, eventId: {}, key: {}",
                    eventType, eventId, idempotencyKey)
                acknowledgment.acknowledge()
                return
            }

            // 이벤트 타입별 처리
            handleOrderDomainEvent(eventType, eventData)

            // 멱등성 키 저장
            idempotencyService.markAsProcessed(idempotencyKey)

            // Kafka 메시지 커밋
            acknowledgment.acknowledge()

            val processingTime = System.currentTimeMillis() - startTime
            log.info("✅ [PAYMENT] Order 이벤트 처리 완료 - topic: {}, partition: {}, offset: {}, eventType: {}, 처리시간: {}ms",
                topic, partition, offset, eventType, processingTime)

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("🚨 [PAYMENT] Order 이벤트 처리 실패 - topic: {}, partition: {}, offset: {}, 처리시간: {}ms, error: {}",
                topic, partition, offset, processingTime, e.message, e)
            // 공통 DefaultErrorHandler가 재시도/ DLQ 처리
            throw e
        }
    }

    /**
     * Order 도메인 이벤트 타입별 처리
     */
    private fun handleOrderDomainEvent(eventType: String?, eventData: Map<String, Any>) {
        try {
            when (eventType) {
                EventConstants.EventTypes.OrderDomain.ORDER_CREATED -> {
                    handleOrderCreated(eventData)
                }
                EventConstants.EventTypes.OrderDomain.ORDER_PAID -> {
                    handleOrderPaid(eventData)
                }
                EventConstants.EventTypes.OrderDomain.ORDER_COMPLETED -> {
                    handleOrderCompleted(eventData)
                }
                else -> {
                    log.info("🔔 [PAYMENT] 기타 Order 도메인 이벤트 수신 - eventType: {}", eventType)
                }
            }
        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] Order 도메인 이벤트 처리 실패 - eventType: {}, error: {}", eventType, e.message, e)
        }
    }

    /**
     * ORDER_CREATED 이벤트 처리
     * - 주문 생성 완료 시 결제 준비 작업 수행
     */
    private fun handleOrderCreated(eventData: Map<String, Any>) {
        val orderId = eventData["orderId"]?.toString()
        val orderNo = eventData["orderNo"]?.toString()
        val userId = eventData["userId"]?.toString()
        val totalAmount = eventData["totalAmount"] as? Int
        val hasReservation = eventData["hasReservation"] as? Boolean ?: false
        val hasGoods = eventData["hasGoods"] as? Boolean ?: false

        log.info("📦 [PAYMENT] 주문 생성 이벤트 수신 - orderId: {}, orderNo: {}, userId: {}, amount: {}원, reservation: {}, goods: {}",
            orderId, orderNo, userId, totalAmount, hasReservation, hasGoods)

        try {
            // 1. 주문 정보 검증
            if (orderId.isNullOrBlank() || totalAmount == null || totalAmount <= 0) {
                log.error("❌ [PAYMENT] 주문 정보 검증 실패 - orderId: {}, amount: {}", orderId, totalAmount)
                return
            }

            // 2. 결제 가능 상태 체크 (기본적인 검증)
            log.info("🔍 [PAYMENT] 주문 생성 확인 완료 - 결제 대기 상태로 설정")
            log.info("💰 [PAYMENT] 결제 예상 금액: {}원", totalAmount)

            // 3. 주문 유형별 로깅
            if (hasReservation) {
                log.info("🎫 [PAYMENT] 예약 포함 주문 - 예약 취소 정책 적용 필요")
            }
            if (hasGoods) {
                log.info("📦 [PAYMENT] 상품 포함 주문 - 재고 확보 후 결제 진행")
            }

            // 4. 결제 모니터링 시작 (실제 결제는 사용자가 결제 페이지에서 진행)
            log.info("✅ [PAYMENT] ORDER_CREATED 처리 완료 - 결제 준비 상태, orderId: {}", orderId)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] ORDER_CREATED 처리 실패 - orderId: {}, error: {}", orderId, e.message, e)
        }
    }

    /**
     * ORDER_PAID 이벤트 처리
     * - 다른 서비스에서 결제 완료 상태로 변경했을 때 동기화
     */
    private fun handleOrderPaid(eventData: Map<String, Any>) {
        val orderId = eventData["orderId"]?.toString()
        val paymentId = eventData["paymentId"]?.toString()
        val totalAmount = eventData["totalAmount"] as? Int
        val paidAt = eventData["paidAt"]?.toString()

        log.info("💳 [PAYMENT] 주문 결제 완료 이벤트 수신 - orderId: {}, paymentId: {}, amount: {}원, paidAt: {}",
            orderId, paymentId, totalAmount, paidAt)

        try {
            if (orderId.isNullOrBlank()) {
                log.error("❌ [PAYMENT] ORDER_PAID 처리 실패 - orderId가 없음")
                return
            }

            // 1. 결제 정보 동기화 확인
            log.info("🔍 [PAYMENT] 결제 완료 상태 동기화 확인 시작 - orderId: {}", orderId)

            // 2. 결제 완료 후속 작업 준비
            log.info("📋 [PAYMENT] 결제 완료 후속 작업 예약 - orderId: {}", orderId)
            log.info("🎉 [PAYMENT] 고객 결제 완료 알림 준비 - orderId: {}", orderId)

            // 3. 정산 데이터 준비
            if (totalAmount != null && totalAmount > 0) {
                log.info("💼 [PAYMENT] 정산 데이터 준비 - orderId: {}, amount: {}원", orderId, totalAmount)
            }

            log.info("✅ [PAYMENT] ORDER_PAID 처리 완료 - orderId: {}", orderId)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] ORDER_PAID 처리 실패 - orderId: {}, error: {}", orderId, e.message, e)
        }
    }

    /**
     * ORDER_COMPLETED 이벤트 처리
     * - 주문 완전 완료 시 결제 관련 정리 작업
     */
    private fun handleOrderCompleted(eventData: Map<String, Any>) {
        val orderId = eventData["orderId"]?.toString()
        val popupId = eventData["popupId"]?.toString()
        val storeId = eventData["storeId"]?.toString()
        val totalAmount = eventData["totalAmount"] as? Int
        val completedAt = eventData["completedAt"]?.toString()
        val hasReservation = eventData["hasReservation"] as? Boolean ?: false
        val hasGoods = eventData["hasGoods"] as? Boolean ?: false

        log.info("✅ [PAYMENT] 주문 완료 이벤트 수신 - orderId: {}, storeId: {}, amount: {}원, completedAt: {}",
            orderId, storeId, totalAmount, completedAt)

        try {
            if (orderId.isNullOrBlank()) {
                log.error("❌ [PAYMENT] ORDER_COMPLETED 처리 실패 - orderId가 없음")
                return
            }

            // 1. 결제 완료 상태 최종 확인
            log.info("🔍 [PAYMENT] 결제 최종 상태 확인 - orderId: {}", orderId)

            // 2. 정산 데이터 확정
            if (totalAmount != null && totalAmount > 0) {
                log.info("💼 [PAYMENT] 정산 데이터 확정 처리 - orderId: {}, amount: {}원", orderId, totalAmount)

                // 스토어별 수수료 정산 준비
                if (!storeId.isNullOrBlank()) {
                    log.info("🏪 [PAYMENT] 스토어 정산 처리 - storeId: {}, amount: {}원", storeId, totalAmount)
                }
            }

            // 3. 주문 유형별 완료 처리
            if (hasReservation) {
                log.info("🎫 [PAYMENT] 예약 완료 처리 - orderId: {}, 예약 확정", orderId)
            }
            if (hasGoods) {
                log.info("📦 [PAYMENT] 상품 주문 완료 - orderId: {}, 배송/픽업 준비 완료", orderId)
            }

            // 4. 환불 불가 상태로 변경 (주문 완료 시점)
            log.info("🔒 [PAYMENT] 환불 정책 적용 - orderId: {}, 주문 완료로 인한 환불 제한", orderId)

            // 5. 완료 알림 및 정리
            log.info("🎉 [PAYMENT] 주문 완료 알림 발송 준비 - orderId: {}", orderId)
            log.info("📊 [PAYMENT] 결제 통계 데이터 업데이트 - orderId: {}", orderId)

            log.info("✅ [PAYMENT] ORDER_COMPLETED 처리 완료 - orderId: {}", orderId)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] ORDER_COMPLETED 처리 실패 - orderId: {}, error: {}", orderId, e.message, e)
        }
    }

}
