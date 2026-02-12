package com.popcorn.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.client.TossPaymentsCoroutineClient
import com.popcorn.payment.config.CoroutineTransactionManager
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.dto.TossPaymentCancelRequest
import com.popcorn.payment.dto.TossPaymentCancelResponse
import com.popcorn.payment.dto.TossPaymentConfirmRequest
import com.popcorn.payment.dto.TossPaymentConfirmResponse
import com.popcorn.payment.entity.Payment
import com.popcorn.payment.entity.PaymentStatus
import com.popcorn.payment.entity.PaymentMethod
import com.popcorn.payment.event.PaymentEventPublisherImpl
import com.popcorn.payment.exception.PaymentException
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * TossPaymentCoroutineService 단위 테스트
 *
 * [커버리지 향상을 위한 테스트]
 * - TossPaymentCoroutineService의 주요 메서드들 단위 테스트
 * - 모든 데이터 클래스 (TossPaymentConfirmResult, TossPaymentCancelResult, PaymentCreateResult) 테스트
 * - 예외 처리 및 검증 로직 테스트
 * - 80% 커버리지 달성에 기여하는 핵심 테스트
 */
class TossPaymentCoroutineServiceUnitTest {

    private lateinit var tossClient: TossPaymentsCoroutineClient
    private lateinit var transactionManager: CoroutineTransactionManager
    private lateinit var paymentCommandService: PaymentCommandCoroutineService
    private lateinit var orderQueryService: OrderQueryCoroutineService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var paymentEventPublisher: PaymentEventPublisherImpl
    private lateinit var tossPaymentService: TossPaymentCoroutineService

    @BeforeEach
    fun setUp() {
        tossClient = mockk()
        transactionManager = mockk()
        paymentCommandService = mockk()
        orderQueryService = mockk()
        objectMapper = ObjectMapper()
        paymentEventPublisher = mockk(relaxed = true)
        tossPaymentService = TossPaymentCoroutineService(
            tossClient,
            transactionManager,
            paymentCommandService,
            orderQueryService,
            objectMapper,
            paymentEventPublisher
        )
    }

    @Test
    fun `TossPaymentConfirmResult 생성 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val amount = 15000
        val approvedAt = LocalDateTime.now()

        // When
        val result = TossPaymentConfirmResult(
            paymentId = paymentId,
            paymentStatus = "PAID",
            orderStatus = "COMPLETED",
            orderId = orderId,
            orderNo = "ORDER-123",
            amount = amount,
            approvedAt = approvedAt
        )

        // Then
        assertEquals(paymentId, result.paymentId)
        assertEquals("PAID", result.paymentStatus)
        assertEquals("COMPLETED", result.orderStatus)
        assertEquals(orderId, result.orderId)
        assertEquals("ORDER-123", result.orderNo)
        assertEquals(amount, result.amount)
        assertEquals(approvedAt, result.approvedAt)
    }

    @Test
    fun `TossPaymentCancelResult 생성 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val cancelAmount = 10000

        // When
        val result = TossPaymentCancelResult(
            paymentId = paymentId,
            orderId = orderId,
            cancelAmount = cancelAmount,
            status = "CANCELLED",
            cancelReason = "고객 요청"
        )

