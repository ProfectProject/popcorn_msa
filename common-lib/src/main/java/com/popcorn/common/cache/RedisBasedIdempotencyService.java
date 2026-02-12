package com.popcorn.common.cache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Redis 기반 멱등성 처리 서비스 구현체
 *
 * 기능:
 * - 요청 상태 추적 (IN_PROGRESS, COMPLETED)
 * - 응답 캐싱 및 반환
 * - 동시성 제어 (SETNX 기반)
 * - 메트릭스 수집
 */
@Service
@RequiredArgsConstructor
public class RedisBasedIdempotencyService implements IdempotencyService {

	private static final Logger log = LoggerFactory.getLogger(RedisBasedIdempotencyService.class);

	private static final Duration DEFAULT_RESPONSE_TTL = Duration.ofMinutes(30);
	private static final Duration IN_PROGRESS_TTL = Duration.ofMinutes(2);

	private static final String RESPONSE_PREFIX = "idempotent:response:";
	private static final String IN_PROGRESS_PREFIX = "idempotent:in-progress:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	@Value("${redis.idempotency.clear-on-startup:false}")
	private boolean clearOnStartup;

	private final IdempotencyMetrics metrics = new IdempotencyMetrics();

	@PostConstruct
	public void clearCacheOnStartup() {
		if (!clearOnStartup) {
			return;
		}
		log.warn("🧹 Redis 멱등성 캐시를 시작 시점에 초기화합니다.");
		clearCache();
	}

	@Override
	public <T> IdempotencyResult<T> processRequest(
			String idempotencyKey,
			IdempotentOperation<T> operation,
			Class<T> responseType) {
		return processRequest(idempotencyKey, operation, responseType, 0);
	}

	@Override
	public <T> IdempotencyResult<T> processRequest(
			String idempotencyKey,
			IdempotentOperation<T> operation,
			Class<T> responseType,
			int ttlSeconds) {

		log.debug("🔄 Redis 멱등성 요청 처리 시작 - 키: {}", idempotencyKey);

		if (idempotencyKey == null) {
			log.debug("⚠️ 멱등성 키가 null이므로 캐시 없이 직접 실행");
			try {
				metrics.recordDirectExecution();
				T result = operation.execute();
				return IdempotencyResult.newExecution(result);
			} catch (Exception e) {
				log.error("❌ 요청 처리 실패 - 키: null, 오류: {}", e.getMessage(), e);
				throw new IdempotencyException("작업 실행 중 오류 발생", e);
			}
		}

		String responseKey = RESPONSE_PREFIX + idempotencyKey;
		String inProgressKey = IN_PROGRESS_PREFIX + idempotencyKey;
		Duration responseTtl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : DEFAULT_RESPONSE_TTL;

		// 1. 이미 완료된 요청인지 확인
		try {
			String cachedRecordJson = redisTemplate.opsForValue().get(responseKey);
			if (cachedRecordJson != null) {
				log.debug("✅ Redis 캐시에서 응답 반환 - 키: {}", idempotencyKey);
				metrics.recordCacheHit();

				try {
					IdempotencyRecord cachedRecord = deserializeRecord(cachedRecordJson);
					T cachedResult = deserializeResponse(cachedRecord.responseData(), responseType);
					return IdempotencyResult.cachedExecution(cachedResult, cachedRecord.completedAt());
				} catch (IdempotencyException e) {
					log.warn("⚠️ 손상된 멱등 응답 캐시 삭제 후 재실행 - key={}, error={}",
						idempotencyKey, e.getMessage());
					redisTemplate.delete(responseKey);
				}
			}
		} catch (DataAccessException e) {
			log.warn("Redis 연결 실패로 캐시 우회: key={}, error={}", idempotencyKey, e.getMessage());
		}

		// 2. 동시 요청 체크 및 처리
		Boolean acquired;
		try {
			acquired = redisTemplate.opsForValue().setIfAbsent(inProgressKey, "1", IN_PROGRESS_TTL);
		} catch (DataAccessException e) {
			log.warn("Redis 연결 실패로 동시성 제어 우회: key={}, error={}", idempotencyKey, e.getMessage());
			acquired = Boolean.TRUE;
		}

		if (Boolean.FALSE.equals(acquired)) {
			log.warn("🔄 동시 요청 감지 - 키: {}", idempotencyKey);
			metrics.recordConcurrentRequest();
			throw new IdempotencyException(
					String.format("동시에 처리 중인 요청이 있습니다. 키: %s", idempotencyKey),
					idempotencyKey);
		}

		try {
			log.debug("🚀 새 요청 실행 시작 - 키: {}", idempotencyKey);
			metrics.recordNewRequest();

			T result = operation.execute();

			try {
				String serializedResult = serializeResponse(result);
				IdempotencyRecord record = new IdempotencyRecord(
						idempotencyKey,
						serializedResult,
						LocalDateTime.now()
				);
				String recordJson = serializeRecord(record);
				redisTemplate.opsForValue().set(responseKey, recordJson, responseTtl);
				log.debug("✅ 요청 완료 및 Redis 캐싱 - 키: {}", idempotencyKey);
			} catch (Exception e) {
				log.warn("⚠️ 결과 캐싱 실패하지만 요청은 성공 처리 - 키: {}, 오류: {}",
					idempotencyKey, e.getMessage());
			}

			metrics.recordSuccessfulExecution();
			return IdempotencyResult.newExecution(result);

		} catch (Exception e) {
			log.error("❌ 요청 처리 실패 - 키: {}, 오류: {}", idempotencyKey, e.getMessage());
			metrics.recordOperationError();
			throw new IdempotencyException("작업 실행 중 오류 발생", e);

		} finally {
			try {
				redisTemplate.delete(inProgressKey);
			} catch (DataAccessException e) {
				log.debug("Redis 진행 상태 정리 실패: key={}, error={}", idempotencyKey, e.getMessage());
			}
		}
	}

