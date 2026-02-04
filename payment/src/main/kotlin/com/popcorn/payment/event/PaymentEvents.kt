package com.popcorn.payment.event

import com.popcorn.payment.event.integration.PaymentSuccessIntegrationEvent
import java.time.LocalDateTime
import java.util.*

/**
 * 결제 관련 이벤트 정의 (도메인 관점)
 *
 * 🎪 이벤트 기반 아키텍처:
 * - 도메인 간 느슨한 결합
 * - 비동기 후속 처리
 * - 확장성과 유지보수성 향상
 * - 트랜잭션 경계 분리
 *
 * 📝 BaseEvent 통합:
 * - common-lib의 BaseEvent를 상속하여 일관된 이벤트 구조 제공
 * - 메타데이터, 상관관계 추적, 버전 관리 등 공통 기능 활용
 */

/**
 * 결제 생성 이벤트
 * 새로운 결제가 생성되었을 때 발행
 */
data class PaymentCreatedEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val amount: Int,
    val paymentMethod: String,
    val customerId: Long?,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-created",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "amount" to amount,
        "paymentMethod" to paymentMethod,
        "occurredAt" to occurredAt.toString(),
        "customerId" to (customerId ?: "null")
    )

    companion object {
        fun create(
            paymentId: UUID,
            orderId: UUID,
            orderNo: String,
            amount: Int,
            paymentMethod: String,
            customerId: Long?
        ): PaymentCreatedEvent {
            return PaymentCreatedEvent(
                _paymentId = paymentId,
                orderId = orderId,
                orderNo = orderNo,
                amount = amount,
                paymentMethod = paymentMethod,
                customerId = customerId
            )
        }
    }
}

/**
 * 결제 승인 이벤트
 * 결제가 성공적으로 승인되었을 때 발행
 */
data class PaymentApprovedEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val amount: Int,
    val paymentMethod: String,
    val paymentKey: String?,
    val approvedAt: LocalDateTime,
    val customerId: Long?,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-approved",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "amount" to amount,
        "paymentMethod" to paymentMethod,
        "paymentKey" to (paymentKey ?: "null"),
        "approvedAt" to approvedAt.toString(),
        "occurredAt" to occurredAt.toString(),
        "customerId" to (customerId ?: "null")
    )
}

/**
 * 결제 실패 이벤트
 * 결제 승인이 실패했을 때 발행
 */
data class PaymentFailedEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val amount: Int,
    val paymentMethod: String,
    val failureReason: String,
    val customerId: Long?,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-failed",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "amount" to amount,
        "paymentMethod" to paymentMethod,
        "failureReason" to failureReason,
        "occurredAt" to occurredAt.toString(),
        "customerId" to (customerId ?: "null")
    )
}

/**
 * 결제 취소 이벤트
 * 결제가 취소되었을 때 발행
 */
data class PaymentCancelledEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val cancelAmount: Int,
    val cancelReason: String,
    val customerId: Long?,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-cancelled",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "cancelAmount" to cancelAmount,
        "cancelReason" to cancelReason,
        "occurredAt" to occurredAt.toString(),
        "customerId" to (customerId ?: "null")
    )
}

/**
 * 결제 취소 실패 이벤트
 * 결제 취소가 실패했을 때 발행 (재시도 큐에 추가)
 */
data class PaymentCancelFailedEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val cancelReason: String,
    val failureReason: String,
    val retryCount: Int = 0,
    val customerId: Long?,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-cancel-failed",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "cancelReason" to cancelReason,
        "failureReason" to failureReason,
        "retryCount" to retryCount,
        "occurredAt" to occurredAt.toString(),
        "customerId" to (customerId ?: "null")
    )
}

/**
 * 결제 만료 이벤트
 * READY 상태로 오래 방치된 결제를 정리할 때 발행
 */
data class PaymentExpiredEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val amount: Int,
    val expiredAt: LocalDateTime,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-expired",
    userId = null // 만료 이벤트는 사용자 정보가 없을 수 있음
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "amount" to amount,
        "expiredAt" to expiredAt.toString(),
        "occurredAt" to occurredAt.toString()
    )
}

/**
 * 결제 성공 이벤트 (통합)
 * 결제 승인 완료 후 후속 처리를 위해 발행하는 통합 이벤트
 */
