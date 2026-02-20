package com.popcorn.payment.service

import com.popcorn.payment.client.UserServiceClient
import com.popcorn.payment.client.StoreValidationClient
import com.popcorn.payment.dto.BatchPriceValidationRequest
import com.popcorn.payment.dto.LineItemPriceRequest
import com.popcorn.payment.dto.OrderInfoResponse
import com.popcorn.payment.event.domain.payment.EventLineItem
import com.popcorn.payment.exception.PaymentValidationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/**
 * 🛡️ 개선된 Hybrid 검증 서비스
 * - Kotlin 3.x 최신 패턴 적용
 * - 보안 우선 원칙 (Fail-Safe)
 * - Suspend functions with proper coroutine usage
 * - Sealed classes for type-safe results
 */
@Service
class ImprovedHybridValidationService(
    private val userServiceClient: UserServiceClient,
    private val storeValidationClient: StoreValidationClient
) {
    private val log = LoggerFactory.getLogger(ImprovedHybridValidationService::class.java)

    companion object {
        private const val VALIDATION_TIMEOUT_MS = 5000L
        private const val PRICE_TOLERANCE_PERCENTAGE = 0.1
        private const val MIN_PRICE_TOLERANCE = 1000
    }

    /**
     * 🏗️ 검증 결과를 나타내는 Sealed Class
     */
    sealed class ValidationResult {
        data class Success(
            val addressValid: Boolean,
            val priceValid: Boolean,
            val totalAmount: Int,
            val processingTimeMs: Long,
            val details: String = ""
        ) : ValidationResult()

        data class Failure(
            val reason: String,
            val cause: Throwable? = null,
            val processingTimeMs: Long
        ) : ValidationResult()
    }

    /**
     * 🎯 주소 검증 결과 (Type-Safe)
     */
    sealed class AddressValidation {
        object Valid : AddressValidation()
        object Invalid : AddressValidation()
        data class Error(val cause: Throwable) : AddressValidation()
    }

    /**
     * 💰 가격 검증 결과 (Type-Safe)
     */
    sealed class PriceValidation {
        data class Valid(val actualPrice: Int) : PriceValidation()
        data class Invalid(val expected: Int, val actual: Int, val reason: String) : PriceValidation()
        data class Error(val cause: Throwable) : PriceValidation()
    }

    /**
     * 🏠 사용자 주소 검증 (보안 강화)
     * 예외 발생 시 검증 실패로 처리 (Fail-Safe)
     */
    suspend fun validateUserAddress(userId: Long): AddressValidation {
        return withTimeoutOrNull(VALIDATION_TIMEOUT_MS) {
            try {
                log.debug("🔍 [주소 검증] 시작 - userId: {}", userId)

                val hasAddress = userServiceClient.hasDefaultAddress(userId)

                if (hasAddress) {
                    log.info("✅ [주소 검증] 성공 - userId: {}", userId)
                    AddressValidation.Valid
                } else {
                    log.warn("❌ [주소 검증] 기본 주소 없음 - userId: {}", userId)
                    AddressValidation.Invalid
                }
            } catch (e: Exception) {
                log.error("💀 [주소 검증] 서비스 장애 - userId: {}, 보안상 검증 실패 처리", userId, e)
                AddressValidation.Error(e)
            }
        } ?: run {
            log.error("⏰ [주소 검증] 타임아웃 - userId: {}, 보안상 검증 실패 처리", userId)
            AddressValidation.Error(RuntimeException("Address validation timeout"))
        }
    }

    /**
     * 💲 세션 가격 검증 (보안 강화)
     */
    @Cacheable(value = ["sessionPrices"], key = "#sessionId", unless = "#result == null")
    suspend fun validateSessionPrice(sessionId: UUID, expectedPrice: Int): PriceValidation {
        return withTimeoutOrNull(VALIDATION_TIMEOUT_MS) {
            try {
                log.debug("🔍 [세션 가격] 검증 시작 - sessionId: {}, expected: {}", sessionId, expectedPrice)

                val actualPrice = storeValidationClient.getSessionPrice(sessionId)
                    ?: return@withTimeoutOrNull PriceValidation.Error(
                        RuntimeException("Session price not found: $sessionId")
                    )

                val priceDiff = abs(actualPrice - expectedPrice)
                val toleranceDiff = max(actualPrice * PRICE_TOLERANCE_PERCENTAGE, MIN_PRICE_TOLERANCE.toDouble()).toInt()

                when {
                    priceDiff <= toleranceDiff -> {
                        log.info("✅ [세션 가격] 검증 성공 - sessionId: {}, actual: {}, expected: {}, diff: {}",
                            sessionId, actualPrice, expectedPrice, priceDiff)
                        PriceValidation.Valid(actualPrice)
                    }
                    else -> {
                        log.error("❌ [세션 가격] 불일치 - sessionId: {}, actual: {}, expected: {}, diff: {}, tolerance: {}",
                            sessionId, actualPrice, expectedPrice, priceDiff, toleranceDiff)
                        PriceValidation.Invalid(expectedPrice, actualPrice, "Price difference exceeds tolerance")
                    }
                }
            } catch (e: Exception) {
                log.error("💀 [세션 가격] 서비스 장애 - sessionId: {}, 보안상 검증 실패", sessionId, e)
                PriceValidation.Error(e)
            }
        } ?: run {
            log.error("⏰ [세션 가격] 타임아웃 - sessionId: {}, 보안상 검증 실패", sessionId)
            PriceValidation.Error(RuntimeException("Session price validation timeout"))
        }
    }

    /**
     * 🛍️ 굿즈 가격 검증 (보안 강화)
     */
    @Cacheable(value = ["goodsPrices"], key = "#goodsId", unless = "#result == null")
    suspend fun validateGoodsPrice(goodsId: UUID, expectedPrice: Int): PriceValidation {
        return withTimeoutOrNull(VALIDATION_TIMEOUT_MS) {
            try {
                log.debug("🔍 [굿즈 가격] 검증 시작 - goodsId: {}, expected: {}", goodsId, expectedPrice)

                val actualPrice = storeValidationClient.getGoodsPrice(goodsId)
                    ?: return@withTimeoutOrNull PriceValidation.Error(
                        RuntimeException("Goods price not found: $goodsId")
                    )

                when {
                    actualPrice == expectedPrice -> {
                        log.info("✅ [굿즈 가격] 검증 성공 - goodsId: {}, price: {}", goodsId, actualPrice)
                        PriceValidation.Valid(actualPrice)
                    }
                    else -> {
                        log.error("❌ [굿즈 가격] 불일치 - goodsId: {}, actual: {}, expected: {}",
                            goodsId, actualPrice, expectedPrice)
                        PriceValidation.Invalid(expectedPrice, actualPrice, "Exact price match required for goods")
                    }
                }
            } catch (e: Exception) {
                log.error("💀 [굿즈 가격] 서비스 장애 - goodsId: {}, 보안상 검증 실패", goodsId, e)
                PriceValidation.Error(e)
            }
        } ?: run {
            log.error("⏰ [굿즈 가격] 타임아웃 - goodsId: {}, 보안상 검증 실패", goodsId)
            PriceValidation.Error(RuntimeException("Goods price validation timeout"))
        }
    }

    /**
     * 🔒 종합 결제 검증 (보안 우선 원칙 적용)
     * 모든 검증이 성공해야만 결제 허용
     */
    suspend fun validatePaymentRequest(
        orderId: UUID,
        userId: Long,
        lineItems: List<EventLineItem>,
        expectedAmount: Int
    ): ValidationResult = coroutineScope {
        val startTime = System.currentTimeMillis()

        try {
            log.info("🔍 [종합 검증] 시작 - orderId: {}, userId: {}, items: {}, amount: {}",
                orderId, userId, lineItems.size, expectedAmount)

            // 굿즈 포함 여부 확인
            val hasGoods = lineItems.any { it.itemType == "GOODS" }

            // 🏠 주소 검증 (굿즈가 있는 경우에만)
            val addressValidation = if (hasGoods) {
                validateUserAddress(userId)
            } else {
                AddressValidation.Valid
            }

            // 💰 가격 검증들을 병렬로 실행
            val priceValidations = lineItems.map { lineItem ->
                async {
                    when (lineItem.itemType) {
                        "SCHEDULE", "RESERVATION" -> {
                            lineItem.scheduleId?.let { scheduleId ->
                                lineItem.unitPrice?.let { unitPrice ->
                                    validateSessionPrice(scheduleId, unitPrice)
                                } ?: PriceValidation.Error(RuntimeException("Unit price missing"))
                            } ?: PriceValidation.Error(RuntimeException("Schedule ID missing"))
                        }
                        "GOODS" -> {
                            lineItem.goodsId?.let { goodsId ->
                                lineItem.unitPrice?.let { unitPrice ->
                                    validateGoodsPrice(goodsId, unitPrice)
                                } ?: PriceValidation.Error(RuntimeException("Unit price missing"))
                            } ?: PriceValidation.Error(RuntimeException("Goods ID missing"))
                        }
                        else -> {
                            log.error("❌ [종합 검증] 알 수 없는 상품 타입 - type: {}", lineItem.itemType)
                            PriceValidation.Error(RuntimeException("Unknown item type: ${lineItem.itemType}"))
                        }
                    }
                }
            }

            // 모든 검증 결과 수집
            val allPriceValidations = priceValidations.map { it.await() }

            // 🛡️ 보안 검증: 모든 검증이 성공해야만 통과
            val addressValid = when (addressValidation) {
                is AddressValidation.Valid -> true
                is AddressValidation.Invalid -> {
                    log.error("💀 [보안] 주소 검증 실패 - 결제 차단")
                    false
                }
                is AddressValidation.Error -> {
                    log.error("💀 [보안] 주소 검증 오류 - 결제 차단", addressValidation.cause)
                    false
                }
            }

            val priceValidationResults = mutableListOf<String>()
            var totalCalculatedAmount = 0
            var allPricesValid = true

            allPriceValidations.forEachIndexed { index, validation ->
                val lineItem = lineItems[index]
                when (validation) {
                    is PriceValidation.Valid -> {
                        val lineTotal = validation.actualPrice * (lineItem.qty ?: 1)
                        totalCalculatedAmount += lineTotal
                        priceValidationResults.add("✅ ${lineItem.itemType}: ${validation.actualPrice}")
                    }
                    is PriceValidation.Invalid -> {
                        log.error("💀 [보안] 가격 검증 실패 - {} 결제 차단: {}",
                            lineItem.itemType, validation.reason)
                        allPricesValid = false
                        priceValidationResults.add("❌ ${lineItem.itemType}: ${validation.reason}")
                    }
                    is PriceValidation.Error -> {
                        log.error("💀 [보안] 가격 검증 오류 - {} 결제 차단", lineItem.itemType, validation.cause)
                        allPricesValid = false
                        priceValidationResults.add("💀 ${lineItem.itemType}: Service error")
                    }
                }
            }

            // 총 금액 검증
            val amountValid = totalCalculatedAmount == expectedAmount
            if (!amountValid) {
                log.error("💀 [보안] 총 금액 불일치 - 결제 차단: calculated={}, expected={}",
                    totalCalculatedAmount, expectedAmount)
                allPricesValid = false
            }

            val processingTime = System.currentTimeMillis() - startTime
            val isValid = addressValid && allPricesValid

            return@coroutineScope if (isValid) {
                log.info("✅ [종합 검증] 성공 - 결제 허용: orderId={}, 처리시간={}ms", orderId, processingTime)
                ValidationResult.Success(
                    addressValid = addressValid,
                    priceValid = allPricesValid,
                    totalAmount = totalCalculatedAmount,
                    processingTimeMs = processingTime,
                    details = priceValidationResults.joinToString(", ")
                )
            } else {
                log.error("💀 [보안] 종합 검증 실패 - 결제 차단: orderId={}, 처리시간={}ms", orderId, processingTime)
                ValidationResult.Failure(
                    reason = "Validation failed: address=$addressValid, price=$allPricesValid",
                    processingTimeMs = processingTime
                )
            }

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("💀 [보안] 종합 검증 시스템 오류 - 결제 차단: orderId={}, 처리시간={}ms",
                orderId, processingTime, e)
            ValidationResult.Failure(
                reason = "System error during validation",
                cause = e,
                processingTimeMs = processingTime
            )
        }
    }

    /**
     * 🎯 간단한 주문 검증 (기본 검증)
     * Order 서비스에서 받은 정보와 결제 요청 금액만 검증
     */
    suspend fun validateBasicPayment(
        orderId: UUID,
        userId: Long,
        expectedAmount: Int,
        orderAmount: Int,
        hasGoods: Boolean = false
    ): ValidationResult = coroutineScope {
        val startTime = System.currentTimeMillis()

        try {
            log.info("🔍 [기본 검증] 시작 - orderId: {}, userId: {}, expected: {}, order: {}",
                orderId, userId, expectedAmount, orderAmount)

            // 1️⃣ 필수: 주문 금액 일치 검증
            if (expectedAmount != orderAmount) {
                val processingTime = System.currentTimeMillis() - startTime
                log.error("💀 [보안] 기본 금액 불일치 - 결제 차단: expected={}, order={}", expectedAmount, orderAmount)
                return@coroutineScope ValidationResult.Failure(
                    reason = "Payment amount mismatch: expected=$expectedAmount, order=$orderAmount",
                    processingTimeMs = processingTime
                )
            }

            // 2️⃣ 주소 검증 (굿즈가 있는 경우에만)
            val addressValid = if (hasGoods) {
                when (val addressValidation = validateUserAddress(userId)) {
                    is AddressValidation.Valid -> true
                    is AddressValidation.Invalid -> {
                        log.error("💀 [보안] 굿즈 주문 시 주소 필수 - 결제 차단")
                        false
                    }
                    is AddressValidation.Error -> {
                        log.error("💀 [보안] 주소 검증 서비스 오류 - 결제 차단", addressValidation.cause)
                        false
                    }
                }
            } else {
                true
            }

            val processingTime = System.currentTimeMillis() - startTime
            val isValid = addressValid

            return@coroutineScope if (isValid) {
                log.info("✅ [기본 검증] 성공 - 결제 허용: orderId={}, 처리시간={}ms", orderId, processingTime)
                ValidationResult.Success(
                    addressValid = addressValid,
                    priceValid = true, // 기본 금액 검증은 이미 통과
                    totalAmount = expectedAmount,
                    processingTimeMs = processingTime
                )
            } else {
                log.error("💀 [보안] 기본 검증 실패 - 결제 차단: orderId={}, 처리시간={}ms", orderId, processingTime)
                ValidationResult.Failure(
                    reason = "Basic validation failed: address validation required for goods orders",
                    processingTimeMs = processingTime
                )
            }

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            log.error("💀 [보안] 기본 검증 시스템 오류 - 결제 차단: orderId={}, 처리시간={}ms",
                orderId, processingTime, e)
            ValidationResult.Failure(
                reason = "System error during basic validation",
                cause = e,
                processingTimeMs = processingTime
            )
        }
    }
}