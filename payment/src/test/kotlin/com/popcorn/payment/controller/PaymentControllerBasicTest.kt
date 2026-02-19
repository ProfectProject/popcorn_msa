package com.popcorn.payment.controller

import com.popcorn.payment.dto.*
import com.popcorn.payment.service.*
import com.popcorn.payment.util.PaymentTokenUtil
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * PaymentController 기본 단위 테스트
 *
 * [주요 비즈니스 로직만 테스트]
 * - 복잡한 Security principal 없이 핵심 로직만 검증
 * - Controller 메서드들의 기본 동작 확인
 * - 커버리지 향상에 기여
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("PaymentController 기본 테스트")
class PaymentControllerBasicTest {

    private val tossPaymentService = mockk<TossPaymentCoroutineService>()
    private val paymentCommandService = mockk<PaymentCommandCoroutineService>()
    private val paymentTokenUtil = mockk<PaymentTokenUtil>()
    private val paymentApprovalAsyncService = mockk<PaymentApprovalAsyncService>()

    private lateinit var paymentController: PaymentController

    private val orderId = UUID.randomUUID()
    private val paymentId = UUID.randomUUID()
    private val paymentKey = "test_payment_key"

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        paymentController = PaymentController(
            tossPaymentService,
            paymentCommandService,
            paymentTokenUtil,
            paymentApprovalAsyncService
        )
    }

    @Test
    @DisplayName("결제 승인 비동기 - 기본 성공 케이스")
    fun `confirmPaymentAsync should return accepted response`() = runBlocking {
        // Given
        val request = PaymentConfirmRequest(
            paymentKey = paymentKey,
            orderId = orderId.toString(),
            amount = 15000
        )

        coEvery {
            paymentApprovalAsyncService.confirmAsync(request)
        } just Runs

        // When
        val response = paymentController.confirmPaymentAsync(request)

        // Then
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNotNull(response.body?.data)
        assertEquals("IN_PROGRESS", response.body?.data?.paymentStatus)
        coVerify { paymentApprovalAsyncService.confirmAsync(request) }
    }

    @Test
    @DisplayName("결제 생성 - 토큰 생성 성공")
    fun `createPayment should generate payment token successfully`() = runBlocking {
        // Given
        val request = PaymentCreateRequest(
            orderId = orderId,
            paymentMethod = "CARD",
            amount = 20000
        )

        every {
            paymentTokenUtil.generatePaymentToken(any(), any(), any(), any())
        } returns "test_token_12345"

        // When
        val response = paymentController.createPayment(request)

        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body?.data)
        assertEquals("READY", response.body?.data?.status)
        assertEquals(20000, response.body?.data?.amount)
        assertNotNull(response.body?.data?.paymentUrl)
        verify { paymentTokenUtil.generatePaymentToken(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("결제 취소 - 기본 성공 케이스")
    fun `cancelPayment should process cancellation successfully`() = runBlocking {
        // Given
        val request = PaymentCancelRequest(cancelReason = "사용자 요청")
        val mockPaymentDetail = PaymentDetailResult(
            paymentId = paymentId,
            orderId = orderId,
            status = "PAID",
            amount = 10000,
            approvedAt = LocalDateTime.now(),
            rawPayload = null
        )

        val mockCancelResult = TossPaymentCancelResult(
            paymentId = paymentId,
            orderId = orderId,
            cancelAmount = 10000,
            status = "CANCELLED",
            cancelReason = "사용자 요청"
        )

        coEvery {
            paymentCommandService.getLatestPaymentByOrderId(paymentId)
        } returns mockPaymentDetail

        coEvery {
            tossPaymentService.cancelPayment(orderId, "사용자 요청")
        } returns mockCancelResult

        // When
        val response = paymentController.cancelPayment(paymentId, request)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.data)
        assertEquals("CANCELLED", response.body?.data?.status)
        coVerify { paymentCommandService.getLatestPaymentByOrderId(paymentId) }
        coVerify { tossPaymentService.cancelPayment(orderId, "사용자 요청") }
    }

    @Test
    @DisplayName("결제 토큰 복호화 - 성공 케이스")
    fun `decodePaymentToken should decode token successfully`() = runBlocking {
        // Given
        val token = "test_payment_token"
        val mockPaymentData: Map<String, Any> = mapOf(
            "orderId" to orderId.toString(),
            "orderNo" to "ORDER-123",
            "amount" to 15000,
            "customerKey" to "customer_123",
            "successUrl" to "http://localhost:3000/payment/success",
            "failUrl" to "http://localhost:3000/payment/fail"
        )

        every { paymentTokenUtil.decryptPaymentToken(token) } returns mockPaymentData
        every { paymentTokenUtil.validatePaymentToken(mockPaymentData) } returns true

        // When
        val response = paymentController.decodePaymentToken(token)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.data)
        assertEquals(orderId, response.body?.data?.orderId)
        assertEquals(15000, response.body?.data?.amount)
        assertEquals("ORDER-123", response.body?.data?.orderNo)
        assertEquals("customer_123", response.body?.data?.customerKey)
        assertEquals("http://localhost:3000/payment/success", response.body?.data?.successUrl)
        assertEquals("http://localhost:3000/payment/fail", response.body?.data?.failUrl)
        verify { paymentTokenUtil.decryptPaymentToken(token) }
        verify { paymentTokenUtil.validatePaymentToken(mockPaymentData) }
    }

    @Test
    @DisplayName("결제 조회 - 성공 케이스")
    fun `getPayment should return payment details`() = runBlocking {
        // Given
        val mockResult = PaymentDetailResult(
            paymentId = paymentId,
            orderId = orderId,
            status = "PAID",
            amount = 25000,
            approvedAt = LocalDateTime.now(),
            rawPayload = "{\"test\": \"data\"}"
        )

        coEvery {
            paymentCommandService.getLatestPaymentByOrderId(paymentId)
        } returns mockResult

        // When
        val response = paymentController.getPayment(paymentId)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.data)
        assertEquals("PAID", response.body?.data?.status)
        assertEquals(25000, response.body?.data?.amount)
        coVerify { paymentCommandService.getLatestPaymentByOrderId(paymentId) }
    }

    @Test
    @DisplayName("주문별 결제 목록 조회 - 성공 케이스")
    fun `getPaymentsByOrderId should return payment list`() = runBlocking {
        // When
        val response = paymentController.getPaymentsByOrderId(orderId)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.data)
        assertEquals(0, response.body?.data?.totalCount) // 기본적으로 빈 리스트 반환
    }

    @Test
    @DisplayName("최신 결제 상태 조회 - 성공 케이스")
    fun `getLatestPaymentStatus should return latest payment status`() = runBlocking {
        // Given
        val mockResult = PaymentDetailResult(
            paymentId = paymentId,
            orderId = orderId,
            status = "PAID",
            amount = 30000,
            approvedAt = LocalDateTime.now(),
            rawPayload = null
        )

        coEvery {
            paymentCommandService.getLatestPaymentByOrderId(orderId)
        } returns mockResult

        // When
        val response = paymentController.getLatestPaymentStatus(orderId)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.data)
        assertEquals("PAID", response.body?.data?.status)
        assertEquals(paymentId, response.body?.data?.paymentId)
        coVerify { paymentCommandService.getLatestPaymentByOrderId(orderId) }
    }

    @Test
    @DisplayName("헬스체크 - 성공 케이스")
    fun `healthCheck should return UP status`() = runBlocking {
        // When
        val response = paymentController.healthCheck()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("UP", response.body?.get("status"))
        assertEquals("payment-service", response.body?.get("service"))
    }

    @Test
    @DisplayName("결제 생성 실패 - 토큰 생성 예외 처리")
    fun `createPayment should handle token generation exception`() = runBlocking {
        // Given
        val request = PaymentCreateRequest(
            orderId = orderId,
            paymentMethod = "CARD",
            amount = 20000
        )

        every {
            paymentTokenUtil.generatePaymentToken(any(), any(), any(), any())
        } throws RuntimeException("토큰 생성 실패")

        // When
        val response = paymentController.createPayment(request)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body?.success)
        verify { paymentTokenUtil.generatePaymentToken(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("결제 토큰 복호화 실패 - 검증 실패")
    fun `decodePaymentToken should handle validation failure`() = runBlocking {
        // Given
        val token = "test_payment_token"
        val mockPaymentData: Map<String, Any> = mapOf(
            "orderId" to orderId.toString(),
            "orderNo" to "ORDER-123",
            "amount" to 15000,
            "customerKey" to "customer_123"
        )

        every { paymentTokenUtil.decryptPaymentToken(token) } returns mockPaymentData
        every { paymentTokenUtil.validatePaymentToken(mockPaymentData) } returns false

        // When
        val response = paymentController.decodePaymentToken(token)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body?.success)
        verify { paymentTokenUtil.decryptPaymentToken(token) }
        verify { paymentTokenUtil.validatePaymentToken(mockPaymentData) }
    }
}