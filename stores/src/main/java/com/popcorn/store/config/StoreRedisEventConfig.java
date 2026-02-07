package com.popcorn.store.config;

import com.popcorn.store.event.StoreRedisStreamListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;

/**
 * Stores 서비스 Redis Stream 이벤트 설정
 *
 * Redis Stream을 사용하여 주문, 결제 서비스에서 발생한 이벤트 수신
 * - 메시지 지속성 보장
 * - Consumer Group 기반 부하 분산
 * - 메시지 ACK 처리
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "redis.events.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class StoreRedisEventConfig {

    private final StoreRedisStreamListener storeRedisStreamListener;
    private final RedisTemplate<String, Object> redisTemplate;

    // Stream 이름 상수
    private static final String ORDER_EVENTS_STREAM = "order-events";    // 주문 생성, 결제 완료 등
    private static final String SCHEDULE_EVENTS_STREAM = "schedule-events";  // 스케줄 예약
    private static final String GOODS_EVENTS_STREAM = "goods-events";        // 굿즈 예약
    private static final String MIXED_EVENTS_STREAM = "mixed-events";        // 복합형 (스케줄+굿즈)
    private static final String STOCK_EVENTS_STREAM = "stock-events";        // 재고 차감
    private static final String PRICE_EVENTS_STREAM = "price-events";        // 가격 조회
    private static final String STORE_LOOKUP_STREAM = "store-lookup-requests";
    private static final String INVENTORY_EVENTS_STREAM = "inventory-events"; // 결제→재고 확정/복구

    // Consumer Group 이름
    private static final String STORE_CONSUMER_GROUP = "store-service-group";
    private static final String STORE_CONSUMER_NAME = "store-consumer-1";

    @PostConstruct
    public void initializeStreamsAndConsumerGroups() {
        try {
            // Consumer Group 생성 (이미 존재하면 무시)
            createConsumerGroupIfNotExists(ORDER_EVENTS_STREAM);
            createConsumerGroupIfNotExists(SCHEDULE_EVENTS_STREAM);
            createConsumerGroupIfNotExists(GOODS_EVENTS_STREAM);
            createConsumerGroupIfNotExists(MIXED_EVENTS_STREAM);
            createConsumerGroupIfNotExists(STOCK_EVENTS_STREAM);
            createConsumerGroupIfNotExists(PRICE_EVENTS_STREAM);
            createConsumerGroupIfNotExists(STORE_LOOKUP_STREAM);
            createConsumerGroupIfNotExists(INVENTORY_EVENTS_STREAM);

            log.info("✅ Redis Stream Consumer Groups 초기화 완료");
        } catch (Exception e) {
            log.warn("⚠️ Redis Stream 초기화 중 오류 (정상 동작 가능): {}", e.getMessage());
        }
    }

    private void createConsumerGroupIfNotExists(String streamName) {
        try {
            redisTemplate.opsForStream().createGroup(streamName, ReadOffset.from("0"), STORE_CONSUMER_GROUP);
            log.info("📝 Consumer Group 생성: {} - {}", streamName, STORE_CONSUMER_GROUP);
        } catch (Exception e) {
            if (isStreamMissing(e)) {
                // Stream이 없으면 임시 레코드로 생성 후 그룹 생성
                String recordId = redisTemplate.opsForStream()
                        .add(StreamRecords.mapBacked(Map.of("_init", "1")).withStreamKey(streamName))
                        .getValue();
                if (recordId != null) {
                    redisTemplate.opsForStream().delete(streamName, recordId);
                }
                redisTemplate.opsForStream().createGroup(streamName, ReadOffset.from("0"), STORE_CONSUMER_GROUP);
                log.info("📝 Consumer Group 생성(MKSTREAM): {} - {}", streamName, STORE_CONSUMER_GROUP);
            } else {
                // Consumer Group이 이미 존재하는 경우 무시
                log.debug("Consumer Group 이미 존재: {} - {}", streamName, STORE_CONSUMER_GROUP);
            }
        }
    }

    private boolean isStreamMissing(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("requires the key to exist") || lower.contains("no such key");
    }

    /**
     * Redis Stream 메시지 리스너 컨테이너 설정
     */
    @Bean
    public StreamMessageListenerContainer storeStreamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        .batchSize(20)  // 배치 크기 상향
                        .pollTimeout(Duration.ofMillis(5000))  // 폴링 타임아웃 증가 (5초)
                        .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        // 각 Stream에 대한 Consumer 등록
        registerStreamConsumer(container, ORDER_EVENTS_STREAM);
        registerStreamConsumer(container, SCHEDULE_EVENTS_STREAM);
        registerStreamConsumer(container, GOODS_EVENTS_STREAM);
        registerStreamConsumer(container, MIXED_EVENTS_STREAM);
        registerStreamConsumer(container, STOCK_EVENTS_STREAM);
        registerStreamConsumer(container, PRICE_EVENTS_STREAM);
        registerStreamConsumer(container, STORE_LOOKUP_STREAM);
        registerStreamConsumer(container, INVENTORY_EVENTS_STREAM);

        container.start();
        log.info("🚀 Store Redis Stream Listener Container 시작됨");

        return container;
    }

    private void registerStreamConsumer(
            StreamMessageListenerContainer container,
            String streamName) {

        Subscription subscription = container.receive(
                Consumer.from(STORE_CONSUMER_GROUP, STORE_CONSUMER_NAME),
                StreamOffset.create(streamName, ReadOffset.lastConsumed()),  // 새로운 메시지만 읽기
                (org.springframework.data.redis.stream.StreamListener) storeRedisStreamListener
        );

        log.info("📡 Stream Consumer 등록: {} (Group: {}, Consumer: {})",
                streamName, STORE_CONSUMER_GROUP, STORE_CONSUMER_NAME);
    }
}
