# 팝콘 MSA 이벤트 아키텍처 개선 가이드

## 📋 현재 상황 분석

### 현재 구현된 시스템
- **이벤트 브로커**: Redis Stream 기반
- **이벤트 발행**: 직접 Redis Stream 발행 방식
- **이벤트 소비**: Spring Redis Stream Listener
- **패턴**: Event-Driven Architecture (Redis 기반)

### 확인된 문제점
1. **아키텍처 문서와 실제 구현 불일치**
   - 기존 문서: Debezium CDC + Outbox 패턴 ❌
   - 실제 구현: 직접 발행 방식 ✅
   - **→ 문서를 실제 구현에 맞게 수정 필요**

2. **Deprecated API 사용**
   - `SeekToCurrentErrorHandler` → `DefaultErrorHandler` 교체 필요 (Spring Kafka 2.8+)

3. **폴링 방식과 직접 발행 혼재**
   - `@Scheduled` 폴링 + `KafkaTemplate` 직접 발행이 혼재
   - 일관성 있는 발행 방식으로 통일 필요

4. **이벤트 처리 에러 핸들링**
   - 현재: Redis Stream ACK 기반 처리
   - 문제: pending 메시지 누적 시 수동 정리 필요

---

## 🎯 개선 방안 선택

### Option 1: Redis Stream 기반 개선 (현재 유지)
**현재 시스템을 개선하여 안정성 향상**

### Option 2: Kafka 직접 발행 전환 (권장)
**CDC 없는 단순하고 표준적인 Kafka 아키텍처**

---

## 🚀 Option 1: Redis Stream 아키텍처 개선

### 1. 개선된 Redis Stream 설정

```yaml
# application-local.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 10000ms
      database: 0
      lettuce:
        pool:
          max-active: 30
          max-idle: 10
          min-idle: 5
          max-wait: 2000ms
        shutdown-timeout: 2000ms

# 이벤트 스트림 설정
redis:
  streams:
    retry:
      max-attempts: 3
      backoff-delay: 1000ms
      exponential-multiplier: 2.0
    dead-letter:
      enabled: true
      retention-hours: 24
    consumer-groups:
      auto-create: true
      start-from: latest

logging:
  level:
    com.popcorn.*.event: DEBUG
    org.springframework.data.redis.stream: INFO
```

### 2. 강화된 이벤트 발행자 (Publisher)

```java
// common-lib/src/main/java/com/popcorn/common/event/RedisEventPublisher.java
@Component
@Slf4j
@RequiredArgsConstructor
public class EnhancedRedisEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${redis.streams.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${redis.streams.dead-letter.enabled:true}")
    private boolean deadLetterEnabled;

    /**
     * 이벤트 발행 (재시도 및 DLQ 포함)
     */
    public String publishEvent(String streamName, String eventType, Object eventData) {
        return publishEventWithRetry(streamName, eventType, eventData, 0);
    }

    private String publishEventWithRetry(String streamName, String eventType, Object eventData, int attempt) {
        try {
            Map<String, Object> eventMap = createEventMap(eventType, eventData);

            // 파티션 키 추출 (순서 보장)
            String partitionKey = extractPartitionKey(eventType, eventData);
            if (partitionKey != null) {
                eventMap.put("partitionKey", partitionKey);
            }

            // Redis Stream 발행
            RecordId recordId = redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                    .ofMap(eventMap)
                    .withStreamKey(streamName));

            // 메트릭 수집
            recordPublishMetrics(streamName, eventType, "success");

            log.info("✅ [REDIS] 이벤트 발행 성공 - stream: {}, type: {}, recordId: {}",
                streamName, eventType, recordId);

            return recordId.getValue();

        } catch (Exception e) {
            log.error("❌ [REDIS] 이벤트 발행 실패 - stream: {}, type: {}, attempt: {}, error: {}",
                streamName, eventType, attempt + 1, e.getMessage());

            if (attempt < maxRetryAttempts - 1) {
                // 재시도 (지수 백오프)
                long delay = (long) (1000 * Math.pow(2, attempt));
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return publishEventWithRetry(streamName, eventType, eventData, attempt + 1);
            } else {
                // 최종 실패 시 DLQ 처리
                if (deadLetterEnabled) {
                    publishToDeadLetterQueue(streamName, eventType, eventData, e);
                }
                recordPublishMetrics(streamName, eventType, "failed");
                throw new EventPublishException("이벤트 발행 최종 실패: " + e.getMessage(), e);
            }
        }
    }

    private Map<String, Object> createEventMap(String eventType, Object eventData) throws Exception {
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("eventType", eventType);
        eventMap.put("eventId", UUID.randomUUID().toString());
        eventMap.put("eventTime", LocalDateTime.now().toString());
        eventMap.put("data", objectMapper.writeValueAsString(eventData));
        return eventMap;
    }

    private String extractPartitionKey(String eventType, Object eventData) {
        try {
            Map<String, Object> data = objectMapper.convertValue(eventData, Map.class);

            switch (eventType) {
                case "schedule-reservation-requested":
                case "schedule-reservation-success":
                case "schedule-reservation-failed":
                    return "schedule:" + data.get("popupId");

                case "goods-reservation-requested":
                case "goods-reserved":
                case "goods-reservation-failed":
                    return "goods:" + data.get("goodsId");

                case "order-created":
                case "order-updated":
                case "order-cancelled":
                    return "order:" + data.get("orderId");

                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("파티션 키 추출 실패 - eventType: {}, using default", eventType);
            return null;
        }
    }

    private void publishToDeadLetterQueue(String streamName, String eventType, Object eventData, Exception error) {
        try {
            String dlqStream = streamName + "-dlq";
            Map<String, Object> dlqEvent = Map.of(
                "originalStream", streamName,
                "eventType", eventType,
                "eventData", objectMapper.writeValueAsString(eventData),
                "errorMessage", error.getMessage(),
                "errorTime", LocalDateTime.now().toString(),
                "retryCount", maxRetryAttempts
            );

            redisTemplate.opsForStream().add(dlqStream, dlqEvent);
            log.warn("💀 [DLQ] 이벤트를 DLQ로 전송 - stream: {}, dlq: {}", streamName, dlqStream);

        } catch (Exception dlqError) {
            log.error("💥 [DLQ] DLQ 전송마저 실패 - stream: {}, error: {}", streamName, dlqError.getMessage());
        }
    }

    private void recordPublishMetrics(String streamName, String eventType, String status) {
        meterRegistry.counter("redis.stream.publish",
            "stream", streamName,
            "eventType", eventType,
            "status", status
        ).increment();
    }
}
```

