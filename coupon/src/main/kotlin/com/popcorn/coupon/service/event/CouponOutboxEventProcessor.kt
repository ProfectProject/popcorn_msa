package com.popcorn.coupon.service.event

import com.popcorn.coupon.domain.entity.CouponOutboxEvent
import com.popcorn.coupon.domain.repository.CouponOutboxEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class CouponOutboxEventProcessor(
    private val couponOutboxEventRepository: CouponOutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val BATCH_SIZE = 100
        private const val COUPON_EVENTS_TOPIC = "coupon-events"
        private const val COUPON_ANALYTICS_EVENTS_TOPIC = "coupon-analytics-events"
    }

    /**
     * 미발행 이벤트 처리 (스케줄링)
     * 매 30초마다 실행
     */
    @Scheduled(fixedDelay = 30000)
    suspend fun processUnpublishedEvents() = withContext(Dispatchers.IO) {
        logger.debug { "🔄 미발행 이벤트 처리 시작" }

        try {
            val unpublishedEvents = couponOutboxEventRepository.findUnpublishedEvents(
                PageRequest.of(0, BATCH_SIZE)
            )

            if (unpublishedEvents.isNotEmpty()) {
                logger.info { "📤 미발행 이벤트 발견: ${unpublishedEvents.size}개" }
                publishEvents(unpublishedEvents)
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ 미발행 이벤트 처리 중 오류 발생" }
        }
    }

    /**
     * 이벤트 일괄 발행
     */
    suspend fun publishEvents(events: List<CouponOutboxEvent>) = withContext(Dispatchers.IO) {
        val publishedEventIds = mutableListOf<Long>()
        val now = LocalDateTime.now()

        for (event in events) {
            try {
                publishSingleEvent(event)
                publishedEventIds.add(event.id!!)
                logger.debug { "✅ 이벤트 발행 성공: eventId=${event.id}, type=${event.eventType}" }
            } catch (e: Exception) {
                logger.error(e) { "❌ 이벤트 발행 실패: eventId=${event.id}, type=${event.eventType}" }
                // 개별 이벤트 실패 시에도 다른 이벤트는 계속 처리
            }
        }

        // 성공한 이벤트들을 발행됨으로 마킹
        if (publishedEventIds.isNotEmpty()) {
            couponOutboxEventRepository.markAsProcessed(publishedEventIds, now)
            logger.info { "🏷️ 이벤트 발행 완료 마킹: ${publishedEventIds.size}개" }
        }
    }

    /**
     * 개별 이벤트 발행
     */
    private suspend fun publishSingleEvent(event: CouponOutboxEvent) = withContext(Dispatchers.IO) {
        val topic = getTopicForEvent(event)
        val key = generateEventKey(event)

        kafkaTemplate.send(topic, key, event.eventData)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) { "Kafka 전송 실패: eventId=${event.id}, topic=$topic" }
                    throw ex
                } else {
                    logger.debug { "Kafka 전송 성공: eventId=${event.id}, topic=$topic, partition=${result?.recordMetadata?.partition()}" }
                }
            }
    }

    /**
     * 이벤트 타입에 따른 토픽 결정
     */
    private fun getTopicForEvent(event: CouponOutboxEvent): String {
        return when (event.aggregateType) {
            "COUPON_ANALYTICS" -> COUPON_ANALYTICS_EVENTS_TOPIC
            else -> COUPON_EVENTS_TOPIC
        }
    }

    /**
     * 이벤트 키 생성 (파티셔닝용)
     */
    private fun generateEventKey(event: CouponOutboxEvent): String {
        return when (event.eventType) {
            "COUPON_CREATED", "COUPON_ACTIVATED", "COUPON_DEACTIVATED" ->
                "coupon-${event.aggregateId}"
            "COUPON_ISSUED", "COUPON_RESERVED", "COUPON_USED", "COUPON_RELEASED", "COUPON_EXPIRED" ->
                "user-coupon-${event.aggregateId}"
            else ->
                "default-${event.aggregateId}"
        }
    }

    /**
     * 장애 복구를 위한 오래된 미발행 이벤트 처리
     * 매일 오전 2시에 실행
     */
    @Scheduled(cron = "0 0 2 * * ?")
    suspend fun recoverOldUnpublishedEvents() = withContext(Dispatchers.IO) {
        logger.info { "🔧 장애 복구: 오래된 미발행 이벤트 처리 시작" }

        try {
            val cutoffTime = LocalDateTime.now().minusHours(24) // 24시간 전
            val oldEvents = couponOutboxEventRepository.findOldUnprocessedEvents(
                cutoffTime,
                PageRequest.of(0, BATCH_SIZE * 5) // 더 많은 수량 처리
            ).content

            if (oldEvents.isNotEmpty()) {
                logger.warn { "⚠️ 24시간 이상된 미발행 이벤트 발견: ${oldEvents.size}개" }
                publishEvents(oldEvents)
            } else {
                logger.info { "✅ 처리할 오래된 미발행 이벤트 없음" }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ 오래된 미발행 이벤트 복구 중 오류 발생" }
        }
    }

    /**
     * 발행된 이벤트 정리 (보관 정책)
     * 매주 일요일 오전 3시에 실행
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    suspend fun cleanupOldPublishedEvents() = withContext(Dispatchers.IO) {
        logger.info { "🧹 발행된 이벤트 정리 시작" }

        try {
            val cutoffTime = LocalDateTime.now().minusDays(30) // 30일 전
            val oldPublishedEvents = couponOutboxEventRepository.findOldProcessedEvents(
                cutoffTime,
                PageRequest.of(0, 1000)
            ).content

            if (oldPublishedEvents.isNotEmpty()) {
                val eventIds = oldPublishedEvents.map { it.id!! }
                couponOutboxEventRepository.deleteAllById(eventIds)
                logger.info { "🗑️ 30일 이상된 발행 완료 이벤트 정리: ${eventIds.size}개" }
            } else {
                logger.info { "✅ 정리할 오래된 발행 완료 이벤트 없음" }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ 발행된 이벤트 정리 중 오류 발생" }
        }
    }

    /**
     * 수동 이벤트 재발행 (관리 도구용)
     */
    suspend fun republishEventsByAggregateId(aggregateId: String): Int = withContext(Dispatchers.IO) {
        logger.info { "🔄 수동 이벤트 재발행: aggregateId=$aggregateId" }

        val events = couponOutboxEventRepository.findByAggregateId(aggregateId)
        publishEvents(events)

        logger.info { "✅ 이벤트 재발행 완료: ${events.size}개" }
        events.size
    }

    /**
     * 이벤트 발행 통계 조회
     */
    suspend fun getEventStatistics(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        val statistics = couponOutboxEventRepository.getEventStatistics(startDate, endDate)
        val result = mutableMapOf<String, Long>()

        statistics.forEach { row ->
            val eventType = row[0] as String
            val published = row[1] as Long
            val unpublished = row[2] as Long

            result["${eventType}_PUBLISHED"] = published
            result["${eventType}_UNPUBLISHED"] = unpublished
        }

        // 전체 미발행 이벤트 수
        result["TOTAL_UNPUBLISHED"] = couponOutboxEventRepository.countByProcessedAtIsNull()

        result
    }
}
