package com.popcorn.payment.event.domain.compensation

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.util.*

/**
 * 결제 검증 실패 이벤트
 *
 * 주소 검증이나 가격 검증 실패 시 발생하는 이벤트입니다.
 * 이 이벤트가 발생하면 보상 트랜잭션이 시작됩니다.
 */
data class PaymentValidationFailedEvent(
    val validationPaymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val customerId: Long,
    val validationType: String, // "ADDRESS", "PRICE", "ORDER_STATUS" 등
    val failureReason: String,
    val amount: Int,
    private val eventUserId: Long? = null,
    private val eventMetadata: Map<String, Any>? = null
) : BasePaymentEvent(
    paymentId = validationPaymentId,
    eventType = EventConstants.EventTypes.PAYMENT_VALIDATION_FAILED,
    userId = eventUserId
) {

    override fun getEventPayload(): MutableMap<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "paymentId" to validationPaymentId,
            "orderId" to orderId.toString(),
            "orderNo" to orderNo,
            "customerId" to customerId,
            "validationType" to validationType,
            "failureReason" to failureReason,
            "amount" to amount,
            "requiresCompensation" to true
        )
        eventMetadata?.let { payload.putAll(it) }
        return payload
    }

    /**
     * 주소 검증 실패인지 확인
     */
    fun isAddressValidationFailure(): Boolean = validationType == "ADDRESS"

    /**
     * 가격 검증 실패인지 확인
     */
    fun isPriceValidationFailure(): Boolean = validationType == "PRICE"

    /**
     * 주문 상태 검증 실패인지 확인
     */
    fun isOrderStatusValidationFailure(): Boolean = validationType == "ORDER_STATUS"

    /**
     * 보상이 필요한지 확인
     */
    fun requiresCompensation(): Boolean = true

    /**
     * 검증 실패 이벤트용 로그 메시지
     */
    fun getValidationFailureDescription(): String {
        return "❌ [VALIDATION-FAILURE] $validationType validation failed - orderId: $orderId, reason: $failureReason"
    }

    companion object {
        /**
         * 주소 검증 실패 이벤트 생성
         */
        fun addressValidationFailed(
            validationPaymentId: UUID,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            failureReason: String,
            amount: Int,
            eventUserId: Long? = null
        ): PaymentValidationFailedEvent {
            return PaymentValidationFailedEvent(
                validationPaymentId = validationPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                validationType = "ADDRESS",
                failureReason = failureReason,
                amount = amount,
                eventUserId = eventUserId
            )
        }

        /**
         * 가격 검증 실패 이벤트 생성
         */
        fun priceValidationFailed(
            validationPaymentId: UUID,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            failureReason: String,
            amount: Int,
            eventUserId: Long? = null
        ): PaymentValidationFailedEvent {
            return PaymentValidationFailedEvent(
                validationPaymentId = validationPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                validationType = "PRICE",
                failureReason = failureReason,
                amount = amount,
                eventUserId = eventUserId
            )
        }

        /**
         * 주문 상태 검증 실패 이벤트 생성
         */
        fun orderStatusValidationFailed(
            validationPaymentId: UUID,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            failureReason: String,
            amount: Int,
            eventUserId: Long? = null
        ): PaymentValidationFailedEvent {
            return PaymentValidationFailedEvent(
                validationPaymentId = validationPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                validationType = "ORDER_STATUS",
                failureReason = failureReason,
                amount = amount,
                eventUserId = eventUserId
            )
        }
    }
}