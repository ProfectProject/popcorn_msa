package com.popcorn.order.config;

import com.popcorn.order.event.OrderRedisStreamListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.Map;

/**
 * Order 서비스 Redis Stream 이벤트 설정
 *
 * Store 서비스에서 발생한 재고 처리 결과 및 가격 조회 응답 이벤트를 Stream으로 수신
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "redis.events.enabled", havingValue = "true", matchIfMissing = true)
public class RedisEventConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisEventConfig.class);

    private final OrderRedisStreamListener orderRedisStreamListener;
    private final RedisTemplate<String, Object> redisTemplate;

    // Stream 이름 상수
    private static final String RESPONSE_EVENTS_STREAM = "response-events";
    private static final String STOCK_EVENTS_STREAM = "stock-events";
    private static final String GOODS_EVENTS_STREAM = "goods-events";
    private static final String ADDRESS_RESPONSE_STREAM = "order-address-response";
    private static final String PAYMENT_EVENTS_STREAM = "payment-events";
    private static final String STORE_LOOKUP_RESPONSES_STREAM = "store-lookup-responses";
    private static final String SCHEDULE_EVENTS_STREAM = "schedule-events";
    private static final String ORDER_QUERY_STREAM = "order:query:stream";
    private static final String ORDER_UPDATE_STREAM = "order:update:stream";

    // Consumer Group 이름
    private static final String ORDER_CONSUMER_GROUP = "order-service-group";
    private static final String RESPONSE_CONSUMER_NAME = "response-consumer-1";
    private static final String STOCK_CONSUMER_NAME = "stock-consumer-1";
    private static final String ORDER_INFO_CONSUMER_NAME = "order-info-consumer-1";
    private static final String ADDRESS_CONSUMER_NAME = "address-consumer-1";
    private static final String PAYMENT_CONSUMER_NAME = "payment-consumer-1";

    @PostConstruct
    public void initializeStreamsAndConsumerGroups() {
        try {
            // Consumer Group 생성 (이미 존재하면 무시)
            createConsumerGroupIfNotExists(RESPONSE_EVENTS_STREAM);
            createConsumerGroupIfNotExists(STOCK_EVENTS_STREAM);
            createConsumerGroupIfNotExists(GOODS_EVENTS_STREAM);
            createConsumerGroupIfNotExists(ADDRESS_RESPONSE_STREAM);  // User 서비스 응답 수신용
            createConsumerGroupIfNotExists("order-info-requests"); // Order 정보 요청 처리용
            createConsumerGroupIfNotExists(PAYMENT_EVENTS_STREAM); // Payment 이벤트 수신용
            createConsumerGroupIfNotExists(STORE_LOOKUP_RESPONSES_STREAM); // Store 조회 응답 수신용
            createConsumerGroupIfNotExists(SCHEDULE_EVENTS_STREAM); // 스케줄 예약 결과 수신용
            createConsumerGroupIfNotExists(ORDER_QUERY_STREAM); // 주문 조회 요청 수신용
            createConsumerGroupIfNotExists(ORDER_UPDATE_STREAM); // 주문 상태 업데이트 요청 수신용

            log.info("✅ Order Service Redis Stream Consumer Groups 초기화 완료");
        } catch (Exception e) {
            log.warn("⚠️ Redis Stream 초기화 중 오류 (정상 동작 가능): {}", e.getMessage());
        }
    }

    private void createConsumerGroupIfNotExists(String streamName) {
        try {
            redisTemplate.opsForStream().createGroup(streamName, ReadOffset.from("0"), ORDER_CONSUMER_GROUP);
            log.info("📝 Order Consumer Group 생성: {} - {}", streamName, ORDER_CONSUMER_GROUP);
        } catch (Exception e) {
            if (isStreamMissing(e)) {
                String recordId = redisTemplate.opsForStream()
                        .add(StreamRecords.mapBacked(Map.of("_init", "1")).withStreamKey(streamName))
                        .getValue();
                if (recordId != null) {
                    redisTemplate.opsForStream().delete(streamName, recordId);
                }
                redisTemplate.opsForStream().createGroup(streamName, ReadOffset.from("0"), ORDER_CONSUMER_GROUP);
                log.info("📝 Order Consumer Group 생성(MKSTREAM): {} - {}", streamName, ORDER_CONSUMER_GROUP);
            } else {
                // Consumer Group이 이미 존재하는 경우 무시
                log.debug("Order Consumer Group 이미 존재: {} - {}", streamName, ORDER_CONSUMER_GROUP);
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
    public StreamMessageListenerContainer orderStreamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        .batchSize(50)  // 배치 크기 5배 증가 - 처리량 향상
                        .pollTimeout(Duration.ofMillis(500))  // 폴링 타임아웃 0.5초 - 성능과 안정성 균형
                        .errorHandler(t -> {
                            if (t.getCause() instanceof org.springframework.dao.QueryTimeoutException) {
                                log.warn("🔄 Redis Stream 타임아웃 발생, 재시도 예정: {}", t.getMessage());
                                // 타임아웃은 정상적인 상황으로 간주 (스택트레이스 출력 안함)
                            } else {
                                log.error("❌ Redis Stream 처리 중 예상치 못한 오류: {}", t.getMessage(), t);
                            }
                        })
                        .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        // 응답 이벤트 Stream 구독 (가격 조회 응답)
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, RESPONSE_CONSUMER_NAME),
                StreamOffset.create(RESPONSE_EVENTS_STREAM, ReadOffset.lastConsumed()),  // 새로운 메시지만 읽기
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", RESPONSE_EVENTS_STREAM, RESPONSE_CONSUMER_NAME);

        // 재고 이벤트 Stream 구독 (재고 차감 결과)
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, STOCK_CONSUMER_NAME),
                StreamOffset.create(STOCK_EVENTS_STREAM, ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", STOCK_EVENTS_STREAM, STOCK_CONSUMER_NAME);

        // 굿즈 예약 이벤트 Stream 구독 (재고 예약 성공/실패)
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, "goods-consumer-1"),
                StreamOffset.create(GOODS_EVENTS_STREAM, ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", GOODS_EVENTS_STREAM, "goods-consumer-1");

        // Order 정보 요청 Stream 구독 (Payment 서비스 등에서 Order 정보 요청)
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, ORDER_INFO_CONSUMER_NAME),
                StreamOffset.create("order-info-requests", ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", "order-info-requests", ORDER_INFO_CONSUMER_NAME);

        // 사용자 주소 응답 Stream 구독 (User 서비스에서 주소 조회 응답)
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, ADDRESS_CONSUMER_NAME),
                StreamOffset.create(ADDRESS_RESPONSE_STREAM, ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", ADDRESS_RESPONSE_STREAM, ADDRESS_CONSUMER_NAME);

        // 결제 이벤트 Stream 구독 (Payment 서비스에서 결제 완료 알림)
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, PAYMENT_CONSUMER_NAME),
                StreamOffset.create(PAYMENT_EVENTS_STREAM, ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", PAYMENT_EVENTS_STREAM, PAYMENT_CONSUMER_NAME);

        // Store 조회 응답 Stream 구독
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, "store-lookup-consumer-1"),
                StreamOffset.create(STORE_LOOKUP_RESPONSES_STREAM, ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", STORE_LOOKUP_RESPONSES_STREAM, "store-lookup-consumer-1");

        // 스케줄 예약 결과 Stream 구독
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, "schedule-consumer-1"),
                StreamOffset.create(SCHEDULE_EVENTS_STREAM, ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", SCHEDULE_EVENTS_STREAM, "schedule-consumer-1");

        // 주문 조회 요청 Stream 구독 (Payment 서비스 등)
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, "order-query-consumer-1"),
                StreamOffset.create(ORDER_QUERY_STREAM, ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", ORDER_QUERY_STREAM, "order-query-consumer-1");

        // 주문 상태 업데이트 요청 Stream 구독 (Payment 서비스 등)
        container.receive(
                Consumer.from(ORDER_CONSUMER_GROUP, "order-update-consumer-1"),
                StreamOffset.create(ORDER_UPDATE_STREAM, ReadOffset.lastConsumed()),
                (org.springframework.data.redis.stream.StreamListener) orderRedisStreamListener
        );
        log.info("📝 Redis Stream Consumer 등록: {} - {}", ORDER_UPDATE_STREAM, "order-update-consumer-1");

        try {
            container.start();
            log.info("🚀 Order Redis Stream Listener Container 시작됨");

            // Container 상태 확인
            if (container.isRunning()) {
                log.info("✅ StreamMessageListenerContainer 실행 중 확인됨");
            } else {
                log.warn("⚠️ StreamMessageListenerContainer가 실행되지 않음");
            }
        } catch (Exception e) {
            log.error("❌ StreamMessageListenerContainer 시작 실패: {}", e.getMessage(), e);
            throw e;
        }

        return container;
    }
}