data class PaymentSuccessEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val orderType: String, // PURCHASE, RESERVATION
    val totalAmount: Int,
    val orderItems: List<OrderItemInfo>?,
    val paidAt: LocalDateTime,
    val paymentMethod: String,
    val paymentKey: String?,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-success",
    userId = null // userId는 orderItems나 별도 조회를 통해 확인
) {
    override fun getEventPayload(): Map<String, Any> = buildMap {
        put("paymentId", paymentId)
        put("orderId", orderId)
        put("orderNo", orderNo)
        put("orderType", orderType)
        put("totalAmount", totalAmount)
        put("paidAt", paidAt.toString())
        put("paymentMethod", paymentMethod)
        put("occurredAt", occurredAt.toString())

        paymentKey?.let { put("paymentKey", it) }
        orderItems?.let { put("orderItems", it.map { item ->
            mapOf(
                "id" to item.id,
                "productId" to item.productId,
                "productName" to item.productName,
                "quantity" to item.quantity,
                "price" to item.price,
                "itemType" to item.itemType
            )
        }) }
    }
}

// ================ 새로운 이벤트 타입들 (사용자 요청) ================

/**
 * 사용자 결제 취소 이벤트 (payment-events)
 * 사용자가 직접 결제를 취소했을 때 발행
 */
data class PaymentUserCancelledEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val amount: Int,
    val cancelReason: String,
    val customerId: Long?,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-user-cancelled",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "amount" to amount,
        "cancelReason" to cancelReason,
        "occurredAt" to occurredAt.toString(),
        "customerId" to (customerId ?: "null")
    )
}

/**
 * 결제 취소 성공 이벤트 (payment-events)
 * 결제 취소가 성공적으로 완료되었을 때 발행
 */
data class PaymentCancelSucceededEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val cancelAmount: Int,
    val cancelReason: String,
    val customerId: Long?,
    val cancelledAt: LocalDateTime = LocalDateTime.now(),
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-cancel-succeeded",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "cancelAmount" to cancelAmount,
        "cancelReason" to cancelReason,
        "cancelledAt" to cancelledAt.toString(),
        "occurredAt" to occurredAt.toString(),
        "customerId" to (customerId ?: "null")
    )
}

/**
 * 결제 생성 요청 이벤트 (payment-requests)
 * Order 서비스에서 결제 생성을 요청할 때 발행
 */
data class PaymentCreateRequestedEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val amount: Int,
    val paymentMethod: String,
    val customerId: Long?,
    val successUrl: String?,
    val failUrl: String?,
    val requestedAt: LocalDateTime = LocalDateTime.now(),
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-create-requested",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = buildMap {
        put("paymentId", paymentId)
        put("orderId", orderId)
        put("orderNo", orderNo)
        put("amount", amount)
        put("paymentMethod", paymentMethod)
        put("requestedAt", requestedAt.toString())
        put("occurredAt", occurredAt.toString())
        put("customerId", customerId ?: "null")

        successUrl?.let { put("successUrl", it) }
        failUrl?.let { put("failUrl", it) }
    }
}

/**
 * 결제 취소 요청 이벤트 (payment-requests)
 * Order 서비스에서 결제 취소를 요청할 때 발행
 */
data class PaymentCancelRequestedEvent(
    private val _paymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val cancelReason: String,
    val customerId: Long?,
    val requestedAt: LocalDateTime = LocalDateTime.now(),
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : BasePaymentEvent(
    paymentId = _paymentId,
    eventType = "payment-cancel-requested",
    userId = customerId
) {
    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "cancelReason" to cancelReason,
        "requestedAt" to requestedAt.toString(),
        "occurredAt" to occurredAt.toString(),
        "customerId" to (customerId ?: "null")
    )
}

// ================ 기본 데이터 클래스들 ================

/**
 * 주문 항목 정보 (이벤트용 DTO)
 */
data class OrderItemInfo(
    val id: UUID,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val price: Int,
    val itemType: String // PRODUCT, RESERVATION 등
)

/**
 * 이벤트 발행을 위한 Publisher 인터페이스
 */
interface PaymentEventPublisher {
    suspend fun publish(event: BasePaymentEvent)
    suspend fun publishAll(events: List<BasePaymentEvent>)
}

/**
 * 이벤트 핸들러 기본 인터페이스
 */
interface PaymentEventHandler<T : BasePaymentEvent> {
    suspend fun handle(event: T)
    val eventType: Class<T>
}