### 3. 강화된 이벤트 소비자 (Consumer)

```java
// common-lib/src/main/java/com/popcorn/common/event/RedisEventConsumer.java
@Component
@Slf4j
@RequiredArgsConstructor
public abstract class EnhancedRedisEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${redis.streams.retry.max-attempts:3}")
    private int maxRetryAttempts;

    /**
     * 이벤트 처리 (재시도 및 ACK 포함)
     */
    protected void handleEventSafely(String streamName, String consumerGroup, MapRecord<String, Object, Object> record) {
        String eventType = null;
        try {
            eventType = (String) record.getValue().get("eventType");
            String eventId = (String) record.getValue().get("eventId");

            log.debug("🔔 [REDIS] 이벤트 수신 - stream: {}, recordId: {}, eventType: {}",
                streamName, record.getId(), eventType);

            // 중복 처리 방지 (Idempotency)
            if (isAlreadyProcessed(eventId)) {
                log.debug("⚠️ [REDIS] 이미 처리된 이벤트 - eventId: {}", eventId);
                acknowledgeEvent(streamName, consumerGroup, record.getId());
                return;
            }

            // 이벤트 처리 실행
            processEvent(eventType, record.getValue());

            // 처리 완료 마킹
            markAsProcessed(eventId);

            // ACK 처리
            acknowledgeEvent(streamName, consumerGroup, record.getId());

            // 메트릭 수집
            recordConsumerMetrics(streamName, eventType, "success");

            log.debug("✅ [REDIS] 이벤트 처리 완료 - stream: {}, recordId: {}", streamName, record.getId());

        } catch (Exception e) {
            log.error("❌ [REDIS] 이벤트 처리 실패 - stream: {}, recordId: {}, eventType: {}, error: {}",
                streamName, record.getId(), eventType, e.getMessage(), e);

            handleProcessingError(streamName, consumerGroup, record, e);
        }
    }

    /**
     * 서브클래스에서 구현할 실제 이벤트 처리 로직
     */
    protected abstract void processEvent(String eventType, Map<String, Object> eventData) throws Exception;

    private void handleProcessingError(String streamName, String consumerGroup, MapRecord<String, Object, Object> record, Exception error) {
        try {
            // 재시도 횟수 확인
            int retryCount = getRetryCount(record.getId().getValue());

            if (retryCount < maxRetryAttempts) {
                // 재시도 카운트 증가
                incrementRetryCount(record.getId().getValue());

                // 재시도 지연 (지수 백오프)
                long delay = (long) (1000 * Math.pow(2, retryCount));
                scheduleRetry(streamName, consumerGroup, record, delay);

                log.warn("🔄 [RETRY] 이벤트 재시도 예약 - recordId: {}, attempt: {}, delay: {}ms",
                    record.getId(), retryCount + 1, delay);
            } else {
                // 최대 재시도 초과 - DLQ 전송
                sendToDLQ(streamName, record, error);
                acknowledgeEvent(streamName, consumerGroup, record.getId());

                log.error("💀 [DLQ] 최대 재시도 초과, DLQ 전송 - recordId: {}", record.getId());
            }

            recordConsumerMetrics(streamName, (String) record.getValue().get("eventType"), "failed");

        } catch (Exception retryError) {
            log.error("💥 [ERROR] 에러 처리 중 추가 에러 발생 - recordId: {}, error: {}",
                record.getId(), retryError.getMessage());
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return redisTemplate.hasKey("processed:event:" + eventId);
    }

    private void markAsProcessed(String eventId) {
        redisTemplate.opsForValue().set("processed:event:" + eventId, "true", Duration.ofDays(1));
    }

    private void acknowledgeEvent(String streamName, String consumerGroup, RecordId recordId) {
        redisTemplate.opsForStream().acknowledge(streamName, consumerGroup, recordId);
    }

    private int getRetryCount(String recordId) {
        String key = "retry:count:" + recordId;
        String count = (String) redisTemplate.opsForValue().get(key);
        return count != null ? Integer.parseInt(count) : 0;
    }

    private void incrementRetryCount(String recordId) {
        String key = "retry:count:" + recordId;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    private void scheduleRetry(String streamName, String consumerGroup, MapRecord<String, Object, Object> record, long delayMs) {
        // Redis 기반 지연 실행 (Sorted Set 활용)
        String retryQueue = "retry:queue:" + streamName;
        long executeAt = System.currentTimeMillis() + delayMs;

        try {
            String recordData = objectMapper.writeValueAsString(Map.of(
                "streamName", streamName,
                "consumerGroup", consumerGroup,
                "recordId", record.getId().getValue(),
                "eventData", record.getValue()
            ));

            redisTemplate.opsForZSet().add(retryQueue, recordData, executeAt);
        } catch (Exception e) {
            log.error("재시도 스케줄링 실패 - recordId: {}, error: {}", record.getId(), e.getMessage());
        }
    }

    private void sendToDLQ(String streamName, MapRecord<String, Object, Object> record, Exception error) {
        try {
            String dlqStream = streamName + "-dlq";
            Map<String, Object> dlqEvent = Map.of(
                "originalStreamName", streamName,
                "originalRecordId", record.getId().getValue(),
                "eventData", record.getValue(),
                "errorMessage", error.getMessage(),
                "failedTime", LocalDateTime.now().toString(),
                "finalRetryCount", maxRetryAttempts
            );

            redisTemplate.opsForStream().add(dlqStream, dlqEvent);
        } catch (Exception dlqError) {
            log.error("DLQ 전송 실패 - recordId: {}, error: {}", record.getId(), dlqError.getMessage());
        }
    }

    private void recordConsumerMetrics(String streamName, String eventType, String status) {
        meterRegistry.counter("redis.stream.consume",
            "stream", streamName,
            "eventType", eventType,
            "status", status
        ).increment();
    }
}
```

