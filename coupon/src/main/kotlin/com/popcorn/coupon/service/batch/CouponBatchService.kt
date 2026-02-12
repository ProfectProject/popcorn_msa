package com.popcorn.coupon.service.batch

import com.popcorn.coupon.service.core.CouponDomainService
import com.popcorn.coupon.service.core.UserCouponDomainService
import com.popcorn.coupon.service.event.CouponEventPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CouponBatchService(
    private val couponDomainService: CouponDomainService,
    private val userCouponDomainService: UserCouponDomainService,
    private val couponEventPublisher: CouponEventPublisher
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 만료된 쿠폰 상태 변경
     * 매일 오전 2시에 실행
     */
    @Scheduled(cron = "0 0 2 * * ?")
    suspend fun processExpiredCoupons() = withContext(Dispatchers.IO) {
        logger.info { "🕐 [배치] 만료된 쿠폰 처리 시작" }

        try {
            val startTime = System.currentTimeMillis()
            val expiredCoupons = couponDomainService.processExpiredCoupons()

            if (expiredCoupons.isNotEmpty()) {
                logger.info { "📋 만료 처리된 쿠폰: ${expiredCoupons.size}개" }

                // 만료된 쿠폰들에 대한 이벤트 발행
                expiredCoupons.forEach { coupon ->
                    try {
                        // 쿠폰 만료 이벤트는 여기서는 단순 로깅만 (필요시 이벤트 추가 구현)
                        logger.debug { "만료 처리됨: couponId=${coupon.id}, name=${coupon.name}" }
                    } catch (e: Exception) {
                        logger.error(e) { "쿠폰 만료 이벤트 발행 실패: couponId=${coupon.id}" }
                    }
                }
            } else {
                logger.info { "✅ 처리할 만료된 쿠폰 없음" }
            }

            val endTime = System.currentTimeMillis()
            logger.info { "✅ [배치] 만료된 쿠폰 처리 완료 - 처리시간: ${endTime - startTime}ms, 처리건수: ${expiredCoupons.size}" }

        } catch (e: Exception) {
            logger.error(e) { "❌ [배치] 만료된 쿠폰 처리 실패" }
        }
    }

    /**
     * 만료된 사용자 쿠폰 처리
     * 매일 오전 3시에 실행
     */
    @Scheduled(cron = "0 0 3 * * ?")
    suspend fun processExpiredUserCoupons() = withContext(Dispatchers.IO) {
        logger.info { "🕐 [배치] 만료된 사용자 쿠폰 처리 시작" }

        try {
            val startTime = System.currentTimeMillis()
            val expiredUserCoupons = userCouponDomainService.processExpiredCoupons()

            if (expiredUserCoupons.isNotEmpty()) {
                logger.info { "📋 만료 처리된 사용자 쿠폰: ${expiredUserCoupons.size}개" }

                // 만료된 사용자 쿠폰들에 대한 이벤트 발행
                expiredUserCoupons.forEach { userCoupon ->
                    try {
                        val coupon = couponDomainService.getCouponById(userCoupon.couponId)
                        couponEventPublisher.publishCouponExpiredEvent(userCoupon, coupon)
                        logger.debug { "만료 처리됨: userCouponId=${userCoupon.id}, userId=${userCoupon.userId}" }
                    } catch (e: Exception) {
                        logger.error(e) { "사용자 쿠폰 만료 이벤트 발행 실패: userCouponId=${userCoupon.id}" }
                    }
                }
            } else {
                logger.info { "✅ 처리할 만료된 사용자 쿠폰 없음" }
            }

            val endTime = System.currentTimeMillis()
            logger.info { "✅ [배치] 만료된 사용자 쿠폰 처리 완료 - 처리시간: ${endTime - startTime}ms, 처리건수: ${expiredUserCoupons.size}" }

        } catch (e: Exception) {
            logger.error(e) { "❌ [배치] 만료된 사용자 쿠폰 처리 실패" }
        }
    }

    /**
     * 만료된 쿠폰 예약 복구
     * 매 30분마다 실행
     */
    @Scheduled(cron = "0 */30 * * * ?")
    suspend fun restoreExpiredReservations() = withContext(Dispatchers.IO) {
        logger.info { "🔄 [배치] 만료된 쿠폰 예약 복구 시작" }

        try {
            val startTime = System.currentTimeMillis()
            val restoredCoupons = userCouponDomainService.restoreExpiredReservations()

            if (restoredCoupons.isNotEmpty()) {
                logger.info { "🔓 복구된 예약 쿠폰: ${restoredCoupons.size}개" }

                // 복구된 쿠폰들에 대한 이벤트 발행
                restoredCoupons.forEach { userCoupon ->
                    try {
                        val coupon = couponDomainService.getCouponById(userCoupon.couponId)
                        couponEventPublisher.publishCouponReleasedEvent(userCoupon, coupon, -1) // orderId -1은 자동 복구를 의미
                        logger.debug { "예약 복구됨: userCouponId=${userCoupon.id}, userId=${userCoupon.userId}" }
                    } catch (e: Exception) {
                        logger.error(e) { "쿠폰 예약 복구 이벤트 발행 실패: userCouponId=${userCoupon.id}" }
                    }
                }
            } else {
                logger.debug { "✅ 복구할 만료된 예약 쿠폰 없음" }
            }

            val endTime = System.currentTimeMillis()
            logger.info { "✅ [배치] 만료된 쿠폰 예약 복구 완료 - 처리시간: ${endTime - startTime}ms, 처리건수: ${restoredCoupons.size}" }

        } catch (e: Exception) {
            logger.error(e) { "❌ [배치] 만료된 쿠폰 예약 복구 실패" }
        }
    }

    /**
     * 쿠폰 발급 수량 동기화
     * 매일 오전 4시에 실행
     */
    @Scheduled(cron = "0 0 4 * * ?")
    suspend fun syncCouponIssuedQuantity() = withContext(Dispatchers.IO) {
        logger.info { "🔄 [배치] 쿠폰 발급 수량 동기화 시작" }

        try {
            val startTime = System.currentTimeMillis()

            // 활성 쿠폰들의 실제 발급 수량을 재계산하여 동기화
            val activeCoupons = couponDomainService.getActiveCoupons()
            var syncCount = 0

            for (coupon in activeCoupons) {
                try {
                    // 실제 발급된 사용자 쿠폰 수량 조회
                    val actualIssuedCount = userCouponDomainService.countByCouponId(coupon.id!!)

                    if (coupon.issuedQuantity != actualIssuedCount.toInt()) {
                        // 수량 불일치 시 동기화
                        logger.warn { "⚠️ 쿠폰 발급 수량 불일치 발견: couponId=${coupon.id}, DB=${coupon.issuedQuantity}, 실제=${actualIssuedCount}" }

                        // 실제 발급 수량으로 업데이트 (차이만큼)
                        val difference = actualIssuedCount.toInt() - coupon.issuedQuantity
                        if (difference != 0) {
                            coupon.id?.let { couponId ->
                                couponDomainService.incrementIssuedQuantity(couponId, difference)
                            }
                            syncCount++
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "쿠폰 발급 수량 동기화 실패: couponId=${coupon.id}" }
                }
            }

            val endTime = System.currentTimeMillis()
            logger.info { "✅ [배치] 쿠폰 발급 수량 동기화 완료 - 처리시간: ${endTime - startTime}ms, 동기화건수: $syncCount" }

        } catch (e: Exception) {
            logger.error(e) { "❌ [배치] 쿠폰 발급 수량 동기화 실패" }
        }
    }

    /**
     * 배치 작업 상태 모니터링
     * 매 10분마다 실행
     */
    @Scheduled(cron = "0 */10 * * * ?")
    suspend fun monitorBatchHealth() = withContext(Dispatchers.IO) {
        try {
            // 미발행 이벤트 수 체크
            val unpublishedEventCount = couponEventPublisher.getUnpublishedEventCount()

            if (unpublishedEventCount > 1000) {
                logger.warn { "⚠️ [모니터링] 미발행 이벤트가 많습니다: $unpublishedEventCount 개" }
            }

            // 만료된 예약 쿠폰 수 체크
            val expiredReservationCount = userCouponDomainService.getExpiredReservationCount()

            if (expiredReservationCount > 100) {
                logger.warn { "⚠️ [모니터링] 만료된 예약 쿠폰이 많습니다: $expiredReservationCount 개" }
            }

            logger.debug { "✅ [모니터링] 배치 작업 상태 정상 - 미발행 이벤트: $unpublishedEventCount, 만료 예약: $expiredReservationCount" }

        } catch (e: Exception) {
            logger.error(e) { "❌ [모니터링] 배치 작업 상태 체크 실패" }
        }
    }
}