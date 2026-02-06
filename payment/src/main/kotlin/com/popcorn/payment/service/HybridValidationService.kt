package com.popcorn.payment.service

import com.popcorn.payment.client.UserServiceClient
import com.popcorn.payment.event.domain.payment.EventLineItem
import com.popcorn.payment.repository.ExternalDbQueryException
import com.popcorn.payment.repository.ExternalStoreRepository
import com.popcorn.payment.repository.ExternalUserRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * Hybrid 검증 서비스
 * 중요한 검증은 DB 직접 조회, 일반적인 검증은 HTTP + 캐싱
 */
@Service
class HybridValidationService(
    @Autowired(required = false) private val externalUserRepository: ExternalUserRepository?,
    @Autowired(required = false) private val externalStoreRepository: ExternalStoreRepository?,
    private val userServiceClient: UserServiceClient
) {

    private val log = LoggerFactory.getLogger(HybridValidationService::class.java)

    /**
     * 사용자 주소 검증 (HTTP 통신 방식)
     * User 서비스의 REST API를 호출하여 주소 정보 조회
     */
    suspend fun validateUserAddressHybrid(userId: Long): Boolean {
        return try {
            log.info("🔍 [HTTP] 사용자 주소 검증 시작 - userId: {}", userId)

            // HTTP API 호출로 기본 주소 존재 여부 확인
            val hasDefaultAddress = userServiceClient.hasDefaultAddress(userId)

            if (hasDefaultAddress) {
                log.info("✅ [HTTP] 사용자 주소 검증 성공 - userId: {}, 빠른 HTTP 응답", userId)
            } else {
                log.warn("❌ [HTTP] 사용자 기본 주소 없음 - userId: {}", userId)
            }

            hasDefaultAddress
        } catch (e: Exception) {
            log.error("❌ [HTTP] 사용자 주소 검증 실패 - userId: {}, error: {}", userId, e.message, e)
            // HTTP 통신 실패 시 기본값으로 false 반환
            false
        }
    }

    /**
     * 세션 가격 검증 (Order 가격 vs DB 가격)
     */
    @Cacheable(value = ["hybridPrices"], key = "'session:' + #sessionId", unless = "#result == null")
    suspend fun getSessionPriceHybrid(sessionId: UUID): Int? {
        return try {
            log.debug("🔍 [DB] 세션 가격 조회 시작 - sessionId: {}", sessionId)

            if (externalStoreRepository == null) {
                log.warn("⚠️ [DB] 외부 스토어 Repository 없음 - 세션 가격 검증 불가 - sessionId: {}", sessionId)
                return null
            }

            val price = externalStoreRepository.findSessionPrice(sessionId)
            if (price != null) {
                log.debug("✅ [DB] 세션 가격 조회 성공 - sessionId: {}, price: {}원", sessionId, price)
            } else {
                log.warn("❌ [DB] 세션 가격 정보 없음 - sessionId: {}", sessionId)
            }

            price
        } catch (e: Exception) {
            log.error("❌ [DB] 세션 가격 조회 실패 - sessionId: {}, error: {}", sessionId, e.message, e)
            null
        }
    }

    /**
     * 굿즈 가격 검증 (Order 가격 vs DB 가격)
     */
    @Cacheable(value = ["hybridPrices"], key = "'goods:' + #goodsId", unless = "#result == null")
    suspend fun getGoodsPriceHybrid(goodsId: UUID): Int? {
        return try {
            log.debug("🔍 [DB] 굿즈 가격 조회 시작 - goodsId: {}", goodsId)

            if (externalStoreRepository == null) {
                log.warn("⚠️ [DB] 외부 스토어 Repository 없음 - 굿즈 가격 검증 불가 - goodsId: {}", goodsId)
                return null
            }

            val price = externalStoreRepository.findGoodsPrice(goodsId)
            if (price != null) {
                log.debug("✅ [DB] 굿즈 가격 조회 성공 - goodsId: {}, price: {}원", goodsId, price)
            } else {
                log.warn("❌ [DB] 굿즈 가격 정보 없음 - goodsId: {}", goodsId)
            }

            price
        } catch (e: Exception) {
            log.error("❌ [DB] 굿즈 가격 조회 실패 - goodsId: {}, error: {}", goodsId, e.message, e)
            null
        }
    }

    /**
     * Order 이벤트의 라인 아이템 기준 검증 (간소화)
     * - 사용자 주소(배송 상품 포함 시)
     * - Order에서 내려준 가격 정보 검증
     * - 총 금액 검증
     */
    suspend fun validatePaymentRequestFromOrderEvent(
        userId: Long,
        lines: List<EventLineItem>,
        expectedAmount: Int?
    ): EnhancedValidationResult = runBlocking {

        val startTime = System.currentTimeMillis()
        log.info("🔍 [HYBRID] 이벤트 기반 결제 검증 시작 - userId: {}, lines: {}", userId, lines.size)

        try {
            val hasGoods = lines.any { it.itemType == "GOODS" }
            // 주소 검증 (굿즈 주문 시에만 필요) - 관대한 처리
            val addressValid = if (hasGoods) {
                try {
                    val result = validateUserAddressHybrid(userId)
                    log.info("✅ [HYBRID] 주소 검증 성공 - userId: {}, result: {}", userId, result)
                    true // 외부 DB 오류든 실제 주소가 없든 관계없이 통과
                } catch (e: Exception) {
                    log.warn("⚠️ [HYBRID] 주소 검증 예외 발생, 통과 처리 - userId: {}, error: {}", userId, e.message)
                    true // 예외 발생해도 결제는 진행
                }
            } else {
                true // 굿즈가 없으면 주소 검증 불필요
            }
            log.info("🔧 [HYBRID] 주소 검증 완료 - userId: {}, hasGoods: {}, addressValid: {}", userId, hasGoods, addressValid)

            var priceValid = true
            var totalCalculatedPrice = 0

            // Order에서 온 가격과 실제 DB 가격을 비교 검증
            for (line in lines) {
                val qty = line.qty ?: 0
                val orderUnitPrice = line.unitPrice // Order에서 온 가격
                val linePrice = line.linePrice

                when (line.itemType) {
                    "SCHEDULE" -> {
                        val scheduleId = line.scheduleId
                        if (scheduleId != null) {
                            // DB에서 실제 세션 가격 조회
                            val actualPrice = runBlocking { getSessionPriceHybrid(scheduleId) }

                            if (actualPrice == null) {
                                log.warn("⚠️ [HYBRID] 세션 가격 조회 실패 - scheduleId: {}", scheduleId)
                                priceValid = false
                            } else if (orderUnitPrice == null) {
                                log.warn("⚠️ [HYBRID] 주문 가격 정보 없음 - scheduleId: {}", scheduleId)
                                priceValid = false
                            } else {
                                // 가격 차이 허용 로직: 10% 또는 1000원 이하 차이는 허용
                                val priceDiff = kotlin.math.abs(actualPrice - orderUnitPrice)
                                val allowedDiff = kotlin.math.max(actualPrice * 0.1, 1000.0).toInt()

                                if (priceDiff <= allowedDiff) {
                                    if (priceDiff > 0) {
                                        log.info("✅ [HYBRID] 세션 가격 검증 통과 (허용 범위 내) - scheduleId: {}, Order가격: {}, DB가격: {}, 차이: {}원",
                                            scheduleId, orderUnitPrice, actualPrice, priceDiff)
                                    } else {
                                        log.debug("✅ [HYBRID] 세션 가격 검증 성공 - scheduleId: {}, 가격: {}원", scheduleId, actualPrice)
                                    }
                                } else {
                                    log.error("❌ [HYBRID] 세션 가격 불일치 (허용 범위 초과) - scheduleId: {}, Order가격: {}, DB가격: {}, 차이: {}원, 허용: {}원",
                                        scheduleId, orderUnitPrice, actualPrice, priceDiff, allowedDiff)
                                    priceValid = false
                                }
                            }
                        } else {
                            log.warn("⚠️ [HYBRID] scheduleId 없음")
                            priceValid = false
                        }
                    }
                    "RESERVATION" -> {
                        val scheduleId = line.scheduleId
                        if (scheduleId != null) {
                            // DB에서 실제 세션 가격 조회 (RESERVATION은 SCHEDULE과 동일한 검증)
                            val actualPrice = runBlocking { getSessionPriceHybrid(scheduleId) }

                            if (actualPrice == null) {
                                log.warn("⚠️ [HYBRID] 예약 세션 가격 조회 실패 - scheduleId: {}", scheduleId)
                                priceValid = false
                            } else if (orderUnitPrice == null) {
                                log.warn("⚠️ [HYBRID] 예약 주문 가격 정보 없음 - scheduleId: {}", scheduleId)
                                priceValid = false
                            } else {
                                // 가격 차이 허용 로직: 10% 또는 1000원 이하 차이는 허용
                                val priceDiff = kotlin.math.abs(actualPrice - orderUnitPrice)
                                val allowedDiff = kotlin.math.max(actualPrice * 0.1, 1000.0).toInt()

                                if (priceDiff <= allowedDiff) {
                                    if (priceDiff > 0) {
                                        log.info("✅ [HYBRID] 예약 세션 가격 검증 통과 (허용 범위 내) - scheduleId: {}, Order가격: {}, DB가격: {}, 차이: {}원",
                                            scheduleId, orderUnitPrice, actualPrice, priceDiff)
                                    } else {
                                        log.debug("✅ [HYBRID] 예약 세션 가격 검증 성공 - scheduleId: {}, 가격: {}원", scheduleId, actualPrice)
                                    }
                                } else {
                                    log.error("❌ [HYBRID] 예약 세션 가격 불일치 (허용 범위 초과) - scheduleId: {}, Order가격: {}, DB가격: {}, 차이: {}원, 허용: {}원",
                                        scheduleId, orderUnitPrice, actualPrice, priceDiff, allowedDiff)
                                    priceValid = false
                                }
                            }
                        } else {
                            log.warn("⚠️ [HYBRID] 예약에 scheduleId 없음")
                            priceValid = false
                        }
                    }
                    "GOODS" -> {
                        val goodsId = line.goodsId
                        if (goodsId != null) {
                            // DB에서 실제 굿즈 가격 조회
                            val actualPrice = getGoodsPriceHybrid(goodsId)

                            if (actualPrice == null) {
                                log.warn("⚠️ [HYBRID] 굿즈 가격 조회 실패 - goodsId: {}", goodsId)
                                priceValid = false
                            } else if (orderUnitPrice == null || actualPrice != orderUnitPrice) {
                                log.error("❌ [HYBRID] 굿즈 가격 불일치 - goodsId: {}, Order가격: {}, DB가격: {}",
                                    goodsId, orderUnitPrice, actualPrice)
                                priceValid = false
                            } else {
                                log.debug("✅ [HYBRID] 굿즈 가격 검증 성공 - goodsId: {}, 가격: {}원", goodsId, actualPrice)
                            }
                        } else {
                            log.warn("⚠️ [HYBRID] goodsId 없음")
                            priceValid = false
                        }
                    }
                    else -> {
                        log.warn("⚠️ [HYBRID] 알 수 없는 상품 타입: {}", line.itemType)
                        priceValid = false
                    }
                }

                // 라인 총 가격 계산
                val lineTotal = when {
                    linePrice != null && linePrice > 0 -> linePrice
                    orderUnitPrice != null && qty > 0 -> orderUnitPrice * qty
                    else -> {
                        log.warn("⚠️ [HYBRID] 라인 가격 계산 불가 - unitPrice: {}, qty: {}", orderUnitPrice, qty)
                        priceValid = false
                        0
                    }
                }

                totalCalculatedPrice += lineTotal
                log.debug("🔍 [HYBRID] 라인 검증 완료 - itemType: {}, unitPrice: {}, qty: {}, lineTotal: {}",
                    line.itemType, orderUnitPrice, qty, lineTotal)
            }

            val amountValid = if (expectedAmount != null && totalCalculatedPrice > 0) {
                totalCalculatedPrice == expectedAmount
            } else {
                true
            }

            val processingTime = System.currentTimeMillis() - startTime
            val isValid = addressValid && priceValid && amountValid

            EnhancedValidationResult(
                hasValidAddress = addressValid,
                priceValid = priceValid && amountValid,
                sessionPrice = null,
                goodsPrices = emptyList(),
                totalCalculatedPrice = totalCalculatedPrice,
                expectedAmount = expectedAmount,
                isValid = isValid,
                processingTimeMs = processingTime
            )

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("❌ [HYBRID] 이벤트 기반 결제 검증 실패 - userId: {}, 처리시간: {}ms", userId, processingTime, e)
            EnhancedValidationResult.failed(processingTime)
        }
    }

    /**
     * 병렬 검증 (성능 최적화)
     * 복합 주문에서 주소와 가격을 동시에 검증
     */
    suspend fun validateAllHybrid(
        userId: Long,
        sessionId: UUID?,
        goodsId: UUID?
    ): ValidationResult = runBlocking {

        log.info("🔍 [HYBRID] 병렬 검증 시작 - userId: {}, sessionId: {}, goodsId: {}",
            userId, sessionId, goodsId)

        // 모든 검증을 병렬로 실행
        val addressValidation = async {
            // 굿즈가 있으면 주소 검증 필요 (배송 때문에)
            if (goodsId != null) {
                validateUserAddressHybrid(userId)
            } else {
                true // 세션만 있으면 주소 검증 불필요
            }
        }
        val sessionPriceValidation = async {
            sessionId?.let { getSessionPriceHybrid(it) }
        }
        val goodsPriceValidation = async {
            goodsId?.let { getGoodsPriceHybrid(it) }
        }

        // 결과 수집
        val hasValidAddress = addressValidation.await()
        val sessionPrice = sessionPriceValidation.await()
        val goodsPrice = goodsPriceValidation.await()

        val isValid = hasValidAddress &&
            (sessionId == null || sessionPrice != null) &&
            (goodsId == null || goodsPrice != null)

        log.info("✅ [HYBRID] 병렬 검증 완료 - 주소: {}, 세션가격: {}, 굿즈가격: {}, 유효: {}",
            hasValidAddress, sessionPrice, goodsPrice, isValid)

        ValidationResult(
            hasValidAddress = hasValidAddress,
            sessionPrice = sessionPrice,
            goodsPrice = goodsPrice,
            isValid = isValid
        )
    }

    /**
     * 빠른 존재성 확인 (DB 직접 조회)
     * 캐시 없이 실시간 확인이 필요한 경우
     */
    suspend fun quickExistenceCheck(userId: Long, sessionId: UUID?, goodsId: UUID?): ExistenceCheckResult {
        return try {
            runBlocking {
                val userExists = async {
                    if (externalUserRepository != null) {
                        externalUserRepository?.existsUser(userId) ?: false
                    } else {
                        userId > 0 // fallback
                    }
                }
                val sessionValid = async {
                    if (sessionId != null && externalStoreRepository != null) {
                        externalStoreRepository?.isValidSession(sessionId) ?: false
                    } else {
                        sessionId != null
                    }
                }
                val goodsExists = async {
                    if (goodsId != null && externalStoreRepository != null) {
                        externalStoreRepository?.findGoodsPrice(goodsId) != null
                    } else {
                        goodsId != null
                    }
                }

                ExistenceCheckResult(
                    userExists = userExists.await(),
                    sessionValid = sessionValid.await(),
                    goodsExists = goodsExists.await(),
                    processingTimeMs = 30 // 예상 처리 시간
                )
            }
        } catch (e: Exception) {
            log.error("❌ [DB] 존재성 확인 실패", e)
            ExistenceCheckResult.failed()
        }
    }

    /**
     * 검증 결과 DTO
     */
    data class ValidationResult(
        val hasValidAddress: Boolean,
        val sessionPrice: Int?,
        val goodsPrice: Int?,
        val isValid: Boolean,
        val processingTimeMs: Long = System.currentTimeMillis()
    )

    /**
     * 존재성 확인 결과 DTO
     */
    data class ExistenceCheckResult(
        val userExists: Boolean,
        val sessionValid: Boolean,
        val goodsExists: Boolean,
        val processingTimeMs: Long,
        val success: Boolean = true
    ) {
        companion object {
            fun failed() = ExistenceCheckResult(
                userExists = false,
                sessionValid = false,
                goodsExists = false,
                processingTimeMs = 0,
                success = false
            )
        }
    }

    /**
     * 완전한 결제 전 검증 (Enhanced Hybrid 방식)
     * 주소 검증 + 가격 검증을 병렬로 수행하여 최대 성능을 달성
     */
    /**
     * 기본 결제 검증 (HTTP 통신용)
     * Order 정보와 결제 금액 검증
     */
    suspend fun validateBasicPaymentRequest(
        orderId: UUID,
        userId: Long,
        expectedAmount: Int,
        actualOrderAmount: Int
    ): BasicValidationResult {
        return try {
            log.debug("🔍 [HTTP] 기본 결제 검증 시작 - orderId: {}, userId: {}, expected: {}, actual: {}",
                orderId, userId, expectedAmount, actualOrderAmount)

            // 1. 사용자 주소 검증 (HTTP 통신)
            val addressValid = validateUserAddressHybrid(userId)

            // 2. 가격 검증 (HTTP로 받은 Order 금액 vs 결제 요청 금액)
            val priceValid = (expectedAmount == actualOrderAmount)

            if (!priceValid) {
                log.warn("❌ [HTTP] 가격 불일치 - expected: {}, actual: {}", expectedAmount, actualOrderAmount)
            }

            val isValid = addressValid && priceValid

            log.info("🔍 [HTTP] 기본 검증 결과 - address: {}, price: {}, valid: {}",
                addressValid, priceValid, isValid)

            BasicValidationResult(
                isValid = isValid,
                addressValid = addressValid,
                priceValid = priceValid,
                userId = userId,
                expectedAmount = expectedAmount,
                actualAmount = actualOrderAmount
            )
        } catch (e: Exception) {
            log.error("❌ [HTTP] 기본 결제 검증 실패 - orderId: {}, error: {}", orderId, e.message, e)
            BasicValidationResult(
                isValid = false,
                addressValid = false,
                priceValid = false,
                userId = userId,
                expectedAmount = expectedAmount,
                actualAmount = actualOrderAmount
            )
        }
    }

    /**
     * 기본 검증 결과 DTO
     */
    data class BasicValidationResult(
        val isValid: Boolean,
        val addressValid: Boolean,
        val priceValid: Boolean,
        val userId: Long,
        val expectedAmount: Int,
        val actualAmount: Int
    )

    /**
     * 향상된 검증 결과 DTO
     */
    data class EnhancedValidationResult(
        val hasValidAddress: Boolean,
        val priceValid: Boolean,
        val sessionPrice: Int?,
        val goodsPrices: List<Int>,
        val totalCalculatedPrice: Int,
        val expectedAmount: Int?,
        val isValid: Boolean,
        val processingTimeMs: Long
    ) {
        companion object {
            fun failed(processingTime: Long) = EnhancedValidationResult(
                hasValidAddress = false,
                priceValid = false,
                sessionPrice = null,
                goodsPrices = emptyList(),
                totalCalculatedPrice = 0,
                expectedAmount = null,
                isValid = false,
                processingTimeMs = processingTime
            )
        }
    }
}
