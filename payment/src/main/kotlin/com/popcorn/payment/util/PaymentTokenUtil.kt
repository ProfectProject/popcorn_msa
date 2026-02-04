@file:Suppress("DEPRECATION")

package com.popcorn.payment.util

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

/**
 * 결제 토큰 JWT 파싱 유틸리티 (기존 backend 호환)
 *
 * JWT HS256 방식으로 암호화된 결제 정보를 안전하게 복호화
 * Order 서비스의 PaymentTokenUtil과 동일한 방식 사용
 */
@Component
class PaymentTokenUtil {

    @Value("\${payment.token.secret:\${jwt.secret}}")
    private lateinit var secretKey: String

    private val log = LoggerFactory.getLogger(PaymentTokenUtil::class.java)

    /**
     * JWT 토큰을 복호화하여 결제 정보 반환
     */
    fun decryptPaymentToken(token: String): Map<String, Any> {
        try {
            log.info("🔓 JWT 결제 토큰 복호화 시작 - 토큰 길이: {}자", token.length)

            val claims = Jwts.parser()
                .setSigningKey(secretKey.toByteArray(Charsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .body

            // 축약된 필드명에서 원본 필드명으로 변환
            val orderIdValue = claims.get("i", String::class.java)
            val paymentIdValue = claims.get("p", String::class.java)

            val paymentData = mutableMapOf<String, Any>()
            paymentData["orderId"] = orderIdValue ?: ""
            paymentData["orderNo"] = claims.get("o", String::class.java) ?: ""
            paymentData["amount"] = claims.get("a", Integer::class.java) ?: 0
            paymentData["customerKey"] = claims.get("c", String::class.java) ?: ""
            paymentData["paymentId"] = paymentIdValue ?: ""
            // URL은 프론트엔드에서 하드코딩으로 처리
            paymentData["successUrl"] = "http://localhost:3000/payments/success"
            paymentData["failUrl"] = "http://localhost:3000/payments/fail"

            log.info("✅ JWT 결제 토큰 복호화 완료 - 주문번호: {}, 금액: {}원",
                paymentData["orderNo"], paymentData["amount"])

            return paymentData

        } catch (e: Exception) {
            val tokenPreview = if (token.length > 20) token.substring(0, 20) + "..." else token
            log.error("💥 JWT 결제 토큰 복호화 실패 - 토큰: {}, 에러: {}", tokenPreview, e.message, e)
            throw RuntimeException("결제 토큰 복호화에 실패했습니다", e)
        }
    }

    /**
     * JWT 토큰 검증 (만료시간 자동 검증)
     */
    fun validatePaymentToken(paymentData: Map<String, Any>): Boolean {
        try {
            // JWT는 자체적으로 만료시간을 검증하므로, 필수 필드만 확인
            val requiredFields = listOf("orderId", "orderNo", "amount", "customerKey")
            for (field in requiredFields) {
                if (!paymentData.containsKey(field) || paymentData[field] == null) {
                    log.warn("⚠️ 필수 필드 누락: {}", field)
                    return false
                }
            }

            log.debug("✅ JWT 결제 토큰 검증 통과 - 주문번호: {}", paymentData["orderNo"])
            return true

        } catch (e: Exception) {
            log.error("💥 JWT 결제 토큰 검증 실패: {}", e.message, e)
            return false
        }
    }

    /**
     * JWT 토큰 자체 유효성 검증
     */
    fun isValidToken(token: String): Boolean {
        return try {
            Jwts.parser()
                .setSigningKey(secretKey.toByteArray(Charsets.UTF_8))
                .build()
                .parseClaimsJws(token)
            true
        } catch (e: Exception) {
            log.warn("⚠️ JWT 토큰 유효성 검증 실패: {}", e.message)
            false
        }
    }

    /**
     * 결제 URL용 JWT 토큰 생성
     */
    fun generatePaymentToken(orderId: String, orderNo: String, amount: Int, customerKey: String): String {
        try {
            val now = Date()
            val expiry = Date(now.time + 30 * 60 * 1000) // 30분 만료

            val token = Jwts.builder()
                .setSubject("p") // "payment" → "p"
                .setIssuedAt(now)
                .setExpiration(expiry)
                .claim("i", orderId) // "orderId" → "i"
                .claim("o", orderNo) // "orderNo" → "o"
                .claim("a", amount) // "amount" → "a"
                .claim("c", customerKey) // "customerKey" → "c"
                .signWith(SignatureAlgorithm.HS256, secretKey.toByteArray(Charsets.UTF_8))
                .compact()

            log.info("💳 JWT 결제 URL 토큰 생성 완료 - 주문번호: {}, 토큰 길이: {}자", orderNo, token.length)
            return token

        } catch (e: Exception) {
            log.error("💥 JWT 결제 URL 토큰 생성 실패 - 주문번호: {}, 에러: {}", orderNo, e.message, e)
            throw RuntimeException("결제 URL 토큰 생성에 실패했습니다", e)
        }
    }
}
