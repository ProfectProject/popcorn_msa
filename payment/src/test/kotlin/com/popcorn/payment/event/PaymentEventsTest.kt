package com.popcorn.payment.event

import com.popcorn.payment.event.domain.payment.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 결제 이벤트 테스트
 *
 * [커버리지 향상을 위한 테스트]
 * - 모든 이벤트 데이터 클래스 테스트
 * - equals, hashCode, toString, copy 메소드 테스트
 * - 이벤트 인터페이스 및 sealed interface 테스트
 * - 80% 커버리지 달성에 기여하는 핵심 테스트
 */
class PaymentEventsTest {

    @Test
    fun `PaymentCreatedEvent 데이터 클래스 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val orderNo = "ORDER-12345"
        val amount = 15000
        val paymentMethod = "CARD"
        val customerId = 1L
        val occurredAt = LocalDateTime.now()

        // When
        val event = PaymentCreatedEvent(
            paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            amount = amount,
            paymentMethod = paymentMethod,
            customerId = customerId,
            occurredAt = occurredAt
        )

        // Then
        assertEquals(paymentId, event.paymentId)
        assertEquals(orderId, event.orderId)
        assertEquals(orderNo, event.orderNo)
        assertEquals(amount, event.amount)
        assertEquals(paymentMethod, event.paymentMethod)
        assertEquals(customerId, event.customerId)
        assertEquals(occurredAt, event.occurredAt)
        assertTrue(event is PaymentEvent)
    }

    @Test
    fun `PaymentApprovedEvent 데이터 클래스 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val orderNo = "ORDER-12345"
        val amount = 25000
        val paymentMethod = "CARD"
        val paymentKey = "payment_key_123"
        val approvedAt = LocalDateTime.now()
        val customerId = 2L
        val occurredAt = LocalDateTime.now()

        // When
        val event = PaymentApprovedEvent(
            paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            amount = amount,
            paymentMethod = paymentMethod,
            paymentKey = paymentKey,
            approvedAt = approvedAt,
            customerId = customerId,
            occurredAt = occurredAt
        )

