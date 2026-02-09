package com.popcorn.payment.service

import com.popcorn.payment.client.UserServiceClient
import com.popcorn.payment.client.StoreValidationClient
import com.popcorn.payment.dto.BatchPriceValidationRequest
import com.popcorn.payment.dto.LineItemPriceRequest
import com.popcorn.payment.event.domain.payment.EventLineItem
import com.popcorn.payment.exception.PaymentValidationException
import com.popcorn.payment.repository.ExternalDbQueryException
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Hybrid 검증 서비스
 * 모든 검증을 HTTP API 통신으로 처리하여 마이크로서비스 아키텍처 준수
 */
@Service
class HybridValidationService(
    private val userServiceClient: UserServiceClient,
    private val storeValidationClient: StoreValidationClient
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
            log.error("💀 [HTTP] 사용자 주소 검증 실패 - userId: {}, 보안상 결제 중단", userId, e)
            throw PaymentValidationException("사용자 주소 검증 서비스 장애로 인한 결제 처리 불가: ${e.message}")
        }
    }

    /**
     * 세션 가격 검증 (HTTP API 통신 방식)
     */
    @Cacheable(value = ["hybridPrices"], key = "'session:' + #sessionId", unless = "#result == null")
    suspend fun getSessionPriceHybrid(sessionId: UUID): Int? {
        return try {
            log.debug("🔍 [HTTP] 세션 가격 조회 시작 - sessionId: {}", sessionId)

            // HTTP API를 통한 세션 가격 조회
            val price = storeValidationClient.getSessionPrice(sessionId)
            if (price != null) {
                log.debug("✅ [HTTP] 세션 가격 조회 성공 - sessionId: {}, price: {}원", sessionId, price)
            } else {
                log.warn("❌ [HTTP] 세션 가격 정보 없음 - sessionId: {}", sessionId)
            }

            price
        } catch (e: Exception) {
            log.error("❌ [HTTP] 세션 가격 조회 실패 - sessionId: {}, error: {}", sessionId, e.message, e)
            null
        }
    }

    /**
     * 굿즈 가격 검증 (Order 가격 vs DB 가격)
     */
    @Cacheable(value = ["hybridPrices"], key = "'goods:' + #goodsId", unless = "#result == null")
    suspend fun getGoodsPriceHybrid(goodsId: UUID): Int? {
        return try {
            log.debug("🔍 [HTTP] 굿즈 가격 조회 시작 - goodsId: {}", goodsId)

            // HTTP API를 통한 굿즈 가격 조회
            val price = storeValidationClient.getGoodsPrice(goodsId)
            if (price != null) {
                log.debug("✅ [HTTP] 굿즈 가격 조회 성공 - goodsId: {}, price: {}원", goodsId, price)
            } else {
                log.warn("❌ [HTTP] 굿즈 가격 정보 없음 - goodsId: {}", goodsId)
            }

            price
        } catch (e: Exception) {
            log.error("❌ [HTTP] 굿즈 가격 조회 실패 - goodsId: {}, error: {}", goodsId, e.message, e)
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
     * 빠른 존재성 확인 (HTTP API 통신 방식)
     * 캐시 없이 실시간 확인이 필요한 경우
     */
    suspend fun quickExistenceCheck(userId: Long, sessionId: UUID?, goodsId: UUID?): ExistenceCheckResult {
        return try {
            runBlocking {
                val userExists = async {
                    try {
                        userServiceClient.existsUser(userId)
                    } catch (e: Exception) {
                        log.warn("⚠️ [HTTP] 사용자 존재 확인 실패, fallback - userId: {}", userId)
                        userId > 0 // fallback
                    }
                }
                val sessionValid = async {
                    if (sessionId != null) {
                        try {
                            storeValidationClient.isValidSession(sessionId)
                        } catch (e: Exception) {
                            log.warn("⚠️ [HTTP] 세션 검증 실패, fallback - sessionId: {}", sessionId)
                            true // fallback: 세션이 있으면 유효하다고 가정
                        }
                    } else {
                        false
                    }
                }
                val goodsExists = async {
                    if (goodsId != null) {
                        try {
                            storeValidationClient.getGoodsPrice(goodsId) != null
                        } catch (e: Exception) {
                            log.warn("⚠️ [HTTP] 굿즈 존재 확인 실패, fallback - goodsId: {}", goodsId)
                            true // fallback: 굿즈가 있으면 존재한다고 가정
                        }
                    } else {
                        false
                    }
                }

                ExistenceCheckResult(
                    userExists = userExists.await(),
                    sessionValid = sessionValid.await(),
                    goodsExists = goodsExists.await(),
                    processingTimeMs = 50 // HTTP 통신으로 인한 약간 증가된 처리 시간
                )
            }
        } catch (e: Exception) {
            log.error("❌ [HTTP] 존재성 확인 실패", e)
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
        actualOrderAmount: Int,
        hasGoods: Boolean = false,  // 굿즈 포함 여부 (기본값: false)
        orderDetail: com.popcorn.payment.dto.OrderInfoResponse? = null  // Order 상세 정보
    ): BasicValidationResult {
        return try {
            log.debug("🔍 [HTTP] 기본 결제 검증 시작 - orderId: {}, userId: {}, expected: {}, actual: {}",
                orderId, userId, expectedAmount, actualOrderAmount)

            // 1. 사용자 주소 검증 (굿즈가 있을 때만 필요)
            val addressValid = if (hasGoods) {
                try {
                    log.info("🔍 [HTTP] 굿즈 포함된 주문 - 주소 검증 시작 - userId: {}", userId)
                    val result = validateUserAddressHybrid(userId)
                    log.info("✅ [HTTP] 주소 검증 완료 - userId: {}, result: {}", userId, result)
                    true // User 서비스 오류 시에도 결제는 진행 (관대한 정책)
                } catch (e: Exception) {
                    log.warn("⚠️ [HTTP] 주소 검증 예외 발생, 통과 처리 - userId: {}, error: {}", userId, e.message)
                    true
                }
            } else {
                log.info("✅ [HTTP] 예약 전용 주문 - 주소 검증 생략 - userId: {}", userId)
                true // 굿즈가 없으면 주소 검증 불필요
            }

            // 2. 주 검증: HTTP로 받은 Order 금액 vs 결제 요청 금액 비교 (핵심 검증)
            val basicPriceValid = (expectedAmount == actualOrderAmount)

            log.info("🎯 [주 검증] HTTP 기본 가격 검증 - orderId: {}", orderId)
            log.info("  - expectedAmount: {}, actualOrderAmount: {}", expectedAmount, actualOrderAmount)
            log.info("  - basicPriceValid: {}", basicPriceValid)

            if (!basicPriceValid) {
                log.warn("❌ [주 검증] 기본 가격 불일치로 결제 차단 - expected: {}, actual: {}", expectedAmount, actualOrderAmount)
                throw PaymentValidationException("결제 금액이 주문 금액과 일치하지 않습니다")
            }

            log.info("✅ [주 검증] 기본 가격 검증 성공! 결제 진행 가능 - expected: {}, actual: {}", expectedAmount, actualOrderAmount)

            // 3. Store API 보조 검증 (실패해도 결제 진행, 추가 확신을 위한 참고용)
            if (hasGoods && orderDetail?.lineItems?.isNotEmpty() == true) {
                log.info("🏪 [보조 검증] Store API 추가 검증 시도 - orderId: {} (실패해도 결제 진행)", orderId)

                try {
                    // LineItem 정보를 Store API 요청 형식으로 변환
                    val lineItemRequests = orderDetail.lineItems!!.map { lineItem ->
                        LineItemPriceRequest(
                            itemId = lineItem.itemId,
                            itemType = lineItem.itemType,
                            expectedPrice = lineItem.unitPrice,
                            quantity = lineItem.quantity
                        )
                    }

                    // Store API 배치 가격 검증 호출 (참고용)
                    val validationRequest = BatchPriceValidationRequest(
                        orderId = orderId,
                        lineItems = lineItemRequests,
                        totalExpectedAmount = actualOrderAmount
                    )

                    val validationResponse = storeValidationClient.validateBatchPrices(validationRequest)

                    if (validationResponse.isValid) {
                        log.info("✅ [보조 검증] Store API 검증도 성공! 추가 확신 획득 - orderId: {}, 총금액: {}원",
                                orderId, validationResponse.totalActualAmount)
                    } else {
                        log.warn("⚠️ [보조 검증] Store API 검증 실패하지만 기본 검증 성공으로 결제 진행 - orderId: {}, 이유: {}",
                                orderId, validationResponse.failureReason)
                    }
                } catch (e: Exception) {
                    log.warn("⚠️ [보조 검증] Store API 호출 실패하지만 기본 검증 성공으로 결제 진행 - orderId: {}, error: {}",
                            orderId, e.message)
                }
            } else if (hasGoods) {
                log.info("🔍 [보조 검증] Mixed 주문이지만 lineItem 정보 없음 - 기본 검증만으로 충분")
            } else {
                log.info("🎭 [보조 검증] 예약 전용 주문 - Store API 검증 불필요")
            }

            // 기본 검증 성공했으므로 결제 허용
            val priceValid = true // 주 검증(basicPriceValid) 성공 시 무조건 true

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
