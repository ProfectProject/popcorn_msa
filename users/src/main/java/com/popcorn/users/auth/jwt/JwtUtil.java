package com.popcorn.users.auth.jwt;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

// jwt 발급, 검증 담당 클래스 - 성능 최적화 버전
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final JwtParser jwtParser;  // 🚀 성능 개선: parser 재사용

    // 🚀 성능 개선: Claims 캐싱 (같은 토큰에 대한 중복 파싱 방지)
    private final ConcurrentHashMap<String, Claims> claimsCache = new ConcurrentHashMap<>();
    private final long CACHE_TTL = 60000; // 1분 캐시 TTL

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] byteSecretKey = Decoders.BASE64.decode(secret);
        secretKey = Keys.hmacShaKeyFor(byteSecretKey);

        // 🚀 성능 개선: parser를 미리 생성하여 재사용
        jwtParser = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build();
    }

    // 🚀 성능 개선: 캐싱된 Claims 조회
    private Claims getClaims(String token) {
        // 캐시에서 조회 시도
        Claims cached = claimsCache.get(token);
        if (cached != null) {
            // 만료 여부 확인
            if (cached.getExpiration().after(new Date())) {
                return cached;
            } else {
                claimsCache.remove(token); // 만료된 캐시 제거
            }
        }

        // 캐시 미스 시 새로 파싱
        Claims claims = jwtParser.parseClaimsJws(token).getBody();

        // 캐시 크기 제한 (메모리 보호)
        if (claimsCache.size() < 1000) {
            claimsCache.put(token, claims);
        }

        return claims;
    }

    public Long getUserId(String token) {
        return getClaims(token).get("id", Long.class);
    }

    public String getUsername(String token) {
        return getClaims(token).get("email", String.class);
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Boolean isExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    // 🚀 성능 개선: 더 효율적인 JWT 생성
    public String createJwt(Long userId, String email, String role, Long expiredMs) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expiredMs))
                .claim("id", userId)
                .claim("email", email)
                .claim("role", role)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // 🚀 성능 개선: 더 효율적인 Refresh JWT 생성
    public String createRefreshJwt(Long userId, String email, String role, Long expiredMs) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expiredMs))
                .claim("id", userId)
                .claim("email", email)
                .claim("role", role)
                .claim("type", "refresh")
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
