package com.popcorn.payment.event.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.dto.PaymentCreateResult
import com.popcorn.payment.service.TossPaymentCoroutineService
import com.popcorn.payment.event.domain.payment.PaymentCancelFailedEvent
import com.popcorn.payment.event.domain.payment.PaymentUrlCreatedEvent
import com.popcorn.payment.event.domain.payment.EventLineItem
import java.util.UUID
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

            // 🚀 이벤트 타입 기반 처리 (성능 최적화)
            val rawEventType = values[EventConstants.MetadataKeys.EVENT_TYPE] as? String
            val eventType = rawEventType?.trim()?.trim('"')
            handleStreamEvent(eventType, values)

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
                EventConstants.EventTypes.OrderDomain.ORDER_CREATED -> {
                    log.info("📦 [PAYMENT] 주문 생성 이벤트 수신 - orderId: {}", values["orderId"])
                    // 결제 준비 로직 등
                }
                EventConstants.EventTypes.OrderDomain.ORDER_PAID -> {
                    log.info("💳 [PAYMENT] 주문 결제 완료 이벤트 수신 - orderId: {}", values["orderId"])
                    // 결제 완료 후속 처리 등
                }
                EventConstants.EventTypes.OrderDomain.ORDER_COMPLETED -> {
                    log.info("✅ [PAYMENT] 주문 완료 이벤트 수신 - orderId: {}", values["orderId"])
                    // 완료 후속 처리 등
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
            val orderNo = values["orderNo"]?.toString()?.trim()?.trim('"') ?: "ORDER-${System.currentTimeMillis()}"
            val amountRaw = values["amount"]?.toString()?.trim()?.trim('"')
            val paymentMethod = values["paymentMethod"]?.toString()?.trim()?.trim('"') ?: "CARD"
            val customerIdRaw = values["customerId"]?.toString()?.trim()?.trim('"')
            val orderName = values["orderName"]?.toString()?.trim()?.trim('"') ?: orderNo

            if (orderIdRaw.isNullOrBlank() || amountRaw.isNullOrBlank()) {
                log.warn("⚠️ [PAYMENT] 결제 생성 요청 필수 데이터 누락 - values: {}", values)
                return
            }

            val orderId = orderIdRaw
            val amount = amountRaw.toIntOrNull() ?: 0
            val customerId = customerIdRaw?.toLongOrNull()

            if (amount <= 0) {
                log.warn("⚠️ [PAYMENT] 잘못된 금액 - orderId: {}, amount: {}", orderId, amount)
                return
            }

            log.info("💳 [PAYMENT] Redis Stream 결제 생성 요청 처리 시작 - orderId={}, method={}, amount={}원, orderNo={}, customerId={}",
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

                    log.info("✅ [PAYMENT] Redis Stream 결제 URL 생성 및 이벤트 발행 완료 - orderId={}, paymentUrl={}, expiresAt={}",
                        orderId, createResult.paymentUrl, createResult.expiresAt)

                } catch (e: Exception) {
                    log.error("❌ [PAYMENT] Redis Stream 결제 URL 생성 실패 - orderId={}, error={}", orderId, e.message, e)
                }
            }

        } catch (e: Exception) {
            log.error("🚨 [PAYMENT] 결제 생성 요청 처리 실패 - values: {}, error: {}", values, e.message, e)
        }
    }

}
