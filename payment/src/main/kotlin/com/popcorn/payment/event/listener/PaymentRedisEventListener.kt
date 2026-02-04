package com.popcorn.payment.event.listener

import com.popcorn.payment.common.exception.PaymentExceptionHandler
import com.popcorn.payment.common.exception.PaymentExceptionHandler.safeError
import com.popcorn.payment.common.exception.PaymentExceptionHandler.safeInfo
import com.popcorn.payment.event.domain.legacy.PaymentCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component

/**
 * Payment 서비스 범용 이벤트 리스너
 * - Redis 이벤트 구독
 * - Spring Application 이벤트 구독
 * - 모든 이벤트를 로그로 기록
 * - PaymentCompletedEvent를 Redis Stream으로 발행
 */
@Component
class PaymentRedisEventListener(
    private val paymentRedisEventPublisher: PaymentRedisEventPublisher
) : MessageListener {
    private val log = LoggerFactory.getLogger(PaymentRedisEventListener::class.java)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val channel = String(message.channel)
            val body = String(message.body)

            log.safeInfo("[PAYMENT] Redis 이벤트 수신 - channel: {}, body: {}", channel, body)

            handleRedisEvent(channel, body)
        } catch (e: Exception) {
            PaymentExceptionHandler.handlePubSubProcessingException(
                logger = log,
                channel = String(message.channel),
                messageBody = String(message.body),
                exception = e
            )
        }
    }

    private fun handleRedisEvent(channel: String, body: String) {
        try {
            when (channel) {
                "events:order-created" -> log.safeInfo("[PAYMENT] 주문 생성 이벤트 수신 - {}", body)
                "events:order-paid" -> log.safeInfo("[PAYMENT] 주문 결제 완료 이벤트 수신 - {}", body)
                "events:order-cancelled" -> log.safeInfo("[PAYMENT] 주문 취소 이벤트 수신 - {}", body)
                "events:payment-created" -> log.safeInfo("[PAYMENT] 결제 생성 이벤트 수신 - {}", body)
                "events:payment-approved" -> log.safeInfo("[PAYMENT] 결제 승인 이벤트 수신 - {}", body)
                "events:payment-failed" -> log.warn("[PAYMENT] 결제 실패 이벤트 수신 - {}", body)
                "events:payment-cancelled" -> log.safeInfo("[PAYMENT] 결제 취소 이벤트 수신 - {}", body)
                "events:inventory-confirmation-requested" -> log.safeInfo("[PAYMENT] 재고 확정 요청 이벤트 수신 - {}", body)
                "events:inventory-restore-requested" -> log.safeInfo("[PAYMENT] 재고 복구 요청 이벤트 수신 - {}", body)
                else -> log.safeInfo("[PAYMENT] 기타 이벤트 수신 - channel: {}, body: {}", channel, body)
            }
        } catch (e: Exception) {
            PaymentExceptionHandler.handleBusinessLogicException(
                logger = log,
                operation = "Redis 이벤트 처리",
                exception = e
            )
        }
    }

    @EventListener
    fun handleApplicationEvent(event: Any) {
        try {
            val eventType = event.javaClass.simpleName
            if (eventType.contains("Payment") || eventType.contains("Order")) {
                log.safeInfo("[PAYMENT] Application 이벤트 수신 - type: {}, event: {}", eventType, event.toString())

                // PaymentCompletedEvent를 Redis Stream으로 발행
                if (event is PaymentCompletedEvent) {
                    log.safeInfo("[PAYMENT] PaymentCompletedEvent를 Redis Stream으로 발행 - eventId: {}", event.eventId)
                    paymentRedisEventPublisher.publish(event)
                }
            } else {
                log.debug("[PAYMENT] Application 이벤트 수신 - type: {}", eventType)
            }
        } catch (e: Exception) {
            PaymentExceptionHandler.handleEventPublishException(
                logger = log,
                eventType = event.javaClass.simpleName,
                eventClass = event.javaClass.name,
                exception = e
            )
        }
    }
}