### 4. 실제 서비스에서의 구현 예시

```java
// order/src/main/java/com/popcorn/order/event/OrderRedisEventConsumer.java
@Component
@Slf4j
public class OrderRedisEventConsumer extends EnhancedRedisEventConsumer {

    private final OrderEventHandler orderEventHandler;
    private final StoreEventHandler storeEventHandler;

    @Override
    protected void processEvent(String eventType, Map<String, Object> eventData) throws Exception {
        switch (eventType) {
            case "schedule-reservation-success":
                orderEventHandler.handleScheduleReservationSuccess(eventData);
                break;
            case "schedule-reservation-failed":
                orderEventHandler.handleScheduleReservationFailed(eventData);
                break;
            case "goods-reserved":
                orderEventHandler.handleGoodsReserved(eventData);
                break;
            case "goods-reservation-failed":
                orderEventHandler.handleGoodsReservationFailed(eventData);
                break;
            default:
                log.debug("🔔 [ORDER] 처리하지 않는 이벤트 타입 - type: {}", eventType);
        }
    }

    @StreamListener("schedule-events")
    public void handleScheduleEvents(MapRecord<String, Object, Object> record) {
        handleEventSafely("schedule-events", "order-service-group", record);
    }

    @StreamListener("goods-events")
    public void handleGoodsEvents(MapRecord<String, Object, Object> record) {
        handleEventSafely("goods-events", "order-service-group", record);
    }
}
```

### 5. 재시도 스케줄러 구성

