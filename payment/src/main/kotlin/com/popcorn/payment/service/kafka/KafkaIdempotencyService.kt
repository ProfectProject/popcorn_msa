package com.popcorn.payment.service.kafka

import com.popcorn.payment.constants.EventConstants
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Kafka 이벤트 멱등성 처리 서비스 (Redis 기반)
 * - eventId 기반 중복 처리 방지
 * - TTL로 자동 정리 (메모리 효율성)
 * - 빠른 성능, 간단한 구현
 */
@Service
@ConditionalOnProperty(value = ["kafka.enabled"], havingValue = "true")
class KafkaIdempotencyService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(KafkaIdempotencyService::class.java)

    companion object {
        private const val PROCESSED_EVENT_PREFIX = "kafka:processed:event:"
        private val DEFAULT_TTL = Duration.ofHours(24)  // 24시간 후 자동 삭제
    }

    /**
     * 이벤트가 이미 처리되었는지 확인
     */
    fun isDuplicateEvent(eventId: String): Boolean {
        return try {
            val key = getEventKey(eventId)
            val exists = redisTemplate.hasKey(key)

            if (exists) {
                log.debug("⏭️  중복 이벤트 감지: eventId={}", eventId)
            }

            exists
        } catch (e: Exception) {
            log.warn("⚠️ Redis 중복 체크 실패, 안전하게 false 반환: eventId={}, error={}",
                eventId, e.message)
            false  // Redis 장애 시 중복 처리를 허용 (가용성 우선)
        }
    }

    /**
     * 이벤트 처리 완료 기록
     */
    fun recordProcessedEvent(eventId: String,
                           eventType: String? = null,
                           topic: String? = null,
                           partition: Int? = null,
                           offset: Long? = null,
                           processingTimeMs: Long? = null) {
        try {
            val key = getEventKey(eventId)

            // 처리 정보를 Map으로 저장 (디버깅/모니터링용)
            val eventInfo = mutableMapOf<String, Any>(
                EventConstants.MetadataKeys.EVENT_ID to eventId,
                "processedAt" to System.currentTimeMillis(),
            )

            eventType?.let { eventInfo[EventConstants.MetadataKeys.EVENT_TYPE] = it }
            topic?.let { eventInfo["topic"] = it }
            partition?.let { eventInfo["partition"] = it }
            offset?.let { eventInfo["offset"] = it }
            processingTimeMs?.let { eventInfo["processingTimeMs"] = it }

            // Redis에 저장 (TTL 포함)
            redisTemplate.opsForValue().set(key, eventInfo, DEFAULT_TTL)

            log.debug("✅ 이벤트 처리 기록: eventId={}, ttl={}시간", eventId, DEFAULT_TTL.toHours())

        } catch (e: Exception) {
            log.warn("⚠️ Redis 처리 기록 실패: eventId={}, error={}", eventId, e.message)
            // Redis 장애 시에도 메인 로직에 영향 없음
        }
    }

    /**
     * 특정 이벤트 처리 정보 조회 (디버깅용)
     */
    fun getEventInfo(eventId: String): Map<String, Any>? {
        return try {
            val key = getEventKey(eventId)
            @Suppress("UNCHECKED_CAST")
            redisTemplate.opsForValue().get(key) as? Map<String, Any>
        } catch (e: Exception) {
            log.warn("⚠️ Redis 이벤트 정보 조회 실패: eventId={}, error={}", eventId, e.message)
            null
        }
    }

    /**
     * 처리된 이벤트 수 조회 (모니터링용)
     */
    fun getProcessedEventCount(): Long {
        return try {
            val pattern = "$PROCESSED_EVENT_PREFIX*"
            val keys = redisTemplate.keys(pattern)
            keys.size.toLong()
        } catch (e: Exception) {
            log.warn("⚠️ Redis 이벤트 수 조회 실패: error={}", e.message)
            0L
        }
    }

    /**
     * 수동으로 특정 이벤트 삭제 (필요 시)
     */
    fun removeEventRecord(eventId: String): Boolean {
        return try {
            val key = getEventKey(eventId)
            val deleted = redisTemplate.delete(key)

            if (deleted) {
                log.info("🗑️  이벤트 기록 삭제: eventId={}", eventId)
            }

            deleted
        } catch (e: Exception) {
            log.warn("⚠️ Redis 이벤트 삭제 실패: eventId={}, error={}", eventId, e.message)
            false
        }
    }

    /**
     * 모든 처리된 이벤트 삭제 (테스트용)
     */
    fun clearAllProcessedEvents(): Long {
        return try {
            val pattern = "$PROCESSED_EVENT_PREFIX*"
            val keys = redisTemplate.keys(pattern)

            if (keys.isNotEmpty()) {
                val deletedCount = redisTemplate.delete(keys)
                log.info("🗑️  모든 처리된 이벤트 삭제: count={}", deletedCount)
                deletedCount
            } else {
                0L
            }
        } catch (e: Exception) {
            log.warn("⚠️ Redis 전체 이벤트 삭제 실패: error={}", e.message)
            0L
        }
    }

    private fun getEventKey(eventId: String): String {
        return "$PROCESSED_EVENT_PREFIX$eventId"
    }
}
