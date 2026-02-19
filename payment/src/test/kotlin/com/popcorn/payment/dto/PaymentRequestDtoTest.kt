package com.popcorn.payment.dto

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PaymentRequestDto 테스트
 *
 * [커버리지 향상을 위한 테스트]
 * - 모든 Request DTO의 생성과 유효성 검증 테스트
 * - Jakarta Validation 어노테이션 검증 테스트
 * - Data class의 기본 기능들 테스트
 * - 80% 커버리지 달성에 기여하는 핵심 테스트
 */
class PaymentRequestDtoTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Test
    fun `PaymentConfirmRequest 유효한 요청 테스트`() {
        // Given
        val request = PaymentConfirmRequest(
            paymentKey = "test_payment_key_123",
            orderId = "550e8400-e29b-41d4-a716-446655440000",
            amount = 15000
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
        assertEquals("test_payment_key_123", request.paymentKey)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", request.orderId)
        assertEquals(15000, request.amount)
    }

    @Test
    fun `PaymentConfirmRequest 빈 paymentKey 검증 실패 테스트`() {
        // Given
        val request = PaymentConfirmRequest(
            paymentKey = "",
            orderId = "550e8400-e29b-41d4-a716-446655440000",
            amount = 15000
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "결제키는 필수입니다" })
    }

    @Test
    fun `PaymentConfirmRequest 빈 orderId 검증 실패 테스트`() {
        // Given
        val request = PaymentConfirmRequest(
            paymentKey = "test_payment_key",
            orderId = "",
            amount = 15000
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "주문ID는 필수입니다" })
    }

    @Test
    fun `PaymentConfirmRequest 최소 금액 검증 실패 테스트`() {
        // Given
        val request = PaymentConfirmRequest(
            paymentKey = "test_payment_key",
            orderId = "test_order_id",
            amount = 0
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "결제 금액은 1원 이상이어야 합니다" })
    }

    @Test
    fun `PaymentConfirmRequest 최대 금액 검증 실패 테스트`() {
        // Given
        val request = PaymentConfirmRequest(
            paymentKey = "test_payment_key",
            orderId = "test_order_id",
            amount = 100_000_001
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "결제 금액은 1억원을 초과할 수 없습니다" })
    }

    @Test
    fun `PaymentCancelRequest 유효한 요청 테스트`() {
        // Given
        val request = PaymentCancelRequest(
            paymentId = UUID.randomUUID(),
            cancelReason = "사용자 요청으로 인한 취소",
            cancelAmount = 10000
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
        assertEquals("사용자 요청으로 인한 취소", request.cancelReason)
        assertEquals(10000, request.cancelAmount)
    }

    @Test
    fun `PaymentCancelRequest 기본값 테스트`() {
        // Given
        val request = PaymentCancelRequest(
            paymentId = UUID.randomUUID(),
            cancelReason = "전체 취소"
            // cancelAmount는 기본값 null 사용
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
        assertEquals(null, request.cancelAmount)
    }

    @Test
    fun `PaymentCancelRequest 빈 취소 사유 검증 실패 테스트`() {
        // Given
        val request = PaymentCancelRequest(
            paymentId = UUID.randomUUID(),
            cancelReason = "",
            cancelAmount = 5000
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "취소 사유는 필수입니다" })
    }

    @Test
    fun `PaymentCancelRequest 긴 취소 사유 검증 실패 테스트`() {
        // Given
        val longReason = "a".repeat(201)
        val request = PaymentCancelRequest(
            paymentId = UUID.randomUUID(),
            cancelReason = longReason,
            cancelAmount = 5000
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "취소 사유는 1-200자 내로 입력해주세요" })
    }

    @Test
    fun `PaymentCancelRequest 최소 취소 금액 검증 실패 테스트`() {
        // Given
        val request = PaymentCancelRequest(
            paymentId = UUID.randomUUID(),
            cancelReason = "부분 취소",
            cancelAmount = 0
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "취소 금액은 1원 이상이어야 합니다" })
    }

    @Test
    fun `PaymentCreateRequest 유효한 요청 테스트`() {
        // Given
        val orderId = UUID.randomUUID()
        val request = PaymentCreateRequest(
            orderId = orderId,
            paymentMethod = "CARD",
            amount = 25000,
            customerId = 123L,
            orderNo = "ORDER-12345",
            itemName = "팝콘 세트"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
        assertEquals(orderId, request.orderId)
        assertEquals("CARD", request.paymentMethod)
        assertEquals(25000, request.amount)
        assertEquals(123L, request.customerId)
        assertEquals("ORDER-12345", request.orderNo)
        assertEquals("팝콘 세트", request.itemName)
    }

    @Test
    fun `PaymentCreateRequest 기본값 테스트`() {
        // Given
        val orderId = UUID.randomUUID()
        val request = PaymentCreateRequest(
            orderId = orderId,
            paymentMethod = "TRANSFER",
            amount = 30000
            // customerId, orderNo, itemName은 기본값 null 사용
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
        assertEquals(null, request.customerId)
        assertEquals(null, request.orderNo)
        assertEquals(null, request.itemName)
    }

    @Test
    fun `PaymentCreateRequest 유효하지 않은 결제수단 검증 실패 테스트`() {
        // Given
        val request = PaymentCreateRequest(
            orderId = UUID.randomUUID(),
            paymentMethod = "INVALID_METHOD",
            amount = 10000
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "유효하지 않은 결제수단입니다" })
    }

    @Test
    fun `PaymentCreateRequest 다양한 유효한 결제수단 테스트`() {
        val validPaymentMethods = listOf(
            "CARD", "TRANSFER", "VIRTUAL_ACCOUNT", "MOBILE_PHONE", "GIFT_CERTIFICATE"
        )

        validPaymentMethods.forEach { method ->
            // Given
            val request = PaymentCreateRequest(
                orderId = UUID.randomUUID(),
                paymentMethod = method,
                amount = 10000
            )

            // When
            val violations = validator.validate(request)

            // Then
            assertTrue(violations.isEmpty(), "결제수단 $method 는 유효해야 합니다")
        }
    }

    @Test
    fun `PaymentCreateRequest 최소 금액 검증 실패 테스트`() {
        // Given
        val request = PaymentCreateRequest(
            orderId = UUID.randomUUID(),
            paymentMethod = "CARD",
            amount = 0
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "결제 금액은 1원 이상이어야 합니다" })
    }

    @Test
    fun `PaymentStatusUpdateRequest 유효한 요청 테스트`() {
        // Given
        val request = PaymentStatusUpdateRequest(
            status = "PAID",
            reason = "결제 완료"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
        assertEquals("PAID", request.status)
        assertEquals("결제 완료", request.reason)
    }

    @Test
    fun `PaymentStatusUpdateRequest 기본값 테스트`() {
        // Given
        val request = PaymentStatusUpdateRequest(
            status = "CANCELLED"
            // reason은 기본값 null 사용
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
        assertEquals(null, request.reason)
    }

    @Test
    fun `PaymentStatusUpdateRequest 유효하지 않은 상태 검증 실패 테스트`() {
        // Given
        val request = PaymentStatusUpdateRequest(
            status = "INVALID_STATUS",
            reason = "상태 변경"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message == "유효하지 않은 결제 상태입니다" })
    }

    @Test
    fun `PaymentStatusUpdateRequest 다양한 유효한 상태 테스트`() {
        val validStatuses = listOf("READY", "PAID", "CANCELLED", "FAILED")

        validStatuses.forEach { status ->
            // Given
            val request = PaymentStatusUpdateRequest(
                status = status,
                reason = "상태 변경: $status"
            )

            // When
            val violations = validator.validate(request)

            // Then
            assertTrue(violations.isEmpty(), "결제 상태 $status 는 유효해야 합니다")
        }
    }

    @Test
    fun `Data class copy 메소드 테스트`() {
        // Given
        val original = PaymentConfirmRequest(
            paymentKey = "original_key",
            orderId = "original_order",
            amount = 10000
        )

        // When
        val copied = original.copy(paymentKey = "new_key", amount = 15000)

        // Then
        assertEquals("new_key", copied.paymentKey)
        assertEquals("original_order", copied.orderId)
        assertEquals(15000, copied.amount)
    }

    @Test
    fun `Data class equals 및 hashCode 테스트`() {
        // Given
        val orderId = UUID.randomUUID()
        val request1 = PaymentCreateRequest(
            orderId = orderId,
            paymentMethod = "CARD",
            amount = 10000
        )
        val request2 = PaymentCreateRequest(
            orderId = orderId,
            paymentMethod = "CARD",
            amount = 10000
        )
        val request3 = PaymentCreateRequest(
            orderId = orderId,
            paymentMethod = "TRANSFER",
            amount = 10000
        )

        // When & Then
        assertEquals(request1, request2)
        assertEquals(request1.hashCode(), request2.hashCode())
        assertTrue(request1 != request3)
        assertTrue(request1.hashCode() != request3.hashCode())
    }
}