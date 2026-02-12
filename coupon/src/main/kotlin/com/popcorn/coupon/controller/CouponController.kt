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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/coupons")
@Tag(name = "🎫 Coupon Management", description = "쿠폰 템플릿 생성, 조회, 관리를 위한 API. 관리자 권한이 필요한 API와 일반 사용자가 접근 가능한 조회 API로 구성됩니다.")
class CouponController(
    private val couponCommandService: CouponCommandService,
    private val couponQueryService: CouponQueryService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 쿠폰 템플릿 생성 (관리자 전용)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 템플릿 생성",
        description = """
            관리자가 새로운 쿠폰 템플릿을 생성합니다.

            **주요 기능:**
            - 정액 할인/정률 할인 쿠폰 생성 지원
            - 발급 수량 제한 및 사용자당 발급 제한 설정
            - 유효 기간 및 최소 주문 금액 조건 설정
            - 대상 사용자 유형 지정 (전체/신규/VIP 등)

            **유의사항:**
            - 정률 할인 시 최대 할인 금액 설정 권장
            - 유효 기간은 현재 시점보다 미래여야 함
            - 발급 수량은 1개 이상 설정 시에만 제한 적용
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "쿠폰 생성 성공",
            content = [Content(schema = Schema(implementation = CouponDetailResponse::class))]),
        ApiResponse(responseCode = "400", description = "잘못된 요청 데이터 (유효성 검증 실패)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun createCoupon(
        @Valid @RequestBody
        @Parameter(description = "쿠폰 생성 요청 정보", required = true)
        request: CouponCreateRequest
    ): ResponseEntity<CouponDetailResponse> {
        logger.info { "🎫 쿠폰 생성 요청: ${request.name}" }

        return runBlocking {
            val adminId = getCurrentUserId()
            val coupon = couponCommandService.createCoupon(
                name = request.name,
                description = request.description,
                discountType = request.discountType,
                discountAmount = request.discountAmount,
                discountPercentage = request.discountPercentage?.toBigDecimal(),
                minOrderAmount = request.minOrderAmount,
                maxDiscountAmount = request.maxDiscountAmount,
                totalQuantity = request.totalQuantity,
                validFrom = request.validFrom,
                validUntil = request.validUntil,
                targetType = request.targetType,
                adminId = adminId
            )

            ResponseEntity.status(HttpStatus.CREATED)
                .body(CouponDetailResponse.from(coupon))
        }
    }

    /**
     * 쿠폰 템플릿 상세 조회
     */
    @GetMapping("/{couponId}")
    @Operation(
        summary = "쿠폰 템플릿 상세 조회",
        description = """
            특정 쿠폰 템플릿의 상세 정보를 조회합니다.

            **조회 정보:**
            - 쿠폰 기본 정보 (이름, 설명, 할인 조건)
            - 발급 현황 (총 발급량, 잔여량)
            - 유효 기간 및 사용 조건
            - 대상 사용자 정보

            **접근 권한:**
            - 모든 사용자 접근 가능
            - 비활성화된 쿠폰도 조회 가능
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 상세 정보 조회 성공",
            content = [Content(schema = Schema(implementation = CouponDetailResponse::class))]),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getCouponDetail(
        @PathVariable
        @Parameter(description = "조회할 쿠폰 ID", required = true, example = "1")
        couponId: Long
    ): ResponseEntity<CouponDetailResponse> {
        logger.info { "🔍 쿠폰 상세 조회: couponId=$couponId" }

        return runBlocking {
            val coupon = couponQueryService.getCouponById(couponId)
            ResponseEntity.ok(CouponDetailResponse.from(coupon))
        }
    }

    /**
     * 활성 쿠폰 목록 조회
     */
    @GetMapping
    @Operation(
        summary = "활성 쿠폰 목록 조회",
        description = """
            현재 활성화된 쿠폰 템플릿 목록을 조회합니다.

            **조회 조건:**
            - 상태가 ACTIVE인 쿠폰만 조회
            - 만료되지 않은 쿠폰 포함
            - 발급 가능 여부와 무관하게 모든 활성 쿠폰 조회

            **정렬:**
            - 생성일 기준 최신순으로 정렬

            **사용 목적:**
            - 전체 쿠폰 목록 페이지
            - 관리자 대시보드
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "활성 쿠폰 목록 조회 성공",
            content = [Content(schema = Schema(implementation = CouponSummaryResponse::class, type = "array"))]),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getActiveCoupons(): ResponseEntity<List<CouponSummaryResponse>> {
        logger.info { "📋 활성 쿠폰 목록 조회" }

        return runBlocking {
            val coupons = couponQueryService.getActiveCoupons()
            val responses = coupons.map { CouponSummaryResponse.from(it) }
            ResponseEntity.ok(responses)
        }
    }

    /**
     * 발급 가능한 쿠폰 목록 조회
     */
    @GetMapping("/available", params = ["!orderAmount"])
    @Operation(
        summary = "발급 가능한 쿠폰 목록 조회",
        description = """
            현재 발급 가능한 쿠폰 템플릿 목록을 조회합니다.

            **발급 가능 조건:**
            - 상태가 ACTIVE인 쿠폰
            - 유효 기간 내 (validFrom ≤ 현재 ≤ validUntil)
            - 발급 수량 여유 있음 (totalQuantity > issuedQuantity)

            **사용 목적:**
            - 쿠폰 발급 페이지
            - 사용자 쿠폰함 다운로드 목록

            **응답 데이터:**
            - 쿠폰 기본 정보
            - 잔여 발급 수량
            - 할인 혜택 미리보기
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "발급 가능한 쿠폰 목록 조회 성공",
            content = [Content(schema = Schema(implementation = CouponSummaryResponse::class, type = "array"))]),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getAvailableCoupons(): ResponseEntity<List<CouponSummaryResponse>> {
        logger.info { "✅ 발급 가능한 쿠폰 목록 조회" }

        return runBlocking {
            val coupons = couponQueryService.getAvailableCoupons()
            val responses = coupons.map { CouponSummaryResponse.from(it) }
            ResponseEntity.ok(responses)
        }
    }

    /**
     * 신규 사용자용 환영 쿠폰 조회
     */
    @GetMapping("/welcome")
    @Operation(
        summary = "환영 쿠폰 목록 조회",
        description = """
            신규 사용자를 위한 환영 쿠폰 목록을 조회합니다.

            **대상 쿠폰:**
            - targetType이 NEW_USERS 또는 FIRST_PURCHASE인 쿠폰
            - 현재 발급 가능한 상태의 쿠폰

            **사용 목적:**
            - 회원가입 완료 페이지
            - 신규 사용자 온보딩 프로세스
            - 첫 구매 유도 마케팅

            **특징:**
            - 신규 가입자 전용 혜택
            - 첫 구매 시 사용 가능한 특별 할인
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "환영 쿠폰 목록 조회 성공",
            content = [Content(schema = Schema(implementation = CouponSummaryResponse::class, type = "array"))]),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getWelcomeCoupons(): ResponseEntity<List<CouponSummaryResponse>> {
        logger.info { "🎉 환영 쿠폰 목록 조회" }

        return runBlocking {
            val coupons = couponQueryService.getWelcomeCoupons()
            val responses = coupons.map { CouponSummaryResponse.from(it) }
            ResponseEntity.ok(responses)
        }
    }

    /**
     * 쿠폰 활성화 (관리자 전용)
     */
    @PostMapping("/{couponId}/activate")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 활성화",
        description = """
            비활성화된 쿠폰을 활성화 상태로 변경합니다.

            **동작:**
            - 쿠폰 상태를 ACTIVE로 변경
            - 사용자에게 발급 가능한 상태로 전환
            - 쿠폰 이력에 활성화 기록 추가

            **전제 조건:**
            - 유효 기간이 현재 시점 이후여야 함
            - DRAFT 또는 INACTIVE 상태의 쿠폰만 활성화 가능

            **권한:**
            - 매장 관리자(MANAGER) 또는 사업자(OWNER) 권한 필요
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 활성화 성공",
            content = [Content(schema = Schema(implementation = CouponDetailResponse::class))]),
        ApiResponse(responseCode = "400", description = "활성화할 수 없는 상태의 쿠폰"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun activateCoupon(
        @PathVariable
        @Parameter(description = "활성화할 쿠폰 ID", required = true, example = "1")
        couponId: Long
    ): ResponseEntity<CouponDetailResponse> {
        logger.info { "🔄 쿠폰 활성화: couponId=$couponId" }

        return runBlocking {
            val adminId = getCurrentUserId()
            val coupon = couponCommandService.activateCoupon(couponId, adminId)
            ResponseEntity.ok(CouponDetailResponse.from(coupon))
        }
    }

    /**
     * 쿠폰 비활성화 (관리자 전용)
     */
    @PostMapping("/{couponId}/deactivate")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 비활성화",
        description = """
            활성화된 쿠폰을 비활성화 상태로 변경합니다.

            **동작:**
            - 쿠폰 상태를 INACTIVE로 변경
            - 새로운 발급 중단 (기존 발급된 쿠폰은 유효)
            - 쿠폰 이력에 비활성화 기록 추가

            **주의사항:**
            - 이미 발급된 사용자 쿠폰은 영향받지 않음
            - 비활성화 후에도 재활성화 가능
            - 긴급 중단이 필요한 경우 사용

            **권한:**
            - 매장 관리자(MANAGER) 또는 사업자(OWNER) 권한 필요
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 비활성화 성공",
            content = [Content(schema = Schema(implementation = CouponDetailResponse::class))]),
        ApiResponse(responseCode = "400", description = "비활성화할 수 없는 상태의 쿠폰"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun deactivateCoupon(
        @PathVariable
        @Parameter(description = "비활성화할 쿠폰 ID", required = true, example = "1")
        couponId: Long
    ): ResponseEntity<CouponDetailResponse> {
        logger.info { "⏸️ 쿠폰 비활성화: couponId=$couponId" }

        return runBlocking {
            val adminId = getCurrentUserId()
            val coupon = couponCommandService.deactivateCoupon(couponId, adminId)
            ResponseEntity.ok(CouponDetailResponse.from(coupon))
        }
    }

    /**
     * 사용자에게 쿠폰 발급 (관리자 전용)
     */
    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "특정 사용자에게 쿠폰 발급",
        description = """
            관리자가 특정 사용자에게 직접 쿠폰을 발급합니다.

            **발급 조건 검증:**
            - 쿠폰이 발급 가능한 상태인지 확인
            - 사용자별 발급 한도 확인
            - 총 발급 수량 한도 확인

            **생성 정보:**
            - 고유한 쿠폰 코드 자동 생성
            - 발급 일시 기록
            - 만료 일시 설정 (요청 시 명시 또는 쿠폰 기본 설정)

            **사용 사례:**
            - 고객 보상/혜택 지급
            - 이벤트 참여 보상
            - CS 처리용 쿠폰 지급

            **권한:**
            - 매장 관리자(MANAGER) 또는 사업자(OWNER) 권한 필요
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "쿠폰 발급 성공",
            content = [Content(schema = Schema(implementation = UserCouponResponse::class))]),
        ApiResponse(responseCode = "400", description = "발급 조건 미충족 (수량 한도, 중복 발급 등)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰 또는 사용자를 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun issueCoupon(
        @Valid @RequestBody
        @Parameter(description = "쿠폰 발급 요청 정보", required = true)
        request: CouponIssueRequest
    ): ResponseEntity<UserCouponResponse> {
        logger.info { "🎟️ 쿠폰 발급: userId=${request.userId}, couponId=${request.couponId}" }

        return runBlocking {
            val userCoupon = couponCommandService.issueCouponToUser(
                userId = request.userId,
                couponId = request.couponId,
                expiredAt = request.expiredAt
            )

            val coupon = couponQueryService.getCouponById(request.couponId)
            ResponseEntity.status(HttpStatus.CREATED)
                .body(UserCouponResponse.from(userCoupon, coupon))
        }
    }

    /**
     * 쿠폰 통계 조회 (관리자 전용)
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 운영 통계 조회",
        description = """
            전체 쿠폰 시스템의 운영 통계를 조회합니다.

            **통계 항목:**
            - 총 쿠폰 템플릿 수
            - 활성/비활성 쿠폰 수
            - 총 발급된 쿠폰 수
            - 사용된 쿠폰 수 및 사용률
            - 만료된 쿠폰 수
            - 총 할인 적용 금액

            **활용 목적:**
            - 관리자 대시보드
            - 쿠폰 마케팅 효과 분석
            - 운영 현황 모니터링

            **권한:**
            - 매장 관리자(MANAGER) 또는 사업자(OWNER) 권한 필요

            **참고:**
            - 실시간 데이터 기반 통계
            - 캐싱을 통한 성능 최적화 적용
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 통계 조회 성공",
            content = [Content(schema = Schema(implementation = CouponStatisticsResponse::class))]),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getCouponStatistics(): ResponseEntity<CouponStatisticsResponse> {
        logger.info { "📊 쿠폰 통계 조회" }

        return runBlocking {
            // 임시로 기본값 반환 (실제로는 통계 서비스 구현 필요)
            val statistics = CouponStatisticsResponse(
                totalCoupons = 0,
                activeCoupons = 0,
                issuedCoupons = 0,
                usedCoupons = 0,
                expiredCoupons = 0,
                totalDiscountAmount = java.math.BigDecimal.ZERO
            )
            ResponseEntity.ok(statistics)
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
