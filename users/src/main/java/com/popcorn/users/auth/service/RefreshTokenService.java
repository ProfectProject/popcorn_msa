package com.popcorn.users.auth.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    public void saveRefreshToken(Long userId, String refreshToken, long ttlMillis) {
        String key = buildKey(userId);
        stringRedisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(ttlMillis));
        log.info("Saved refresh token. key={}, ttl={}s", key, stringRedisTemplate.getExpire(key));
    }

    public boolean isRefreshTokenValid(Long userId, String refreshToken) {
        String key = buildKey(userId);
        String stored = stringRedisTemplate.opsForValue().get(key);

        // 디버깅 로그 추가
        log.info("🔍 Refresh token validation - userId: {}, key: {}", userId, key);
        log.info("🔍 Request token length: {}", refreshToken != null ? refreshToken.length() : 0);
        log.info("🔍 Stored token length: {}", stored != null ? stored.length() : 0);
        log.info("🔍 Tokens equal: {}", refreshToken != null && refreshToken.equals(stored));

        if (stored == null) {
            log.warn("⚠️ No refresh token found in Redis for userId: {}, key: {}", userId, key);
        }
        if (refreshToken == null) {
            log.warn("⚠️ Request refresh token is null for userId: {}", userId);
        }

        return refreshToken != null && refreshToken.equals(stored);
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