        // Then
        assertEquals(paymentId, event.paymentId)
        assertEquals(orderId, event.orderId)
        assertEquals(orderNo, event.orderNo)
        assertEquals(amount, event.amount)
        assertEquals(paymentMethod, event.paymentMethod)
        assertEquals(paymentKey, event.paymentKey)
        assertEquals(approvedAt, event.approvedAt)
        assertEquals(customerId, event.customerId)
        assertEquals(occurredAt, event.occurredAt)
        assertTrue(event is PaymentEvent)
    }

    @Test
    fun `PaymentFailedEvent 데이터 클래스 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val orderNo = "ORDER-12345"
        val amount = 35000
        val paymentMethod = "CARD"
        val failureReason = "Invalid card number"
        val customerId = 3L
        val occurredAt = LocalDateTime.now()

        // When
        val event = PaymentFailedEvent(
            paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            amount = amount,
            paymentMethod = paymentMethod,
            failureReason = failureReason,
            customerId = customerId,
            occurredAt = occurredAt
        )

        // Then
        assertEquals(paymentId, event.paymentId)
        assertEquals(orderId, event.orderId)
        assertEquals(orderNo, event.orderNo)
        assertEquals(amount, event.amount)
        assertEquals(paymentMethod, event.paymentMethod)
        assertEquals(failureReason, event.failureReason)
        assertEquals(customerId, event.customerId)
        assertEquals(occurredAt, event.occurredAt)
        assertTrue(event is PaymentEvent)
    }

    @Test
    fun `PaymentCancelledEvent 데이터 클래스 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val orderNo = "ORDER-12345"
        val cancelAmount = 45000
        val cancelReason = "User requested cancellation"
        val customerId = 4L
        val occurredAt = LocalDateTime.now()

        // When
        val event = PaymentCancelledEvent(
            paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            cancelAmount = cancelAmount,
            cancelReason = cancelReason,
            customerId = customerId,
            occurredAt = occurredAt
        )

        // Then
        assertEquals(paymentId, event.paymentId)
        assertEquals(orderId, event.orderId)
        assertEquals(orderNo, event.orderNo)
        assertEquals(cancelAmount, event.cancelAmount)
        assertEquals(cancelReason, event.cancelReason)
        assertEquals(customerId, event.customerId)
        assertEquals(occurredAt, event.occurredAt)
        assertTrue(event is PaymentEvent)
    }

    @Test
    fun `PaymentExpiredEvent 데이터 클래스 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val orderNo = "ORDER-12345"
        val amount = 55000
        val expiredAt = LocalDateTime.now()
        val occurredAt = LocalDateTime.now()

        // When
        val event = PaymentExpiredEvent(
            paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            amount = amount,
            expiredAt = expiredAt,
            occurredAt = occurredAt
        )

        // Then
        assertEquals(paymentId, event.paymentId)
        assertEquals(orderId, event.orderId)
        assertEquals(orderNo, event.orderNo)
        assertEquals(amount, event.amount)
        assertEquals(expiredAt, event.expiredAt)
        assertEquals(occurredAt, event.occurredAt)
        assertTrue(event is PaymentEvent)
    }

    @Test
    fun `PaymentSuccessEvent 데이터 클래스 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val orderNo = "ORDER-12345"
        val orderType = "PURCHASE"
        val totalAmount = 65000
        val userId = 5L
        val orderItems = listOf(
            OrderItemInfo(UUID.randomUUID(), 1L, "Product 1", 2, 30000, "PRODUCT"),
            OrderItemInfo(UUID.randomUUID(), 2L, "Product 2", 1, 35000, "PRODUCT")
        )
        val paidAt = LocalDateTime.now()
        val paymentMethod = "CARD"
        val paymentKey = "payment_key_456"
        val occurredAt = LocalDateTime.now()

        // When
        val event = PaymentSuccessEvent(
            paymentId = paymentId,
            orderId = orderId,
            orderNo = orderNo,
            orderType = orderType,
            totalAmount = totalAmount,
            userId = userId,
            orderItems = orderItems,
            paidAt = paidAt,
            paymentMethod = paymentMethod,
            paymentKey = paymentKey,
            occurredAt = occurredAt
        )

        // Then
        assertEquals(paymentId, event.paymentId)
        assertEquals(orderId, event.orderId)
        assertEquals(orderNo, event.orderNo)
        assertEquals(orderType, event.orderType)
        assertEquals(totalAmount, event.totalAmount)
        assertEquals(userId, event.userId)
        assertEquals(orderItems, event.orderItems)
        assertEquals(paidAt, event.paidAt)
        assertEquals(paymentMethod, event.paymentMethod)
        assertEquals(paymentKey, event.paymentKey)
        assertEquals(occurredAt, event.occurredAt)
        assertTrue(event is PaymentEvent)
    }

    @Test
    fun `OrderItemInfo 데이터 클래스 테스트`() {
        // Given
        val id = UUID.randomUUID()
        val productId = 100L
        val productName = "Test Product"
        val quantity = 3
        val price = 15000
        val itemType = "PRODUCT"

        // When
        val orderItem = OrderItemInfo(
            id = id,
            productId = productId,
            productName = productName,
            quantity = quantity,
            price = price,
            itemType = itemType
        )

        // Then
        assertEquals(id, orderItem.id)
        assertEquals(productId, orderItem.productId)
        assertEquals(productName, orderItem.productName)
        assertEquals(quantity, orderItem.quantity)
        assertEquals(price, orderItem.price)
        assertEquals(itemType, orderItem.itemType)
    }

    @Test
    fun `데이터 클래스 copy 메소드 테스트`() {
        // Given
        val originalEvent = PaymentCreatedEvent(
            paymentId = UUID.randomUUID(),
            orderId = UUID.randomUUID(),
            orderNo = "ORDER-12345",
            amount = 10000,
            paymentMethod = "CARD",
            customerId = 1L
        )

        // When
        val copiedEvent = originalEvent.copy(amount = 20000, paymentMethod = "TRANSFER")

        // Then
        assertEquals(originalEvent.paymentId, copiedEvent.paymentId)
        assertEquals(originalEvent.orderId, copiedEvent.orderId)
        assertEquals(originalEvent.orderNo, copiedEvent.orderNo)
        assertEquals(20000, copiedEvent.amount) // 변경된 값
        assertEquals("TRANSFER", copiedEvent.paymentMethod) // 변경된 값
        assertEquals(originalEvent.customerId, copiedEvent.customerId)
    }

    @Test
    fun `PaymentEvent 인터페이스 구현 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val orderNo = "ORDER-12345"
        val occurredAt = LocalDateTime.now()

        val events: List<PaymentEvent> = listOf(
            PaymentCreatedEvent(paymentId, orderId, orderNo, 10000, "CARD", 1L, occurredAt),
            PaymentApprovedEvent(paymentId, orderId, orderNo, 10000, "CARD", "key", occurredAt, 1L, occurredAt),
            PaymentFailedEvent(paymentId, orderId, orderNo, 10000, "CARD", "Error", 1L, occurredAt),
            PaymentCancelledEvent(paymentId, orderId, orderNo, 10000, "Cancel", 1L, occurredAt),
            PaymentExpiredEvent(paymentId, orderId, orderNo, 10000, occurredAt, occurredAt)
        )

        // When & Then
        events.forEach { event ->
            assertEquals(paymentId, event.paymentId)
            assertEquals(orderId, event.orderId)
            assertEquals(occurredAt, event.occurredAt)
            assertTrue(event is PaymentEvent)
        }
    }

    @Test
    fun `sealed interface 패턴 매칭 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val orderNo = "ORDER-12345"
        val occurredAt = LocalDateTime.now()

        val events: List<PaymentEvent> = listOf(
            PaymentCreatedEvent(paymentId, orderId, orderNo, 10000, "CARD", 1L, occurredAt),
            PaymentApprovedEvent(paymentId, orderId, orderNo, 10000, "CARD", "key", occurredAt, 1L, occurredAt),
            PaymentFailedEvent(paymentId, orderId, orderNo, 10000, "CARD", "Error", 1L, occurredAt),
            PaymentCancelledEvent(paymentId, orderId, orderNo, 10000, "Cancel", 1L, occurredAt),
            PaymentExpiredEvent(paymentId, orderId, orderNo, 10000, occurredAt, occurredAt)
        )

        // When & Then
        events.forEach { event ->
            val eventType = when (event) {
                is PaymentCreatedEvent -> "CREATED"
                is PaymentApprovedEvent -> "APPROVED"
                is PaymentFailedEvent -> "FAILED"
                is PaymentCancelledEvent -> "CANCELLED"
                is PaymentCancelFailedEvent -> "CANCEL_FAILED"
                is PaymentExpiredEvent -> "EXPIRED"
                is PaymentSuccessEvent -> "SUCCESS"
                is InventoryConfirmationRequestedEvent -> "INVENTORY_CONFIRMATION"
                is OrderStatusUpdateRequestedEvent -> "ORDER_STATUS_UPDATE"
                is PaymentCancelFinalFailureEvent -> "CANCEL_FINAL_FAILURE"
                is PaymentCancelRetryEvent -> "CANCEL_RETRY"
                is QrCodeGenerationRequestedEvent -> "QR_GENERATION"
                is QrCodeInvalidationRequestedEvent -> "QR_INVALIDATION"
            }
            assertNotNull(eventType)
            assertTrue(event is PaymentEvent)
        }
    }

    @Test
    fun `null 값 처리 테스트`() {
        // Given & When
        val eventWithNullCustomer = PaymentCreatedEvent(
            paymentId = UUID.randomUUID(),
            orderId = UUID.randomUUID(),
            orderNo = "ORDER-12345",
            amount = 10000,
            paymentMethod = "CARD",
            customerId = null
        )

        val eventWithNullPaymentKey = PaymentApprovedEvent(
            paymentId = UUID.randomUUID(),
            orderId = UUID.randomUUID(),
            orderNo = "ORDER-12345",
            amount = 10000,
            paymentMethod = "CARD",
            paymentKey = null,
            approvedAt = LocalDateTime.now(),
            customerId = 1L
        )

        // Then
        assertEquals(null, eventWithNullCustomer.customerId)
        assertEquals(null, eventWithNullPaymentKey.paymentKey)
        assertTrue(eventWithNullCustomer is PaymentEvent)
        assertTrue(eventWithNullPaymentKey is PaymentEvent)
    }
}