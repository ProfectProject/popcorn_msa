package com.popcorn.checkIns.config;

import com.popcorn.checkIns.event.listener.CheckInRedisStreamListener;
import com.popcorn.checkIns.constants.EventConstants;
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
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.connection.stream.StreamRecords;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;

/**
 * CheckIns 서비스 Redis Stream 이벤트 설정
 *
 * 표준 CheckIns 이벤트 및 외부 서비스 이벤트를 Stream으로 수신하여 처리
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "redis.events.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class CheckInRedisEventConfig {

    private final CheckInRedisStreamListener checkInRedisStreamListener;
    private final RedisTemplate<String, Object> redisTemplate;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> checkInContainer;

    // EventConstants 사용으로 상수 통합 관리
    private static final String STANDARD_CHECKINS_EVENTS_STREAM = EventConstants.Streams.STANDARD_CHECKINS_EVENTS;
    private static final String PAYMENT_EVENTS_STREAM = EventConstants.Streams.PAYMENT_EVENTS;
    private static final String ORDER_EVENTS_STREAM = EventConstants.Streams.ORDER_EVENTS;
    private static final String CHECKIN_REQUESTS_STREAM = EventConstants.Streams.CHECKIN_REQUESTS;
    private static final String CHECKIN_EVENTS_STREAM = EventConstants.Streams.CHECKIN_EVENTS;

    // Consumer Group 이름
    private static final String CHECKIN_CONSUMER_GROUP = EventConstants.ConsumerGroups.CHECKIN_SERVICE_GROUP;
    private static final String STANDARD_CONSUMER_NAME = EventConstants.Consumers.STANDARD_CONSUMER;
    private static final String PAYMENT_CONSUMER_NAME = EventConstants.Consumers.PAYMENT_EVENTS_CONSUMER;
    private static final String ORDER_CONSUMER_NAME = EventConstants.Consumers.ORDER_EVENTS_CONSUMER;
    private static final String CHECKIN_REQUESTS_CONSUMER_NAME = EventConstants.Consumers.CHECKIN_REQUESTS_CONSUMER;
    private static final String CHECKIN_EVENTS_CONSUMER_NAME = EventConstants.Consumers.CHECKIN_EVENTS_CONSUMER;
    private static final String CONSUMER_REGISTER_LOG = "📝 Redis Stream Consumer 등록: {} - {}";

    @PostConstruct
    public void initializeStreamsAndConsumerGroups() {
        try {
            // 기존 Standard 이벤트 Consumer Group 생성
            createConsumerGroupIfNotExists(STANDARD_CHECKINS_EVENTS_STREAM);
            createConsumerGroupIfNotExists(PAYMENT_EVENTS_STREAM);
            createConsumerGroupIfNotExists(ORDER_EVENTS_STREAM);

            // 새로운 BaseEvent 기반 Consumer Group 생성
            createConsumerGroupIfNotExists(CHECKIN_REQUESTS_STREAM);
            createConsumerGroupIfNotExists(CHECKIN_EVENTS_STREAM);

            log.info("✅ CheckIns Service Redis Stream Consumer Groups 초기화 완료 (총 5개 스트림)");
        } catch (Exception e) {
            log.warn("⚠️ Redis Stream 초기화 중 오류 (정상 동작 가능): {}", e.getMessage());
        }
    }

    private void createConsumerGroupIfNotExists(String streamName) {
        try {
            redisTemplate.opsForStream().createGroup(streamName, ReadOffset.from("0"), CHECKIN_CONSUMER_GROUP);
            log.info("📝 CheckIns Consumer Group 생성: {} - {}", streamName, CHECKIN_CONSUMER_GROUP);
        } catch (Exception e) {
            if (isStreamMissing(e)) {
                String recordId = redisTemplate.opsForStream()
                        .add(StreamRecords.mapBacked(Map.of("_init", "1")).withStreamKey(streamName))
                        .getValue();
                if (recordId != null) {
                    redisTemplate.opsForStream().delete(streamName, recordId);
                }
                redisTemplate.opsForStream().createGroup(streamName, ReadOffset.from("0"), CHECKIN_CONSUMER_GROUP);
                log.info("📝 CheckIns Consumer Group 생성(MKSTREAM): {} - {}", streamName, CHECKIN_CONSUMER_GROUP);
            } else {
                log.debug("CheckIns Consumer Group 이미 존재: {} - {}", streamName, CHECKIN_CONSUMER_GROUP);
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
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> checkInStreamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        .batchSize(10)
                        .pollTimeout(Duration.ofMillis(500))
                        .errorHandler(t -> {
                            if (t.getCause() instanceof org.springframework.dao.QueryTimeoutException) {
                                log.warn("🔄 CheckIns Redis Stream 타임아웃 발생, 재시도 예정: {}", t.getMessage());
                            } else if (isConnectionClosedException(t)) {
                                log.debug("🔌 CheckIns Redis 연결 종료됨 (정상 종료 과정): {}", t.getMessage());
                            } else {
                                log.error("❌ CheckIns Redis Stream 처리 중 예상치 못한 오류: {}", t.getMessage(), t);
                            }
                        })
                        .build();

        checkInContainer = StreamMessageListenerContainer.create(connectionFactory, options);

        // 표준 CheckIns 이벤트 Stream 구독
        checkInContainer.receive(
                Consumer.from(CHECKIN_CONSUMER_GROUP, STANDARD_CONSUMER_NAME),
                StreamOffset.create(STANDARD_CHECKINS_EVENTS_STREAM, ReadOffset.lastConsumed()),
                checkInRedisStreamListener
        );
        log.info(CONSUMER_REGISTER_LOG, STANDARD_CHECKINS_EVENTS_STREAM, STANDARD_CONSUMER_NAME);

        // 결제 이벤트 Stream 구독 (향후 확장용)
        checkInContainer.receive(
                Consumer.from(CHECKIN_CONSUMER_GROUP, PAYMENT_CONSUMER_NAME),
                StreamOffset.create(PAYMENT_EVENTS_STREAM, ReadOffset.lastConsumed()),
                checkInRedisStreamListener
        );
        log.info(CONSUMER_REGISTER_LOG, PAYMENT_EVENTS_STREAM, PAYMENT_CONSUMER_NAME);

        // 주문 이벤트 Stream 구독 (향후 확장용)
        checkInContainer.receive(
                Consumer.from(CHECKIN_CONSUMER_GROUP, ORDER_CONSUMER_NAME),
                StreamOffset.create(ORDER_EVENTS_STREAM, ReadOffset.lastConsumed()),
                checkInRedisStreamListener
        );
        log.info(CONSUMER_REGISTER_LOG, ORDER_EVENTS_STREAM, ORDER_CONSUMER_NAME);

        // 새로운 BaseEvent 기반 CheckIn 요청 Stream 구독 (Payment → CheckIn)
        checkInContainer.receive(
                Consumer.from(CHECKIN_CONSUMER_GROUP, CHECKIN_REQUESTS_CONSUMER_NAME),
                StreamOffset.create(CHECKIN_REQUESTS_STREAM, ReadOffset.lastConsumed()),
                checkInRedisStreamListener
        );
        log.info(CONSUMER_REGISTER_LOG, CHECKIN_REQUESTS_STREAM, CHECKIN_REQUESTS_CONSUMER_NAME);

        // 새로운 BaseEvent 기반 CheckIn 이벤트 Stream 구독 (CheckIn → 다른 도메인)
        checkInContainer.receive(
                Consumer.from(CHECKIN_CONSUMER_GROUP, CHECKIN_EVENTS_CONSUMER_NAME),
                StreamOffset.create(CHECKIN_EVENTS_STREAM, ReadOffset.lastConsumed()),
                checkInRedisStreamListener
        );
        log.info(CONSUMER_REGISTER_LOG, CHECKIN_EVENTS_STREAM, CHECKIN_EVENTS_CONSUMER_NAME);

        try {
            checkInContainer.start();
            log.info("🚀 CheckIns Redis Stream Listener Container 시작됨");

            if (checkInContainer.isRunning()) {
                log.info("✅ CheckIns StreamMessageListenerContainer 실행 중 확인됨");
            } else {
                log.warn("⚠️ CheckIns StreamMessageListenerContainer가 실행되지 않음");
            }
        } catch (Exception e) {
            log.error("❌ CheckIns StreamMessageListenerContainer 시작 실패: {}", e.getMessage(), e);
            throw e;
        }

        return checkInContainer;
    }

    /**
     * 연결 종료 관련 예외인지 확인
     */
    private boolean isConnectionClosedException(Throwable t) {
        if (t == null) return false;

        String message = t.getMessage();
        if (message != null && message.toLowerCase().contains("connection closed")) {
            return true;
        }

        return isConnectionClosedException(t.getCause());
    }

    /**
     * 서비스 종료 시 Redis Stream 리스너 정리
     */
    @PreDestroy
    public void cleanupRedisStreams() {
        if (checkInContainer != null && checkInContainer.isRunning()) {
            try {
                log.info("🔌 CheckIns Redis Stream Listener Container 종료 중...");
                checkInContainer.stop();
                log.info("✅ CheckIns Redis Stream Listener Container 정상 종료됨");
            } catch (Exception e) {
                log.warn("⚠️ CheckIns Redis Stream Container 종료 중 오류: {}", e.getMessage());
            }
        }
    }
}