```java
// common-lib/src/main/java/com/popcorn/common/event/RetryScheduler.java
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisEventRetryScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 재시도 큐 처리 (1분마다)
     */
    @Scheduled(fixedDelay = 60000)
    public void processRetryQueue() {
        String[] retryQueues = {"retry:queue:schedule-events", "retry:queue:goods-events", "retry:queue:payment-events"};

        for (String queueName : retryQueues) {
            processRetryQueueItems(queueName);
        }
    }

    private void processRetryQueueItems(String queueName) {
        try {
            long currentTime = System.currentTimeMillis();

            // 실행 시간이 된 아이템들 가져오기
            Set<ZSetOperations.TypedTuple<Object>> readyItems =
                redisTemplate.opsForZSet().rangeByScoreWithScores(queueName, 0, currentTime);

            if (readyItems.isEmpty()) {
                return;
            }

            log.debug("🔄 [RETRY] 재시도 대상 아이템 발견 - queue: {}, count: {}", queueName, readyItems.size());

            for (ZSetOperations.TypedTuple<Object> item : readyItems) {
                try {
                    processRetryItem(item.getValue());

                    // 처리 완료된 아이템 제거
                    redisTemplate.opsForZSet().remove(queueName, item.getValue());

                } catch (Exception e) {
                    log.error("재시도 아이템 처리 실패 - queue: {}, error: {}", queueName, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("재시도 큐 처리 중 오류 - queue: {}, error: {}", queueName, e.getMessage());
        }
    }

    private void processRetryItem(Object retryData) throws Exception {
        Map<String, Object> retryInfo = objectMapper.readValue(retryData.toString(), Map.class);

        String streamName = (String) retryInfo.get("streamName");
        String consumerGroup = (String) retryInfo.get("consumerGroup");
        Map<String, Object> eventData = (Map<String, Object>) retryInfo.get("eventData");

        log.info("🔄 [RETRY] 이벤트 재시도 실행 - stream: {}, eventType: {}",
            streamName, eventData.get("eventType"));

        // 재시도 이벤트를 원본 스트림에 다시 발행
        redisTemplate.opsForStream().add(streamName, eventData);
    }
}
```

### 6. 모니터링 및 메트릭

```java
// common-lib/src/main/java/com/popcorn/common/event/RedisEventMetrics.java
@Component
@RequiredArgsConstructor
public class RedisEventMetrics {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 30000) // 30초마다
    public void collectStreamMetrics() {
        String[] streams = {"schedule-events", "goods-events", "payment-events"};

        for (String stream : streams) {
            collectStreamInfo(stream);
            collectConsumerGroupInfo(stream);
        }
    }

    private void collectStreamInfo(String streamName) {
        try {
            StreamInfo.XInfoStream streamInfo = redisTemplate.opsForStream().info(streamName);

            // 스트림 길이
            meterRegistry.gauge("redis.stream.length",
                Tags.of("stream", streamName), streamInfo.streamLength());

            // 소비자 그룹 수
            meterRegistry.gauge("redis.stream.consumer_groups",
                Tags.of("stream", streamName), streamInfo.groupCount());

        } catch (Exception e) {
            log.debug("스트림 정보 수집 실패 - stream: {}", streamName);
        }
    }

    private void collectConsumerGroupInfo(String streamName) {
        try {
            List<StreamInfo.XInfoGroup> groups = redisTemplate.opsForStream().groups(streamName);

            for (StreamInfo.XInfoGroup group : groups) {
                // Pending 메시지 수
                meterRegistry.gauge("redis.stream.pending",
                    Tags.of("stream", streamName, "group", group.groupName()),
                    group.pendingCount());
            }
        } catch (Exception e) {
            log.debug("소비자 그룹 정보 수집 실패 - stream: {}", streamName);
        }
    }
}
```

---

## 🚀 Option 2: Kafka + Transactional Outbox (CDC 없이)

### 1. 아키텍처 개요

```
[비즈니스 로직] → [DB + Outbox 테이블] → [스케줄러 폴링] → [Kafka 발행] → [processed 마킹]
```

**특징:**
- ✅ 트랜잭션 안정성 보장 (DB와 이벤트가 같은 트랜잭션)
- ✅ CDC 복잡성 제거
- ✅ 이벤트 발행 실패 시 자동 재시도
- ✅ 이중화 방지 (processed 상태 관리)

### 2. 공통 라이브러리 Kafka 설정

```yaml
# .env 업데이트
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ENABLED=false

# application-local.yml (각 서비스)
spring:
  kafka:
    enabled: ${KAFKA_ENABLED:false}
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      group-id: ${spring.application.name}-group
    producer:
      retries: 3
      acks: all
      enable-idempotence: true

# Spring Kafka Logging
logging:
  level:
    com.adjh.springbootkafka: DEBUG
    org:
      apache.kafka: INFO
      springframework.kafka: INFO
```

