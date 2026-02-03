package com.popcorn.order.service.impl;

import com.popcorn.order.dto.idempotency.IdempotencyKeyResponse;
import com.popcorn.order.dto.idempotency.IdempotencyKeyValidationResponse;
import com.popcorn.order.dto.idempotency.IdempotencyStatisticsResponse;
import com.popcorn.order.service.util.OrderIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 멱등성 관리 서비스 구현체
 *
 * Redis를 사용한 멱등성 키 관리 시스템입니다.
 * 고성능 중복 요청 방지와 분산 환경에서의 안전한 멱등성을 보장합니다.
 *
 * Redis 키 구조:
 * - idempotency:key:{키} : 멱등성 키 메타데이터
 * - idempotency:result:{키} : 처리 결과 캐시
 * - idempotency:stats:{날짜} : 일별 통계
 * - idempotency:user:{사용자ID} : 사용자별 활성 키
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderIdempotencyServiceImpl implements OrderIdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "idempotency:key:";
    private static final String RESULT_PREFIX = "idempotency:result:";
    private static final String STATS_PREFIX = "idempotency:stats:";
    private static final String USER_PREFIX = "idempotency:user:";

    private static final int DEFAULT_KEY_TTL_MINUTES = 60; // 키 기본 만료 시간
    private static final int DEFAULT_RESULT_TTL_MINUTES = 30; // 결과 기본 보관 시간

    @Override
    public IdempotencyKeyResponse generateKey() {
        // 고유한 멱등성 키 생성
        String idempotencyKey = generateUniqueKey();

        // 키 메타데이터 생성
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(DEFAULT_KEY_TTL_MINUTES);

        IdempotencyKeyResponse keyResponse = IdempotencyKeyResponse.builder()
                .key(idempotencyKey)
                .createdAt(now)
                .expiresAt(expiresAt)
                .status("GENERATED")
                .usageCount(0)
                .build();

        // Redis에 저장
        String redisKey = KEY_PREFIX + idempotencyKey;
        Map<String, Object> keyData = new HashMap<>();
        keyData.put("key", idempotencyKey);
        keyData.put("createdAt", now.toString());
        keyData.put("expiresAt", expiresAt.toString());
        keyData.put("status", "GENERATED");
        keyData.put("usageCount", 0);

        redisTemplate.opsForHash().putAll(redisKey, keyData);
        redisTemplate.expire(redisKey, DEFAULT_KEY_TTL_MINUTES, TimeUnit.MINUTES);

        // 일일 통계 업데이트
        updateDailyStats("keysGenerated", 1);

        log.info("멱등성 키 생성 완료: {}", idempotencyKey);
        return keyResponse;
    }

    @Override
    public IdempotencyKeyValidationResponse validateAndUseKey(String idempotencyKey, String requestId, Long userId) {
        String redisKey = KEY_PREFIX + idempotencyKey;

        try {
            // Redis에서 키 정보 조회
            Map<Object, Object> keyData = redisTemplate.opsForHash().entries(redisKey);

            if (keyData.isEmpty()) {
                log.warn("존재하지 않는 멱등성 키: {}", idempotencyKey);
                return IdempotencyKeyValidationResponse.invalidKey(idempotencyKey, requestId, "존재하지 않는 키");
            }

            // 키 만료 확인
            LocalDateTime expiresAt = LocalDateTime.parse((String) keyData.get("expiresAt"));
            if (LocalDateTime.now().isAfter(expiresAt)) {
                log.warn("만료된 멱등성 키: {}", idempotencyKey);
                return IdempotencyKeyValidationResponse.invalidKey(idempotencyKey, requestId, "만료된 키");
            }

            String status = (String) keyData.get("status");
            Integer usageCount = (Integer) keyData.get("usageCount");

            // 중복 요청 확인
            if ("USED".equals(status)) {
                Object previousResult = getPreviousResult(idempotencyKey);
                LocalDateTime previousTime = keyData.get("usedAt") != null ?
                        LocalDateTime.parse((String) keyData.get("usedAt")) : null;

                log.info("중복 요청 감지 - 키: {}, 이전 사용: {}", idempotencyKey, previousTime);

                // 중복 요청 통계 업데이트
                updateDailyStats("duplicateRequests", 1);

                return IdempotencyKeyValidationResponse.duplicateRequest(
                        idempotencyKey, requestId, userId, previousResult, previousTime);
            }

            // 키 사용 처리
            keyData.put("status", "USED");
            keyData.put("usedAt", LocalDateTime.now().toString());
            keyData.put("requestId", requestId);
            keyData.put("userId", userId != null ? userId : 0L);
            keyData.put("usageCount", (usageCount != null ? usageCount : 0) + 1);

            redisTemplate.opsForHash().putAll(redisKey, keyData);

            log.info("멱등성 키 사용 처리 완료 - 키: {}, 요청ID: {}, 사용자: {}", idempotencyKey, requestId, userId);

            // 키 사용 통계 업데이트
            updateDailyStats("keysUsed", 1);

            return IdempotencyKeyValidationResponse.validNewRequest(idempotencyKey, requestId, userId);

        } catch (Exception e) {
            log.error("멱등성 키 검증 중 오류 발생: {}", idempotencyKey, e);
            return IdempotencyKeyValidationResponse.invalidKey(idempotencyKey, requestId, "검증 오류: " + e.getMessage());
        }
    }

    @Override
    public Object getPreviousResult(String idempotencyKey) {
        String resultKey = RESULT_PREFIX + idempotencyKey;
        return redisTemplate.opsForValue().get(resultKey);
    }

    @Override
    public void storeResult(String idempotencyKey, Object result, int ttlMinutes) {
        String resultKey = RESULT_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(resultKey, result, ttlMinutes, TimeUnit.MINUTES);
        log.debug("멱등성 키 결과 저장 완료: {} ({}분 보관)", idempotencyKey, ttlMinutes);
    }

    @Override
    public void invalidateKey(String idempotencyKey, String reason) {
        String redisKey = KEY_PREFIX + idempotencyKey;

        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "FAILED");
            updates.put("failedAt", LocalDateTime.now().toString());
            updates.put("failureReason", reason);

            redisTemplate.opsForHash().putAll(redisKey, updates);

            // 결과 캐시도 삭제
            String resultKey = RESULT_PREFIX + idempotencyKey;
            redisTemplate.delete(resultKey);

            // 실패 통계 업데이트
            updateDailyStats("keysFailed", 1);

            log.info("멱등성 키 무효화 완료: {} - 사유: {}", idempotencyKey, reason);

        } catch (Exception e) {
            log.error("멱등성 키 무효화 중 오류 발생: {}", idempotencyKey, e);
        }
    }

    @Override
    public IdempotencyStatisticsResponse getStatistics(LocalDateTime startTime, LocalDateTime endTime, String period) {
        try {
            // 기본 통계 수집
            Long totalGenerated = 0L;
            Long totalUsed = 0L;
            Long totalExpired = 0L;
            Long totalFailed = 0L;
            Long totalDuplicates = 0L;

            // 날짜별 통계 수집
            LocalDateTime currentDate = startTime.toLocalDate().atStartOfDay();
            while (!currentDate.isAfter(endTime)) {
                String dateKey = STATS_PREFIX + currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                Map<Object, Object> dailyStats = redisTemplate.opsForHash().entries(dateKey);

                totalGenerated += getLongValue(dailyStats, "keysGenerated");
                totalUsed += getLongValue(dailyStats, "keysUsed");
                totalExpired += getLongValue(dailyStats, "keysExpired");
                totalFailed += getLongValue(dailyStats, "keysFailed");
                totalDuplicates += getLongValue(dailyStats, "duplicateRequests");

                currentDate = currentDate.plusDays(1);
            }

            // 비율 계산
            double usageRate = totalGenerated > 0 ? (double) totalUsed / totalGenerated * 100 : 0.0;
            double duplicateRate = totalUsed > 0 ? (double) totalDuplicates / totalUsed * 100 : 0.0;

            return IdempotencyStatisticsResponse.builder()
                    .startTime(startTime)
                    .endTime(endTime)
                    .period(period)
                    .totalKeysGenerated(totalGenerated)
                    .totalKeysUsed(totalUsed)
                    .totalKeysExpired(totalExpired)
                    .totalKeysFailed(totalFailed)
                    .totalDuplicateRequests(totalDuplicates)
                    .keyUsageRate(usageRate)
                    .duplicateRequestRate(duplicateRate)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("멱등성 통계 조회 중 오류 발생", e);
            return IdempotencyStatisticsResponse.builder()
                    .startTime(startTime)
                    .endTime(endTime)
                    .period(period)
                    .totalKeysGenerated(0L)
                    .generatedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public int cleanupExpiredKeys() {
        int cleanedCount = 0;

        try {
            // 만료된 키 패턴 검색
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            LocalDateTime now = LocalDateTime.now();

            for (String redisKey : keys) {
                Map<Object, Object> keyData = redisTemplate.opsForHash().entries(redisKey);
                if (keyData.isEmpty()) continue;

                String expiresAtStr = (String) keyData.get("expiresAt");
                if (expiresAtStr != null) {
                    LocalDateTime expiresAt = LocalDateTime.parse(expiresAtStr);
                    if (now.isAfter(expiresAt)) {
                        // 만료된 키 삭제
                        String idempotencyKey = redisKey.replace(KEY_PREFIX, "");
                        redisTemplate.delete(redisKey);
                        redisTemplate.delete(RESULT_PREFIX + idempotencyKey);
                        cleanedCount++;
                    }
                }
            }

            // 만료된 키 통계 업데이트
            if (cleanedCount > 0) {
                updateDailyStats("keysExpired", cleanedCount);
            }

            log.info("만료된 멱등성 키 정리 완료: {}개", cleanedCount);

        } catch (Exception e) {
            log.error("만료된 키 정리 중 오류 발생", e);
        }

        return cleanedCount;
    }

    @Override
    public Map<String, Object> getKeyStatus(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        Map<Object, Object> keyData = redisTemplate.opsForHash().entries(redisKey);

        Map<String, Object> status = new HashMap<>();
        status.put("exists", !keyData.isEmpty());

        if (!keyData.isEmpty()) {
            // Map<Object, Object>를 Map<String, Object>로 안전하게 변환
            for (Map.Entry<Object, Object> entry : keyData.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : null;
                if (key != null) {
                    status.put(key, entry.getValue());
                }
            }
            status.put("hasResult", redisTemplate.hasKey(RESULT_PREFIX + idempotencyKey));

            // 만료 상태 확인
            String expiresAtStr = (String) keyData.get("expiresAt");
            if (expiresAtStr != null) {
                LocalDateTime expiresAt = LocalDateTime.parse(expiresAtStr);
                status.put("isExpired", LocalDateTime.now().isAfter(expiresAt));
                status.put("remainingMinutes",
                    Math.max(0, java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes()));
            }
        }

        return status;
    }

    @Override
    public IdempotencyKeyResponse getActiveKeysForUser(Long userId) {
        // 단순 구현: 새로운 키 생성
        return generateKey();
    }

    // ========================= 내부 메서드 =========================

    /**
     * 고유한 멱등성 키 생성
     */
    private String generateUniqueKey() {
        return "idem-" + UUID.randomUUID().toString();
    }

    /**
     * 일일 통계 업데이트
     */
    private void updateDailyStats(String statName, long increment) {
        try {
            String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String statsKey = STATS_PREFIX + today;

            redisTemplate.opsForHash().increment(statsKey, statName, increment);
            redisTemplate.expire(statsKey, 90, TimeUnit.DAYS); // 90일 보관

        } catch (Exception e) {
            log.error("일일 통계 업데이트 중 오류 발생: {}", statName, e);
        }
    }

    /**
     * Map에서 Long 값 안전하게 추출
     */
    private Long getLongValue(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

}