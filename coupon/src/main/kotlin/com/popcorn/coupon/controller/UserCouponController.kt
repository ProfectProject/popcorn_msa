package com.popcorn.coupon.controller

import com.popcorn.coupon.dto.request.*
import com.popcorn.coupon.dto.response.*
import com.popcorn.coupon.service.core.CouponCommandService
import com.popcorn.coupon.service.core.CouponQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/coupons")
@Tag(name = "👤 User Coupons", description = "사용자 개별 쿠폰 관리 API. 쿠폰 발급, 사용, 조회 등 사용자 중심의 쿠폰 기능을 제공합니다. 대부분의 API는 인증된 사용자 본인의 쿠폰만 접근 가능합니다.")
class UserCouponController(
    private val couponCommandService: CouponCommandService,
    private val couponQueryService: CouponQueryService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 내 쿠폰 목록 조회 (페이징)
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = """
            사용자 본인이 보유한 쿠폰 목록을 페이징하여 조회합니다.

            **조회 옵션:**
            - 전체 쿠폰 또는 상태별 필터링 지원
            - 최신 발급 순으로 정렬
            - 페이징 처리로 성능 최적화

            **쿠폰 상태:**
            - ISSUED: 사용 가능한 쿠폰
            - RESERVED: 주문에 예약된 쿠폰
            - USED: 사용 완료된 쿠폰
            - EXPIRED: 만료된 쿠폰

            **응답 정보:**
            - 쿠폰 기본 정보 (이름, 할인 조건)
            - 사용 가능 기간
            - 현재 상태 및 사용 이력

            **권한:**
            - 고객(CUSTOMER) 권한 필요
            - 본인 쿠폰만 조회 가능
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "내 쿠폰 목록 조회 성공",
            content = [Content(schema = Schema(implementation = UserCouponListResponse::class))]),
        ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터 (잘못된 상태값 등)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (고객 권한 필요)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getMyCoupons(
        @RequestParam(defaultValue = "0")
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        page: Int,
        @RequestParam(defaultValue = "20")
        @Parameter(description = "페이지 크기 (1-100)", example = "20")
        size: Int,
        @RequestParam(required = false)
        @Parameter(description = "쿠폰 상태 필터 (ISSUED, RESERVED, USED, EXPIRED)", example = "ISSUED")
        status: String?
    ): ResponseEntity<UserCouponListResponse> {
        val userId = getCurrentUserId()
        logger.info { "👤 내 쿠폰 목록 조회: userId=$userId, page=$page, size=$size, status=$status" }

        return runBlocking {
            val limitedSize = size.coerceIn(1, 100)
            val pageable = PageRequest.of(page, limitedSize, Sort.by(Sort.Direction.DESC, "createdAt"))

            val userCouponsPage = if (status != null) {
                val userCouponStatus = com.popcorn.coupon.domain.entity.UserCouponStatus.valueOf(status.uppercase())
                couponQueryService.getUserCouponsByStatus(userId, userCouponStatus, pageable)
            } else {
                couponQueryService.getUserCoupons(userId, pageable)
            }

            // 배치 조회로 N+1 쿼리 방지
            val couponMap = couponQueryService.getCouponsByIds(userCouponsPage.content.map { it.couponId })
            val responses = userCouponsPage.content.mapNotNull { userCoupon ->
                val coupon = couponMap[userCoupon.couponId] ?: return@mapNotNull null
                UserCouponResponse.from(userCoupon, coupon)
            }

            val listResponse = UserCouponListResponse(
                content = responses,
                totalElements = userCouponsPage.totalElements,
                totalPages = userCouponsPage.totalPages,
                currentPage = userCouponsPage.number,
                size = userCouponsPage.size,
                hasNext = userCouponsPage.hasNext(),
                hasPrevious = userCouponsPage.hasPrevious()
            )

            ResponseEntity.ok(listResponse)
        }
    }

    /**
     * 내 사용 가능한 쿠폰 목록 조회
     */
    @GetMapping("/my/available")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "내 사용 가능 쿠폰", description = "현재 사용 가능한 내 쿠폰을 조회합니다.")
    fun getMyAvailableCoupons(): ResponseEntity<List<UserCouponResponse>> {
        val userId = getCurrentUserId()
        logger.info { "✅ 내 사용 가능한 쿠폰 조회: userId=$userId" }

        return runBlocking {
            val availableUserCoupons = couponQueryService.getAvailableUserCoupons(userId)
            val couponMap = couponQueryService.getCouponsByIds(availableUserCoupons.map { it.couponId })
            val responses = availableUserCoupons.mapNotNull { userCoupon ->
                val coupon = couponMap[userCoupon.couponId] ?: return@mapNotNull null
                UserCouponResponse.from(userCoupon, coupon)
            }

            ResponseEntity.ok(responses)
        }
    }

    /**
     * 쿠폰 다운로드 (발급)
     */
    @PostMapping("/download")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
        summary = "쿠폰 다운로드 (발급)",
        description = """
            사용자가 직접 쿠폰을 다운로드하여 내 쿠폰함에 추가합니다.

            **발급 과정:**
            1. 쿠폰 발급 가능 여부 검증 (재고, 기간, 중복 등)
            2. 고유한 쿠폰 코드 자동 생성
            3. 사용자 쿠폰함에 추가
            4. 발급 통계 업데이트

            **발급 제한 검증:**
            - 쿠폰 활성 상태 및 유효 기간 확인
            - 사용자별 발급 한도 확인 (perUserLimit)
            - 총 발급 수량 확인 (totalQuantity)
            - 중복 발급 방지

            **성공 시:**
            - 즉시 사용 가능한 상태로 발급
            - 쿠폰 코드로 식별 가능
            - 내 쿠폰 목록에서 확인 가능

            **실패 사례:**
            - 이미 발급받은 쿠폰 (중복 발급)
            - 발급 한도 초과
            - 비활성 또는 만료된 쿠폰
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "쿠폰 다운로드 성공",
            content = [Content(schema = Schema(implementation = UserCouponResponse::class))]),
        ApiResponse(responseCode = "400", description = "발급 불가 (중복 발급, 수량 초과, 만료 등)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (고객 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun downloadCoupon(
        @Valid @RequestBody
        @Parameter(description = "쿠폰 다운로드 요청 정보", required = true)
        request: CouponDownloadRequest
    ): ResponseEntity<UserCouponResponse> {
        val userId = getCurrentUserId()
        logger.info { "⬇️ 쿠폰 다운로드: userId=$userId, couponId=${request.couponId}" }

        return runBlocking {
            val userCoupon = couponCommandService.issueCouponToUser(
                userId = userId,
                couponId = request.couponId
            )
            val coupon = couponQueryService.getCouponById(request.couponId)
            ResponseEntity.status(HttpStatus.CREATED).body(UserCouponResponse.from(userCoupon, coupon))
        }
    }

    /**
     * 주문 금액 기준 사용 가능 쿠폰 조회
     */
    @GetMapping("/available", params = ["orderAmount"])
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
        summary = "주문 시 적용 가능한 쿠폰 조회",
        description = """
            특정 주문 금액에 대해 사용 가능한 내 쿠폰 목록을 조회하고 할인 금액을 미리 계산합니다.

            **계산 로직:**
            1. 최소 주문 금액 조건 확인 (minOrderAmount ≤ orderAmount)
            2. 할인 금액 계산 (정액 또는 정률)
            3. 최대 할인 금액 제한 적용
            4. 최종 결제 금액 계산
            5. 할인율 계산

            **응답 정보:**
            - 적용 가능한 쿠폰 목록
            - 각 쿠폰별 할인 금액
            - 쿠폰 적용 후 최종 금액
            - 할인율 백분율

            **정렬:**
            - 할인 금액이 큰 순으로 정렬
            - 동일 할인 시 만료일이 빠른 순

            **활용 목적:**
            - 주문/결제 페이지의 쿠폰 선택 UI
            - 고객의 최적 쿠폰 선택 도움

            **주의사항:**
            - 실제 사용 시 재검증 필요
            - 주문 내용 변경 시 다시 조회 권장
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "적용 가능한 쿠폰 목록 조회 성공",
            content = [Content(schema = Schema(implementation = ApplicableCouponResponse::class, type = "array"))]),
        ApiResponse(responseCode = "400", description = "잘못된 주문 금액 (음수 또는 0)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (고객 권한 필요)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getAvailableCouponsForOrder(
        @RequestParam
        @Parameter(description = "주문 금액 (원 단위)", required = true, example = "50000")
        orderAmount: BigDecimal
    ): ResponseEntity<List<ApplicableCouponResponse>> {
        val userId = getCurrentUserId()
        logger.info { "✅ 사용 가능한 쿠폰 조회: userId=$userId, orderAmount=$orderAmount" }

        return runBlocking {
            val applicableCoupons = couponQueryService.getApplicableCoupons(userId, orderAmount)
            val responses = applicableCoupons.map { applicableCoupon ->
                ApplicableCouponResponse(
                    userCouponId = applicableCoupon.userCoupon.id!!,
                    couponCode = applicableCoupon.userCoupon.couponCode,
                    couponName = applicableCoupon.coupon.name,
                    discountType = applicableCoupon.coupon.discountType,
                    discountAmount = applicableCoupon.discountAmount,
                    finalAmount = applicableCoupon.finalAmount,
                    discountRate = applicableCoupon.discountRate,
                    minOrderAmount = applicableCoupon.coupon.minOrderAmount,
                    maxDiscountAmount = applicableCoupon.coupon.maxDiscountAmount,
                    description = applicableCoupon.coupon.description
                )
            }
            ResponseEntity.ok(responses)
        }
    }

    /**
     * 주문에 적용 가능한 쿠폰 조회
     */
    @GetMapping("/applicable")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "주문 적용 가능 쿠폰", description = "주문 금액 기준으로 적용 가능한 쿠폰을 조회합니다.")
    fun getApplicableCoupons(
        @RequestParam orderAmount: BigDecimal
    ): ResponseEntity<List<ApplicableCouponResponse>> {
        val userId = getCurrentUserId()
        logger.info { "🛒 주문 적용 가능한 쿠폰 조회: userId=$userId, orderAmount=$orderAmount" }

        return runBlocking {
            val applicableCoupons = couponQueryService.getApplicableCoupons(userId, orderAmount)
            val responses = applicableCoupons.map { applicableCoupon ->
                ApplicableCouponResponse(
                    userCouponId = applicableCoupon.userCoupon.id!!,
                    couponCode = applicableCoupon.userCoupon.couponCode,
                    couponName = applicableCoupon.coupon.name,
                    discountType = applicableCoupon.coupon.discountType,
                    discountAmount = applicableCoupon.discountAmount,
                    finalAmount = applicableCoupon.finalAmount,
                    discountRate = applicableCoupon.discountRate,
                    minOrderAmount = applicableCoupon.coupon.minOrderAmount,
                    maxDiscountAmount = applicableCoupon.coupon.maxDiscountAmount,
                    description = applicableCoupon.coupon.description
                )
            }

            ResponseEntity.ok(responses)
        }
    }

    /**
     * 쿠폰 코드로 내 쿠폰 조회
     */
    @GetMapping("/my/code/{couponCode}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "쿠폰 코드 조회", description = "쿠폰 코드로 내 쿠폰을 조회합니다.")
    fun getMyCouponByCode(@PathVariable couponCode: String): ResponseEntity<UserCouponResponse?> {
        val userId = getCurrentUserId()
        logger.info { "🔍 쿠폰 코드로 조회: userId=$userId, couponCode=$couponCode" }

        return runBlocking {
            val userCoupon = couponQueryService.getUserCouponByCode(couponCode)
            if (userCoupon != null && userCoupon.userId == userId) {
                val coupon = couponQueryService.getCouponById(userCoupon.couponId)
                ResponseEntity.ok(UserCouponResponse.from(userCoupon, coupon))
            } else {
                ResponseEntity.notFound().build()
            }
        }
    }

    /**
     * 쿠폰 사용 예약 (주문 시)
     */
    @PostMapping("/use")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
        summary = "쿠폰 사용 예약",
        description = """
            주문 진행 시 쿠폰을 사용 예약 상태로 변경합니다. (2단계 커밋 패턴)

            **처리 과정:**
            1. 쿠폰 사용 가능 여부 검증 (상태, 소유권, 조건)
            2. 주문 금액 대비 할인 금액 계산
            3. 쿠폰 상태를 RESERVED로 변경
            4. 주문 ID와 연결하여 임시 예약

            **사용 조건 검증:**
            - 쿠폰 소유권 확인 (본인 쿠폰인지)
            - 사용 가능 상태 (ISSUED) 확인
            - 최소 주문 금액 조건 충족
            - 유효 기간 내 사용

            **예약 상태 특징:**
            - 다른 주문에서 사용 불가
            - 결제 완료 시 사용 확정
            - 결제 실패/취소 시 자동 복원

            **MSA 연동:**
            - 주문 서비스와 트랜잭션 분리
            - 이벤트 기반 최종 확정/취소 처리

            **권한:**
            - 고객(CUSTOMER) 권한 필요
            - 본인 쿠폰만 사용 가능
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 사용 예약 성공",
            content = [Content(schema = Schema(implementation = CouponUsageResponse::class))]),
        ApiResponse(responseCode = "400", description = "사용 불가 쿠폰 (이미 사용됨, 조건 미충족 등)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 쿠폰 접근"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "409", description = "이미 다른 주문에서 사용 중"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun useCoupon(
        @Valid @RequestBody
        @Parameter(description = "쿠폰 사용 요청 정보", required = true)
        request: CouponUsageRequest
    ): ResponseEntity<CouponUsageResponse> {
        val userId = getCurrentUserId()
        logger.info { "💳 쿠폰 사용: userId=$userId, userCouponId=${request.userCouponId}" }

        return runBlocking {
            val userCoupon = couponCommandService.useCoupon(
                userId = userId,
                userCouponId = request.userCouponId,
                orderId = request.orderId,
                orderAmount = request.orderAmount
            )

            val response = CouponUsageResponse(
                userCouponId = userCoupon.id!!,
                couponCode = userCoupon.couponCode,
                orderId = request.orderId,
                discountAmount = BigDecimal.ZERO, // 실제 할인 금액은 확정 시 계산
                status = userCoupon.status,
                message = "쿠폰이 예약되었습니다"
            )

            ResponseEntity.ok(response)
        }
    }

    /**
     * 쿠폰 사용 확정 (결제 완료 시)
     */
    @PostMapping("/{userCouponId}/confirm")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 사용 확정 (관리자)",
        description = """
            결제 완료 후 예약된 쿠폰의 사용을 최종 확정합니다. (2단계 커밋 완료)

            **처리 과정:**
            1. 쿠폰이 RESERVED 상태인지 확인
            2. 주문 ID 일치 여부 확인
            3. 실제 할인 금액 기록
            4. 쿠폰 상태를 USED로 변경
            5. 사용 일시 및 이력 기록

            **MSA 연동 시나리오:**
            - Payment 서비스에서 결제 완료 이벤트 수신
            - Order 서비스에서 주문 확정 이벤트 수신
            - 자동으로 호출되는 내부 API

            **데이터 무결성:**
            - 한 번 확정된 쿠폰은 되돌릴 수 없음
            - 할인 금액은 실제 적용된 금액으로 기록
            - 쿠폰 사용 통계에 반영

            **권한:**
            - 시스템 내부 호출 또는 관리자 권한
            - 매장 관리자(MANAGER) 또는 사업자(OWNER)

            **멱등성:**
            - 동일한 요청 중복 시 기존 결과 반환
            - 안전한 재시도 가능
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 사용 확정 성공",
            content = [Content(schema = Schema(implementation = CouponUsageResponse::class))]),
        ApiResponse(responseCode = "400", description = "확정 불가 상태 (이미 사용됨, 잘못된 주문 ID 등)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun confirmCouponUsage(
        @PathVariable
        @Parameter(description = "확정할 사용자 쿠폰 ID", required = true, example = "123")
        userCouponId: Long,
        @Valid @RequestBody
        @Parameter(description = "쿠폰 사용 확정 요청 정보", required = true)
        request: CouponUsageConfirmRequest
    ): ResponseEntity<CouponUsageResponse> {
        logger.info { "✅ 쿠폰 사용 확정: userCouponId=$userCouponId, orderId=${request.orderId}" }

        return runBlocking {
            val userCoupon = couponCommandService.confirmCouponUsage(
                userCouponId = userCouponId,
                orderId = request.orderId,
                actualDiscountAmount = request.actualDiscountAmount
            )

            val response = CouponUsageResponse(
                userCouponId = userCoupon.id!!,
                couponCode = userCoupon.couponCode,
                orderId = request.orderId,
                discountAmount = request.actualDiscountAmount,
                status = userCoupon.status,
                message = "쿠폰 사용이 확정되었습니다"
            )

            ResponseEntity.ok(response)
        }
    }

    /**
     * 쿠폰 사용 취소 (주문/결제 실패 시)
     */
    @PostMapping("/{userCouponId}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 사용 취소 (관리자)",
        description = """
            결제 실패 또는 주문 취소 시 예약된 쿠폰을 다시 사용 가능한 상태로 복원합니다.

            **처리 과정:**
            1. 쿠폰이 RESERVED 상태인지 확인
            2. 주문 ID 일치 여부 확인
            3. 쿠폰 상태를 ISSUED로 복원
            4. 예약 정보 초기화 (주문 ID, 예약 일시 등)
            5. 취소 이력 기록

            **MSA 연동 시나리오:**
            - Payment 서비스에서 결제 실패 이벤트 수신
            - Order 서비스에서 주문 취소 이벤트 수신
            - 자동으로 호출되는 롤백 API

            **복원 특징:**
            - 쿠폰이 원래 사용 가능한 상태로 완전 복원
            - 다른 주문에서 즉시 사용 가능
            - 취소 사유 및 이력 보존

            **안전성:**
            - 이미 USED 상태의 쿠폰은 취소 불가
            - 잘못된 주문 ID로 취소 시도 시 거부

            **권한:**
            - 시스템 내부 호출 또는 관리자 권한
            - 매장 관리자(MANAGER) 또는 사업자(OWNER)

            **멱등성:**
            - 이미 취소된 쿠폰 취소 시도는 성공 응답
            - 안전한 재시도 가능
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 사용 취소 성공",
            content = [Content(schema = Schema(implementation = CouponUsageResponse::class))]),
        ApiResponse(responseCode = "400", description = "취소 불가 상태 (이미 사용됨, 잘못된 주문 ID 등)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun cancelCouponUsage(
        @PathVariable
        @Parameter(description = "취소할 사용자 쿠폰 ID", required = true, example = "123")
        userCouponId: Long,
        @Valid @RequestBody
        @Parameter(description = "쿠폰 사용 취소 요청 정보", required = true)
        request: CouponUsageCancelRequest
    ): ResponseEntity<CouponUsageResponse> {
        logger.info { "🔄 쿠폰 사용 취소: userCouponId=$userCouponId, orderId=${request.orderId}" }

        return runBlocking {
            val userCoupon = couponCommandService.cancelCouponUsage(
                userCouponId = userCouponId,
                orderId = request.orderId
            )

            val response = CouponUsageResponse(
                userCouponId = userCoupon.id!!,
                couponCode = userCoupon.couponCode,
                orderId = request.orderId,
                discountAmount = BigDecimal.ZERO,
                status = userCoupon.status,
                message = "쿠폰 사용이 취소되었습니다"
            )

            ResponseEntity.ok(response)
        }
    }

    /**
     * 내 쿠폰 통계 조회
     */
    @GetMapping("/my/statistics")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
        summary = "내 쿠폰 사용 통계 조회",
        description = """
            사용자 개인의 쿠폰 보유 및 사용 통계를 조회합니다.

            **통계 항목:**
            - 총 보유 쿠폰 수 (모든 상태 포함)
            - 사용 가능한 쿠폰 수 (ISSUED 상태)
            - 사용 완료한 쿠폰 수 (USED 상태)
            - 만료된 쿠폰 수 (EXPIRED 상태)
            - 예약 중인 쿠폰 수 (RESERVED 상태)
            - 총 절약 금액 (사용한 쿠폰의 할인 금액 합계)

            **활용 목적:**
            - 마이페이지 대시보드
            - 쿠폰 사용 현황 확인
            - 절약 효과 시각화

            **데이터 특징:**
            - 실시간 계산 기반
            - 사용자별 개인 통계만 제공
            - 캐싱 적용으로 성능 최적화

            **권한:**
            - 고객(CUSTOMER) 권한 필요
            - 본인 통계만 조회 가능
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "내 쿠폰 통계 조회 성공",
            content = [Content(schema = Schema(implementation = UserCouponStatisticsResponse::class))]),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (고객 권한 필요)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getMyCouponStatistics(): ResponseEntity<UserCouponStatisticsResponse> {
        val userId = getCurrentUserId()
        logger.info { "📊 내 쿠폰 통계 조회: userId=$userId" }

        return runBlocking {
            val totalCoupons = couponQueryService.getUserCouponCountByStatus(
                userId, com.popcorn.coupon.domain.entity.UserCouponStatus.ISSUED
            ) + couponQueryService.getUserCouponCountByStatus(
                userId, com.popcorn.coupon.domain.entity.UserCouponStatus.USED
            ) + couponQueryService.getUserCouponCountByStatus(
                userId, com.popcorn.coupon.domain.entity.UserCouponStatus.EXPIRED
            ) + couponQueryService.getUserCouponCountByStatus(
                userId, com.popcorn.coupon.domain.entity.UserCouponStatus.RESERVED
            )

            val statistics = UserCouponStatisticsResponse(
                totalCoupons = totalCoupons,
                availableCoupons = couponQueryService.getUserCouponCountByStatus(
                    userId, com.popcorn.coupon.domain.entity.UserCouponStatus.ISSUED
                ),
                usedCoupons = couponQueryService.getUserCouponCountByStatus(
                    userId, com.popcorn.coupon.domain.entity.UserCouponStatus.USED
                ),
                expiredCoupons = couponQueryService.getUserCouponCountByStatus(
                    userId, com.popcorn.coupon.domain.entity.UserCouponStatus.EXPIRED
                ),
                reservedCoupons = couponQueryService.getUserCouponCountByStatus(
                    userId, com.popcorn.coupon.domain.entity.UserCouponStatus.RESERVED
                ),
                totalSavedAmount = BigDecimal.ZERO // 실제로는 할인된 총 금액 계산 필요
            )

            ResponseEntity.ok(statistics)
        }
    }

    /**
     * 쿠폰 사용 이력 조회
     */
    @GetMapping("/usage-history")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "쿠폰 사용 이력", description = "내 쿠폰 사용 이력을 페이징 조회합니다.")
    fun getUsageHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<CouponHistoryListResponse> {
        val userId = getCurrentUserId()
        logger.info { "📜 쿠폰 사용 이력 조회: userId=$userId, page=$page, size=$size" }

        return runBlocking {
            val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
            val historyPage = couponQueryService.getUserCouponHistory(userId, pageable)
            val content = historyPage.content.map { CouponHistoryItemResponse.from(it) }
            ResponseEntity.ok(
                CouponHistoryListResponse(
                    content = content,
                    totalElements = historyPage.totalElements,
                    totalPages = historyPage.totalPages,
                    currentPage = historyPage.number,
                    size = historyPage.size,
                    hasNext = historyPage.hasNext(),
                    hasPrevious = historyPage.hasPrevious()
                )
            )
        }
    }

    /**
     * 현재 인증된 사용자 ID 조회
     */
    private fun getCurrentUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication.name.toLong()
    }
}