```java
// common-lib/src/main/java/com/popcorn/common/config/KafkaConfig.java
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
@Slf4j
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // 성능 최적화
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setDefaultTopic("default-topic");
        return template;
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // 안정성 설정
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // 성능 설정
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

        // 역직렬화 신뢰성
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.popcorn.*");
        props.put(JsonDeserializer.TYPE_MAPPINGS,
            "order:com.popcorn.common.dto.OrderEventDto," +
            "payment:com.popcorn.common.dto.PaymentEventDto");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3); // 동시 처리 스레드

        // ✅ DefaultErrorHandler 사용 (SeekToCurrentErrorHandler → DefaultErrorHandler 교체)
        factory.setCommonErrorHandler(createDefaultErrorHandler());

        return factory;
    }

    private DefaultErrorHandler createDefaultErrorHandler() {
        DeadLetterPublishingRecoverer dlqRecoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate(),
            (record, ex) -> new TopicPartition(record.topic() + "-dlq", record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(dlqRecoverer,
            new FixedBackOff(1000L, 3)); // 1초 간격으로 3회 재시도

        return errorHandler;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000") // 1일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .build();
    }
}

    // 토픽 자동 생성
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000") // 1일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
                .build();
    }

    @Bean
    public NewTopic storeEventsTopic() {
        return TopicBuilder.name("store-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .build();
    }
}
```

### 4. 순서 보장을 위한 파티셔닝 전략

```java
// common-lib/src/main/java/com/popcorn/common/kafka/PartitionKeyExtractor.java
@Component
public class PartitionKeyExtractor {

    public static String extractKey(String eventType, Object eventData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> data = mapper.convertValue(eventData, Map.class);

            switch (eventType) {
                case "ORDER-CREATED":
                case "ORDER-UPDATED":
                case "ORDER-CANCELLED":
                case "ORDER-PAID":
                    return "order:" + data.get("orderId");

                case "PAYMENT-APPROVED":
                case "PAYMENT-FAILED":
                case "PAYMENT-CANCELLED":
                    return "payment:" + data.get("orderId"); // 주문별 순서 보장

                case "GOODS-RESERVATION-REQUESTED":
                case "GOODS-RESERVED":
                case "GOODS-RESERVATION-FAILED":
                case "STOCK-DEDUCTED":
                    return "goods:" + data.get("goodsId");

                case "SCHEDULE-RESERVATION-REQUESTED":
                case "SCHEDULE-RESERVATION-SUCCESS":
                case "SCHEDULE-RESERVATION-FAILED":
                    return "schedule:" + data.get("popupId");

                default:
                    // 기본 파티셔닝 (라운드 로빈)
                    return null;
            }
        } catch (Exception e) {
            log.warn("파티션 키 추출 실패 - eventType: {}, using random partitioning", eventType);
            return null;
        }
    }
}
```

### 3. Transactional Outbox Pattern (CDC 없이)

#### Outbox 테이블 설계
```sql
-- 공통 Outbox 테이블
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_outbox_events_processed ON outbox_events(processed_at) WHERE processed_at IS NULL;
CREATE INDEX idx_outbox_events_created_at ON outbox_events(created_at);
```

#### Outbox 이벤트 발행자
```java
// common-lib/src/main/java/com/popcorn/common/outbox/OutboxEventPublisher.java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 트랜잭션 내에서 Outbox에 이벤트 저장
     */
    @Transactional
    public void publishEvent(String aggregateType, String aggregateId, String eventType, Object eventData) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .eventData(objectMapper.writeValueAsString(eventData))
                .createdAt(LocalDateTime.now())
                .build();

            outboxEventRepository.save(outboxEvent);

            log.debug("✅ [OUTBOX] 이벤트 저장 - aggregateType: {}, eventType: {}, aggregateId: {}",
                aggregateType, eventType, aggregateId);

        } catch (Exception e) {
            log.error("❌ [OUTBOX] 이벤트 저장 실패 - aggregateType: {}, eventType: {}, error: {}",
                aggregateType, eventType, e.getMessage());
            throw new OutboxPublishException("Outbox 이벤트 발행 실패: " + e.getMessage(), e);
        }
    }

    public void publishOrderEvent(String orderId, String eventType, Object eventData) {
        publishEvent("order", orderId, eventType, eventData);
    }

    public void publishStoreEvent(String storeId, String eventType, Object eventData) {
        publishEvent("store", storeId, eventType, eventData);
    }
}
```