	@Override
	public IdempotencyCacheStats getCacheStats() {
		long cacheSize = 0L;
		int inProgressCount = 0;

		try {
			Set<String> responseKeys = redisTemplate.keys(RESPONSE_PREFIX + "*");
			cacheSize = responseKeys != null ? responseKeys.size() : 0L;

			Set<String> inProgressKeys = redisTemplate.keys(IN_PROGRESS_PREFIX + "*");
			inProgressCount = inProgressKeys != null ? inProgressKeys.size() : 0;
		} catch (DataAccessException e) {
			log.debug("Redis 통계 조회 실패: {}", e.getMessage());
		}

		return RedisIdempotencyCacheStats.builder()
				.cacheSize(cacheSize)
				.hitCount(metrics.getCacheHitCount())
				.missCount(metrics.getNewRequestCount())
				.hitRate(calculateHitRate())
				.totalLoadTime(0L)
				.evictionCount(0L)
				.inProgressRequestCount(inProgressCount)
				.newRequestCount(metrics.getNewRequestCount())
				.cacheHitCount(metrics.getCacheHitCount())
				.concurrentRequestCount(metrics.getConcurrentRequestCount())
				.operationErrorCount(metrics.getOperationErrorCount())
				.build();
	}

	@Override
	public void clearCache() {
		try {
			Set<String> responseKeys = redisTemplate.keys(RESPONSE_PREFIX + "*");
			if (responseKeys != null && !responseKeys.isEmpty()) {
				redisTemplate.delete(responseKeys);
			}
			Set<String> inProgressKeys = redisTemplate.keys(IN_PROGRESS_PREFIX + "*");
			if (inProgressKeys != null && !inProgressKeys.isEmpty()) {
				redisTemplate.delete(inProgressKeys);
			}
			log.info("🧹 Redis 멱등성 캐시 전체 초기화");
			metrics.recordCacheCleared();
		} catch (DataAccessException e) {
			log.warn("Redis 캐시 초기화 실패: {}", e.getMessage());
		}
	}

	@Override
	public void invalidateKey(String idempotencyKey) {
		String responseKey = RESPONSE_PREFIX + idempotencyKey;
		String inProgressKey = IN_PROGRESS_PREFIX + idempotencyKey;
		try {
			redisTemplate.delete(responseKey);
			redisTemplate.delete(inProgressKey);
			log.debug("🗑️ Redis 멱등성 키 무효화 - 키: {}", idempotencyKey);
			metrics.recordKeyInvalidation();
		} catch (DataAccessException e) {
			log.warn("Redis 키 무효화 실패: key={}, error={}", idempotencyKey, e.getMessage());
		}
	}

	@Override
	public void clearByPrefix(String keyPrefix) {
		if (keyPrefix == null || keyPrefix.isBlank()) {
			return;
		}
		String responsePattern = RESPONSE_PREFIX + keyPrefix + "*";
		String inProgressPattern = IN_PROGRESS_PREFIX + keyPrefix + "*";
		try {
			Set<String> responseKeys = redisTemplate.keys(responsePattern);
			if (responseKeys != null && !responseKeys.isEmpty()) {
				redisTemplate.delete(responseKeys);
			}
			Set<String> inProgressKeys = redisTemplate.keys(inProgressPattern);
			if (inProgressKeys != null && !inProgressKeys.isEmpty()) {
				redisTemplate.delete(inProgressKeys);
			}
			log.info("🧹 Redis 멱등성 캐시 접두사 삭제 - prefix={}", keyPrefix);
		} catch (DataAccessException e) {
			log.warn("Redis 접두사 삭제 실패: prefix={}, error={}", keyPrefix, e.getMessage());
		}
	}

	private <T> String serializeResponse(T response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (JsonProcessingException e) {
			log.warn("📝 응답 직렬화 실패, 캐시 저장 생략 - 응답 타입: {}, 오류: {}",
				response != null ? response.getClass().getSimpleName() : "null", e.getMessage());
			metrics.recordSerializationError();
			throw new IdempotencyException("응답 직렬화 실패", e);
		}
	}

