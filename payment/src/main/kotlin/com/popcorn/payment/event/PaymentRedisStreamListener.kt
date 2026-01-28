package com.popcorn.payment.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.service.PaymentOrderInfoService
import com.popcorn.payment.event.standard.OrderInfoResponseEvent
import com.popcorn.payment.service.TossPaymentCoroutineService
import com.popcorn.payment.event.PaymentCancelFailedEvent
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
    private val paymentEventPublisher: PaymentEventPublisherImpl
) : StreamListener<String, MapRecord<String, String, Any>> {

    private val log = LoggerFactory.getLogger(PaymentRedisStreamListener::class.java)
    private val eventScope = CoroutineScope(Dispatchers.Default)

    override fun onMessage(record: MapRecord<String, String, Any>) {
        try {
            val streamName = record.stream
            val recordId = record.id.value
            val values = record.value

            log.info("🔔 [PAYMENT] Stream 메시지 수신 - stream: {}, recordId: {}, eventType: {}",
                streamName, recordId, values["eventType"])

            // 스트림별 처리
            if ("order-info-responses" == streamName) {
                log.info("📞 [PAYMENT] Order 정보 응답 수신")
                handleOrderInfoResponse(values)
            } else {
                // 기존 eventType 기반 처리
                val rawEventType = values["eventType"] as? String
                val eventType = rawEventType?.trim()?.trim('"')
                handleStreamEvent(eventType, values)
            }

            // 메시지 처리 완료 후 ACK (자동으로 처리됨)
            log.debug("✅ [PAYMENT] 메시지 처리 완료 - stream: {}, recordId: {}", streamName, recordId)

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] Stream 메시지 처리 실패 - record: {}, error: {}",
                record, e.message, e)
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
                "order-created" -> {
                    log.info("📦 [PAYMENT] 주문 생성 이벤트 수신 - orderId: {}", values["orderId"])
                    // 결제 준비 로직 등
                }
                "order-paid" -> {
                    log.info("💳 [PAYMENT] 주문 결제 완료 이벤트 수신 - orderId: {}", values["orderId"])
                    // 결제 완료 후속 처리 등
                }

                // 결제 관련 이벤트 (자체 모니터링)
                "payment-created" -> {
                    log.info("🧾 [PAYMENT] 결제 생성 이벤트 수신 - paymentId: {}", values["paymentId"])
                }
                "payment-create-requested" -> {
                    log.info("🧾 [PAYMENT] 결제 생성 요청 이벤트 수신 - orderId: {}", values["orderId"])
                    handlePaymentCreateRequested(values)
                }
                "payment-approved" -> {
                    log.info("✅ [PAYMENT] 결제 승인 이벤트 수신 - paymentId: {}", values["paymentId"])
                    handlePaymentApproved(values)
                }
                "payment-cancel-requested" -> {
                    log.info("↩️ [PAYMENT] 결제 취소 요청 이벤트 수신 - orderId: {}", values["orderId"])
                    handlePaymentCancelRequested(values)
                }
                "payment-failed" -> {
                    log.warn("❌ [PAYMENT] 결제 실패 이벤트 수신 - paymentId: {}", values["paymentId"])
                    handlePaymentFailed(values)
                }
                "payment-cancelled" -> {
                    log.info("↩️ [PAYMENT] 결제 취소 이벤트 수신 - paymentId: {}", values["paymentId"])
                }
                "payment-completed" -> {
                    log.info("✅ [PAYMENT] 결제 완료 이벤트 수신 - paymentId: {}", values["paymentId"])
                }

                // 재고 관련 이벤트
                "inventory-confirmation-requested" -> {
                    log.info("📦 [PAYMENT] 재고 확정 요청 이벤트 수신 - orderId: {}", values["orderId"])
                    // 재고 확정 관련 로직
                }
                "inventory-restore-requested" -> {
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
                        paymentId = java.util.UUID(0, 0),
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

            // Map을 OrderInfoResponseEvent로 변환
            val response = OrderInfoResponseEvent().apply {
                requestId = normalizeString(values["requestId"])
                success = normalizeString(values["success"])?.toBoolean() ?: false
                actualOrderNo = normalizeString(values["actualOrderNo"])
                actualUserId = normalizeString(values["actualUserId"])?.toLongOrNull()
                actualPopupId = normalizeString(values["actualPopupId"])
                actualHasReservation = normalizeString(values["actualHasReservation"])?.toBoolean()
                actualHasGoods = normalizeString(values["actualHasGoods"])?.toBoolean()

                // lines JSON 파싱
                actualLines = parseEventLineItems(values["actualLines"])
            }

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

    private fun parseEventLineItems(rawLines: Any?): List<com.popcorn.payment.event.standard.EventLineItem> {
        if (rawLines == null) {
            return emptyList()
        }
        return try {
            when (rawLines) {
                is List<*> -> rawLines.mapNotNull { item ->
                    when (item) {
                        is com.popcorn.payment.event.standard.EventLineItem -> item
                        is Map<*, *> -> objectMapper.convertValue(item, com.popcorn.payment.event.standard.EventLineItem::class.java)
                        else -> null
                    }
                }
                is String -> {
                    val trimmed = rawLines.trim().trim('"')
                    if (trimmed.isBlank() || trimmed == "[]") {
                        emptyList()
                    } else {
                        val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<com.popcorn.payment.event.standard.EventLineItem>>() {}
                        objectMapper.readValue(trimmed, typeRef)
                    }
                }
                else -> {
                    val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<com.popcorn.payment.event.standard.EventLineItem>>() {}
                    objectMapper.convertValue(rawLines, typeRef)
                }
            }
        } catch (e: Exception) {
            log.warn("Order lines JSON 파싱 실패: {}", e.message)
            emptyList()
        }
    }
}
