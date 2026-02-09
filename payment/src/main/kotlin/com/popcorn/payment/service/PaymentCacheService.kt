package com.popcorn.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.dto.TossPaymentConfirmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 🚀 고성능 결제 캐싱 서비스
 * - 결제 결과 캐싱으로 중복 요청 시 즉시 응답
 * - Redis 기반 분산 캐싱
 * - TTL 기반 자동 만료
 */
@Service
class PaymentCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(PaymentCacheService::class.java)

    companion object {
        private const val PAYMENT_RESULT_PREFIX = "payment:result:"
        private const val PAYMENT_VALIDATION_PREFIX = "payment:validation:"
        private val RESULT_TTL = Duration.ofMinutes(30)      // 결제 결과: 30분
        private val VALIDATION_TTL = Duration.ofMinutes(5)    // 검증 결과: 5분
    }

    /**
     * ⚡ 결제 결과 캐시 조회 (초고속)
     */
    suspend fun getCachedPaymentResult(paymentKey: String): TossPaymentConfirmResult? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheKey = "$PAYMENT_RESULT_PREFIX$paymentKey"
                val cachedValue = redisTemplate.opsForValue().get(cacheKey)

                if (cachedValue != null) {
                    log.debug("🎯 캐시 HIT - paymentKey: {}", paymentKey)
                    objectMapper.readValue(cachedValue, TossPaymentConfirmResult::class.java)
                } else {
                    log.debug("❌ 캐시 MISS - paymentKey: {}", paymentKey)
                    null
                }
            } catch (e: Exception) {
                log.warn("⚠️ 캐시 조회 실패 - paymentKey: {}, error: {}", paymentKey, e.message)
                null
            }
        }
    }

    /**
     * 💾 결제 결과 캐시 저장
     */
    suspend fun cachePaymentResult(paymentKey: String, result: TossPaymentConfirmResult) {
        withContext(Dispatchers.IO) {
            try {
                val cacheKey = "$PAYMENT_RESULT_PREFIX$paymentKey"
                val jsonValue = objectMapper.writeValueAsString(result)

                redisTemplate.opsForValue().set(cacheKey, jsonValue, RESULT_TTL)
                log.debug("💾 결제 결과 캐싱 완료 - paymentKey: {}", paymentKey)
            } catch (e: Exception) {
                log.warn("⚠️ 캐시 저장 실패 - paymentKey: {}, error: {}", paymentKey, e.message)
            }
        }
    }

    /**
     * ⚡ 주문 검증 결과 캐시 조회
     */
    suspend fun getCachedValidationResult(orderId: String): Boolean? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheKey = "$PAYMENT_VALIDATION_PREFIX$orderId"
                val cached = redisTemplate.opsForValue().get(cacheKey)

                when (cached) {
                    "true" -> {
                        log.debug("🎯 검증 캐시 HIT (성공) - orderId: {}", orderId)
                        true
                    }
                    "false" -> {
                        log.debug("🎯 검증 캐시 HIT (실패) - orderId: {}", orderId)
                        false
                    }
                    else -> {
                        log.debug("❌ 검증 캐시 MISS - orderId: {}", orderId)
                        null
                    }
                }
            } catch (e: Exception) {
                log.warn("⚠️ 검증 캐시 조회 실패 - orderId: {}, error: {}", orderId, e.message)
                null
            }
        }
    }

    /**
     * 💾 주문 검증 결과 캐시 저장
     */
    suspend fun cacheValidationResult(orderId: String, isValid: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val cacheKey = "$PAYMENT_VALIDATION_PREFIX$orderId"
                redisTemplate.opsForValue().set(cacheKey, isValid.toString(), VALIDATION_TTL)
                log.debug("💾 검증 결과 캐싱 완료 - orderId: {}, valid: {}", orderId, isValid)
            } catch (e: Exception) {
                log.warn("⚠️ 검증 캐시 저장 실패 - orderId: {}, error: {}", orderId, e.message)
            }
        }
    }

    /**
     * 🗑️ 결제 관련 캐시 삭제
     */
    suspend fun evictPaymentCache(paymentKey: String, orderId: String) {
        withContext(Dispatchers.IO) {
            try {
                val paymentCacheKey = "$PAYMENT_RESULT_PREFIX$paymentKey"
                val validationCacheKey = "$PAYMENT_VALIDATION_PREFIX$orderId"

                redisTemplate.delete(setOf(paymentCacheKey, validationCacheKey))
                log.debug("🗑️ 캐시 삭제 완료 - paymentKey: {}, orderId: {}", paymentKey, orderId)
            } catch (e: Exception) {
                log.warn("⚠️ 캐시 삭제 실패 - paymentKey: {}, orderId: {}, error: {}",
                    paymentKey, orderId, e.message)
            }
        }
    }

    /**
     * 📊 캐시 통계 정보
     */
    suspend fun getCacheStats(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                val resultKeys = redisTemplate.keys("$PAYMENT_RESULT_PREFIX*").size
                val validationKeys = redisTemplate.keys("$PAYMENT_VALIDATION_PREFIX*").size

                mapOf<String, Any>(
                    "paymentResultCacheCount" to resultKeys,
                    "validationCacheCount" to validationKeys,
                    "resultTtlMinutes" to RESULT_TTL.toMinutes(),
                    "validationTtlMinutes" to VALIDATION_TTL.toMinutes()
                )
            } catch (e: Exception) {
                log.warn("⚠️ 캐시 통계 조회 실패: {}", e.message)
                mapOf<String, Any>("error" to (e.message ?: "Unknown error"))
            }
        }
    }
}