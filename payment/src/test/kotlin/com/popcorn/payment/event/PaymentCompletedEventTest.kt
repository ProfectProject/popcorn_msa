package com.popcorn.payment.event

import com.popcorn.payment.event.domain.legacy.PaymentCompletedEvent
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaymentCompletedEventTest {

    @Test
    fun `PaymentCompletedEvent 기본 생성 테스트`() {
        // Given
        val eventId = "event_123"
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val paymentKey = "payment_key_123"
        val amount = 10000
        val paymentMethod = "CARD"
        val completedAt = LocalDateTime.now()

        // When
        val event = PaymentCompletedEvent(
            eventId = eventId,
            orderId = orderId,
            paymentId = paymentId,
            paymentKey = paymentKey,
            amount = amount,
            paymentMethod = paymentMethod,
            completedAt = completedAt
        )

        // Then
        assertEquals(eventId, event.eventId)
        assertEquals(orderId, event.orderId)
        assertEquals(paymentId, event.paymentId)
        assertEquals(paymentKey, event.paymentKey)
        assertEquals(amount, event.amount)
        assertEquals(paymentMethod, event.paymentMethod)
        assertEquals(completedAt, event.completedAt)
        assertNotNull(event.eventTime)
        assertNull(event.pgResponse)
    }

    @Test
    fun `PaymentCompletedEvent create 팩토리 메서드 테스트`() {
        // Given
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val paymentKey = "payment_key_456"
        val amount = 25000
        val paymentMethod = "BANK_TRANSFER"

        // When
        val event = PaymentCompletedEvent.create(
            orderId = orderId,
            paymentId = paymentId,
            paymentKey = paymentKey,
            amount = amount,
            paymentMethod = paymentMethod
        )

        // Then
        assertNotNull(event.eventId)
        assertEquals(orderId, event.orderId)
        assertEquals(paymentId, event.paymentId)
        assertEquals(paymentKey, event.paymentKey)
        assertEquals(amount, event.amount)
        assertEquals(paymentMethod, event.paymentMethod)
        assertNotNull(event.completedAt)
        assertNotNull(event.eventTime)
        assertNull(event.pgResponse)
    }

    @Test
    fun `PaymentCompletedEvent createWithPgResponse 팩토리 메서드 테스트`() {
        // Given
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val paymentKey = "payment_key_789"
        val amount = 35000
        val paymentMethod = "VIRTUAL_ACCOUNT"
        val pgResponse = """{"status":"success","transactionId":"txn_123","method":"virtual_account"}"""

        // When
        val event = PaymentCompletedEvent.createWithPgResponse(
            orderId = orderId,
            paymentId = paymentId,
            paymentKey = paymentKey,
            amount = amount,
            paymentMethod = paymentMethod,
            pgResponse = pgResponse
        )

        // Then
        assertNotNull(event.eventId)
        assertEquals(orderId, event.orderId)
        assertEquals(paymentId, event.paymentId)
        assertEquals(paymentKey, event.paymentKey)
        assertEquals(amount, event.amount)
        assertEquals(paymentMethod, event.paymentMethod)
        assertNotNull(event.completedAt)
        assertNotNull(event.eventTime)
        assertEquals(pgResponse, event.pgResponse)
    }

    @Test
    fun `PaymentCompletedEvent copy 메서드 테스트`() {
        // Given
        val originalEvent = PaymentCompletedEvent.create(
            orderId = UUID.randomUUID(),
            paymentId = UUID.randomUUID(),
            paymentKey = "original_key",
            amount = 10000,
            paymentMethod = "CARD"
        )

        // When
        val copiedEvent = originalEvent.copy(
            amount = 20000,
            paymentMethod = "BANK_TRANSFER"
        )

        // Then
        assertEquals(originalEvent.eventId, copiedEvent.eventId)
        assertEquals(originalEvent.orderId, copiedEvent.orderId)
        assertEquals(originalEvent.paymentId, copiedEvent.paymentId)
        assertEquals(originalEvent.paymentKey, copiedEvent.paymentKey)
        assertEquals(20000, copiedEvent.amount) // 변경된 값
        assertEquals("BANK_TRANSFER", copiedEvent.paymentMethod) // 변경된 값
        assertEquals(originalEvent.completedAt, copiedEvent.completedAt)
        assertEquals(originalEvent.eventTime, copiedEvent.eventTime)
    }

    @Test
    fun `null paymentKey 처리 테스트`() {
        // Given & When
        val event = PaymentCompletedEvent.create(
            orderId = UUID.randomUUID(),
            paymentId = UUID.randomUUID(),
            paymentKey = null,
            amount = 15000,
            paymentMethod = "CASH"
        )

        // Then
        assertNull(event.paymentKey)
        assertNotNull(event.eventId)
        assertEquals("CASH", event.paymentMethod)
    }

    @Test
    fun `eventId 유니크성 테스트`() {
        // Given
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()

        // When
        val event1 = PaymentCompletedEvent.create(orderId, paymentId, "key1", 10000, "CARD")
        val event2 = PaymentCompletedEvent.create(orderId, paymentId, "key2", 20000, "CARD")

        // Then
        // 같은 주문/결제 ID여도 eventId는 달라야 함
        assertNotNull(event1.eventId)
        assertNotNull(event2.eventId)
        assert(event1.eventId != event2.eventId)
    }

    @Test
    fun `시간 필드 값 검증 테스트`() {
        // Given
        val beforeCreate = LocalDateTime.now()

        // When
        val event = PaymentCompletedEvent.create(
            orderId = UUID.randomUUID(),
            paymentId = UUID.randomUUID(),
            paymentKey = "time_test_key",
            amount = 30000,
            paymentMethod = "CARD"
        )

        val afterCreate = LocalDateTime.now()

        // Then
        // completedAt과 eventTime이 생성 시간 범위 내에 있어야 함
        assert(!event.completedAt.isBefore(beforeCreate))
        assert(!event.completedAt.isAfter(afterCreate))
        assert(!event.eventTime.isBefore(beforeCreate))
        assert(!event.eventTime.isAfter(afterCreate))
    }
}