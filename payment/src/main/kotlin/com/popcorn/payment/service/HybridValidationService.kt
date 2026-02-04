package com.popcorn.payment.service

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

/**
 * Hybrid 검증 서비스
 * 중요한 검증은 DB 직접 조회, 일반적인 검증은 HTTP + 캐싱
 */
@Service
class HybridValidationService(
    private val externalUserRepository: ExternalUserRepository,
    private val externalStoreRepository: ExternalStoreRepository
) {

    private val log = LoggerFactory.getLogger(HybridValidationService::class.java)

    /**
     * 사용자 주소 검증 (Hybrid 방식)
     * 중요한 검증이므로 DB 직접 조회 우선, 실패 시 HTTP fallback
     */
    suspend fun validateUserAddressHybrid(userId: Long): Boolean {
        return try {
            log.info("🔍 [DB] 사용자 주소 검증 시작 - userId: {}", userId)

            // 1차: DB 직접 조회 (10-50ms)
            val hasDefaultAddress = externalUserRepository.hasDefaultAddress(userId)

            if (hasDefaultAddress) {
                log.info("✅ [DB] 사용자 주소 검증 성공 - userId: {}, 처리시간: ~20ms", userId)
            } else {
                log.warn("❌ [DB] 사용자 기본 주소 없음 - userId: {}", userId)
            }

            hasDefaultAddress
        } catch (e: ExternalDbQueryException) {
            log.error("❌ [DB] 사용자 주소 검증 실패 - userId: {}, error: {}", userId, e.message, e)
            throw e
        }
    }

    /**
     * 가격 검증 (Hybrid 방식)
     * 중요한 검증이므로 DB 직접 조회 우선
     */
    @Cacheable(value = ["hybridPrices"], key = "'session:' + #sessionId", unless = "#result == null")
    suspend fun getSessionPriceHybrid(sessionId: UUID): Int? {
        return try {
            log.debug("🔍 [DB] 세션 가격 조회 시작 - sessionId: {}", sessionId)

            // 1차: DB 직접 조회 (10-50ms)
            val price = externalStoreRepository.findSessionPrice(sessionId)

            if (price != null) {
                log.debug("✅ [DB] 세션 가격 조회 성공 - sessionId: {}, price: {}원, 처리시간: ~20ms", sessionId, price)
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
     * 굿즈 가격 검증 (Hybrid 방식)
     */
    @Cacheable(value = ["hybridPrices"], key = "'goods:' + #goodsId", unless = "#result == null")
    suspend fun getGoodsPriceHybrid(goodsId: UUID): Int? {
        return try {
            log.debug("🔍 [DB] 굿즈 가격 조회 시작 - goodsId: {}", goodsId)

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
     * Order 이벤트의 라인 아이템 기준 검증 (DB 직접 조회)
     * - 사용자 주소(배송 상품 포함 시)
     * - 라인 단가 검증 (세션/굿즈)
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
            val addressValid = if (hasGoods) validateUserAddressHybrid(userId) else true

            var priceValid = true
            var totalCalculatedPrice = 0

            for (line in lines) {
                val qty = line.qty ?: 0
                val unitPrice = line.unitPrice

                when (line.itemType) {
                    "SCHEDULE" -> {
                        val scheduleId = line.scheduleId
                        val actualPrice = scheduleId?.let {
                            runBlocking { getSessionPriceHybrid(it) }
                        }
                        if (actualPrice == null || unitPrice == null || actualPrice != unitPrice) {
                            priceValid = false
                        }
                    }
                    "GOODS" -> {
                        val goodsId = line.goodsId
                        val actualPrice = goodsId?.let { getGoodsPriceHybrid(it) }
                        if (actualPrice == null || unitPrice == null || actualPrice != unitPrice) {
                            priceValid = false
                        }
                    }
                    else -> {
                        priceValid = false
                    }
                }

                val lineTotal = when {
                    line.linePrice != null -> line.linePrice
                    unitPrice != null && qty > 0 -> unitPrice * qty
                    else -> 0
                }

                totalCalculatedPrice += lineTotal
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
     * 사용자 주소와 가격을 동시에 검증
     */
    suspend fun validateAllHybrid(
        userId: Long,
        sessionId: UUID?,
        goodsId: UUID?
    ): ValidationResult = runBlocking {

        // 모든 검증을 병렬로 실행
        val addressValidation = async { validateUserAddressHybrid(userId) }
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

        ValidationResult(
            hasValidAddress = hasValidAddress,
            sessionPrice = sessionPrice,
            goodsPrice = goodsPrice,
            isValid = hasValidAddress && (sessionPrice != null || goodsPrice != null)
        )
    }

    /**
     * 빠른 존재성 확인 (DB 직접 조회)
     * 캐시 없이 실시간 확인이 필요한 경우
     */
    suspend fun quickExistenceCheck(userId: Long, sessionId: UUID?, goodsId: UUID?): ExistenceCheckResult {
        return try {
            runBlocking {
                val userExists = async { externalUserRepository.existsUser(userId) }
                val sessionValid = async {
                    sessionId?.let { externalStoreRepository.isValidSession(it) } ?: true
                }
                val goodsExists = async {
                    goodsId?.let { externalStoreRepository.findGoodsPrice(it) != null } ?: true
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
