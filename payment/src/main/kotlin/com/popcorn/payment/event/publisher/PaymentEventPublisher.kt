package com.popcorn.payment.event.publisher

import com.popcorn.payment.event.base.BasePaymentEvent
import com.popcorn.payment.event.base.BasePaymentEventPublisher
import com.popcorn.payment.event.listener.PaymentRedisEventPublisher
import com.popcorn.payment.event.domain.payment.*
import com.popcorn.payment.event.domain.legacy.PaymentCompletedEvent
import com.popcorn.payment.event.integration.request.*
import com.popcorn.payment.common.exception.PaymentExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 결제 이벤트 발행 서비스 구현체
 */
@Component
class BasePaymentEventPublisherImpl(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val paymentRedisEventPublisher: PaymentRedisEventPublisher
) : BasePaymentEventPublisher {

    private val log = LoggerFactory.getLogger(BasePaymentEventPublisherImpl::class.java)
    private val eventScope = CoroutineScope(Dispatchers.Default)

    /**
     * 단일 이벤트 발행
     */
    override suspend fun publish(event: BasePaymentEvent) {
        try {
            log.debug("📨 이벤트 발행: {}", event::class.simpleName)
            applicationEventPublisher.publishEvent(event)
            paymentRedisEventPublisher.publish(event)
        } catch (e: Exception) {
            PaymentExceptionHandler.handleEventPublishException(
                logger = log,
                eventType = event.eventType,
                eventClass = event::class.simpleName ?: "Unknown",
                exception = e
            )
            // 이벤트 발행 실패해도 메인 로직에는 영향 없음
        }
    }

    /**
     * 다중 이벤트 발행
     */
    override suspend fun publishAll(events: List<BasePaymentEvent>) {
        events.forEach { event ->
            eventScope.launch {
                publish(event)
            }
        }
    }

    /**
     * 비동기 이벤트 발행 (Fire and Forget)
     */
    override fun publishAsync(event: BasePaymentEvent) {
        eventScope.launch {
            publish(event)
        }
    }

    /**
     * 결제 생성 이벤트 발행
     */
    suspend fun publishPaymentCreated(
        paymentId: java.util.UUID,
        orderId: java.util.UUID,
        orderNo: String,
        amount: Int,
        paymentMethod: String,
        customerId: Long?
    ) {
        val event = PaymentCreatedEvent(
            _paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            amount = amount,
            paymentMethod = paymentMethod,
            customerId = customerId
        )
        publish(event)
    }

    /**
     * 결제 승인 이벤트 발행
     */
    suspend fun publishPaymentApproved(
        paymentId: java.util.UUID,
        orderId: java.util.UUID,
        orderNo: String,
        amount: Int,
        paymentMethod: String,
        paymentKey: String?,
        approvedAt: java.time.LocalDateTime,
        customerId: Long?
    ) {
        val event = PaymentApprovedEvent(
            _paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            amount = amount,
            paymentMethod = paymentMethod,
            paymentKey = paymentKey,
            approvedAt = approvedAt,
            customerId = customerId
        )
        publish(event)
    }

    /**
     * 결제 실패 이벤트 발행
     */
    suspend fun publishPaymentFailed(
        paymentId: java.util.UUID,
        orderId: java.util.UUID,
        orderNo: String,
        amount: Int,
        paymentMethod: String,
        failureReason: String,
        customerId: Long?
    ) {
        val event = PaymentFailedEvent(
            _paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            amount = amount,
            paymentMethod = paymentMethod,
            failureReason = failureReason,
            customerId = customerId
        )
        publish(event)
    }

    /**
     * 결제 취소 이벤트 발행
     */
    suspend fun publishPaymentCancelled(
        paymentId: java.util.UUID,
        orderId: java.util.UUID,
        orderNo: String,
        cancelAmount: Int,
        cancelReason: String,
        customerId: Long?
    ) {
        val event = PaymentCancelledEvent(
            _paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            cancelAmount = cancelAmount,
            cancelReason = cancelReason,
            customerId = customerId
        )
        publish(event)
    }

    /**
     * 결제 완료 이벤트 발행 (Order 서비스 호환용)
     */
    suspend fun publishPaymentCompleted(
        paymentId: java.util.UUID,
        orderId: java.util.UUID,
        paymentKey: String?,
        amount: Int,
        paymentMethod: String,
        pgResponse: String? = null
    ) {
        val event = if (pgResponse != null) {
            PaymentCompletedEvent.createWithPgResponse(
                orderId = orderId,
                paymentId = paymentId,
                paymentKey = paymentKey,
                amount = amount,
                paymentMethod = paymentMethod,
                pgResponse = pgResponse
            )
        } else {
            PaymentCompletedEvent.create(
                orderId = orderId,
                paymentId = paymentId,
                paymentKey = paymentKey,
                amount = amount,
                paymentMethod = paymentMethod
            )
        }

        log.info("🚀 결제 완료 이벤트 발행: orderId={}, amount={}원", orderId, amount)
        applicationEventPublisher.publishEvent(event)
    }

    /**
     * QR 코드 생성 요청 이벤트 발행
     */
    suspend fun publishQrCodeGenerationRequested(
        paymentId: java.util.UUID,
        orderId: java.util.UUID,
        orderNo: String,
        customerId: Long?
    ) {
        val event = QrCodeGenerationRequestedEvent.create(
            paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            customerId = customerId
        )

        log.info("🚀 QR 코드 생성 요청 이벤트 발행: paymentId={}, orderId={}, orderNo={}", paymentId, orderId, orderNo)
        applicationEventPublisher.publishEvent(event)
    }
}
