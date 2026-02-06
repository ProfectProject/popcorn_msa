package com.popcorn.payment.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.common.dto.PassportEnvelope
import com.popcorn.common.dto.PassportPayload
import com.popcorn.common.dto.PassportUser
import com.popcorn.common.util.HmacUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 시스템 간 내부 호출을 위한 Passport 토큰 생성기
 *
 * Gateway를 통한 서비스 간 통신에서 인증을 위해 사용
 */
@Component
class SystemPassportGenerator(
    @Value("\${passport.secret}")
    private val passportSecret: String
) {
    private val objectMapper = ObjectMapper()

    /**
     * 시스템 수준의 Passport 토큰 생성
     */
    fun generateSystemPassport(): String {
        val now = Instant.now().epochSecond
        val exp = now + 300 // 5분 유효

        // 시스템 사용자 정보
        val systemUser = PassportUser(
            "0",
            "payment-service@internal",
            "SYSTEM"
        )

        val payload = PassportPayload(
            systemUser,
            now,
            exp
        )

        // payload를 JSON으로 직렬화
        val payloadJson = objectMapper.writeValueAsString(payload)

        // HMAC 서명 생성
        val userIntegrity = HmacUtil.hmacSha256Base64Url(passportSecret, payloadJson)

        // Passport envelope 생성
        val envelope = PassportEnvelope(payload, userIntegrity)

        // 최종 passport JSON 반환
        return objectMapper.writeValueAsString(envelope)
    }
}