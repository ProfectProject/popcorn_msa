package com.popcorn.payment.controller

import com.popcorn.payment.dto.*
import com.popcorn.payment.exception.PaymentException
import com.popcorn.payment.service.*
import com.popcorn.payment.util.PaymentTokenUtil
import com.popcorn.common.security.PassportPrincipal
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.core.GrantedAuthority
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * PaymentController 고급 테스트
 *
 * [커버리지 향상을 위한 테스트]
 * - PaymentController의 모든 엔드포인트 테스트
 * - 예외 처리 시나리오 테스트
 * - 다양한 입력값에 대한 응답 검증
 * - 80% 커버리지 달성에 기여하는 핵심 테스트
 */
class PaymentControllerAdvancedTest {

    private lateinit var tossPaymentService: TossPaymentCoroutineService
    private lateinit var paymentCommandService: PaymentCommandCoroutineService
    private lateinit var paymentTokenUtil: PaymentTokenUtil
    private lateinit var paymentApprovalAsyncService: PaymentApprovalAsyncService
    private lateinit var paymentController: PaymentController

    @BeforeEach
    fun setUp() {
        tossPaymentService = mockk()
        paymentCommandService = mockk()
        paymentTokenUtil = mockk()
        paymentApprovalAsyncService = mockk(relaxed = true)
        paymentController = PaymentController(
            tossPaymentService,
            paymentCommandService,
            paymentTokenUtil,
            paymentApprovalAsyncService
        )
    }

    @Test
    fun `결제 승인 성공 테스트`() = runBlocking {
        // Given
        val request = PaymentConfirmRequest("payment_key", UUID.randomUUID().toString(), 10000)
        val principal = createMockPrincipal()

        val result = TossPaymentConfirmResult(
            paymentId = UUID.randomUUID(),
            paymentStatus = "PAID",
            orderStatus = "COMPLETED",
            orderId = UUID.fromString(request.orderId),
            orderNo = "ORDER-123",
            amount = 10000,
            approvedAt = LocalDateTime.now()
        )

        coEvery { tossPaymentService.confirmPayment(any(), any(), any()) } returns result

        // When
        val response = paymentController.confirmPayment(request, principal)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(true, response.body!!.success)
        assertNotNull(response.body!!.data)

        coVerify { tossPaymentService.confirmPayment(request.paymentKey, request.orderId, request.amount) }
    }

