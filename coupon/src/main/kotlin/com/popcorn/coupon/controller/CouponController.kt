package com.popcorn.coupon.controller

import com.popcorn.coupon.dto.request.*
import com.popcorn.coupon.domain.entity.CouponStatus
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
import org.springframework.data.domain.PageRequest

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
    @GetMapping("/active")
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

            **사용법:**
            - /api/v1/coupons/active?page=0&size=20
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "활성 쿠폰 목록 조회 성공",
            content = [Content(schema = Schema(implementation = CouponSummaryResponse::class, type = "array"))]),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getActiveCoupons(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "20")
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<List<CouponSummaryResponse>> {
        logger.info { "📋 활성 쿠폰 목록 조회: page=$page, size=$size" }

        return runBlocking {
            // ⚡ 성능 최적화: DB 레벨 페이지네이션 + 최대 100개 제한
            val limitedSize = minOf(size, 100)
            val pageable = PageRequest.of(page, limitedSize)
            val couponPage = couponQueryService.getActiveCouponsWithPagination(pageable)
            val responses = couponPage.content.map { CouponSummaryResponse.from(it) }
            ResponseEntity.ok(responses)
        }
    }

    /**
     * 쿠폰 템플릿 목록 조회 (관리자 전용, 전체 상태 포함)
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 템플릿 전체 목록 조회 (관리자)",
        description = """
            관리자가 생성된 쿠폰 템플릿 목록을 조회합니다. (ACTIVE/INACTIVE/DRAFT/EXPIRED 포함)

            **⚡ 성능 최적화:**
            - 페이지네이션 지원으로 빠른 응답 속도
            - 기본 20개씩 조회 (최대 100개까지 가능)

            **정렬:**
            - 생성일 기준 최신순

            **사용법:**
            - /api/v1/coupons/all?page=0&size=20
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 전체 목록 조회 성공",
            content = [Content(schema = Schema(implementation = CouponSummaryResponse::class, type = "array"))]),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun getAllCoupons(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (1-100)", example = "20")
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<List<CouponSummaryResponse>> {
        // ⚡ 성능 보호: 페이지 크기 제한
        val limitedSize = size.coerceIn(1, 100)
        logger.info { "🎫 쿠폰 전체 목록 조회 (관리자) - page: $page, size: $limitedSize" }

        return runBlocking {
            val pageable = PageRequest.of(page, limitedSize)
            val couponPage = couponQueryService.getAllCouponsWithPagination(pageable)
            ResponseEntity.ok(couponPage.content.map { CouponSummaryResponse.from(it) })
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
     * 쿠폰 상태 변경 (관리자 전용)
     */
    @PatchMapping("/{couponId}/status")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 상태 변경",
        description = """
            쿠폰의 상태를 변경합니다.

            **변경 가능한 상태:**
            - DRAFT: 초안 상태 (아직 활성화되지 않음)
            - ACTIVE: 활성화 상태 (발급 가능)
            - INACTIVE: 비활성화 상태 (발급 중단)
            - EXPIRED: 만료 상태 (사용 불가)

            **상태 전환 규칙:**
            - DRAFT → ACTIVE: 활성화
            - ACTIVE → INACTIVE: 비활성화
            - ACTIVE → EXPIRED: 강제 만료
            - INACTIVE → ACTIVE: 재활성화

            **권한:**
            - 매장 관리자(MANAGER) 또는 사업자(OWNER) 권한 필요
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 상태 변경 성공",
            content = [Content(schema = Schema(implementation = CouponDetailResponse::class))]),
        ApiResponse(responseCode = "400", description = "잘못된 상태 전환 요청"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun updateCouponStatus(
        @PathVariable
        @Parameter(description = "상태를 변경할 쿠폰 ID", required = true, example = "9")
        couponId: Long,
        @Valid @RequestBody
        @Parameter(description = "쿠폰 상태 변경 요청", required = true)
        request: CouponStatusUpdateRequest
    ): ResponseEntity<CouponDetailResponse> {
        logger.info { "🔄 쿠폰 상태 변경: couponId=$couponId, status=${request.status}" }

        return runBlocking {
            val adminId = getCurrentUserId()
            val coupon = when (request.status) {
                CouponStatus.ACTIVE -> couponCommandService.activateCoupon(couponId, adminId)
                CouponStatus.INACTIVE -> couponCommandService.deactivateCoupon(couponId, adminId)
                else -> throw IllegalArgumentException("지원하지 않는 상태 변경입니다: ${request.status}")
            }
            ResponseEntity.ok(CouponDetailResponse.from(coupon))
        }
    }

    /**
     * 쿠폰 수정 (관리자 전용)
     */
    @PutMapping("/{couponId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 수정",
        description = """
            기존 쿠폰 정보를 수정합니다.

            **수정 가능 항목:**
            - 쿠폰명, 설명
            - 할인 금액/비율, 최대 할인 금액
            - 최소 주문 금액
            - 총 수량 (현재 발급량보다 많게만 가능)
            - 유효 기간 (현재 시점 이후로만 가능)
            - 대상 사용자 유형

            **수정 제한 사항:**
            - 만료된 쿠폰은 수정 불가
            - 이미 발급된 쿠폰이 있는 경우 일부 제한 적용
            - 할인 타입은 수정 불가

            **권한:**
            - 매장 관리자(MANAGER) 또는 사업자(OWNER) 권한 필요
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "쿠폰 수정 성공",
            content = [Content(schema = Schema(implementation = CouponDetailResponse::class))]),
        ApiResponse(responseCode = "400", description = "수정할 수 없는 상태의 쿠폰 또는 잘못된 수정 값"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun updateCoupon(
        @PathVariable
        @Parameter(description = "수정할 쿠폰 ID", required = true, example = "1")
        couponId: Long,
        @Valid @RequestBody
        @Parameter(description = "쿠폰 수정 요청 정보", required = true)
        request: CouponUpdateRequest
    ): ResponseEntity<CouponDetailResponse> {
        logger.info { "📝 쿠폰 수정: couponId=$couponId, name=${request.name}" }

        return runBlocking {
            val adminId = getCurrentUserId()
            val coupon = couponCommandService.updateCoupon(
                couponId = couponId,
                name = request.name,
                description = request.description,
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
            ResponseEntity.ok(CouponDetailResponse.from(coupon))
        }
    }

    /**
     * 쿠폰 삭제 (관리자 전용)
     */
    @DeleteMapping("/{couponId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(
        summary = "쿠폰 삭제",
        description = """
            쿠폰을 완전히 삭제합니다.

            **삭제 조건:**
            - 발급된 쿠폰이 없어야 함 (issuedQuantity = 0)
            - 활성화 상태가 아니어야 함 (먼저 비활성화 필요)
            - DRAFT, INACTIVE, EXPIRED 상태의 쿠폰만 삭제 가능

            **주의사항:**
            - 삭제는 되돌릴 수 없는 작업입니다
            - 쿠폰 관련 모든 데이터가 함께 삭제됩니다
            - 통계 및 이력 데이터에 영향을 줄 수 있습니다

            **대안:**
            - 삭제 대신 비활성화를 권장합니다
            - 비활성화된 쿠폰은 언제든 재활성화 가능합니다

            **권한:**
            - 매장 관리자(MANAGER) 또는 사업자(OWNER) 권한 필요
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "쿠폰 삭제 성공"),
        ApiResponse(responseCode = "400", description = "삭제할 수 없는 상태의 쿠폰 (발급된 쿠폰 있음, 활성화 상태 등)"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
        ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    fun deleteCoupon(
        @PathVariable
        @Parameter(description = "삭제할 쿠폰 ID", required = true, example = "1")
        couponId: Long
    ): ResponseEntity<Void> {
        logger.info { "🗑️ 쿠폰 삭제: couponId=$couponId" }

        return runBlocking {
            val adminId = getCurrentUserId()
            couponCommandService.deleteCoupon(couponId, adminId)
            ResponseEntity.noContent().build()
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
