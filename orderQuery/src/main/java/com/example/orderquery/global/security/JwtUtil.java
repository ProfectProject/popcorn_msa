package com.example.orderquery.global.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String DEFAULT_JWT_SECRET_BASE64 =
            "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItbG9jYWwtZGV2ZWxvcG1lbnQtb25seS1kb25vdC11c2UtaW4tcHJvZHVjdGlvbg==";

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        String effectiveSecret = StringUtils.hasText(secret) ? secret : DEFAULT_JWT_SECRET_BASE64;
        SecretKey key;
        try {
            byte[] byteSecretKey = Decoders.BASE64.decode(effectiveSecret);
            key = Keys.hmacShaKeyFor(byteSecretKey);
        } catch (Exception e) {
            log.warn("Invalid jwt.secret detected, fallback to default local secret");
            byte[] byteSecretKey = Decoders.BASE64.decode(DEFAULT_JWT_SECRET_BASE64);
            key = Keys.hmacShaKeyFor(byteSecretKey);
        }
        this.secretKey = key;
    }

    public Long getUserId(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("id", Long.class);
    }

    public String getRole(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
    }

    public boolean isExpired(String token) {
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        return expiration != null && expiration.before(new Date());
    }
}