#### Outbox 스케줄러 (폴링)
```java
// common-lib/src/main/java/com/popcorn/common/outbox/OutboxScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000) // 1초마다 폴링
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> unprocessedEvents = outboxEventRepository
            .findTop100ByProcessedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : unprocessedEvents) {
            try {
                // Kafka로 이벤트 발행
                String topic = event.getAggregateType() + "-events";
                String partitionKey = PartitionKeyExtractor.extractKey(event.getEventType(), event.getEventData());

                Map<String, Object> message = createKafkaMessage(event);
                kafkaTemplate.send(topic, partitionKey, message).get(); // 동기 발행

                // 발행 성공 시 processed 마킹
                event.markAsProcessed();
                outboxEventRepository.save(event);

                log.debug("✅ [OUTBOX] Kafka 발행 완료 - eventId: {}, topic: {}", event.getId(), topic);

            } catch (Exception e) {
                // 재시도 카운트 증가
                event.incrementRetryCount();
                outboxEventRepository.save(event);

                log.error("❌ [OUTBOX] Kafka 발행 실패 - eventId: {}, retryCount: {}, error: {}",
                    event.getId(), event.getRetryCount(), e.getMessage());

                // 최대 재시도 초과 시 DLQ 처리
                if (event.getRetryCount() > 3) {
                    handleFailedEvent(event, e);
                }
            }
        }
    }

    private Map<String, Object> createKafkaMessage(OutboxEvent event) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", event.getEventType());
        message.put("eventId", event.getId().toString());
        message.put("aggregateId", event.getAggregateId());
        message.put("timestamp", event.getCreatedAt().toString());
        message.put("data", objectMapper.readValue(event.getEventData(), Map.class));
        return message;
    }

    private void handleFailedEvent(OutboxEvent event, Exception error) {
        // DLQ나 에러 로그 테이블로 이동
        log.error("💀 [OUTBOX] 최대 재시도 초과 - eventId: {}", event.getId());
        event.markAsProcessed(); // 더 이상 처리하지 않도록 마킹
        outboxEventRepository.save(event);
    }
}
```

### 4. 이벤트 소비자 (Consumer)

```java
// order/src/main/java/com/popcorn/order/kafka/OrderKafkaConsumer.java
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderKafkaConsumer {

    private final OrderEventHandler orderEventHandler;

    @KafkaListener(
        topics = "store-events",
        groupId = "order-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleStoreEvents(
            @Payload Map<String, Object> message,
            Acknowledgment ack) {

        try {
            String eventType = (String) message.get("eventType");
            String eventId = (String) message.get("eventId");
            Map<String, Object> eventData = (Map<String, Object>) message.get("data");

            log.info("🔔 [KAFKA] 스토어 이벤트 수신 - eventType: {}, eventId: {}", eventType, eventId);

            // 중복 처리 방지
            if (isDuplicateEvent(eventId)) {
                log.debug("⚠️ [KAFKA] 중복 이벤트 스킵 - eventId: {}", eventId);
                ack.acknowledge();
                return;
            }

            // 이벤트 타입별 처리
            switch (eventType) {
                case "SCHEDULE_RESERVATION_SUCCESS":
                    orderEventHandler.handleScheduleReservationSuccess(eventData);
                    break;
                case "SCHEDULE_RESERVATION_FAILED":
                    orderEventHandler.handleScheduleReservationFailed(eventData);
                    break;
                case "GOODS_RESERVED":
                    orderEventHandler.handleGoodsReserved(eventData);
                    break;
                case "GOODS_RESERVATION_FAILED":
                    orderEventHandler.handleGoodsReservationFailed(eventData);
                    break;
                default:
                    log.debug("🔔 [KAFKA] 처리하지 않는 이벤트 - eventType: {}", eventType);
            }

            // 처리 완료 마킹
            markEventAsProcessed(eventId);

            // 수동 커밋
            ack.acknowledge();

            log.debug("✅ [KAFKA] 이벤트 처리 완료 - eventType: {}, eventId: {}", eventType, eventId);

        } catch (Exception e) {
            log.error("❌ [KAFKA] 이벤트 처리 실패 - error: {}", e.getMessage(), e);
            // 예외를 다시 던져서 재시도 로직 활성화
            throw new EventProcessingException("Kafka 이벤트 처리 실패: " + e.getMessage(), e);
        }
    }

    private boolean isDuplicateEvent(String eventId) {
        // Redis를 활용한 중복 체크 (예시)
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        // Redis에 처리 완료 마킹 (예시)
    }
}
```

### 4. 에러 처리 및 재시도 전략

#### Dead Letter Queue 설정
```java
@Component
public class KafkaErrorHandler {

    @Bean
    public DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, Object> template) {
        return new DeadLetterPublishingRecoverer(template,
            (record, ex) -> {
                String dlqTopic = record.topic() + "-dlq";
                return new TopicPartition(dlqTopic, record.partition());
            }
        );
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        // ✅ SeekToCurrentErrorHandler → DefaultErrorHandler 교체
        return new DefaultErrorHandler(recoverer,
            new FixedBackOff(1000L, 3) // 1초 간격으로 3회 재시도
        );
    }
}
```

### 5. 메시지 순서 보장

