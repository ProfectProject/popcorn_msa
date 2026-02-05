package com.popcorn.payment.event.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.domain.payment.OrderInfoResponseEvent
import com.popcorn.payment.event.domain.payment.PaymentCancelFailedEvent
import com.popcorn.payment.event.domain.payment.EventLineItem
import com.popcorn.payment.event.publisher.BasePaymentEventPublisherImpl
import com.popcorn.payment.service.PaymentOrderInfoService
import com.popcorn.payment.service.TossPaymentCoroutineService
import com.popcorn.payment.service.kafka.KafkaIdempotencyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
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
    private val paymentOrderInfoService: PaymentOrderInfoService,
    private val tossPaymentCoroutineService: TossPaymentCoroutineService,
    private val paymentEventPublisher: BasePaymentEventPublisherImpl,
    private val kafkaIdempotencyService: KafkaIdempotencyService,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(PaymentKafkaListener::class.java)
    private val eventScope = CoroutineScope(Dispatchers.Default)

    // 상수 정의
    companion object {
        private const val DEFAULT_RETRY_ATTEMPT = 1
        private const val DEFAULT_MAX_RETRIES = 3
    }

    /**
     * 메시지 처리 데이터 클래스 (매개변수 수 줄이기용)
     */
    private data class MessageProcessingData(
        val originalTopic: String,
        val partition: Int,
        val offset: Long,
        val eventData: Map<String, Any>,
        val exception: Exception,
        val eventType: String? = null,
        val eventId: String? = null,
        val retryAttempt: Int = DEFAULT_RETRY_ATTEMPT,
        val maxRetries: Int = DEFAULT_MAX_RETRIES
    )


    /**
     * Payment Events 토픽 구독
     * - 결제 관련 이벤트 모니터링
     * - 자체 이벤트 포함
     */
    @KafkaListener(
        topics = [EventConstants.Streams.PAYMENT_EVENTS],
        groupId = EventConstants.ConsumerGroups.PAYMENT_SERVICE_GROUP,
        concurrency = "6"
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
                    eventId = eventId,
                    eventType = eventType,
                    topic = topic,
                    partition = partition,
                    offset = offset,
                    processingTimeMs = processingTime
                )
            }
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("🚨 [PAYMENT] Payment 이벤트 처리 실패 - topic: {}, partition: {}, offset: {}, 처리시간: {}ms, error: {}",
                topic, partition, offset, processingTime, e.message, e)

            // 실패한 메시지를 재시도 큐로 전송 (Exponential Backoff)
            val processingData = MessageProcessingData(
                originalTopic = topic,
                partition = partition,
                offset = offset,
                eventData = eventData,
                exception = e,
                eventType = eventType,
                eventId = eventId,
                retryAttempt = DEFAULT_RETRY_ATTEMPT,
                maxRetries = DEFAULT_MAX_RETRIES
            )
            sendToRetryQueue(processingData)

            acknowledgment.acknowledge()
        }
    }

    /**
     * Payment Requests 토픽 구독
     * - Order 서비스로부터의 결제 요청 처리
     */
    @KafkaListener(
        topics = [EventConstants.Streams.PAYMENT_REQUESTS],
        groupId = EventConstants.ConsumerGroups.PAYMENT_SERVICE_GROUP,
        concurrency = "6"
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
                    eventId = eventId,
                    eventType = eventType,
                    topic = topic,
                    partition = partition,
                    offset = offset,
                    processingTimeMs = processingTime
                )
            }
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("🚨 [PAYMENT] Payment 요청 처리 실패 - topic: {}, partition: {}, offset: {}, 처리시간: {}ms, error: {}",
                topic, partition, offset, processingTime, e.message, e)

            // 실패한 메시지를 재시도 큐로 전송
            val processingData = MessageProcessingData(
                originalTopic = topic,
                partition = partition,
                offset = offset,
                eventData = eventData,
                exception = e,
                eventType = eventType,
                eventId = eventId,
                retryAttempt = DEFAULT_RETRY_ATTEMPT,
                maxRetries = DEFAULT_MAX_RETRIES
            )
            sendToRetryQueue(processingData)

            acknowledgment.acknowledge()
        }
    }

    /**
     * Order Requests 토픽 구독
     * - Order 서비스로부터의 주문 정보 응답 수신
     */
    @KafkaListener(
        topics = [EventConstants.Streams.ORDER_REQUESTS],
        groupId = EventConstants.ConsumerGroups.PAYMENT_SERVICE_GROUP,
        concurrency = "6"
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

            // Order 정보 응답 처리
            when (eventType) {
                EventConstants.EventTypes.Integration.ORDER_INFO_RESPONSE -> handleOrderInfoResponse(eventData)
                else -> log.info("🔔 [PAYMENT] 기타 Order 요청 - eventType: {}", eventType)
            }

            val processingTime = System.currentTimeMillis() - startTime
            log.debug("✅ [PAYMENT] Order 요청 처리 완료 - eventType: {}, eventId: {}, 처리시간: {}ms",
                eventType, eventId, processingTime)

            // Redis에 성공적으로 처리된 이벤트 기록
            if (eventId != null) {
                kafkaIdempotencyService.recordProcessedEvent(
                    eventId = eventId,
                    eventType = eventType,
                    topic = topic,
                    partition = partition,
                    offset = offset,
                    processingTimeMs = processingTime
                )
            }
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("🚨 [PAYMENT] Order 요청 처리 실패 - topic: {}, partition: {}, offset: {}, 처리시간: {}ms, error: {}",
                topic, partition, offset, processingTime, e.message, e)

            // 실패한 메시지를 재시도 큐로 전송
            val processingData = MessageProcessingData(
                originalTopic = topic,
                partition = partition,
                offset = offset,
                eventData = eventData,
                exception = e,
                eventType = eventType,
                eventId = eventId,
                retryAttempt = DEFAULT_RETRY_ATTEMPT,
                maxRetries = DEFAULT_MAX_RETRIES
            )
            sendToRetryQueue(processingData)

            acknowledgment.acknowledge()
        }
    }

    /**
     * Payment 이벤트 타입별 처리
     */
    private fun handlePaymentEvent(eventType: String?, eventData: Map<String, Any>) {
        try {
            when (eventType) {
                // 주문 관련 이벤트
                "ORDER_CREATED" -> {
                    log.info("📦 [PAYMENT] 주문 생성 이벤트 수신 - orderId: {}", eventData["orderId"])
                }
                "ORDER_PAID" -> {
                    log.info("💳 [PAYMENT] 주문 결제 완료 이벤트 수신 - orderId: {}", eventData["orderId"])
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
                    // QR 코드 생성 이벤트 발행 (예약 결제만, CheckIns 직결)
                    if (hasReservation == true) {
                        publishQrCodeGenerationEventKafka(paymentId, orderId, orderNo, customerId)
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
            val orderNo = eventData["orderNo"]?.toString()?.trim()?.trim('"')
            val amountRaw = eventData["amount"]?.toString()?.trim()?.trim('"')
            val paymentMethod = eventData["paymentMethod"]?.toString()?.trim()?.trim('"') ?: "CARD"
            val customerIdRaw = eventData["customerId"]?.toString()?.trim()?.trim('"')

            if (orderIdRaw.isNullOrBlank() || amountRaw.isNullOrBlank()) {
                log.warn("⚠️ [PAYMENT] 결제 생성 요청 필수 데이터 누락 - eventData: {}", eventData)
                return
            }

            log.info("📝 [PAYMENT] 결제 생성 요청 수신(대기) - orderId={}, method={}, amount={}원, orderNo={}, customerId={}",
                orderIdRaw, paymentMethod, amountRaw, orderNo, customerIdRaw)
            log.info("🕒 [PAYMENT] 결제 기록 생성은 승인 시점에 처리됩니다 - orderId={}", orderIdRaw)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 결제 생성 요청 처리 실패 - eventData: {}, error: {}", eventData, e.message, e)
        }
    }

    /**
     * Order 정보 응답 이벤트 처리 (기존 Redis 로직과 동일)
     */
    private fun handleOrderInfoResponse(eventData: Map<String, Any>) {
        try {
            log.info("📞 [PAYMENT] Order 정보 응답 처리 시작 - requestId: {}, success: {}",
                eventData["requestId"], eventData["success"])

            // Map을 OrderInfoResponseEvent로 변환
            val customerIdValue = normalizeString(eventData["customerId"])
                ?: normalizeString(eventData["actualUserId"])
            val orderStatusValue = normalizeString(eventData["orderStatus"])
                ?: normalizeString(eventData["status"])
            val totalAmountValue = normalizeString(eventData["totalAmount"])
                ?: normalizeString(eventData["amount"])

            val response = OrderInfoResponseEvent(
                requestId = normalizeString(eventData["requestId"]),
                success = normalizeString(eventData["success"])?.toBoolean() ?: false,
                errorMessage = normalizeString(eventData["errorMessage"])
                    ?: normalizeString(eventData["message"]),
                actualLines = parseOrderLineInfos(eventData["actualLines"]),
                customerId = customerIdValue?.toLongOrNull(),
                orderStatus = orderStatusValue,
                totalAmount = totalAmountValue?.toIntOrNull(),
                actualOrderNo = normalizeString(eventData["actualOrderNo"]),
                actualUserId = normalizeString(eventData["actualUserId"])?.toLongOrNull(),
                actualPopupId = normalizeString(eventData["actualPopupId"]),
                actualHasReservation = normalizeString(eventData["actualHasReservation"])?.toBoolean(),
                actualHasGoods = normalizeString(eventData["actualHasGoods"])?.toBoolean()
            )

            // PaymentOrderInfoService에 응답 전달
            paymentOrderInfoService.handleOrderInfoResponse(response)

            log.info("✅ [PAYMENT] Order 정보 응답 처리 완료 - requestId: {}, success: {}",
                response.requestId, response.success)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] Order 정보 응답 처리 실패 - eventData: {}, error: {}", eventData, e.message, e)
        }
    }

    // === 유틸리티 메서드들 (기존 Redis 로직과 동일) ===

    private fun normalizeString(value: Any?): String? {
        val raw = value as? String ?: return null
        val trimmed = raw.trim()
        return trimmed.trim('"')
    }

    private fun parseOrderLineInfos(rawLines: Any?): List<EventLineItem> {
        log.info("🔍 [DEBUG] parseOrderLineInfos 시작 - rawLines type: {}, value: {}",
            rawLines?.javaClass?.simpleName, rawLines)

        if (rawLines == null) {
            log.info("🔍 [DEBUG] rawLines is null, returning empty list")
            return emptyList()
        }

        return try {
            when (rawLines) {
                is List<*> -> {
                    log.info("🔍 [DEBUG] rawLines is List, size: {}", rawLines.size)
                    rawLines.mapNotNull { item ->
                        log.info("🔍 [DEBUG] Processing list item type: {}, value: {}",
                            item?.javaClass?.simpleName, item)
                        when (item) {
                            is Map<*, *> -> {
                                val result = objectMapper.convertValue(item, EventLineItem::class.java)
                                log.info("🔍 [DEBUG] Converted map to EventLineItem: {}", result)
                                result
                            }
                            else -> {
                                log.warn("🔍 [DEBUG] Unexpected list item type: {}", item?.javaClass?.simpleName)
                                null
                            }
                        }
                    }
                }
                is String -> {
                    log.info("🔍 [DEBUG] rawLines is String: '{}'", rawLines)
                    var trimmed = rawLines.trim().trim('"')
                    log.info("🔍 [DEBUG] After basic trimming: '{}'", trimmed)

                    // 이중 이스케이프 처리
                    if (trimmed.startsWith("[{\\\"") || trimmed.startsWith("{\\\"")) {
                        log.info("🔍 [DEBUG] Detected escaped JSON, unescaping...")
                        trimmed = trimmed.replace("\\\"", "\"").replace("\\\\", "\\")
                        log.info("🔍 [DEBUG] After unescaping: '{}'", trimmed)
                    }

                    if (trimmed.isBlank() || trimmed == "[]") {
                        log.info("🔍 [DEBUG] Empty or blank string, returning empty list")
                        emptyList()
                    } else {
                        log.info("🔍 [DEBUG] Attempting to parse JSON string: '{}'", trimmed)
                        val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<EventLineItem>>() {}
                        val result = objectMapper.readValue(trimmed, typeRef)
                        log.info("🔍 [DEBUG] JSON parsing successful, result count: {}", result.size)
                        result.forEachIndexed { index, item ->
                            log.info("🔍 [DEBUG] Parsed item[{}]: itemType={}, qty={}, unitPrice={}, linePrice={}",
                                index, item.itemType, item.qty, item.unitPrice, item.linePrice)
                        }
                        result
                    }
                }
                else -> {
                    log.info("🔍 [DEBUG] rawLines is other type: {}, attempting direct conversion", rawLines.javaClass.simpleName)
                    val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<EventLineItem>>() {}
                    val result = objectMapper.convertValue(rawLines, typeRef)
                    log.info("🔍 [DEBUG] Direct conversion successful, result count: {}", result.size)
                    result
                }
            }
        } catch (e: Exception) {
            log.error("❌ [DEBUG] EventLineItem JSON 파싱 실패 - rawLines: {}, error: {}", rawLines, e.message, e)
            emptyList()
        }
    }

    /**
     * 실패한 메시지를 DLQ로 라우팅
     * - 원본 메시지 정보와 에러 정보를 포함하여 DLQ 토픽으로 전송
     * - 관리자 모니터링 및 수동 재처리를 위해 사용
     */
    private fun sendToDlq(data: MessageProcessingData) {
        try {
            val dlqTopic = "${data.originalTopic}-dlq"

            val dlqMessage = buildDlqMessage(data)

            kafkaTemplate.send(dlqTopic, data.eventId ?: "unknown", dlqMessage)
                .thenAccept { result ->
                    log.info("✅ [DLQ] 메시지 DLQ 전송 완료: topic={} eventType={} eventId={} partition={} offset={}",
                        dlqTopic, data.eventType, data.eventId,
                        result.recordMetadata.partition(), result.recordMetadata.offset())
                }
                .exceptionally { dlqError ->
                    log.error("❌ [DLQ] DLQ 전송 실패: topic={} eventType={} eventId={} error={}",
                        dlqTopic, data.eventType, data.eventId, dlqError.message, dlqError)
                    null
                }

        } catch (e: Exception) {
            log.error("❌ [DLQ] DLQ 메시지 생성 실패: originalTopic={} eventType={} eventId={} error={}",
                data.originalTopic, data.eventType, data.eventId, e.message, e)
        }
    }

    /**
     * DLQ 메시지 빌더 (중복 제거)
     */
    private fun buildDlqMessage(data: MessageProcessingData): Map<String, Any> {
        return mapOf(
            "originalTopic" to data.originalTopic,
            "partition" to data.partition,
            "offset" to data.offset,
            EventConstants.MetadataKeys.EVENT_TYPE to (data.eventType ?: "UNKNOWN"),
            EventConstants.MetadataKeys.EVENT_ID to (data.eventId ?: "UNKNOWN"),
            "originalMessage" to data.eventData,
            "error" to mapOf(
                "message" to (data.exception.message ?: "Unknown error"),
                "type" to data.exception.javaClass.simpleName,
                "stackTrace" to data.exception.stackTrace.take(10).map { it.toString() }
            ),
            EventConstants.MetadataKeys.TIMESTAMP to System.currentTimeMillis(),
            "service" to "payment-service",
            "retryAttempt" to data.retryAttempt
        )
    }

    /**
     * Exponential Backoff 재시도를 위한 Retry 큐로 메시지 전송
     * - DLQ 전송 전에 지정된 횟수만큼 재시도
     */
    private fun sendToRetryQueue(data: MessageProcessingData) {
        try {
            if (data.retryAttempt >= data.maxRetries) {
                log.warn("⚠️ [RETRY] 최대 재시도 횟수 초과 - DLQ로 전송: eventType={} eventId={} attempts={}",
                    data.eventType, data.eventId, data.retryAttempt)
                sendToDlq(data)
                return
            }

            val retryTopic = "${data.originalTopic}-retry"
            val delayMillis = calculateRetryDelay(data.retryAttempt) // Exponential backoff

            val retryMessage = mapOf(
                "originalTopic" to data.originalTopic,
                "partition" to data.partition,
                "offset" to data.offset,
                EventConstants.MetadataKeys.EVENT_TYPE to (data.eventType ?: "UNKNOWN"),
                EventConstants.MetadataKeys.EVENT_ID to (data.eventId ?: "UNKNOWN"),
                "originalMessage" to data.eventData,
                "retryAttempt" to data.retryAttempt,
                "maxRetries" to data.maxRetries,
                "nextRetryAt" to System.currentTimeMillis() + delayMillis,
                "lastError" to data.exception.message,
                EventConstants.MetadataKeys.TIMESTAMP to System.currentTimeMillis()
            )

            // 딜레이 후 재시도 토픽으로 전송 (실제로는 Kafka Streams나 스케줄러 필요)
            kafkaTemplate.send(retryTopic, data.eventId ?: "unknown", retryMessage)
                .thenAccept { _ ->
                    log.info("🔄 [RETRY] 재시도 큐로 전송 완료: eventType={} eventId={} attempt={}/{} delay={}ms",
                        data.eventType, data.eventId, data.retryAttempt, data.maxRetries, delayMillis)
                }

        } catch (e: Exception) {
            log.error("❌ [RETRY] 재시도 큐 전송 실패 - DLQ로 전송: eventType={} eventId={} error={}",
                data.eventType, data.eventId, e.message, e)
            sendToDlq(data)
        }
    }

    /**
     * Exponential Backoff 딜레이 계산
     * - 1초 시작, 최대 5분, 2배씩 증가
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        val baseDelayMs = 1000L // 1초
        val maxDelayMs = 300000L // 5분
        val exponentialDelay = baseDelayMs * (1L shl attempt) // 2^attempt
        return minOf(exponentialDelay, maxDelayMs)
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

}
