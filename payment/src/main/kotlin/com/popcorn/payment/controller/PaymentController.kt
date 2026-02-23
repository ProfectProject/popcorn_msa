package com.popcorn.payment.controller

import com.popcorn.payment.dto.*
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.exception.PaymentException
import com.popcorn.payment.service.TossPaymentCoroutineService
import com.popcorn.payment.service.PaymentCommandCoroutineService
import com.popcorn.payment.service.PaymentApprovalAsyncService
import com.popcorn.payment.util.PaymentTokenUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import jakarta.validation.Valid
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.bind.annotation.*
import com.popcorn.common.security.PassportPrincipal
import java.util.*

/**
 * 결제 API 컨트롤러 (코루틴 기반)
 */
@Tag(name = "Payment API", description = "결제 관리 API")
@RestController
@RequestMapping("/api/pay/v1/payments")
class PaymentController(
    private val tossPaymentService: TossPaymentCoroutineService,
    private val paymentCommandService: PaymentCommandCoroutineService,
    private val paymentTokenUtil: PaymentTokenUtil,
    private val paymentApprovalAsyncService: PaymentApprovalAsyncService
) {

    private val log = LoggerFactory.getLogger(PaymentController::class.java)
    private val genericErrorMessage = "시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요."

    @Value("\${frontend.base-url:\${FRONTEND_BASE_URL:http://localhost:3000}}")
    private lateinit var frontendBaseUrl: String

    private val frontendPaymentPath: String = "/auto-payment"

    /**
     * 토스페이먼츠 결제 승인
     */
    @Operation(
        summary = "결제 승인",
        description = "토스페이먼츠를 통한 결제 승인을 처리합니다. 멱등성이 보장되어 중복 요청 시 동일한 결과를 반환합니다."
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "결제 승인 성공"),
        SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/confirm")
    suspend fun confirmPayment(
        @Valid @RequestBody
        @Parameter(description = "결제 승인 요청 정보", required = true)
        request: PaymentConfirmRequest,
        @AuthenticationPrincipal principal: PassportPrincipal
    ): ResponseEntity<ApiResponse<PaymentConfirmResponse>> = coroutineScope {

        log.info("💳 결제 승인 요청: paymentKey={}, orderId={}, amount={}원",
            request.paymentKey, request.orderId, request.amount)

        try {
            val result = tossPaymentService.confirmPayment(
                paymentKey = request.paymentKey,
                orderId = request.orderId,
                amount = request.amount
            )

            val response = PaymentConfirmResponse(
                paymentId = result.paymentId,
                paymentStatus = result.paymentStatus,
                orderStatus = result.orderStatus,
                orderId = result.orderId,
                orderNo = result.orderNo,
                amount = result.amount,
                approvedAt = result.approvedAt
            )

            log.info("✅ 결제 승인 성공: paymentId={}, amount={}원", result.paymentId, result.amount)
            ResponseEntity.ok(ApiResponse.success(response, "결제가 성공적으로 승인되었습니다."))

        } catch (e: PaymentException) {
            log.error("❌ 결제 승인 실패: paymentKey={}, error={}", request.paymentKey, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "결제 승인에 실패했습니다."))

        } catch (e: Exception) {
            log.error("❌ 결제 승인 중 예외 발생: paymentKey={}", request.paymentKey, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(genericErrorMessage))
        }
    }

    /**
     * 토스페이먼츠 결제 승인 (비동기) - orderId 기반 간단 승인
     */
    @PostMapping("/confirm-async")
    suspend fun confirmPaymentAsync(
        @Valid @RequestBody
        @Parameter(description = "결제 승인 요청 정보", required = true)
        request: PaymentConfirmRequest
    ): ResponseEntity<ApiResponse<PaymentConfirmResponse>> {
        log.info("💳 결제 승인(비동기) 요청: paymentKey={}, orderId={}, amount={}원",
            request.paymentKey, request.orderId, request.amount)

        return try {
            // 🎯 이벤트 기반 MSA: Order Service 직접 호출 없이 이벤트만 발행
            log.info("🚀 결제 승인 이벤트 발행: orderId={}, amount={}원", request.orderId, request.amount)

            paymentApprovalAsyncService.confirmAsync(request)

            val response = PaymentConfirmResponse(
                paymentId = UUID.randomUUID(),
                paymentStatus = "IN_PROGRESS",
                orderStatus = "PAYMENT_PENDING",
                orderId = UUID.fromString(request.orderId),
                orderNo = "주문번호_미정", // Order Service가 이벤트로 처리
                amount = request.amount,
                approvedAt = null
            )

            ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response, "결제 승인 이벤트가 발행되었습니다."))
        } catch (e: Exception) {
            log.error("❌ 결제 승인(비동기) 중 예외 발생: paymentKey={}", request.paymentKey, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(genericErrorMessage))
        }
    }

    /**
     * 결제 취소
     */
    @Operation(
        summary = "결제 취소",
        description = "결제를 취소합니다"
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "결제 취소 성공"),
        SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
        SwaggerApiResponse(responseCode = "404", description = "결제 정보 없음"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/{paymentId}/cancel")
    suspend fun cancelPayment(
        @Parameter(
            name = "paymentId",
            description = "결제 ID",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @PathVariable paymentId: UUID,

        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "취소 요청 정보",
            required = true,
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = PaymentCancelRequest::class),
                examples = [ExampleObject(
                    name = "취소 요청 예시",
                    value = """
                    {
                        "paymentId": "550e8400-e29b-41d4-a716-446655440000",
                        "cancelReason": "고객 요청",
                        "cancelAmount": 10000
                    }
                    """
                )]
            )]
        )
        @Valid @RequestBody request: PaymentCancelRequest
    ): ResponseEntity<ApiResponse<PaymentCancelResponse>> {

        log.info("🔄 결제 취소 요청: paymentId={}, reason={}", paymentId, request.cancelReason)

        try {
            if (request.paymentId != paymentId) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("path paymentId와 body paymentId가 일치하지 않습니다."))
            }

            val result = tossPaymentService.cancelPaymentByPaymentId(
                paymentId = paymentId,
                cancelReason = request.cancelReason
            )

            val response = PaymentCancelResponse(
                paymentId = result.paymentId,
                orderId = result.orderId,
                cancelAmount = result.cancelAmount,
                status = result.status,
                cancelReason = result.cancelReason
            )

            log.info("✅ 결제 취소 성공: paymentId={}, cancelAmount={}원", result.paymentId, result.cancelAmount)
            return ResponseEntity.ok(ApiResponse.success(response, "결제가 성공적으로 취소되었습니다."))

        } catch (e: PaymentException) {
            log.error("❌ 결제 취소 실패: paymentId={}, error={}", paymentId, e.message)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "결제 취소에 실패했습니다."))

        } catch (e: Exception) {
            log.error("❌ 결제 취소 중 예외 발생: paymentId={}", paymentId, e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(genericErrorMessage))
        }
    }

    /**
     * 결제 생성
     */
    @PostMapping
    suspend fun createPayment(
        @Valid @RequestBody request: PaymentCreateRequest
    ): ResponseEntity<ApiResponse<PaymentCreateResponse>> {

        log.info("📝 결제 생성 요청: orderId={}, method={}, amount={}원",
            request.orderId, request.paymentMethod, request.amount)

        try {
            // 🎯 이벤트 기반: 결제 생성 이벤트 발행 (Order Service가 상태 관리)
            log.info("🚀 결제 생성 이벤트 발행: orderId={}", request.orderId)

            // 결제 기록은 승인 시점에 생성하므로, 여기서는 URL만 발급
            val paymentUrl = buildFrontendPaymentUrl(request)

            val response = PaymentCreateResponse(
                paymentId = null,
                orderId = request.orderId,
                amount = request.amount,
                status = "READY",
                paymentMethod = request.paymentMethod,
                createdAt = java.time.LocalDateTime.now(),
                paymentUrl = paymentUrl,
                expiresAt = java.time.LocalDateTime.now().plusMinutes(30)
            )

            log.info("✅ 결제 링크 생성 성공: orderId={}", request.orderId)
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "결제 링크가 생성되었습니다."))

        } catch (e: PaymentException) {
            log.error("❌ 결제 생성 실패: orderId={}, error={}", request.orderId, e.message)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "결제 생성에 실패했습니다."))

        } catch (e: Exception) {
            log.error("❌ 결제 생성 중 예외 발생: orderId={}", request.orderId, e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(genericErrorMessage))
        }
    }

    private fun buildFrontendPaymentUrl(request: PaymentCreateRequest): String {
        val orderNo = request.orderNo ?: request.orderId.toString()
        val customerKey = request.customerId?.toString() ?: "guest"

        // JWT 토큰으로 결제 정보 암호화
        val token = paymentTokenUtil.generatePaymentToken(
            orderId = request.orderId.toString(),
            orderNo = orderNo,
            amount = request.amount,
            customerKey = customerKey
        )

        return buildPaymentEntryUrl(token)
    }

    private fun buildPaymentEntryUrl(token: String): String {
        val paymentPath = if (frontendPaymentPath.startsWith("/")) frontendPaymentPath else "/$frontendPaymentPath"
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
            .path(paymentPath)
            .queryParam("token", token)
            .build()
            .toUriString()
    }

    /**
     * 결제 토큰 디코드
     */
    @GetMapping("/decode")
    suspend fun decodePaymentToken(
        @RequestParam token: String
    ): ResponseEntity<ApiResponse<PaymentTokenDecodeResponse>> {
        return try {
            log.info("🔓 결제 토큰 디코드 요청 - 토큰 길이: {}자", token.length)

            // JWT 토큰 복호화
            val paymentData = paymentTokenUtil.decryptPaymentToken(token)

            // 토큰 유효성 검증
            if (!paymentTokenUtil.validatePaymentToken(paymentData)) {
                throw PaymentException.invalidRequest("결제 토큰이 유효하지 않습니다.")
            }

            // 토큰에서 데이터 추출
            val orderId = UUID.fromString(paymentData["orderId"] as String)
            val orderNo = paymentData["orderNo"] as String
            val amount = paymentData["amount"] as Int
            val customerKey = paymentData["customerKey"] as String
            val successUrl = paymentData["successUrl"] as String
            val failUrl = paymentData["failUrl"] as String

            val response = PaymentTokenDecodeResponse(
                orderId = orderId,
                orderNo = orderNo,
                amount = amount,
                customerKey = customerKey,
                successUrl = successUrl,
                failUrl = failUrl
            )

            log.info("✅ 결제 토큰 디코드 성공 - 주문번호: {}, 금액: {}원", orderNo, amount)
            ResponseEntity.ok(ApiResponse.success(response, "결제 토큰 디코드 성공"))
        } catch (e: PaymentException) {
            log.error("❌ 결제 토큰 디코드 실패 - PaymentException: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "결제 토큰이 유효하지 않습니다."))
        } catch (e: Exception) {
            log.error("❌ 결제 토큰 디코드 중 예외 발생", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(genericErrorMessage))
        }
    }

    /**
     * 결제 토큰 재발급 (재결제용)
     */
    @GetMapping("/refresh")
    suspend fun refreshPaymentToken(
        @RequestParam token: String
    ): ResponseEntity<ApiResponse<PaymentTokenRefreshResponse>> {
        return try {
            log.info("🔄 결제 토큰 재발급 요청 - 토큰 길이: {}자", token.length)

            val paymentData = paymentTokenUtil.decryptPaymentToken(token)
            if (!paymentTokenUtil.validatePaymentToken(paymentData)) {
                throw PaymentException.invalidRequest("결제 토큰이 유효하지 않습니다.")
            }

            val orderId = paymentData["orderId"] as String
            val orderNo = paymentData["orderNo"] as String
            val amount = paymentData["amount"] as Int
            val customerKey = paymentData["customerKey"] as String

            val newToken = paymentTokenUtil.generatePaymentToken(
                orderId = orderId,
                orderNo = orderNo,
                amount = amount,
                customerKey = customerKey
            )

            val paymentUrl = buildPaymentEntryUrl(newToken)

            ResponseEntity.ok(
                ApiResponse.success(
                    PaymentTokenRefreshResponse(newToken, paymentUrl),
                    "결제 토큰 재발급 성공"
                )
            )
        } catch (e: PaymentException) {
            log.error("❌ 결제 토큰 재발급 실패 - PaymentException: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "결제 토큰 재발급 실패"))
        } catch (e: Exception) {
            log.error("❌ 결제 토큰 재발급 중 예외 발생", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(genericErrorMessage))
        }
    }

    /**
     * 결제 상세 조회
     */
    @GetMapping("/{paymentId}")
    suspend fun getPayment(
        @PathVariable paymentId: UUID
    ): ResponseEntity<ApiResponse<PaymentDetailResponse>> {

        log.debug("🔍 결제 조회 요청: paymentId={}", paymentId)

        try {
            val result = paymentCommandService.getPaymentById(paymentId)

            val response = PaymentDetailResponse(
                paymentId = result.paymentId,
                orderId = result.orderId ?: UUID.randomUUID(),
                amount = result.amount,
                status = result.status,
                paymentMethod = "CARD", // 임시값
                createdAt = java.time.LocalDateTime.now(),
                approvedAt = result.approvedAt,
                updatedAt = java.time.LocalDateTime.now()
            )

            return ResponseEntity.ok(ApiResponse.success(response))

        } catch (e: PaymentException) {
            log.error("❌ 결제 조회 실패: paymentId={}, error={}", paymentId, e.message)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.message ?: "결제 정보를 찾을 수 없습니다."))

        } catch (e: Exception) {
            log.error("❌ 결제 조회 중 예외 발생: paymentId={}", paymentId, e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(genericErrorMessage))
        }
    }

    /**
     * 주문의 결제 목록 조회
     */
    @GetMapping("/orders/{orderId}")
    suspend fun getPaymentsByOrderId(
        @PathVariable orderId: UUID
    ): ResponseEntity<ApiResponse<PaymentListResponse>> = coroutineScope {

        log.debug("🔍 주문 결제 목록 조회: orderId={}", orderId)

        try {
            // 비동기로 결제 목록과 통계 조회
            val paymentsDeferred = async {
                // 임시 구현 - 실제로는 PaymentRepository의 조회 메서드 사용
                listOf<PaymentDetailResponse>()
            }

            val statsDeferred = async {
                // 결제 통계 계산
                Pair(0, 0L) // (건수, 총금액)
            }

            val payments = paymentsDeferred.await()
            val (totalCount, totalAmount) = statsDeferred.await()

            val response = PaymentListResponse(
                payments = payments,
                totalCount = totalCount,
                totalAmount = totalAmount
            )

            ResponseEntity.ok(ApiResponse.success(response))

        } catch (e: Exception) {
            log.error("❌ 주문 결제 목록 조회 실패: orderId={}", orderId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("시스템 오류가 발생했습니다."))
        }
    }

    /**
     * 주문의 최신 결제 상태 조회 (프론트 폴링용)
     */
    @GetMapping("/orders/{orderId}/latest")
    suspend fun getLatestPaymentStatus(
        @PathVariable orderId: UUID
    ): ResponseEntity<ApiResponse<PaymentStatusResponse>> {
        return try {
            val result = paymentCommandService.getLatestPaymentByOrderId(orderId)
            val response = PaymentStatusResponse(
                paymentId = result.paymentId,
                orderId = result.orderId ?: orderId,
                status = result.status,
                approvedAt = result.approvedAt
            )
            ResponseEntity.ok(ApiResponse.success(response))
        } catch (e: PaymentException) {
            log.error("❌ 최신 결제 조회 실패: orderId={}, error={}", orderId, e.message)
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.message ?: "결제 정보를 찾을 수 없습니다."))
        } catch (e: Exception) {
            log.error("❌ 최신 결제 조회 중 예외 발생: orderId={}", orderId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(genericErrorMessage))
        }
    }

    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    suspend fun healthCheck(): ResponseEntity<Map<String, Any>> {
        val healthInfo = mapOf(
            "status" to "UP",
            EventConstants.MetadataKeys.TIMESTAMP to java.time.LocalDateTime.now(),
            "service" to "payment-service"
        )
        return ResponseEntity.ok(healthInfo)
    }
}