#### 파티셔닝 전략
```java
// 이벤트별 파티션 키 설정
public class PartitionKeyExtractor {

    public static String extractKey(String eventType, Object eventData) {
        Map<String, Object> data = (Map<String, Object>) eventData;

        switch (eventType) {
            case "ORDER-CREATED":
            case "ORDER-UPDATED":
            case "ORDER-CANCELLED":
                return "order:" + data.get("orderId");

            case "PAYMENT-APPROVED":
            case "PAYMENT-FAILED":
                return "payment:" + data.get("paymentId");

            case "GOODS-RESERVATION-REQUESTED":
            case "STOCK-DEDUCTED":
                return "goods:" + data.get("goodsId");

            case "SCHEDULE-RESERVATION-REQUESTED":
                return "schedule:" + data.get("scheduleId");

            default:
                return eventType;
        }
    }
}
```

### 6. 사용 예시

```java
// OrderService.java
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    public Order createOrder(CreateOrderRequest request) {
        // 1. 비즈니스 로직 실행
        Order order = Order.create(request);
        orderRepository.save(order);

        // 2. Outbox에 이벤트 저장 (같은 트랜잭션)
        OrderCreatedEvent event = OrderCreatedEvent.from(order);
        outboxEventPublisher.publishOrderEvent(order.getId(), "ORDER_CREATED", event);

        return order; // 스케줄러가 나중에 Kafka로 발행
    }
}
```

### 7. 모니터링 및 검증

#### 핵심 메트릭 모니터링
```java
@Component
public class KafkaMetricsCollector {

    @Scheduled(fixedRate = 30000) // 30초마다
    public void collectMetrics() {
        // Consumer Lag 모니터링
        AdminClient adminClient = AdminClient.create(kafkaProperties);

        try {
            Map<TopicPartition, OffsetAndMetadata> offsets =
                adminClient.listConsumerGroupOffsets("order-service-group").all().get();

            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long lag = calculateLag(tp, entry.getValue());

                Metrics.gauge("kafka.consumer.lag",
                    Tags.of("topic", tp.topic(), "partition", String.valueOf(tp.partition())),
                    lag);
            }
        } catch (Exception e) {
            log.error("메트릭 수집 중 오류", e);
        }
    }
}
```

#### 성능 대시보드 설정
```yaml
# Grafana 대시보드 정의
dashboard:
  panels:
    - title: "메시지 처리량 비교"
      type: "stat"
      targets:
        - expr: "rate(events_processed_total[5m])"
          legendFormat: "{{source}} - {{type}}"

    - title: "응답 시간 비교"
      type: "graph"
      targets:
        - expr: "histogram_quantile(0.95, rate(events_processing_time_seconds_bucket[5m]))"
          legendFormat: "{{source}} 95th percentile"

    - title: "Consumer Lag"
      type: "graph"
      targets:
        - expr: "kafka_consumer_lag"
          legendFormat: "{{topic}} - {{partition}}"
```

#### 일관성 검증
```java
@Component
public class ConsistencyVerifier {

    @Scheduled(fixedRate = 300000) // 5분마다
    public void verifyEventConsistency() {
        // Redis와 Kafka에서 처리된 이벤트 비교
        List<String> redisEvents = getRecentRedisEvents();
        List<String> kafkaEvents = getRecentKafkaEvents();

        Set<String> redisSet = new HashSet<>(redisEvents);
        Set<String> kafkaSet = new HashSet<>(kafkaEvents);

        // 누락된 이벤트 확인
        Set<String> missingInKafka = Sets.difference(redisSet, kafkaSet);
        Set<String> extraInKafka = Sets.difference(kafkaSet, redisSet);

        if (!missingInKafka.isEmpty()) {
            log.warn("Kafka에서 누락된 이벤트: {}", missingInKafka);
            alertingService.sendAlert("Kafka 이벤트 누락", missingInKafka);
        }

        double consistencyRate = calculateConsistencyRate(redisSet, kafkaSet);
        Metrics.gauge("event.consistency.rate", consistencyRate);

        if (consistencyRate < 0.99) { // 99% 미만
            alertingService.sendAlert("이벤트 일관성 임계값 미달", consistencyRate);
        }
    }
}
```

#### 데이터 정합성 복구
```java
@Service
public class DataRecoveryService {

    public void recoverInconsistentData() {
        // 1. 불일치 데이터 식별
        List<InconsistencyRecord> inconsistencies = findDataInconsistencies();

        for (InconsistencyRecord record : inconsistencies) {
            try {
                // 2. Redis 데이터를 소스로 복구
                Object redisData = redisDataService.getData(record.getKey());

                // 3. 애플리케이션 상태 보정
                applicationStateService.correctState(record.getKey(), redisData);

                // 4. 복구 로그 기록
                recoveryAuditService.logRecovery(record, "SUCCESS");

            } catch (Exception e) {
                recoveryAuditService.logRecovery(record, "FAILED", e.getMessage());
                log.error("데이터 복구 실패: {}", record.getKey(), e);
            }
        }
    }
}
```