    @Test
    fun `결제 승인 실패 - PaymentException 테스트`() = runBlocking {
        // Given
        val request = PaymentConfirmRequest("invalid_key", UUID.randomUUID().toString(), 10000)
        val principal = createMockPrincipal()

        coEvery { tossPaymentService.confirmPayment(any(), any(), any()) } throws
            PaymentException.invalidRequest("잘못된 결제 요청입니다")

        // When
        val response = paymentController.confirmPayment(request, principal)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body!!.success)
        assertEquals("잘못된 결제 요청입니다", response.body!!.message)
    }

    @Test
    fun `결제 승인 실패 - 일반 Exception 테스트`() = runBlocking {
        // Given
        val request = PaymentConfirmRequest("error_key", UUID.randomUUID().toString(), 10000)
        val principal = createMockPrincipal()

        coEvery { tossPaymentService.confirmPayment(any(), any(), any()) } throws
            RuntimeException("Internal server error")

        // When
        val response = paymentController.confirmPayment(request, principal)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body!!.success)
        assertEquals("시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", response.body!!.message)
    }

    @Test
    fun `비동기 결제 승인 성공 테스트`() = runBlocking {
        // Given
        val request = PaymentConfirmRequest("async_key", UUID.randomUUID().toString(), 15000)

        coEvery { paymentApprovalAsyncService.confirmAsync(any()) } just Runs

        // When
        val response = paymentController.confirmPaymentAsync(request)

        // Then
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNotNull(response.body)
        assertEquals(true, response.body!!.success)
        assertNotNull(response.body!!.data)
        assertEquals("IN_PROGRESS", response.body!!.data!!.paymentStatus)
        assertEquals("PAYMENT_PENDING", response.body!!.data!!.orderStatus)

        coVerify { paymentApprovalAsyncService.confirmAsync(request) }
    }

    @Test
    fun `비동기 결제 승인 실패 테스트`() = runBlocking {
        // Given
        val request = PaymentConfirmRequest("async_error", UUID.randomUUID().toString(), 15000)

        coEvery { paymentApprovalAsyncService.confirmAsync(any()) } throws
            RuntimeException("Async processing error")

        // When
        val response = paymentController.confirmPaymentAsync(request)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body!!.success)
    }

    @Test
    fun `결제 취소 성공 테스트`() = runBlocking {
        // Given
        val paymentId = UUID.randomUUID()
        val request = PaymentCancelRequest(paymentId, "고객 요청 취소", 10000)

        val paymentDetail = PaymentDetailResult(
            paymentId = paymentId,
            orderId = UUID.randomUUID(),
            amount = 10000,
            status = "PAID",
            rawPayload = null,
            approvedAt = LocalDateTime.now(),
            paymentKey = "test_key"
        )

        val cancelResult = TossPaymentCancelResult(
            paymentId = paymentId,
            orderId = paymentDetail.orderId!!,
            cancelAmount = 10000,
            status = "CANCELLED",
            cancelReason = "고객 요청 취소"
        )

        coEvery { paymentCommandService.getLatestPaymentByOrderId(any()) } returns paymentDetail
        coEvery { tossPaymentService.cancelPayment(any(), any()) } returns cancelResult

        // When
        val response = paymentController.cancelPayment(paymentId, request)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(true, response.body!!.success)
        assertNotNull(response.body!!.data)
        assertEquals("CANCELLED", response.body!!.data!!.status)
    }

    @Test
    fun `결제 취소 실패 - PaymentException 테스트`() = runBlocking {
        // Given
        val paymentId = UUID.randomUUID()
        val request = PaymentCancelRequest(paymentId, "취소 사유", 10000)

        coEvery { paymentCommandService.getLatestPaymentByOrderId(any()) } throws
            PaymentException.paymentNotFound()

        // When
        val response = paymentController.cancelPayment(paymentId, request)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body!!.success)
    }

    @Test
    fun `결제 생성 성공 테스트`() = runBlocking {
        // Given
        val request = PaymentCreateRequest(
            orderId = UUID.randomUUID(),
            paymentMethod = "CARD",
            amount = 20000,
            orderNo = "ORDER-789",
            customerId = 123L
        )

        val token = "generated_token_123"
        every { paymentTokenUtil.generatePaymentToken(any(), any(), any(), any()) } returns token

        // When
        val response = paymentController.createPayment(request)

        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        assertEquals(true, response.body!!.success)
        assertNotNull(response.body!!.data)
        assertEquals("READY", response.body!!.data!!.status)
        assertEquals(request.amount, response.body!!.data!!.amount)

        verify { paymentTokenUtil.generatePaymentToken(
            request.orderId.toString(),
            request.orderNo!!,
            request.amount,
            request.customerId.toString()
        )}
    }

    @Test
    fun `결제 생성 실패 테스트`() = runBlocking {
        // Given
        val request = PaymentCreateRequest(
            orderId = UUID.randomUUID(),
            paymentMethod = "CARD",
            amount = 20000
        )

        every { paymentTokenUtil.generatePaymentToken(any(), any(), any(), any()) } throws
            RuntimeException("Token generation failed")

        // When
        val response = paymentController.createPayment(request)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body!!.success)
    }

    @Test
    fun `결제 토큰 디코드 성공 테스트`() = runBlocking {
        // Given
        val token = "valid_token_123"
        val orderId = UUID.randomUUID()
        val paymentData = mapOf(
            "orderId" to orderId.toString(),
            "orderNo" to "ORDER-456",
            "amount" to 25000,
            "customerKey" to "customer_123",
            "successUrl" to "https://success.url",
            "failUrl" to "https://fail.url"
        )

        every { paymentTokenUtil.decryptPaymentToken(token) } returns paymentData
        every { paymentTokenUtil.validatePaymentToken(paymentData) } returns true

        // When
        val response = paymentController.decodePaymentToken(token)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(true, response.body!!.success)
        assertNotNull(response.body!!.data)
        assertEquals(orderId, response.body!!.data!!.orderId)
        assertEquals(25000, response.body!!.data!!.amount)
    }

    @Test
    fun `결제 토큰 디코드 실패 - 유효하지 않은 토큰 테스트`() = runBlocking {
        // Given
        val token = "invalid_token"
        val paymentData = mapOf("orderId" to "test")

        every { paymentTokenUtil.decryptPaymentToken(token) } returns paymentData
        every { paymentTokenUtil.validatePaymentToken(paymentData) } returns false

        // When
        val response = paymentController.decodePaymentToken(token)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body!!.success)
    }

    @Test
    fun `결제 상세 조회 성공 테스트`() = runBlocking {
        // Given
        val paymentId = UUID.randomUUID()
        val paymentDetail = PaymentDetailResult(
            paymentId = paymentId,
            orderId = UUID.randomUUID(),
            amount = 30000,
            status = "PAID",
            rawPayload = null,
            approvedAt = LocalDateTime.now(),
            paymentKey = "detail_key"
        )

        coEvery { paymentCommandService.getLatestPaymentByOrderId(paymentId) } returns paymentDetail

        // When
        val response = paymentController.getPayment(paymentId)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(true, response.body!!.success)
        assertNotNull(response.body!!.data)
        assertEquals(paymentId, response.body!!.data!!.paymentId)
        assertEquals(30000, response.body!!.data!!.amount)
    }

    @Test
    fun `결제 상세 조회 실패 테스트`() = runBlocking {
        // Given
        val paymentId = UUID.randomUUID()

        coEvery { paymentCommandService.getLatestPaymentByOrderId(paymentId) } throws
            PaymentException.paymentNotFound()

        // When
        val response = paymentController.getPayment(paymentId)

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body!!.success)
    }

    @Test
    fun `주문별 결제 목록 조회 테스트`() = runBlocking {
        // Given
        val orderId = UUID.randomUUID()

        // When
        val response = paymentController.getPaymentsByOrderId(orderId)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(true, response.body!!.success)
        assertNotNull(response.body!!.data)
        assertEquals(0, response.body!!.data!!.totalCount)
        assertEquals(0L, response.body!!.data!!.totalAmount)
    }

    @Test
    fun `최신 결제 상태 조회 성공 테스트`() = runBlocking {
        // Given
        val orderId = UUID.randomUUID()
        val paymentDetail = PaymentDetailResult(
            paymentId = UUID.randomUUID(),
            orderId = orderId,
            amount = 40000,
            status = "PAID",
            rawPayload = null,
            approvedAt = LocalDateTime.now(),
            paymentKey = "status_key"
        )

        coEvery { paymentCommandService.getLatestPaymentByOrderId(orderId) } returns paymentDetail

        // When
        val response = paymentController.getLatestPaymentStatus(orderId)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(true, response.body!!.success)
        assertNotNull(response.body!!.data)
        assertEquals(orderId, response.body!!.data!!.orderId)
        assertEquals("PAID", response.body!!.data!!.status)
    }

    @Test
    fun `헬스 체크 테스트`() = runBlocking {
        // When
        val response = paymentController.healthCheck()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("UP", response.body!!["status"])
        assertEquals("payment-service", response.body!!["service"])
        assertNotNull(response.body!!["timestamp"])
    }

    @Test
    fun `buildFrontendPaymentUrl private 메서드 테스트`() = runBlocking {
        // Given
        val request = PaymentCreateRequest(
            orderId = UUID.randomUUID(),
            paymentMethod = "CARD",
            amount = 15000,
            orderNo = "TEST-ORDER",
            customerId = 999L
        )

        val token = "test_frontend_token"
        every { paymentTokenUtil.generatePaymentToken(any(), any(), any(), any()) } returns token

        // When
        val response = paymentController.createPayment(request)

        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body?.data?.paymentUrl)
        val expectedUrl = "http://localhost:3000/auto-payment?token=$token"
        assertEquals(expectedUrl, response.body!!.data!!.paymentUrl)
    }

    @Test
    fun `다양한 결제 수단 처리 테스트`() = runBlocking {
        val paymentMethods = listOf("CARD", "TRANSFER", "VIRTUAL_ACCOUNT", "MOBILE_PHONE", "GIFT_CERTIFICATE")

        paymentMethods.forEach { method ->
            // Given
            val request = PaymentCreateRequest(
                orderId = UUID.randomUUID(),
                paymentMethod = method,
                amount = 10000
            )

            val token = "token_for_$method"
            every { paymentTokenUtil.generatePaymentToken(any(), any(), any(), any()) } returns token

            // When
            val response = paymentController.createPayment(request)

            // Then
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals(method, response.body!!.data!!.paymentMethod)
        }
    }

    private fun createMockPrincipal(): PassportPrincipal {
        return PassportPrincipal(
            userId = UUID.randomUUID(),
            email = "test@example.com",
            userAuthorities = emptyList<GrantedAuthority>()
        )
    }
}