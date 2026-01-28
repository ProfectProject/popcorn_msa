package com.popcorn.common.util;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.common.dto.PassportEnvelope;
import com.popcorn.common.dto.PassportPayload;

public class PassportVerifier {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static PassportPayload verify(
            String passportJson,
            String secret
    ) {
        try {
            PassportEnvelope envelope =
                    mapper.readValue(passportJson, PassportEnvelope.class);

            String payloadJson =
                    mapper.writeValueAsString(envelope.payload());

            String expected =
                    HmacUtil.hmacSha256Base64Url(secret, payloadJson);

            // 디버그 로그 추가
            System.out.println("🔍 PassportVerifier - payloadJson: " + payloadJson);
            System.out.println("🔍 PassportVerifier - expected: " + expected);
            System.out.println("🔍 PassportVerifier - actual: " + envelope.userIntegrity());

            if (!expected.equals(envelope.userIntegrity())) {
                throw new SecurityException("Passport integrity mismatch");
            }

            long now = Instant.now().getEpochSecond();
            long exp = envelope.payload().exp();
            System.out.println("🔍 PassportVerifier - now: " + now + ", exp: " + exp + ", valid: " + (exp >= now));

            if (exp < now) {
                throw new SecurityException("Passport expired");
            }

            return envelope.payload();

        } catch (Exception e) {
            throw new SecurityException("Invalid passport", e);
        }
    }
}
