package com.popcorn.payment.config

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.listener.PaymentRedisStreamListener
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import jakarta.annotation.PostConstruct
import java.time.Duration

/**
 * Payment 서비스 Redis Stream 이벤트 설정
 *
 * 주문/결제 관련 이벤트를 Stream으로 수신하여 로그 및 후속 처리 기반을 마련
 */
@Configuration
@ConditionalOnProperty(name = ["redis.events.enabled"], havingValue = "true", matchIfMissing = true)
class PaymentRedisStreamConfig(
    private val paymentRedisStreamListener: PaymentRedisStreamListener,
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(PaymentRedisStreamConfig::class.java)

    // Stream 이름 상수
    companion object {
        private const val ORDER_EVENTS_STREAM = "order-events"
        private val PAYMENT_EVENTS_STREAM = EventConstants.Streams.PAYMENT_EVENTS
        private const val INVENTORY_EVENTS_STREAM = "inventory-events"

        // Consumer Group 이름
        private val PAYMENT_CONSUMER_GROUP = EventConstants.ConsumerGroups.PAYMENT_SERVICE_GROUP
        private val PAYMENT_CONSUMER_NAME = EventConstants.Consumers.PAYMENT_EVENTS_CONSUMER
    }

    @PostConstruct
    fun initializeStreamsAndConsumerGroups() {
        try {
            // Consumer Group 생성 (이미 존재하면 무시)
            createConsumerGroupIfNotExists(ORDER_EVENTS_STREAM)
            createConsumerGroupIfNotExists(PAYMENT_EVENTS_STREAM)
            createConsumerGroupIfNotExists(INVENTORY_EVENTS_STREAM)
            createConsumerGroupIfNotExists("order-info-responses") // Order 정보 응답 수신용

            log.info("✅ Payment Service Redis Stream Consumer Groups 초기화 완료")
        } catch (e: Exception) {
            log.warn("⚠️ Redis Stream 초기화 중 오류 (정상 동작 가능): ${e.message}")
        }
    }

    private fun createConsumerGroupIfNotExists(streamName: String) {
        try {
            redisTemplate.opsForStream<String, Any>().createGroup(streamName, ReadOffset.from("0"), PAYMENT_CONSUMER_GROUP)
            log.info("📝 Payment Consumer Group 생성: {} - {}", streamName, PAYMENT_CONSUMER_GROUP)
        } catch (e: Exception) {
            // Consumer Group이 이미 존재하는 경우 무시
            log.debug("Payment Consumer Group 이미 존재: {} - {}", streamName, PAYMENT_CONSUMER_GROUP)
        }
    }

    @Bean
    fun paymentStreamListenerContainer(
        connectionFactory: RedisConnectionFactory
    ): StreamMessageListenerContainer<String, MapRecord<String, String, Any>> {

        val options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
            .builder()
            .batchSize(50)  // 배치 처리량 2.5배 증가 - 더 효율적 처리
            .pollTimeout(Duration.ofMillis(5000))  // 폴링 타임아웃 5초 - 안정성 향상
            .build()

        val container = StreamMessageListenerContainer.create(connectionFactory, options)

        // 주문 이벤트 Stream 구독
        container.receive(
            Consumer.from(PAYMENT_CONSUMER_GROUP, PAYMENT_CONSUMER_NAME),
            StreamOffset.create(ORDER_EVENTS_STREAM, ReadOffset.lastConsumed()),
            paymentRedisStreamListener as StreamListener<String, MapRecord<String, String, String>>
        )

        // 결제 이벤트 Stream 구독 (자체 모니터링)
        container.receive(
            Consumer.from(PAYMENT_CONSUMER_GROUP, PAYMENT_CONSUMER_NAME),
            StreamOffset.create(PAYMENT_EVENTS_STREAM, ReadOffset.lastConsumed()),
            paymentRedisStreamListener as StreamListener<String, MapRecord<String, String, String>>
        )

        // 재고 이벤트 Stream 구독
        container.receive(
            Consumer.from(PAYMENT_CONSUMER_GROUP, PAYMENT_CONSUMER_NAME),
            StreamOffset.create(INVENTORY_EVENTS_STREAM, ReadOffset.lastConsumed()),
            paymentRedisStreamListener as StreamListener<String, MapRecord<String, String, String>>
        )

        // Order 정보 응답 Stream 구독 (Payment에서 요청한 Order 정보 응답 수신)
        container.receive(
            Consumer.from(PAYMENT_CONSUMER_GROUP, PAYMENT_CONSUMER_NAME),
            StreamOffset.create("order-info-responses", ReadOffset.lastConsumed()),
            paymentRedisStreamListener as StreamListener<String, MapRecord<String, String, String>>
        )

        container.start()
        log.info("🚀 Payment Redis Stream Listener Container 시작됨")

        return container as StreamMessageListenerContainer<String, MapRecord<String, String, Any>>
    }
}
