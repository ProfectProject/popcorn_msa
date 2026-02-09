package com.popcorn.checkIns.util;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.common.dto.PassportEnvelope;
import com.popcorn.common.dto.PassportPayload;
import com.popcorn.common.dto.PassportUser;
import com.popcorn.common.util.HmacUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CheckIns 서비스용 시스템 Passport 토큰 생성기
 *
 * Order 서비스 API 호출 시 인증을 위해 사용
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SystemPassportGenerator {

    @Value("${passport.secret}")
    private String passportSecret;

    private final ObjectMapper objectMapper;

    /**
     * 시스템 수준의 Passport 토큰 생성
     */
    public String generateSystemPassport() {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = now + 300; // 5분 유효

            // CheckIns 시스템 사용자 정보
            PassportUser systemUser = new PassportUser(
                "0",
                "checkins-service@internal",
                "SYSTEM"
            );

            PassportPayload payload = new PassportPayload(
                systemUser,
                now,
                exp
            );

            // payload를 JSON으로 직렬화
            String payloadJson = objectMapper.writeValueAsString(payload);

            // HMAC 서명 생성
            String userIntegrity = HmacUtil.hmacSha256Base64Url(passportSecret, payloadJson);

            // Passport envelope 생성
            PassportEnvelope envelope = new PassportEnvelope(payload, userIntegrity);

            // 최종 passport JSON 반환
            return objectMapper.writeValueAsString(envelope);

        } catch (JsonProcessingException e) {
            log.error("❌ [PASSPORT] 시스템 Passport 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("시스템 Passport 생성 실패", e);
        }
    }
}