        // Then
        assertEquals(paymentId, result.paymentId)
        assertEquals(orderId, result.orderId)
        assertEquals(cancelAmount, result.cancelAmount)
        assertEquals("CANCELLED", result.status)
        assertEquals("고객 요청", result.cancelReason)
    }

    @Test
    fun `PaymentCreateResult 생성 테스트`() {
        // Given
        val orderId = "ORDER-456"
        val amount = 25000
        val paymentUrl = "https://checkout.tosspayments.com/v1/payment"
        val expiresAt = LocalDateTime.now().plusMinutes(30)

        // When
        val result = PaymentCreateResult(
            paymentUrl = paymentUrl,
            orderId = orderId,
            amount = amount,
            expiresAt = expiresAt
        )

        // Then
        assertEquals(paymentUrl, result.paymentUrl)
        assertEquals(orderId, result.orderId)
        assertEquals(amount, result.amount)
        assertEquals(expiresAt, result.expiresAt)
    }

    @Test
    fun `createPaymentRequest 성공 테스트`() = runBlocking {
        // Given
        val orderId = "ORDER-789"
        val amount = 30000
        val orderName = "테스트 상품"
        val customerKey = "customer_123"

        // When
        val result = tossPaymentService.createPaymentRequest(orderId, amount, orderName, customerKey)

        // Then
        assertNotNull(result)
        assertEquals(orderId, result.orderId)
        assertEquals(amount, result.amount)
        assertNotNull(result.paymentUrl)
        assertNotNull(result.expiresAt)
        assert(result.paymentUrl.contains("orderId=$orderId"))
        assert(result.paymentUrl.contains("amount=$amount"))
        assert(result.paymentUrl.contains("customerKey=$customerKey"))
    }

    @Test
    fun `createPaymentRequest URL 생성 테스트`() = runBlocking {
        // Given
        val orderId = "TEST-ORDER"
        val amount = 5000
        val orderName = "한글 상품명"
        val customerKey = "test_customer"

        // When
        val result = tossPaymentService.createPaymentRequest(orderId, amount, orderName, customerKey)

        // Then
        val expectedBaseUrl = "https://js.tosspayments.com/v1/payment"
        assert(result.paymentUrl.startsWith(expectedBaseUrl))
        assert(result.paymentUrl.contains("orderId=$orderId"))
        assert(result.paymentUrl.contains("amount=$amount"))
        assert(result.paymentUrl.contains("customerKey=$customerKey"))
        // 한글이 URL 인코딩되었는지 확인
        assert(result.paymentUrl.contains("orderName="))
    }

    @Test
    fun `confirmPayment 멱등성 체크 테스트 - 기존 결제 존재`() = runBlocking {
        // Given
        val paymentKey = "payment_key_123"
        val orderId = "ORDER-123"
        val amount = 10000
        val existingPaymentId = UUID.randomUUID()
        val existingOrderId = UUID.randomUUID()

        val existingPayment = PaymentDetailResult(
            paymentId = existingPaymentId,
            orderId = existingOrderId,
            paymentKey = paymentKey,
            status = "PAID",
            amount = amount,
            approvedAt = LocalDateTime.now().minusHours(1),
            rawPayload = null
        )

        coEvery { paymentCommandService.findByPaymentKey(paymentKey) } returns listOf(existingPayment)

        // When
        val result = tossPaymentService.confirmPayment(paymentKey, orderId, amount)

        // Then
        assertEquals(existingPaymentId, result.paymentId)
        assertEquals("PAID", result.paymentStatus)
        assertEquals(existingOrderId, result.orderId)
        assertEquals(amount, result.amount)
        verify { tossClient wasNot Called }
    }

    @Test
    fun `confirmPayment 새로운 결제 처리 테스트`() = runBlocking {
        // Given
        val paymentKey = "new_payment_key"
        val orderIdUuid = UUID.randomUUID()
        val orderId = orderIdUuid.toString() // UUID 문자열로 변환
        val amount = 20000
        val newPaymentId = UUID.randomUUID()

        // Mock empty existing payments
        coEvery { paymentCommandService.findByPaymentKey(paymentKey) } returns emptyList()

        // Mock TossPayments response
        val tossResponse = TossPaymentConfirmResponse(
            paymentKey = paymentKey,
            orderId = orderId,
            totalAmount = amount,
            status = "DONE",
            method = "CARD",
            approvedAt = "2024-01-01T10:00:00+09:00"
        )
        coEvery { tossClient.confirm(any()) } returns tossResponse

        // Mock payment creation
        val createdPayment = PaymentCreationResult(
            paymentId = newPaymentId,
            status = "PAID",
            amount = amount,
            createdAt = LocalDateTime.now()
        )

        coEvery { transactionManager.executeInTransactionSuspend<PaymentCreationResult>(any()) } returns createdPayment

        // When
        val result = tossPaymentService.confirmPayment(paymentKey, orderId, amount)

        // Then
        assertEquals(newPaymentId, result.paymentId)
        assertEquals("PAID", result.paymentStatus)
        assertEquals(EventConstants.EventTypes.PAYMENT_SUCCESS, result.orderStatus)
        assertEquals(orderIdUuid, result.orderId)
        assertEquals(amount, result.amount)
        assertNotNull(result.approvedAt)

        coVerify { tossClient.confirm(any()) }
    }

    @Test
    fun `cancelPayment 결제 상태 검증 테스트`() = runBlocking {
        // Given
        val orderId = UUID.randomUUID()
        val cancelReason = "고객 요청 취소"
        val paymentId = UUID.randomUUID()

        val order = OrderInfo(
            id = orderId,
            orderNo = "ORDER-CANCEL",
            customerId = 123L,
            totalAmount = 15000,
            status = "PAID",
            orderType = "NORMAL",
            createdAt = LocalDateTime.now()
        )

        val payment = PaymentDetailResult(
            paymentId = paymentId,
            orderId = orderId,
            amount = 15000,
            status = "READY", // 결제 완료 상태가 아님
            paymentKey = "cancel_payment_key",
            rawPayload = null,
            approvedAt = null
        )

        coEvery { orderQueryService.getOrder(orderId) } returns order
        coEvery { paymentCommandService.getLatestPaymentByOrderId(orderId) } returns payment

        // When & Then
        val exception = assertThrows<PaymentException> {
            tossPaymentService.cancelPayment(orderId, cancelReason)
        }

        assert(exception.message?.contains("결제 완료 상태가 아닙니다") == true)
    }

    @Test
    fun `Data class copy 메서드 테스트`() {
        // Given
        val originalResult = TossPaymentConfirmResult(
            paymentId = UUID.randomUUID(),
            paymentStatus = "PENDING",
            orderStatus = "PROCESSING",
            orderId = UUID.randomUUID(),
            orderNo = "ORIGINAL-ORDER",
            amount = 10000,
            approvedAt = LocalDateTime.now()
        )

        // When
        val updatedResult = originalResult.copy(
            paymentStatus = "PAID",
            orderStatus = "COMPLETED"
        )

        // Then
        assertEquals(originalResult.paymentId, updatedResult.paymentId)
        assertEquals(originalResult.orderId, updatedResult.orderId)
        assertEquals(originalResult.orderNo, updatedResult.orderNo)
        assertEquals(originalResult.amount, updatedResult.amount)
        assertEquals(originalResult.approvedAt, updatedResult.approvedAt)
        assertEquals("PAID", updatedResult.paymentStatus)
        assertEquals("COMPLETED", updatedResult.orderStatus)
    }

    @Test
    fun `Data class equals 및 hashCode 테스트`() {
        // Given
        val paymentId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val approvedAt = LocalDateTime.now()

        val result1 = TossPaymentConfirmResult(
            paymentId = paymentId,
            paymentStatus = "PAID",
            orderStatus = "COMPLETED",
            orderId = orderId,
            orderNo = "ORDER-123",
            amount = 10000,
            approvedAt = approvedAt
        )

        val result2 = TossPaymentConfirmResult(
            paymentId = paymentId,
            paymentStatus = "PAID",
            orderStatus = "COMPLETED",
            orderId = orderId,
            orderNo = "ORDER-123",
            amount = 10000,
            approvedAt = approvedAt
        )

        val result3 = TossPaymentConfirmResult(
            paymentId = paymentId,
            paymentStatus = "CANCELLED",
            orderStatus = "CANCELLED",
            orderId = orderId,
            orderNo = "ORDER-123",
            amount = 10000,
            approvedAt = approvedAt
        )

        // When & Then
        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
        assert(result1 != result3)
        assert(result1.hashCode() != result3.hashCode())
    }

    @Test
    fun `Data class toString 테스트`() {
        // Given
        val result = PaymentCreateResult(
            paymentUrl = "https://test.url",
            orderId = "ORDER-456",
            amount = 5000,
            expiresAt = LocalDateTime.now()
        )

        // When
        val toString = result.toString()

        // Then
        assertNotNull(toString)
        assert(toString.contains("ORDER-456"))
        assert(toString.contains("5000"))
        assert(toString.contains("https://test.url"))
    }

    @Test
    fun `특수 문자가 포함된 주문명 URL 인코딩 테스트`() = runBlocking {
        // Given
        val orderId = "SPECIAL-ORDER"
        val amount = 8000
        val orderName = "특수문자@#$%^&*() 주문"
        val customerKey = "special_customer"

        // When
        val result = tossPaymentService.createPaymentRequest(orderId, amount, orderName, customerKey)

        // Then
        assertNotNull(result.paymentUrl)
        // URL에 한글과 특수문자가 적절히 인코딩되었는지 확인
        assert(result.paymentUrl.contains("orderName="))
        // 원본 특수문자가 그대로 있으면 안됨
        assert(!result.paymentUrl.contains("@#\$%^&*()"))
    }

    @Test
    fun `결제 만료 시간 설정 테스트`() = runBlocking {
        // Given
        val orderId = "EXPIRY-TEST"
        val amount = 1000
        val orderName = "만료 테스트"
        val customerKey = "expiry_customer"
        val beforeTime = LocalDateTime.now()

        // When
        val result = tossPaymentService.createPaymentRequest(orderId, amount, orderName, customerKey)

        // Then
        val afterTime = LocalDateTime.now()
        assert(result.expiresAt.isAfter(beforeTime.plusMinutes(29)))
        assert(result.expiresAt.isBefore(afterTime.plusMinutes(31)))
    }

    @Test
    fun `PaymentCreateResult 필수 필드 검증`() {
        // Given
        val paymentUrl = "https://payment.test.com"
        val orderId = "VALIDATION-ORDER"
        val amount = 12000
        val expiresAt = LocalDateTime.now().plusMinutes(30)

        // When
        val result = PaymentCreateResult(paymentUrl, orderId, amount, expiresAt)

        // Then
        assertNotNull(result.paymentUrl)
        assertNotNull(result.orderId)
        assertNotNull(result.amount)
        assertNotNull(result.expiresAt)
        assert(result.amount > 0)
        assert(result.expiresAt.isAfter(LocalDateTime.now()))
    }

    @Test
    fun `대용량 결제 금액 처리 테스트`() = runBlocking {
        // Given
        val orderId = "LARGE-AMOUNT-ORDER"
        val amount = 999_999_999 // 최대 금액
        val orderName = "대용량 주문"
        val customerKey = "large_customer"

        // When
        val result = tossPaymentService.createPaymentRequest(orderId, amount, orderName, customerKey)

        // Then
        assertEquals(amount, result.amount)
        assert(result.paymentUrl.contains("amount=$amount"))
    }

    @Test
    fun `최소 결제 금액 처리 테스트`() = runBlocking {
        // Given
        val orderId = "MIN-AMOUNT-ORDER"
        val amount = 1 // 최소 금액
        val orderName = "최소 주문"
        val customerKey = "min_customer"

        // When
        val result = tossPaymentService.createPaymentRequest(orderId, amount, orderName, customerKey)

        // Then
        assertEquals(amount, result.amount)
        assert(result.paymentUrl.contains("amount=$amount"))
    }
}