---

## 📊 모니터링 및 운영

### 1. 메트릭 수집

```yaml
# application-local.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      service: ${spring.application.name}
      environment: local

# 커스텀 메트릭
custom:
  metrics:
    redis:
      enabled: true
      streams: ["schedule-events", "goods-events", "payment-events"]
    kafka:
      enabled: false
```

### 2. 헬스 체크

```java
// common-lib/src/main/java/com/popcorn/common/health/EventSystemHealthIndicator.java
@Component
public class EventSystemHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try {
            // Redis Stream 연결 테스트
            redisTemplate.opsForStream().info("schedule-events");

            // Pending 메시지 수 체크
            long pendingCount = getTotalPendingMessages();

            if (pendingCount > 1000) {
                return Health.down()
                    .withDetail("reason", "Too many pending messages")
                    .withDetail("pendingCount", pendingCount)
                    .build();
            }

            return Health.up()
                .withDetail("pendingMessages", pendingCount)
                .withDetail("status", "Redis Stream is healthy")
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("reason", e.getMessage())
                .build();
        }
    }
}
```

---

## 🚀 마이그레이션 가이드

### Redis Stream 유지 (현재 시스템 개선)

1. **현재 시스템 강화**
   ```bash
   # 1. 의존성 추가 (build.gradle)
   implementation 'io.micrometer:micrometer-registry-prometheus'

   # 2. 설정 업데이트 - 강화된 Redis Stream 설정 적용
   # 3. 에러 핸들링 개선
   # 4. 모니터링 강화
   ```

### Kafka 직접 발행 전환 (권장)

1. **인프라 구성**
   ```bash
   # Kafka 실행 (이미 docker-compose.yml에 있음)
   docker-compose up -d kafka

   # Kafka 토픽 생성
   docker exec kafka kafka-topics --create --topic order-events --partitions 3 --replication-factor 1 --bootstrap-server localhost:9092
   docker exec kafka kafka-topics --create --topic store-events --partitions 3 --replication-factor 1 --bootstrap-server localhost:9092
   ```

2. **애플리케이션 변경**
   ```bash
   # 1. Kafka 설정 활성화 (kafka.enabled=true)
   # 2. KafkaEventPublisher 구현
   # 3. Kafka Consumer 구현
   # 4. Redis Stream 코드를 Kafka로 변경
   # 5. SeekToCurrentErrorHandler → DefaultErrorHandler 교체
   ```

---

## 🎯 결론 및 권장사항

### 현재 상황에 따른 권장사항

1. **Redis Stream 유지 + 강화** (현재 시스템 개선)
   - ✅ 현재 시스템과 호환
   - ✅ 빠른 적용 가능
   - ✅ 검증된 안정성
   - ✅ 운영 복잡도 낮음

2. **Kafka 직접 발행 전환** (권장)
   - ✅ 표준적이고 강력한 메시징 시스템
   - ✅ 더 나은 처리량과 확장성
   - ✅ 풍부한 생태계 (모니터링, 운영 도구)
   - ✅ CDC 복잡성 없이 단순한 구조
   - ❌ 인프라 추가 필요 (이미 Kafka는 구성됨)

### 단계별 적용 계획

1. **1단계**: 문서와 실제 구현 통일 (즉시)
2. **2단계**: Deprecated API 교체 (SeekToCurrentErrorHandler → DefaultErrorHandler)
3. **3단계**: Kafka 직접 발행 방식 적용 (권장)

### 주요 개선 사항

- ❌ **CDC 복잡성 제거** (Debezium, WAL 설정 불필요)
- ✅ **Transactional Outbox Pattern** (트랜잭션 안정성 보장)
- ✅ **폴링 기반 이벤트 발행** (단순하고 안정적)
- ✅ **DefaultErrorHandler 사용** (Deprecated API 교체)
- ✅ **메시지 순서 보장** (파티셔닝 전략)
- ✅ **풍부한 모니터링** (Consumer Lag, 일관성 검증)
- ✅ **아키텍처 문서와 구현 통일**

### 핵심 특징

1. **트랜잭션 안정성**: DB와 이벤트가 같은 트랜잭션에서 처리
2. **복구 가능성**: Outbox 테이블을 통한 이벤트 추적 및 재발행
3. **단순성**: CDC 없이 스케줄러 폴링으로 간단한 구조
4. **확장성**: Kafka의 강력한 처리량과 확장성 활용
5. **모니터링**: 실시간 메트릭과 일관성 검증