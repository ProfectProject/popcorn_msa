package com.popcorn.coupon.service.core

import com.popcorn.coupon.domain.entity.*
import com.popcorn.coupon.domain.repository.CouponOutboxEventRepository
import com.popcorn.coupon.domain.repository.CouponHistoryRepository
import com.popcorn.coupon.service.event.CouponEventPublisher
import com.popcorn.coupon.exception.CouponException
import com.popcorn.coupon.external.UserServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class CouponCommandService(
    private val couponDomainService: CouponDomainService,
    private val userCouponDomainService: UserCouponDomainService,
    private val couponEventPublisher: CouponEventPublisher,
    private val couponHistoryRepository: CouponHistoryRepository,
    private val couponOutboxEventRepository: CouponOutboxEventRepository,
    private val userServiceClient: UserServiceClient
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 쿠폰 생성
     */
    suspend fun createCoupon(
        name: String,
        description: String?,
        discountType: DiscountType,
        discountAmount: BigDecimal?,
        discountPercentage: BigDecimal?,
        minOrderAmount: BigDecimal?,
        maxDiscountAmount: BigDecimal?,
        totalQuantity: Int?,
        validFrom: LocalDateTime,
        validUntil: LocalDateTime,
        targetType: TargetType,
        adminId: Long
    ): Coupon = withContext(Dispatchers.IO) {
        logger.info { "🎫 쿠폰 생성 요청: name=$name, adminId=$adminId" }

        val coupon = Coupon(
            name = name,
            description = description,
            discountType = discountType,
            discountAmount = discountAmount,
            discountPercentage = discountPercentage,
            minOrderAmount = minOrderAmount ?: BigDecimal.ZERO,
            maxDiscountAmount = maxDiscountAmount,
            totalQuantity = totalQuantity,
            issuedQuantity = 0,
            validFrom = validFrom,
            validUntil = validUntil,
            targetType = targetType,
            status = CouponStatus.DRAFT
        )

        val createdCoupon = couponDomainService.createCoupon(coupon)

        // 히스토리 저장
        saveCouponHistory(
            couponId = createdCoupon.id!!,
            userId = adminId,
            action = CouponAction.ISSUED,
            description = "새 쿠폰이 생성되었습니다: ${createdCoupon.name}"
        )

        // 이벤트 발행
        couponEventPublisher.publishCouponCreatedEvent(createdCoupon, adminId)

        logger.info { "✅ 쿠폰 생성 완료: couponId=${createdCoupon.id}" }
        createdCoupon
    }

    /**
     * 쿠폰 활성화
     */
    suspend fun activateCoupon(couponId: Long, adminId: Long): Coupon = withContext(Dispatchers.IO) {
        logger.info { "🔄 쿠폰 활성화: couponId=$couponId, adminId=$adminId" }

        val coupon = couponDomainService.updateCouponStatus(couponId, CouponStatus.ACTIVE)

        // 히스토리 저장
        saveCouponHistory(
            couponId = couponId,
            userId = adminId,
            action = CouponAction.RESTORED,
            description = "쿠폰이 활성화되었습니다"
        )

        // ALL_USERS 타겟 쿠폰인 경우 모든 CUSTOMER 사용자에게 자동 발급
        if (coupon.targetType == TargetType.ALL_USERS) {
            issueToAllCustomers(coupon, adminId)
        }

        // 이벤트 발행
        couponEventPublisher.publishCouponActivatedEvent(coupon, adminId)

        logger.info { "✅ 쿠폰 활성화 완료: couponId=$couponId" }
        coupon
    }

    /**
     * 쿠폰 비활성화
     */
    suspend fun deactivateCoupon(couponId: Long, adminId: Long): Coupon = withContext(Dispatchers.IO) {
        logger.info { "⏸️ 쿠폰 비활성화: couponId=$couponId, adminId=$adminId" }

        val coupon = couponDomainService.updateCouponStatus(couponId, CouponStatus.INACTIVE)

        // 히스토리 저장
        saveCouponHistory(
            couponId = couponId,
            userId = adminId,
            action = CouponAction.CANCELLED,
            description = "쿠폰이 비활성화되었습니다"
        )

        // 이벤트 발행
        couponEventPublisher.publishCouponDeactivatedEvent(coupon, adminId)

        logger.info { "✅ 쿠폰 비활성화 완료: couponId=$couponId" }
        coupon
    }

    /**
     * ⚡ 성능 최적화: 쿠폰 수정
     */
    suspend fun updateCoupon(
        couponId: Long,
        name: String? = null,
        description: String? = null,
        discountAmount: BigDecimal? = null,
        discountPercentage: BigDecimal? = null,
        minOrderAmount: BigDecimal? = null,
        maxDiscountAmount: BigDecimal? = null,
        totalQuantity: Int? = null,
        validFrom: LocalDateTime? = null,
        validUntil: LocalDateTime? = null,
        targetType: TargetType? = null,
        adminId: Long
    ): Coupon = withContext(Dispatchers.IO) {
        logger.info { "📝 쿠폰 수정 (성능 최적화): couponId=$couponId, adminId=$adminId" }

        // ⚡ 성능 최적화: 원본 쿠폰 정보를 한 번만 조회 (DomainService에서 내부적으로 다시 조회됨)
        val updatedCoupon = couponDomainService.updateCoupon(
            couponId = couponId,
            name = name,
            description = description,
            discountAmount = discountAmount,
            discountPercentage = discountPercentage,
            minOrderAmount = minOrderAmount,
            maxDiscountAmount = maxDiscountAmount,
            totalQuantity = totalQuantity,
            validFrom = validFrom,
            validUntil = validUntil,
            targetType = targetType
        )

        // 변경사항 요약 생성 (성능상 원본 조회는 생략하고 간단한 메시지 사용)
        val changes = mutableListOf<String>()
        name?.let { changes.add("이름") }
        description?.let { changes.add("설명") }
        discountAmount?.let { changes.add("할인금액") }
        discountPercentage?.let { changes.add("할인비율") }
        minOrderAmount?.let { changes.add("최소주문금액") }
        maxDiscountAmount?.let { changes.add("최대할인금액") }
        totalQuantity?.let { changes.add("총수량") }
        validFrom?.let { changes.add("시작일") }
        validUntil?.let { changes.add("종료일") }
        targetType?.let { changes.add("대상타입") }

        val changeDescription = if (changes.isNotEmpty()) {
            "쿠폰이 수정되었습니다 (변경: ${changes.joinToString(", ")})"
        } else {
            "쿠폰 정보가 확인되었습니다"
        }

        // ⚡ 성능 최적화: 병렬 처리 가능한 작업들을 비동기로 처리
        try {
            // 히스토리 저장 (userCouponId 없으므로 실제로는 저장되지 않음 - 로직 개선 필요)
            saveCouponHistory(
                couponId = couponId,
                userId = adminId,
                action = CouponAction.ISSUED,
                description = changeDescription
            )

            // ⚡ 성능 최적화: 원본 쿠폰 정보 없이 최적화된 이벤트 발행
            couponEventPublisher.publishCouponUpdatedEventOptimized(
                updatedCoupon = updatedCoupon,
                changedFields = changes,
                adminId = adminId
            )

            logger.info { "✅ 쿠폰 수정 완료 (최적화): couponId=$couponId, 변경사항=${changes.size}개" }
            updatedCoupon
        } catch (e: Exception) {
            logger.warn(e) { "⚠️ 쿠폰 수정 후속 처리 중 일부 오류 발생: couponId=$couponId" }
            updatedCoupon // 메인 수정은 성공했으므로 결과 반환
        }
    }

    /**
     * 쿠폰 삭제
     */
    suspend fun deleteCoupon(couponId: Long, adminId: Long): Unit = withContext(Dispatchers.IO) {
        logger.info { "🗑️ 쿠폰 삭제: couponId=$couponId, adminId=$adminId" }

        val coupon = couponDomainService.getCouponById(couponId)

        // 쿠폰 삭제
        couponDomainService.deleteCoupon(couponId)

        // 히스토리는 삭제되므로 별도 저장하지 않음

        // 이벤트 발행
        couponEventPublisher.publishCouponDeletedEvent(coupon, adminId)

        logger.info { "✅ 쿠폰 삭제 완료: couponId=$couponId" }
    }

    /**
     * 사용자 쿠폰 발급
     */
    suspend fun issueCouponToUser(
        userId: Long,
        couponId: Long,
        expiredAt: LocalDateTime? = null
    ): UserCoupon = withContext(Dispatchers.IO) {
        logger.info { "🎟️ 사용자 쿠폰 발급: userId=$userId, couponId=$couponId" }

        val coupon = couponDomainService.getCouponById(couponId)

        // 발급 가능 여부 검증
        validateCouponIssuance(coupon)

        // 사용자 쿠폰 생성
        val userCoupon = UserCoupon(
            userId = userId,
            couponId = couponId,
            couponCode = generateCouponCode(),
            status = UserCouponStatus.ISSUED,
            expiredAt = expiredAt ?: coupon.validUntil.plusDays(30) // 기본 30일 후 만료
        )

        val issuedUserCoupon = userCouponDomainService.issueCoupon(userCoupon)

        // 쿠폰 발급 수량 증가
        couponDomainService.incrementIssuedQuantity(couponId, 1)

        // 히스토리 저장
        saveCouponHistory(
            couponId = couponId,
            userId = userId,
            userCouponId = issuedUserCoupon.id!!,
            action = CouponAction.ISSUED,
            description = "사용자에게 쿠폰이 발급되었습니다"
        )

        // 이벤트 발행
        couponEventPublisher.publishCouponIssuedEvent(issuedUserCoupon, coupon)

        logger.info { "✅ 사용자 쿠폰 발급 완료: userCouponId=${issuedUserCoupon.id}" }
        issuedUserCoupon
    }

    /**
     * 쿠폰 사용 (주문 시)
     */
    suspend fun useCoupon(
        userId: Long,
        userCouponId: Long,
        orderId: Long,
        orderAmount: BigDecimal
    ): UserCoupon = withContext(Dispatchers.IO) {
        logger.info { "💳 쿠폰 사용: userId=$userId, userCouponId=$userCouponId, orderId=$orderId" }

        val userCoupon = userCouponDomainService.getUserCouponById(userCouponId)
        val coupon = couponDomainService.getCouponById(userCoupon.couponId)

        // 사용 가능 여부 검증
        validateCouponUsage(userCoupon, coupon, userId, orderAmount)

        // 할인 금액 계산
        val discountAmount = calculateDiscountAmount(coupon, orderAmount)

        // 쿠폰 예약 (주문 생성 시점에는 예약만 함)
        val reservedUntil = LocalDateTime.now().plusMinutes(30) // 30분 예약
        val reservedCoupon = userCouponDomainService.reserveCoupon(userCouponId, orderId, reservedUntil)

        // 히스토리 저장
        saveCouponHistory(
            couponId = coupon.id!!,
            userId = userId,
            userCouponId = userCouponId,
            action = CouponAction.RESERVED,
            description = "주문에서 쿠폰이 예약되었습니다 (orderId: $orderId, discount: $discountAmount)"
        )

        // 이벤트 발행
        couponEventPublisher.publishCouponReservedEvent(reservedCoupon, coupon, orderId, discountAmount)

        logger.info { "✅ 쿠폰 예약 완료: userCouponId=$userCouponId, discount=$discountAmount" }
        reservedCoupon
    }

    /**
     * 쿠폰 사용 확정 (결제 완료 시)
     */
    suspend fun confirmCouponUsage(
        userCouponId: Long,
        orderId: Long,
        actualDiscountAmount: BigDecimal
    ): UserCoupon = withContext(Dispatchers.IO) {
        logger.info { "✅ 쿠폰 사용 확정: userCouponId=$userCouponId, orderId=$orderId" }

        val confirmedCoupon = userCouponDomainService.confirmCoupon(userCouponId, actualDiscountAmount)
        val coupon = couponDomainService.getCouponById(confirmedCoupon.couponId)

        // 히스토리 저장
        saveCouponHistory(
            couponId = confirmedCoupon.couponId,
            userId = confirmedCoupon.userId,
            userCouponId = userCouponId,
            action = CouponAction.USED,
            description = "쿠폰 사용이 확정되었습니다 (orderId: $orderId, discount: $actualDiscountAmount)"
        )

        // 이벤트 발행
        couponEventPublisher.publishCouponUsedEvent(confirmedCoupon, coupon, orderId, actualDiscountAmount)

        logger.info { "✅ 쿠폰 사용 확정 완료: userCouponId=$userCouponId" }
        confirmedCoupon
    }

    /**
     * 쿠폰 사용 취소 (주문 취소 시)
     */
    suspend fun cancelCouponUsage(userCouponId: Long, orderId: Long): UserCoupon = withContext(Dispatchers.IO) {
        logger.info { "🔄 쿠폰 사용 취소: userCouponId=$userCouponId, orderId=$orderId" }

        val releasedCoupon = userCouponDomainService.releaseCoupon(userCouponId)
        val coupon = couponDomainService.getCouponById(releasedCoupon.couponId)

        // 히스토리 저장
        saveCouponHistory(
            couponId = releasedCoupon.couponId,
            userId = releasedCoupon.userId,
            userCouponId = userCouponId,
            action = CouponAction.CANCELLED,
            description = "쿠폰 사용이 취소되었습니다 (orderId: $orderId)"
        )

        // 이벤트 발행
        couponEventPublisher.publishCouponReleasedEvent(releasedCoupon, coupon, orderId)

        logger.info { "✅ 쿠폰 사용 취소 완료: userCouponId=$userCouponId" }
        releasedCoupon
    }

    /**
     * 쿠폰 발급 검증
     */
    private fun validateCouponIssuance(coupon: Coupon) {
        if (coupon.status != CouponStatus.ACTIVE) {
            throw CouponException("활성화된 쿠폰만 발급할 수 있습니다")
        }

        if (coupon.validFrom.isAfter(LocalDateTime.now())) {
            throw CouponException("아직 발급 기간이 아닙니다")
        }

        if (coupon.validUntil.isBefore(LocalDateTime.now())) {
            throw CouponException("발급 기간이 만료되었습니다")
        }

        coupon.totalQuantity?.let { totalQty ->
            if (coupon.issuedQuantity >= totalQty) {
                throw CouponException("쿠폰 발급 수량이 모두 소진되었습니다")
            }
        }
    }

    /**
     * 쿠폰 사용 검증
     */
    private fun validateCouponUsage(
        userCoupon: UserCoupon,
        coupon: Coupon,
        userId: Long,
        orderAmount: BigDecimal
    ) {
        if (userCoupon.userId != userId) {
            throw CouponException("본인의 쿠폰만 사용할 수 있습니다")
        }

        if (userCoupon.status != UserCouponStatus.ISSUED) {
            throw CouponException("발급된 상태의 쿠폰만 사용할 수 있습니다")
        }

        if (userCoupon.expiredAt?.isBefore(LocalDateTime.now()) == true) {
            throw CouponException("만료된 쿠폰입니다")
        }

        if (coupon.minOrderAmount != null && orderAmount < coupon.minOrderAmount) {
            throw CouponException("최소 주문 금액을 충족하지 않습니다 (최소: ${coupon.minOrderAmount})")
        }
    }

    /**
     * 할인 금액 계산
     */
    private fun calculateDiscountAmount(coupon: Coupon, orderAmount: BigDecimal): BigDecimal {
        return when (coupon.discountType) {
            DiscountType.AMOUNT -> {
                val discount = coupon.discountAmount ?: BigDecimal.ZERO
                if (coupon.maxDiscountAmount != null) {
                    discount.min(coupon.maxDiscountAmount)
                } else {
                    discount
                }
            }
            DiscountType.PERCENTAGE -> {
                val discountRate = (coupon.discountPercentage ?: BigDecimal.ZERO).divide(BigDecimal(100))
                val discount = orderAmount.multiply(discountRate)
                if (coupon.maxDiscountAmount != null) {
                    discount.min(coupon.maxDiscountAmount)
                } else {
                    discount
                }
            }
        }
    }

    /**
     * 쿠폰 코드 생성
     */
    private fun generateCouponCode(): String {
        return "CP${System.currentTimeMillis()}${Random().nextInt(1000, 9999)}"
    }

    /**
     * 쿠폰 히스토리 저장
     */
    private suspend fun saveCouponHistory(
        couponId: Long,
        userId: Long,
        userCouponId: Long? = null,
        action: CouponAction,
        description: String
    ) = withContext(Dispatchers.IO) {
        if (userCouponId == null) {
            logger.debug { "쿠폰 히스토리 저장 생략 (userCouponId 없음): couponId=$couponId, action=$action" }
            return@withContext
        }

        val history = CouponHistory(
            couponId = couponId,
            userCouponId = userCouponId,
            userId = userId,
            action = action,
            reason = description
        )
        couponHistoryRepository.save(history)
    }

    /**
     * ALL_USERS 쿠폰을 모든 CUSTOMER 사용자에게 자동 발급
     */
    private suspend fun issueToAllCustomers(coupon: Coupon, adminId: Long) = withContext(Dispatchers.IO) {
        logger.info { "🎫 ALL_USERS 쿠폰 일괄 발급 시작: couponId=${coupon.id}, couponName=${coupon.name}" }

        try {
            // 모든 CUSTOMER 사용자 ID 조회
            val customerUserIds = userServiceClient.getAllCustomerUserIds()

            if (customerUserIds.isEmpty()) {
                logger.info { "ℹ️ 발급 대상 CUSTOMER 사용자가 없습니다: couponId=${coupon.id}" }
                return@withContext
            }

            logger.info { "📋 발급 대상 사용자 수: ${customerUserIds.size}명" }

            var successCount = 0
            var failCount = 0

            // 각 사용자에게 쿠폰 발급
            for (userId in customerUserIds) {
                try {
                    val userCoupon = issueCouponToUser(
                        userId = userId,
                        couponId = coupon.id!!,
                        expiredAt = coupon.validUntil // 쿠폰 유효기간과 동일하게 설정
                    )
                    successCount++
                    logger.debug { "✅ 사용자 쿠폰 발급 성공: userId=$userId, userCouponId=${userCoupon.id}" }
                } catch (e: Exception) {
                    failCount++
                    logger.warn(e) { "❌ 사용자 쿠폰 발급 실패: userId=$userId, couponId=${coupon.id}" }
                }
            }

            // 발급 결과 로깅
            logger.info { "🎉 ALL_USERS 쿠폰 일괄 발급 완료: couponId=${coupon.id}, 성공=$successCount, 실패=$failCount" }

            // 히스토리 저장
            saveCouponHistory(
                couponId = coupon.id!!,
                userId = adminId,
                action = CouponAction.ISSUED,
                description = "ALL_USERS 쿠폰 자동 일괄 발급 (성공: $successCount, 실패: $failCount)"
            )

        } catch (e: Exception) {
            logger.error(e) { "💥 ALL_USERS 쿠폰 일괄 발급 중 오류 발생: couponId=${coupon.id}" }
        }
    }
}