	private <T> T deserializeResponse(String responseData, Class<T> responseType) {
		try {
			return objectMapper.readValue(responseData, responseType);
		} catch (JsonProcessingException e) {
			T fallback = tryDeserializeResponseBody(responseData, responseType);
			if (fallback != null) {
				return fallback;
			}
			metrics.recordSerializationError();
			throw new IdempotencyException("응답 역직렬화 실패", e);
		}
	}

	private <T> T tryDeserializeResponseBody(String responseData, Class<T> responseType) {
		try {
			JsonNode root = objectMapper.readTree(responseData);
			JsonNode body = root.get("body");
			if (body == null || body.isNull()) {
				return null;
			}
			return objectMapper.treeToValue(body, responseType);
		} catch (Exception ignored) {
			return null;
		}
	}

	private String serializeRecord(IdempotencyRecord record) {
		try {
			return objectMapper.writeValueAsString(record);
		} catch (JsonProcessingException e) {
			log.warn("📝 레코드 직렬화 실패, 안전한 레코드로 재시도 - 키: {}, 오류: {}",
				record.key(), e.getMessage());

			// 안전한 레코드 생성 시도
			try {
				IdempotencyRecord safeRecord = new IdempotencyRecord(
					record.key(),
					"SERIALIZATION_FAILED: " + (record.responseData() != null ? "data exists" : "null"),
					record.completedAt()
				);
				String safeResult = objectMapper.writeValueAsString(safeRecord);
				log.debug("✅ 안전한 레코드 직렬화 성공 - 키: {}", record.key());
				return safeResult;
			} catch (Exception fallbackError) {
				log.error("❌ 안전한 레코드 직렬화도 실패 - 키: {}, 오류: {}",
					record.key(), fallbackError.getMessage());
				metrics.recordSerializationError();
				// 최후 수단: 매우 간단한 문자열 반환
				return String.format("{\"key\":\"%s\",\"responseData\":\"FAILED\",\"completedAt\":\"%s\"}",
					record.key(), record.completedAt().toString());
			}
		}
	}

	private IdempotencyRecord deserializeRecord(String recordJson) {
		try {
			return objectMapper.readValue(recordJson, IdempotencyRecord.class);
		} catch (JsonProcessingException e) {
			metrics.recordSerializationError();
			throw new IdempotencyException("레코드 역직렬화 실패", e);
		}
	}

	private double calculateHitRate() {
		long totalRequests = metrics.getNewRequestCount() + metrics.getCacheHitCount();
		if (totalRequests == 0) return 0.0;
		return (double) metrics.getCacheHitCount() / totalRequests;
	}

	private static class IdempotencyRecord {
		@JsonProperty("key")
		private final String key;
		@JsonProperty("responseData")
		private final String responseData;
		@JsonProperty("completedAt")
		private final LocalDateTime completedAt;

		@JsonCreator
		public IdempotencyRecord(
				@JsonProperty("key") String key,
				@JsonProperty("responseData") String responseData,
				@JsonProperty("completedAt") LocalDateTime completedAt
		) {
			this.key = key;
			this.responseData = responseData;
			this.completedAt = completedAt;
		}

		public String key() { return key; }
		public String responseData() { return responseData; }
		public LocalDateTime completedAt() { return completedAt; }

		public String getKey() { return key; }
		public String getResponseData() { return responseData; }
		public LocalDateTime getCompletedAt() { return completedAt; }
	}

	private static class IdempotencyMetrics {
		private final AtomicLong newRequestCount = new AtomicLong(0);
		private final AtomicLong cacheHitCount = new AtomicLong(0);
		private final AtomicLong concurrentRequestCount = new AtomicLong(0);
		private final AtomicLong operationErrorCount = new AtomicLong(0);
		private final AtomicLong serializationErrorCount = new AtomicLong(0);
		private final AtomicLong directExecutionCount = new AtomicLong(0);
		private final AtomicLong cacheClearCount = new AtomicLong(0);
		private final AtomicLong keyInvalidationCount = new AtomicLong(0);

		public void recordNewRequest() { newRequestCount.incrementAndGet(); }
		public void recordCacheHit() { cacheHitCount.incrementAndGet(); }
		public void recordConcurrentRequest() { concurrentRequestCount.incrementAndGet(); }
		public void recordOperationError() { operationErrorCount.incrementAndGet(); }
		public void recordSerializationError() { serializationErrorCount.incrementAndGet(); }
		public void recordDirectExecution() { directExecutionCount.incrementAndGet(); }
		public void recordCacheCleared() { cacheClearCount.incrementAndGet(); }
		public void recordKeyInvalidation() { keyInvalidationCount.incrementAndGet(); }
		public void recordSuccessfulExecution() { /* 성공적인 실행은 별도 카운터 불필요 */ }

		public long getNewRequestCount() { return newRequestCount.get(); }
		public long getCacheHitCount() { return cacheHitCount.get(); }
		public long getConcurrentRequestCount() { return concurrentRequestCount.get(); }
		public long getOperationErrorCount() { return operationErrorCount.get(); }
	}
}
