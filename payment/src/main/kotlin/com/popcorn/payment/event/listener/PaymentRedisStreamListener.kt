package com.popcorn.payment.event.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.service.PaymentOrderInfoService
import com.popcorn.payment.event.domain.payment.OrderInfoResponseEvent
import com.popcorn.payment.service.TossPaymentCoroutineService
import com.popcorn.payment.event.domain.payment.PaymentCancelFailedEvent
import com.popcorn.payment.event.domain.payment.EventLineItem
import com.popcorn.payment.event.publisher.BasePaymentEventPublisherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.stream.StreamListener
import org.springframework.stereotype.Component

/**
 * Payment 서비스 Redis Stream 이벤트 리스너
 * - 주문, 결제, 재고 관련 이벤트를 Stream으로 수신
 * - Consumer Group 기반 메시지 처리
 * - 결제 관련 모니터링 및 로그 기록
 */
@Component
class PaymentRedisStreamListener(
    private val objectMapper: ObjectMapper,
    private val paymentOrderInfoService: PaymentOrderInfoService,
    private val tossPaymentCoroutineService: TossPaymentCoroutineService,
    private val paymentEventPublisher: BasePaymentEventPublisherImpl
) : StreamListener<String, MapRecord<String, String, Any>> {

    private val log = LoggerFactory.getLogger(PaymentRedisStreamListener::class.java)
    private val eventScope = CoroutineScope(Dispatchers.Default)

    override fun onMessage(record: MapRecord<String, String, Any>) {
        val startTime = System.currentTimeMillis()
        try {
            val streamName = record.stream
            val recordId = record.id.value
            val values = record.value

            log.info("🔔 [PAYMENT] Stream 메시지 수신 - stream: {}, recordId: {}, eventType: {}",
                streamName, recordId, values[EventConstants.MetadataKeys.EVENT_TYPE])

            // 🚀 스트림별 최적화된 처리
            when (streamName) {
                "order-info-responses" -> {
                    log.info("📞 [PAYMENT] Order 정보 응답 수신")
                    handleOrderInfoResponse(values)
                }
                "payment-events" -> {
                    // 기존 eventType 기반 처리 (성능 최적화)
                    val rawEventType = values[EventConstants.MetadataKeys.EVENT_TYPE] as? String
                    val eventType = rawEventType?.trim()?.trim('"')
                    handleStreamEvent(eventType, values)
                }
                else -> {
                    // 알려지지 않은 스트림도 기본 처리
                    val rawEventType = values[EventConstants.MetadataKeys.EVENT_TYPE] as? String
                    val eventType = rawEventType?.trim()?.trim('"')
                    handleStreamEvent(eventType, values)
                }
            }

            val processingTime = System.currentTimeMillis() - startTime
            // 메시지 처리 완료 후 ACK (자동으로 처리됨)
            log.debug("✅ [PAYMENT] 메시지 처리 완료 - stream: {}, recordId: {}, 처리시간: {}ms",
                streamName, recordId, processingTime)

            // 🚀 성능 모니터링
            if (processingTime > 100) {
                log.warn("⚠️ [PAYMENT] Stream 메시지 처리 지연 - stream: {}, 처리시간: {}ms",
                    streamName, processingTime)
            }

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("🚨 [PAYMENT] Stream 메시지 처리 실패 - record: {}, 처리시간: {}ms, error: {}",
                record, processingTime, e.message, e)
            // TODO: 실패한 메시지를 DLQ(Dead Letter Queue)로 이동하거나 재시도 로직 구현
        }
    }

    /**
     * Stream 이벤트 타입별 처리
     */
    private fun handleStreamEvent(eventType: String?, values: Map<String, Any>) {
        try {
            when (eventType) {
                // 주문 관련 이벤트
                "ORDER_CREATED" -> {
                    log.info("📦 [PAYMENT] 주문 생성 이벤트 수신 - orderId: {}", values["orderId"])
                    // 결제 준비 로직 등
                }
                "ORDER_PAID" -> {
                    log.info("💳 [PAYMENT] 주문 결제 완료 이벤트 수신 - orderId: {}", values["orderId"])
                    // 결제 완료 후속 처리 등
                }

                // 결제 관련 이벤트 (자체 모니터링)
                "PAYMENT_CREATED" -> {
                    log.info("🧾 [PAYMENT] 결제 생성 이벤트 수신 - paymentId: {}", values["paymentId"])
                }
                "PAYMENT_CREATE_REQUESTED" -> {
                    log.info("🧾 [PAYMENT] 결제 생성 요청 이벤트 수신 - orderId: {}", values["orderId"])
                    handlePaymentCreateRequested(values)
                }
                "PAYMENT_APPROVED" -> {
                    log.info("✅ [PAYMENT] 결제 승인 이벤트 수신 - paymentId: {}", values["paymentId"])
                    handlePaymentApproved(values)
                }
                "PAYMENT_CANCEL_REQUESTED" -> {
                    log.info("↩️ [PAYMENT] 결제 취소 요청 이벤트 수신 - orderId: {}", values["orderId"])
                    handlePaymentCancelRequested(values)
                }
                "PAYMENT_FAILED" -> {
                    log.warn("❌ [PAYMENT] 결제 실패 이벤트 수신 - paymentId: {}", values["paymentId"])
                    handlePaymentFailed(values)
                }
                "PAYMENT_USER_CANCELLED" -> {
                    log.info("↩️ [PAYMENT] 결제 취소 이벤트 수신 - paymentId: {}", values["paymentId"])
                }
                EventConstants.EventTypes.PaymentDomain.PAYMENT_SUCCESS -> {
                    log.info("✅ [PAYMENT] 결제 완료 이벤트 수신 - paymentId: {}", values["paymentId"])
                }

                // 재고 관련 이벤트
                "INVENTORY_CONFIRMATION_REQUESTED" -> {
                    log.info("📦 [PAYMENT] 재고 확정 요청 이벤트 수신 - orderId: {}", values["orderId"])
                    // 재고 확정 관련 로직
                }
                "INVENTORY_RESTORE_REQUESTED" -> {
                    log.info("🔄 [PAYMENT] 재고 복구 요청 이벤트 수신 - orderId: {}", values["orderId"])
                    // 재고 복구 관련 로직
                }

                else -> {
                    log.info("🔔 [PAYMENT] 기타 이벤트 수신 - eventType: {}", eventType)
                }
            }
        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 이벤트 처리 실패 - eventType: {}, error: {}", eventType, e.message, e)
        }
    }

    /**
     * 결제 승인 이벤트 처리
     */
    private fun handlePaymentApproved(values: Map<String, Any>) {
        try {
            val paymentId = values["paymentId"] as? String
            val amount = values["amount"] as? String

            log.info("💰 [PAYMENT] 결제 승인 처리 - paymentId: {}, amount: {}", paymentId, amount)
            // 결제 승인 후속 처리 로직 (알림, 통계 등)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 결제 승인 처리 실패 - values: {}, error: {}", values, e.message, e)
        }
    }

    /**
     * 결제 실패 이벤트 처리
     */
    private fun handlePaymentFailed(values: Map<String, Any>) {
        try {
            val paymentId = values["paymentId"] as? String
            val reason = values["reason"] as? String

            log.warn("❌ [PAYMENT] 결제 실패 처리 - paymentId: {}, reason: {}", paymentId, reason)
            // 결제 실패 후속 처리 로직 (알림, 재시도 등)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 결제 실패 처리 실패 - values: {}, error: {}", values, e.message, e)
        }
    }

    /**
     * 결제 취소 요청 이벤트 처리
     */
    private fun handlePaymentCancelRequested(values: Map<String, Any>) {
        val orderIdRaw = values["orderId"]?.toString()?.trim()?.trim('"')
        val reason = values["reason"]?.toString()?.trim()?.trim('"') ?: "주문 취소"
        val orderNo = values["orderNo"]?.toString()?.trim()?.trim('"') ?: orderIdRaw
        val customerIdRaw = values["customerId"]?.toString()?.trim()?.trim('"')
        val customerId = customerIdRaw?.toLongOrNull()

        if (orderIdRaw.isNullOrBlank()) {
            log.warn("⚠️ [PAYMENT] 결제 취소 요청 필수 데이터 누락 - values: {}", values)
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
     * 결제 생성 요청 이벤트 처리
     */
    private fun handlePaymentCreateRequested(values: Map<String, Any>) {
        try {
            val orderIdRaw = values["orderId"]?.toString()?.trim()?.trim('"')
            val orderNo = values["orderNo"]?.toString()?.trim()?.trim('"')
            val amountRaw = values["amount"]?.toString()?.trim()?.trim('"')
            val paymentMethod = values["paymentMethod"]?.toString()?.trim()?.trim('"') ?: "CARD"
            val customerIdRaw = values["customerId"]?.toString()?.trim()?.trim('"')

            if (orderIdRaw.isNullOrBlank() || amountRaw.isNullOrBlank()) {
                log.warn("⚠️ [PAYMENT] 결제 생성 요청 필수 데이터 누락 - values: {}", values)
                return
            }

            log.info("📝 [PAYMENT] 결제 생성 요청 수신(대기) - orderId={}, method={}, amount={}원, orderNo={}, customerId={}",
                orderIdRaw, paymentMethod, amountRaw, orderNo, customerIdRaw)
            log.info("🕒 [PAYMENT] 결제 기록 생성은 승인 시점에 처리됩니다 - orderId={}", orderIdRaw)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 결제 생성 요청 처리 실패 - values: {}, error: {}", values, e.message, e)
        }
    }

    /**
     * Order 정보 응답 이벤트 처리
     */
    private fun handleOrderInfoResponse(values: Map<String, Any>) {
        try {
            log.info("📞 [PAYMENT] Order 정보 응답 처리 시작 - requestId: {}, success: {}",
                values["requestId"], values["success"])

            // Map을 OrderInfoResponseEvent로 변환 (Order 서비스 응답 포맷 호환)
            val customerIdValue = normalizeString(values["customerId"])
                ?: normalizeString(values["actualUserId"])
            val orderStatusValue = normalizeString(values["orderStatus"])
                ?: normalizeString(values["status"])
            val totalAmountValue = normalizeString(values["totalAmount"])
                ?: normalizeString(values["amount"])

            val response = OrderInfoResponseEvent(
                requestId = normalizeString(values["requestId"]),
                success = normalizeString(values["success"])?.toBoolean() ?: false,
                errorMessage = normalizeString(values["errorMessage"])
                    ?: normalizeString(values["message"]),
                actualLines = parseOrderLineInfos(values["actualLines"]),
                customerId = customerIdValue?.toLongOrNull(),
                orderStatus = orderStatusValue,
                totalAmount = totalAmountValue?.toIntOrNull(),
                actualOrderNo = normalizeString(values["actualOrderNo"]),
                actualUserId = normalizeString(values["actualUserId"])?.toLongOrNull(),
                actualPopupId = normalizeString(values["actualPopupId"]),
                actualStoreId = normalizeString(values["actualStoreId"]),
                actualHasReservation = normalizeString(values["actualHasReservation"])?.toBoolean(),
                actualHasGoods = normalizeString(values["actualHasGoods"])?.toBoolean()
            )

            // PaymentOrderInfoService에 응답 전달
            paymentOrderInfoService.handleOrderInfoResponse(response)

            log.info("✅ [PAYMENT] Order 정보 응답 처리 완료 - requestId: {}, success: {}",
                response.requestId, response.success)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] Order 정보 응답 처리 실패 - values: {}, error: {}", values, e.message, e)
        }
    }

    private fun normalizeString(value: Any?): String? {
        val raw = value as? String ?: return null
        val trimmed = raw.trim()
        return trimmed.trim('"')
    }

    private fun parseEventLineItems(rawLines: Any?): List<EventLineItem> {
        if (rawLines == null) {
            return emptyList()
        }
        return try {
            when (rawLines) {
                is List<*> -> rawLines.mapNotNull { item ->
                    when (item) {
                        is EventLineItem -> item
                        is Map<*, *> -> objectMapper.convertValue(item, EventLineItem::class.java)
                        else -> null
                    }
                }
                is String -> {
                    val trimmed = rawLines.trim().trim('"')
                    if (trimmed.isBlank() || trimmed == "[]") {
                        emptyList()
                    } else {
                        val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<EventLineItem>>() {}
                        objectMapper.readValue(trimmed, typeRef)
                    }
                }
                else -> {
                    val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<EventLineItem>>() {}
                    objectMapper.convertValue(rawLines, typeRef)
                }
            }
        } catch (e: Exception) {
            log.warn("Order lines JSON 파싱 실패: {}", e.message)
            emptyList()
        }
    }

    /**
     * 🚀 EventLineItem 파싱 메서드 (새로운 이벤트 형식용)
     */
    private fun parseOrderLineInfos(rawLines: Any?): List<com.popcorn.payment.event.domain.payment.EventLineItem> {
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
                                val result = objectMapper.convertValue(item, com.popcorn.payment.event.domain.payment.EventLineItem::class.java)
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

                    // 이중 이스케이프 처리 - JSON 문자열이 escape되어 있는 경우 처리
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
                        val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<com.popcorn.payment.event.domain.payment.EventLineItem>>() {}
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
                    val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<com.popcorn.payment.event.domain.payment.EventLineItem>>() {}
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
}
