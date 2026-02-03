# Redis → Kafka 전환 전략

## 목차
1. [현재 상황 분석](#현재-상황-분석)
2. [전환 목표 및 배경](#전환-목표-및-배경)
3. [전환 전략 개요](#전환-전략-개요)
4. [단계별 구현 계획](#단계별-구현-계획)
5. [기술적 고려사항](#기술적-고려사항)
6. [모니터링 및 검증](#모니터링-및-검증)
7. [리스크 관리](#리스크-관리)
8. [롤백 계획](#롤백-계획)

---

## 현재 상황 분석

### Redis Stream 기반 이벤트 아키텍처 현황

**운영 중인 Stream 목록:**
```
• order-events: 주문 생성, 상태 변경, 결제 요청
• payment-events: 결제 생성, 승인, 실패, 취소, 완료
• store-events: 재고 관리, 팝업 정보
• goods-events: 굿즈 예약 성공/실패
• schedule-events: 스케줄 예약 관련
• stock-events: 재고 차감 결과
• response-events: 가격, 팝업 정보 조회 응답
• checkin-events: 체크인 관련
• price-events: 가격 조회 요청
• popup-events: 팝업 정보 조회 요청
• address-events: 주소 조회 응답
```

**서비스별 Consumer 구성:**
- **Order Service**: 11개 Consumer (모든 Stream에서 필요한 이벤트 구독)
- **Stores Service**: 8개 Consumer (재고, 가격, 팝업 관련)
- **Payment Service**: 결제 관련 Consumer
- **Users Service**: 4개 Consumer (주소, 사용자 정보)
- **CheckIns Service**: 3개 Consumer (체크인 관련)

**현재 Redis 설정:**
```yaml
# 공통 설정
Redis 7.2-alpine
Port: 6379
Database: 0 (일부 서비스는 1)
Connection Pool:
  - Order Service: max-active 30, timeout 10s
  - 기타 Service: max-active 20, timeout 2s
```

**핵심 기능:**
1. **멱등성 처리**: RedisBasedIdempotencyService로 중복 요청 방지
2. **원자적 재고 처리**: Lua 스크립트 (hold_schedule.lua, hold_goods.lua 등)
3. **Consumer Group**: 서비스별 분산 처리
4. **배치 처리**: 서비스별 batchSize 설정 (5~50)

---

## 전환 목표 및 배경

### Kafka 전환의 필요성

**1. 확장성 제약**
- Redis 메모리 기반으로 대용량 메시지 처리 시 한계
- Stream 샤딩 복잡도 증가
- 수직 확장만 가능, 수평 확장 어려움

**2. 운영 효율성**
- 메시지 보존 정책 유연성 부족
- 모니터링 도구 제한
- 백업 및 복구 복잡성

**3. 생태계 한계**
- Kafka Connect, Schema Registry 등 통합 도구 부족
- Stream 처리 프레임워크 제한

### Kafka의 장점

**1. 확장성**
- 파티셔닝을 통한 수평 확장
- 클러스터 단위 부하 분산
- 처리량 선형 증가 가능

**2. 안정성**
- 복제본 기반 내결함성
- 리더 선출 및 자동 복구
- 영구 저장을 통한 데이터 보존

**3. 운영성**
- 풍부한 모니터링 도구 (Kafdrop, Kafka Manager 등)
- 메시지 압축 및 정리 정책
- 오프셋 기반 정확한 위치 추적

---

## 전환 전략 개요

### 전환 방식: 점진적 이중 처리 (Blue-Green 방식)

**기본 원칙:**
1. **기존 Redis 유지** 하면서 Kafka 추가 구축
2. **이중 발행/구독**으로 동시 처리
3. **단계별 검증** 후 Redis 제거
4. **언제든 롤백** 가능한 안전장치

**전환 우선순위:**

| 우선순위 | Stream | 이유 | 리스크 |
|----------|--------|------|--------|
| 높음 | order-events, payment-events | 핵심 비즈니스 로직 | 높음 |
| 중간 | stock-events, goods-events | 재고 일관성 중요 | 중간 |
| 낮음 | response-events, checkin-events | 조회성 데이터 | 낮음 |

---

## 단계별 구현 계획

### Phase 0: 인프라 준비 (1주)

**1. Kafka 클러스터 구축**
```yaml
# docker-compose.yml 업데이트
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    hostname: zookeeper
    container_name: zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper_data:/var/lib/zookeeper/data
      - zookeeper_log:/var/lib/zookeeper/log

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    hostname: broker
    container_name: broker
    depends_on:
      - zookeeper
    ports:
      - "29092:29092"
      - "9092:9092"
      - "9101:9101"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://broker:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_JMX_PORT: 9101
      KAFKA_JMX_HOSTNAME: localhost
      # 성능 최적화
      KAFKA_NUM_NETWORK_THREADS: 8
      KAFKA_NUM_IO_THREADS: 8
      KAFKA_SOCKET_SEND_BUFFER_BYTES: 102400
      KAFKA_SOCKET_RECEIVE_BUFFER_BYTES: 102400
      KAFKA_SOCKET_REQUEST_MAX_BYTES: 104857600
    volumes:
      - kafka_data:/var/lib/kafka/data

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    depends_on:
      - kafka
    ports:
      - "8080:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: broker:29092
      KAFKA_CLUSTERS_0_JMXPORT: 9101

volumes:
  kafka_data:
  zookeeper_data:
  zookeeper_log:
```

**2. 공통 라이브러리 Kafka 설정**
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

        // 에러 핸들링
        factory.setErrorHandler(new SeekToCurrentErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate(),
                (record, ex) -> new TopicPartition(record.topic() + "-dlq", record.partition())),
            new FixedBackOff(1000L, 3)
        ));

        return factory;
    }

    // === 메인 토픽 생성 (11개) ===
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(6)                                        // 6개 파티션
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.SEGMENT_MS_CONFIG, "3600000")      // 1시간 세그먼트
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.SEGMENT_MS_CONFIG, "3600000")
                .build();
    }

    @Bean
    public NewTopic stockEventsTopic() {
        return TopicBuilder.name("stock-events")
                .partitions(8)                                        // 높은 동시성
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "259200000")  // 3일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "lz4")    // 빠른 압축
                .config(TopicConfig.SEGMENT_MS_CONFIG, "1800000")      // 30분 세그먼트
                .build();
    }

    @Bean
    public NewTopic goodsEventsTopic() {
        return TopicBuilder.name("goods-events")
                .partitions(5)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "259200000")  // 3일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    public NewTopic scheduleEventsTopic() {
        return TopicBuilder.name("schedule-events")
                .partitions(5)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "259200000")  // 3일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    public NewTopic storeEventsTopic() {
        return TopicBuilder.name("store-events")
                .partitions(4)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "259200000")  // 3일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    public NewTopic responseEventsTopic() {
        return TopicBuilder.name("response-events")
                .partitions(8)                                        // 높은 처리량
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "3600000")    // 1시간
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "lz4")
                .config(TopicConfig.SEGMENT_MS_CONFIG, "300000")       // 5분 세그먼트
                .build();
    }

    @Bean
    public NewTopic priceEventsTopic() {
        return TopicBuilder.name("price-events")
                .partitions(4)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "3600000")    // 1시간
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    public NewTopic popupEventsTopic() {
        return TopicBuilder.name("popup-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "3600000")    // 1시간
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    public NewTopic addressEventsTopic() {
        return TopicBuilder.name("address-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "3600000")    // 1시간
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    public NewTopic checkinEventsTopic() {
        return TopicBuilder.name("checkin-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")   // 1일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    // === DLQ (Dead Letter Queue) 토픽 생성 (11개) ===
    @Bean
    public NewTopic orderEventsDlqTopic() {
        return TopicBuilder.name("order-events-dlq")
                .partitions(2)                                        // DLQ는 적은 파티션
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") // 30일 보존
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")   // 최대 압축
                .build();
    }

    @Bean
    public NewTopic paymentEventsDlqTopic() {
        return TopicBuilder.name("payment-events-dlq")
                .partitions(2)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") // 30일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic stockEventsDlqTopic() {
        return TopicBuilder.name("stock-events-dlq")
                .partitions(2)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic goodsEventsDlqTopic() {
        return TopicBuilder.name("goods-events-dlq")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic scheduleEventsDlqTopic() {
        return TopicBuilder.name("schedule-events-dlq")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic storeEventsDlqTopic() {
        return TopicBuilder.name("store-events-dlq")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic responseEventsDlqTopic() {
        return TopicBuilder.name("response-events-dlq")
                .partitions(2)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7일 (조회는 짧게)
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic priceEventsDlqTopic() {
        return TopicBuilder.name("price-events-dlq")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic popupEventsDlqTopic() {
        return TopicBuilder.name("popup-events-dlq")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic addressEventsDlqTopic() {
        return TopicBuilder.name("address-events-dlq")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }

    @Bean
    public NewTopic checkinEventsDlqTopic() {
        return TopicBuilder.name("checkin-events-dlq")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7일
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .build();
    }
}
```

**3. Kafka 토픽 및 파티션 설계**

**간소화된 토픽 구성 (5개 토픽):**

| 토픽명 | 파티션 수 | 복제본 | 보존기간 | 우선순위 | 통합 내용 |
|--------|-----------|--------|----------|----------|-----------|
| `order-events` | 3 | 1 | 7일 | 높음 | 주문 생성, 상태 변경, 결제 요청 |
| `payment-events` | 3 | 1 | 7일 | 높음 | 결제 생성, 승인, 실패, 취소, 완료 |
| `inventory-events` | 4 | 1 | 3일 | 높음 | stock + goods + schedule 통합 |
| `store-events` | 2 | 1 | 3일 | 중간 | 매장 정보, 팝업 관리 |
| `checkin-events` | 2 | 1 | 1일 | 낮음 | 체크인, QR 코드 관련 |

**총 토픽: 5개, 총 파티션: 14개**

**제거된 이벤트 (기존 Redis/HTTP 유지):**
- `response-events`: 조회 응답 → HTTP 동기 호출 유지
- `price-events`: 가격 조회 → Redis 캐시 활용
- `popup-events`: 팝업 조회 → store-events에 통합
- `address-events`: 주소 조회 → HTTP 동기 호출 유지

**4. 상세 Kafka 설정**
```yaml
# .env 업데이트
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ENABLED=false

# application-local.yml (각 서비스별 상세 설정)

# === Order Service 설정 ===
kafka:
  enabled: ${KAFKA_ENABLED:false}
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

  # Producer 설정
  producer:
    acks: all                              # 모든 복제본 확인
    retries: 2147483647                   # 최대 재시도
    enable-idempotence: true              # 멱등성 활성화
    max-in-flight-requests-per-connection: 5

    # 배치 및 성능 설정
    batch-size: 32768                     # 32KB 배치 크기
    linger-ms: 10                         # 10ms 대기 후 전송
    buffer-memory: 33554432               # 32MB 버퍼
    compression-type: snappy              # 압축 타입

    # 타임아웃 설정
    request-timeout-ms: 30000             # 30초 요청 타임아웃
    delivery-timeout-ms: 120000           # 2분 전송 타임아웃

  # Consumer 설정
  consumer:
    group-id: order-service-group
    enable-auto-commit: false             # 수동 커밋
    auto-offset-reset: earliest           # 처음부터 읽기
    isolation-level: read_committed       # 커밋된 메시지만 읽기

    # 세션 및 하트비트
    session-timeout-ms: 30000             # 30초 세션 타임아웃
    heartbeat-interval-ms: 10000          # 10초 하트비트

    # 폴링 설정
    max-poll-records: 100                 # 한 번에 최대 100개
    max-poll-interval-ms: 300000          # 5분 폴링 간격
    fetch-min-bytes: 2048                 # 최소 2KB 페치
    fetch-max-wait-ms: 1000               # 1초 최대 대기

  # 리스너 컨테이너 설정
  listener:
    concurrency: 6                        # 6개 병렬 처리
    ack-mode: manual_immediate            # 즉시 수동 ACK

# === Payment Service 설정 ===
kafka:
  enabled: ${KAFKA_ENABLED:false}
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

  producer:
    acks: all
    retries: 2147483647
    enable-idempotence: true
    batch-size: 16384                     # 16KB (결제는 메시지 작음)
    linger-ms: 5
    compression-type: snappy

  consumer:
    group-id: payment-service-group
    enable-auto-commit: false
    auto-offset-reset: earliest
    max-poll-records: 50                  # 결제는 안정성 우선
    session-timeout-ms: 45000             # 더 긴 세션 타임아웃
    heartbeat-interval-ms: 15000

  listener:
    concurrency: 3                        # 적은 동시성
    ack-mode: manual_immediate

# === Stores Service 설정 ===
kafka:
  enabled: ${KAFKA_ENABLED:false}
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

  producer:
    acks: all
    retries: 2147483647
    enable-idempotence: true
    batch-size: 65536                     # 64KB (재고 데이터 큼)
    linger-ms: 15
    compression-type: lz4                 # 빠른 압축

  consumer:
    group-id: store-service-group
    enable-auto-commit: false
    auto-offset-reset: earliest
    max-poll-records: 200                 # 높은 처리량
    fetch-min-bytes: 4096                 # 4KB
    fetch-max-wait-ms: 500                # 빠른 응답

  listener:
    concurrency: 8                        # 높은 동시성
    ack-mode: manual_immediate

# === Users Service 설정 ===
kafka:
  enabled: ${KAFKA_ENABLED:false}
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

  consumer:
    group-id: users-service-group
    enable-auto-commit: false
    auto-offset-reset: earliest
    max-poll-records: 30                  # 낮은 처리량
    session-timeout-ms: 60000             # 1분

  listener:
    concurrency: 2
    ack-mode: manual_immediate

# === CheckIns Service 설정 ===
kafka:
  enabled: ${KAFKA_ENABLED:false}
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

  consumer:
    group-id: checkins-service-group
    enable-auto-commit: false
    auto-offset-reset: earliest
    max-poll-records: 20

  listener:
    concurrency: 2
    ack-mode: manual_immediate
```

**5. Kafka Streams 설정 (재고 처리용)**
```yaml
# Stores Service - Kafka Streams 설정
kafka:
  streams:
    enabled: false                        # Phase 2에서 활성화
    application-id: inventory-processor
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

    # 성능 설정
    num-stream-threads: 4                 # 4개 스트림 스레드
    commit-interval-ms: 1000              # 1초마다 커밋
    cache-max-bytes-buffering: 10485760   # 10MB 캐시

    # 상태 저장소 설정
    state-dir: /tmp/kafka-streams
    replication-factor: 1

    # 처리 보장
    processing-guarantee: exactly_once_v2  # 정확히 한 번 처리

    # 토폴로지 최적화
    topology-optimization: all

**6. Consumer Group별 상세 설정**

| Consumer Group | 토픽 구독 | 동시성 | 배치크기 | 폴링주기 | 세션타임아웃 | ACK모드 |
|----------------|-----------|--------|----------|----------|-------------|---------|
| `order-service-group` | payment-events, inventory-events (2개) | 3 | 100 | 1000ms | 30s | manual |
| `payment-service-group` | order-events (1개) | 2 | 50 | 500ms | 45s | manual |
| `store-service-group` | inventory-events, store-events (2개) | 4 | 150 | 500ms | 30s | manual |
| `checkins-service-group` | checkin-events (1개) | 2 | 20 | 2000ms | 60s | manual |

**7. 배치 처리 및 주기 설정**

```yaml
# === 전체 토픽 요약 ===
kafka:
  topics:
    # 메인 토픽: 5개 (총 14개 파티션)
    main-topics: 5
    main-partitions: 14

    # DLQ 토픽: 3개 (총 3개 파티션) - 핵심만
    dlq-topics: 3
    dlq-partitions: 3

    # 전체: 8개 토픽, 17개 파티션
    total-topics: 8
    total-partitions: 17

  # === 배치 처리 설정 ===
  batch-processing:
    # Producer 배치
    order-service:
      batch-size: 32KB        # 주문 이벤트 (중간 크기)
      linger-ms: 10           # 10ms 대기
      max-request-size: 1MB   # 최대 요청 크기

    payment-service:
      batch-size: 16KB        # 결제 이벤트 (작은 크기)
      linger-ms: 5            # 빠른 전송
      max-request-size: 512KB

    store-service:
      batch-size: 64KB        # 재고 이벤트 (큰 크기)
      linger-ms: 15           # 배치 효율성
      max-request-size: 2MB

    # Consumer 배치
    high-throughput:          # order, store, stock events
      max-poll-records: 200
      fetch-min-bytes: 4KB
      fetch-max-wait-ms: 500

    medium-throughput:        # payment, goods, schedule events
      max-poll-records: 100
      fetch-min-bytes: 2KB
      fetch-max-wait-ms: 1000

    low-throughput:           # response, checkin, address events
      max-poll-records: 50
      fetch-min-bytes: 1KB
      fetch-max-wait-ms: 2000

  # === 주기별 설정 ===
  intervals:
    # 하트비트 및 세션
    session-timeout: 30000ms        # 30초 (기본)
    heartbeat-interval: 10000ms     # 10초 (session-timeout / 3)

    # 폴링 및 커밋
    max-poll-interval: 300000ms     # 5분 (최대 폴링 간격)
    auto-commit-interval: 5000ms    # 5초 (사용 안함, 수동 커밋)

    # 연결 및 요청
    connections-max-idle: 540000ms  # 9분 연결 유지
    request-timeout: 30000ms        # 30초 요청 타임아웃

    # 메타데이터 갱신
    metadata-max-age: 300000ms      # 5분 메타데이터 갱신

  # === 오토커밋 설정 (모두 비활성화) ===
  auto-commit:
    enabled: false                  # 모든 서비스에서 수동 커밋 사용
    interval-ms: 5000              # 참고용 (사용 안함)

    # 수동 커밋 전략
    strategy: "manual_immediate"    # 즉시 수동 ACK
    retry-on-failure: true          # 실패 시 재시도
    max-retry-attempts: 3           # 최대 3회 재시도

  # === 재시도 및 백오프 설정 ===
  retry-backoff:
    initial-delay: 1000ms           # 초기 1초 대기
    max-delay: 32000ms              # 최대 32초 대기
    multiplier: 2.0                 # 지수 백오프
    max-attempts: 3                 # 최대 3회 시도

  # === 압축 및 성능 ===
  compression:
    order-events: "snappy"          # 균형잡힌 압축
    payment-events: "snappy"        # 안정성 우선
    stock-events: "lz4"             # 빠른 압축 (높은 처리량)
    response-events: "lz4"          # 빠른 압축 (조회)
    dlq-topics: "gzip"              # 최대 압축 (저장 공간 절약)

  # === 모니터링 주기 ===
  monitoring:
    metrics-collection: 30000ms     # 30초마다 메트릭 수집
    consumer-lag-check: 60000ms     # 1분마다 Consumer Lag 확인
    consistency-verification: 300000ms # 5분마다 일관성 검증
    health-check: 15000ms           # 15초마다 헬스체크
    alert-evaluation: 120000ms      # 2분마다 알림 조건 확인

# === Consumer별 상세 스레드 설정 ===
spring:
  kafka:
    listener:
      # Order Service (6개 토픽 구독)
      order-service:
        concurrency: 6              # 토픽당 1개씩
        poll-timeout: 1000          # 1초 폴링
        ack-timeout: 60000          # 1분 ACK 타임아웃

      # Payment Service (1개 토픽 구독)
      payment-service:
        concurrency: 3              # 안정성을 위해 적게
        poll-timeout: 500           # 빠른 폴링
        ack-timeout: 30000          # 30초 ACK

      # Store Service (5개 토픽 구독)
      store-service:
        concurrency: 8              # 높은 처리량
        poll-timeout: 500           # 빠른 폴링
        ack-timeout: 45000          # 45초 ACK

      # Users Service (1개 토픽 구독)
      users-service:
        concurrency: 2              # 낮은 처리량
        poll-timeout: 2000          # 느린 폴링
        ack-timeout: 60000          # 1분 ACK

      # CheckIns Service (1개 토픽 구독)
      checkins-service:
        concurrency: 2              # 낮은 처리량
        poll-timeout: 2000          # 느린 폴링
        ack-timeout: 60000          # 1분 ACK

**8. 파티션별 처리량 예상 및 모니터링**

```yaml
# === 처리량 예상 (메시지/초) ===
expected-throughput:
  # 높은 처리량 토픽
  order-events:
    partitions: 3
    messages-per-second: 300      # 파티션당 ~100 msg/s
    bytes-per-second: "1.5MB"     # 파티션당 ~512KB/s

  payment-events:
    partitions: 3
    messages-per-second: 200      # 파티션당 ~67 msg/s
    bytes-per-second: "1MB"       # 파티션당 ~341KB/s

  inventory-events:
    partitions: 4
    messages-per-second: 600      # 파티션당 ~150 msg/s (stock+goods+schedule 통합)
    bytes-per-second: "3MB"       # 파티션당 ~768KB/s

  # 낮은 처리량 토픽
  store-events:
    partitions: 2
    messages-per-second: 80       # 파티션당 ~40 msg/s
    bytes-per-second: "320KB"     # 파티션당 ~164KB/s

  checkin-events:
    partitions: 2
    messages-per-second: 40       # 파티션당 ~20 msg/s
    bytes-per-second: "160KB"     # 파티션당 ~82KB/s

# === Consumer Lag 임계값 ===
consumer-lag-thresholds:
  critical:                       # 긴급 알림
    order-events: 500             # 500개 메시지
    payment-events: 300           # 300개 메시지
    inventory-events: 1000        # 1000개 메시지 (통합으로 높은 처리량)

  warning:                        # 경고 알림
    order-events: 200             # 200개 메시지
    payment-events: 100           # 100개 메시지
    inventory-events: 500         # 500개 메시지

  time-based:                     # 시간 기반 임계값
    critical-latency: 30000ms     # 30초 지연
    warning-latency: 15000ms      # 15초 지연

# === 파티션 리밸런싱 전략 ===
partition-rebalancing:
  strategy: "RangeAssignor"       # 기본 전략
  session-timeout: 30000ms        # 30초
  rebalance-timeout: 60000ms      # 1분

  # 파티션 할당 최적화
  optimization:
    order-service:
      preferred-partitions: [0,1,2]      # order-events 파티션 0,1,2
    store-service:
      preferred-partitions: [0,1,2,3]    # stock-events 파티션 0,1,2,3
    payment-service:
      preferred-partitions: [3,4,5]      # payment-events 파티션 3,4,5

# === JVM 및 성능 튜닝 ===
jvm-tuning:
  # Kafka Producer JVM 설정
  producer:
    heap-size: "512m"
    gc-options: "-XX:+UseG1GC -XX:MaxGCPauseMillis=20"
    kafka-specific: "-Djava.awt.headless=true"

  # Kafka Consumer JVM 설정
  consumer:
    heap-size: "1g"               # Consumer는 더 많은 메모리
    gc-options: "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
    buffer-memory: "256m"

  # Kafka Streams JVM 설정 (재고 처리용)
  streams:
    heap-size: "2g"               # Streams는 가장 많은 메모리
    gc-options: "-XX:+UseG1GC -XX:MaxGCPauseMillis=100"
    state-store-cache: "100m"

# === 모니터링 메트릭 상세 설정 ===
monitoring-metrics:
  # Producer 메트릭
  producer:
    - "kafka.producer:type=producer-metrics,client-id=*"
    - "kafka.producer:type=producer-topic-metrics,client-id=*,topic=*"
    - "record-send-rate"
    - "record-error-rate"
    - "batch-size-avg"

  # Consumer 메트릭
  consumer:
    - "kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*"
    - "records-lag-max"
    - "records-consumed-rate"
    - "fetch-latency-avg"

  # Streams 메트릭 (Phase 2부터)
  streams:
    - "kafka.streams:type=stream-metrics,client-id=*"
    - "process-latency-avg"
    - "process-rate"
    - "commit-latency-avg"

# === 알림 규칙 상세 설정 ===
alerting-rules:
  consumer-lag:
    expression: "kafka_consumer_lag_sum > 1000"
    for: "5m"
    severity: "warning"
    message: "Consumer lag exceeded threshold"

  error-rate:
    expression: "rate(kafka_consumer_errors_total[5m]) > 0.1"
    for: "2m"
    severity: "critical"
    message: "High error rate detected"

  throughput-drop:
    expression: "rate(kafka_consumer_records_consumed_total[5m]) < 0.7 * avg_over_time(rate(kafka_consumer_records_consumed_total[5m])[1h])"
    for: "10m"
    severity: "warning"
    message: "Throughput dropped significantly"

  disk-usage:
    expression: "kafka_log_size_bytes / kafka_log_retention_bytes > 0.8"
    for: "15m"
    severity: "warning"
    message: "Kafka disk usage high"
```
```
```

### Phase 1: 핵심 이벤트 이중 처리 (2주)

**토픽 생성 스크립트**
```bash
#!/bin/bash
# create-kafka-topics.sh

BOOTSTRAP_SERVER="localhost:9092"

echo "=== Kafka 토픽 생성 시작 ==="

# 메인 토픽 생성 (5개)
echo "메인 토픽 생성 중..."

kafka-topics.sh --create --bootstrap-server $BOOTSTRAP_SERVER \
  --topic order-events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 --config compression.type=snappy \
  --config segment.ms=3600000

kafka-topics.sh --create --bootstrap-server $BOOTSTRAP_SERVER \
  --topic payment-events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 --config compression.type=snappy \
  --config segment.ms=3600000

kafka-topics.sh --create --bootstrap-server $BOOTSTRAP_SERVER \
  --topic inventory-events --partitions 4 --replication-factor 1 \
  --config retention.ms=259200000 --config compression.type=lz4 \
  --config segment.ms=1800000

kafka-topics.sh --create --bootstrap-server $BOOTSTRAP_SERVER \
  --topic store-events --partitions 2 --replication-factor 1 \
  --config retention.ms=259200000 --config compression.type=snappy

kafka-topics.sh --create --bootstrap-server $BOOTSTRAP_SERVER \
  --topic checkin-events --partitions 2 --replication-factor 1 \
  --config retention.ms=86400000 --config compression.type=snappy

echo "메인 토픽 생성 완료 (5개, 14개 파티션)"

# DLQ 토픽 생성 (3개 - 핵심만)
echo "DLQ 토픽 생성 중..."

kafka-topics.sh --create --bootstrap-server $BOOTSTRAP_SERVER \
  --topic order-events-dlq --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --config compression.type=gzip

kafka-topics.sh --create --bootstrap-server $BOOTSTRAP_SERVER \
  --topic payment-events-dlq --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --config compression.type=gzip

kafka-topics.sh --create --bootstrap-server $BOOTSTRAP_SERVER \
  --topic inventory-events-dlq --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --config compression.type=gzip

echo "DLQ 토픽 생성 완료 (3개, 3개 파티션)"

# 토픽 목록 확인
echo "=== 생성된 토픽 목록 ==="
kafka-topics.sh --list --bootstrap-server $BOOTSTRAP_SERVER

echo "=== 토픽 상세 정보 ==="
kafka-topics.sh --describe --bootstrap-server $BOOTSTRAP_SERVER

echo "총 8개 토픽, 17개 파티션 생성 완료!"
```

**1주차: order-events 전환**

**이중 발행 구현**
```java
// order/src/main/java/com/popcorn/order/event/HybridEventPublisher.java
@Service
@Slf4j
public class HybridEventPublisher {

    private final RedisEventPublisher redisEventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${migration.dual-publish:true}")
    private boolean dualPublishMode;

    public void publishOrderEvent(String eventType, Object eventData) {
        String eventId = generateEventId();

        // 1. Redis 발행 (기존 시스템 - 우선)
        try {
            redisEventPublisher.publishOrderEvent(eventType, eventData);
            log.info("Redis 이벤트 발행 성공: {} - {}", eventType, eventId);
        } catch (Exception e) {
            log.error("Redis 이벤트 발행 실패: {} - {}", eventType, eventId, e);
            throw e; // Redis 실패 시 전체 실패
        }

        // 2. Kafka 발행 (검증용 - 실패해도 계속 진행)
        if (kafkaEnabled && dualPublishMode) {
            CompletableFuture.runAsync(() -> {
                try {
                    // 이벤트 엔벨로프 생성
                    EventEnvelope envelope = EventEnvelope.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .eventTime(Instant.now())
                        .source("order-service")
                        .schemaVersion("v1.0")
                        .payload(eventData)
                        .build();

                    // 파티션 키 설정 (순서 보장)
                    String partitionKey = extractPartitionKey(eventType, eventData);

                    kafkaTemplate.send("order-events", partitionKey, envelope)
                        .addCallback(
                            result -> log.info("Kafka 이벤트 발행 성공: {} - {}", eventType, eventId),
                            failure -> log.error("Kafka 이벤트 발행 실패: {} - {}", eventType, eventId, failure)
                        );
                } catch (Exception e) {
                    log.error("Kafka 이벤트 발행 중 예외: {} - {}", eventType, eventId, e);
                    // Kafka 실패는 무시하고 계속 진행
                }
            });
        }
    }

    private String extractPartitionKey(String eventType, Object eventData) {
        // 이벤트 타입별 파티션 키 추출
        if (eventData instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) eventData;
            if (data.containsKey("orderId")) {
                return data.get("orderId").toString();
            }
            if (data.containsKey("userId")) {
                return data.get("userId").toString();
            }
        }
        return eventType; // 기본값
    }

    private String generateEventId() {
        return UUID.randomUUID().toString();
    }
}
```

**Kafka Consumer 구현**
```java
// order/src/main/java/com/popcorn/order/event/PaymentKafkaEventListener.java
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
@Slf4j
public class PaymentKafkaEventListener {

    private final PaymentEventHandler paymentEventHandler;
    private final RedisBasedIdempotencyService idempotencyService;

    @KafkaListener(
        topics = "payment-events",
        groupId = "order-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(
            @Payload EventEnvelope envelope,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String partitionKey,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment ack) {

        String eventId = envelope.getEventId();
        String eventType = envelope.getEventType();

        log.info("Kafka 이벤트 수신: {} - {} (파티션: {}, 오프셋: {})",
            eventType, eventId, partition, timestamp);

        try {
            // 멱등성 검증 (기존 Redis 멱등성 서비스 활용)
            String idempotencyKey = "kafka:" + eventType + ":" + eventId;
            if (idempotencyService.isAlreadyProcessed(idempotencyKey)) {
                log.info("이미 처리된 이벤트 무시: {} - {}", eventType, eventId);
                ack.acknowledge();
                return;
            }

            // 이벤트 처리
            switch (eventType) {
                case "payment-approved":
                    idempotencyService.executeIdempotently(idempotencyKey, () -> {
                        paymentEventHandler.handlePaymentApproved(envelope.getPayload());
                        return null;
                    });
                    break;
                case "payment-failed":
                    idempotencyService.executeIdempotently(idempotencyKey, () -> {
                        paymentEventHandler.handlePaymentFailed(envelope.getPayload());
                        return null;
                    });
                    break;
                case "payment-cancelled":
                    idempotencyService.executeIdempotently(idempotencyKey, () -> {
                        paymentEventHandler.handlePaymentCancelled(envelope.getPayload());
                        return null;
                    });
                    break;
                default:
                    log.warn("알 수 없는 이벤트 타입: {} - {}", eventType, eventId);
            }

            ack.acknowledge();
            log.info("Kafka 이벤트 처리 완료: {} - {}", eventType, eventId);

        } catch (Exception e) {
            log.error("Kafka 이벤트 처리 실패: {} - {}", eventType, eventId, e);
            // ACK하지 않음으로써 재시도 유발
            throw e;
        }
    }
}
```

**공통 이벤트 엔벨로프**
```java
// common-lib/src/main/java/com/popcorn/common/event/EventEnvelope.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {
    private String eventId;
    private String eventType;
    private Instant eventTime;
    private String source;
    private String schemaVersion;
    private Object payload;

    @JsonIgnore
    public <T> T getPayload(Class<T> clazz) {
        if (payload instanceof Map) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.convertValue(payload, clazz);
        }
        return clazz.cast(payload);
    }
}
```

**2주차: payment-events 전환**

동일한 패턴으로 Payment Service에서 이중 발행/구독 구현

### Phase 2: 재고 관련 이벤트 전환 (2주)

**Kafka Streams를 이용한 재고 상태 관리**
```java
// stores/src/main/java/com/popcorn/store/stream/InventoryStreamProcessor.java
@Component
@ConditionalOnProperty(prefix = "kafka.streams", name = "enabled", havingValue = "true")
@Slf4j
public class InventoryStreamProcessor {

    @Autowired
    private KafkaStreams kafkaStreams;

    @EventListener
    public void handleApplicationReady(ApplicationReadyEvent event) {
        StreamsBuilder builder = new StreamsBuilder();
        buildTopology(builder);

        kafkaStreams = new KafkaStreams(builder.build(), getStreamsConfig());
        kafkaStreams.setUncaughtExceptionHandler((thread, exception) -> {
            log.error("Kafka Streams 예외 발생", exception);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });

        kafkaStreams.start();
        log.info("재고 Kafka Streams 시작됨");
    }

    private void buildTopology(StreamsBuilder builder) {
        // 재고 상태 저장소
        KTable<String, InventoryState> inventoryStore = builder
            .stream("stock-events", Consumed.with(Serdes.String(), getJsonSerde()))
            .groupByKey()
            .aggregate(
                InventoryState::new,
                this::updateInventoryState,
                Materialized.<String, InventoryState, KeyValueStore<Bytes, byte[]>>as("inventory-store")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(getInventorySerde())
            );

        // 굿즈 예약 요청 처리
        builder.stream("goods-events", Consumed.with(Serdes.String(), getJsonSerde()))
            .filter((key, event) -> "goods-reservation-requested".equals(event.getEventType()))
            .leftJoin(inventoryStore, this::processGoodsReservation)
            .filter((key, result) -> result != null)
            .to("stock-events", Produced.with(Serdes.String(), getJsonSerde()));

        // 스케줄 예약 요청 처리
        builder.stream("schedule-events", Consumed.with(Serdes.String(), getJsonSerde()))
            .filter((key, event) -> "schedule-reservation-requested".equals(event.getEventType()))
            .leftJoin(inventoryStore, this::processScheduleReservation)
            .filter((key, result) -> result != null)
            .to("stock-events", Produced.with(Serdes.String(), getJsonSerde()));
    }

    private InventoryState updateInventoryState(String key, EventEnvelope event, InventoryState current) {
        String eventType = event.getEventType();
        Map<String, Object> payload = (Map<String, Object>) event.getPayload();

        switch (eventType) {
            case "stock-initialized":
                return current.withStock(getIntValue(payload, "quantity"));
            case "stock-reserved":
                return current.withReserved(current.getReserved() + getIntValue(payload, "quantity"));
            case "stock-released":
                return current.withReserved(current.getReserved() - getIntValue(payload, "quantity"));
            case "stock-deducted":
                int deducted = getIntValue(payload, "quantity");
                return current.withStock(current.getStock() - deducted)
                             .withReserved(current.getReserved() - deducted);
            default:
                return current;
        }
    }

    private EventEnvelope processGoodsReservation(String key, EventEnvelope request, InventoryState inventory) {
        Map<String, Object> payload = (Map<String, Object>) request.getPayload();
        int requestedQty = getIntValue(payload, "quantity");

        if (inventory != null && inventory.getAvailableStock() >= requestedQty) {
            // 예약 성공
            return EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("goods-reservation-success")
                .eventTime(Instant.now())
                .source("inventory-stream")
                .schemaVersion("v1.0")
                .payload(Map.of(
                    "reservationId", payload.get("reservationId"),
                    "goodsId", key,
                    "quantity", requestedQty,
                    "userId", payload.get("userId")
                ))
                .build();
        } else {
            // 예약 실패
            return EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("goods-reservation-failed")
                .eventTime(Instant.now())
                .source("inventory-stream")
                .schemaVersion("v1.0")
                .payload(Map.of(
                    "reservationId", payload.get("reservationId"),
                    "goodsId", key,
                    "reason", "재고 부족",
                    "availableStock", inventory != null ? inventory.getAvailableStock() : 0
                ))
                .build();
        }
    }

    // 기타 헬퍼 메서드들...
}
```

**재고 상태 클래스**
```java
// stores/src/main/java/com/popcorn/store/stream/InventoryState.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryState {
    private int stock = 0;
    private int reserved = 0;
    private Instant lastUpdated = Instant.now();

    public int getAvailableStock() {
        return stock - reserved;
    }

    public InventoryState withStock(int newStock) {
        return new InventoryState(newStock, this.reserved, Instant.now());
    }

    public InventoryState withReserved(int newReserved) {
        return new InventoryState(this.stock, newReserved, Instant.now());
    }
}
```

### Phase 3: 조회 이벤트 전환 (1주)

**응답 시간 최적화를 위한 설정**
```java
// 조회 관련 토픽은 파티션 수 증가
@Bean
public NewTopic responseEventsTopic() {
    return TopicBuilder.name("response-events")
            .partitions(6) // 높은 병렬 처리
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "3600000") // 1시간
            .config(TopicConfig.SEGMENT_MS_CONFIG, "60000") // 1분
            .build();
}
```

### Phase 4: 검증 및 최적화 (1주)

**성능 비교 및 검증 도구**
```java
// common-lib/src/main/java/com/popcorn/common/monitoring/EventProcessingMetrics.java
@Component
public class EventProcessingMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter redisEventCount;
    private final Counter kafkaEventCount;
    private final Timer redisProcessingTime;
    private final Timer kafkaProcessingTime;

    public EventProcessingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.redisEventCount = Counter.builder("events.processed")
            .tag("source", "redis")
            .register(meterRegistry);
        this.kafkaEventCount = Counter.builder("events.processed")
            .tag("source", "kafka")
            .register(meterRegistry);
        this.redisProcessingTime = Timer.builder("events.processing.time")
            .tag("source", "redis")
            .register(meterRegistry);
        this.kafkaProcessingTime = Timer.builder("events.processing.time")
            .tag("source", "kafka")
            .register(meterRegistry);
    }

    public void recordRedisEvent(String eventType, Duration processingTime) {
        redisEventCount.increment(Tags.of("type", eventType));
        redisProcessingTime.record(processingTime);
    }

    public void recordKafkaEvent(String eventType, Duration processingTime) {
        kafkaEventCount.increment(Tags.of("type", eventType));
        kafkaProcessingTime.record(processingTime);
    }
}
```

---

## 기술적 고려사항

### 메시지 순서 보장

**파티셔닝 전략:**
```java
// 이벤트별 파티션 키 설정
public class PartitionKeyExtractor {

    public static String extractKey(String eventType, Object eventData) {
        Map<String, Object> data = (Map<String, Object>) eventData;

        switch (eventType) {
            case "order-created":
            case "order-updated":
            case "order-cancelled":
                return "order:" + data.get("orderId");

            case "payment-approved":
            case "payment-failed":
                return "payment:" + data.get("paymentId");

            case "goods-reservation-requested":
            case "stock-deducted":
                return "goods:" + data.get("goodsId");

            case "schedule-reservation-requested":
                return "schedule:" + data.get("scheduleId");

            default:
                return eventType;
        }
    }
}
```

### 스키마 진화 및 호환성

**버전 관리 전략:**
```java
// 스키마 버전별 처리
@Component
public class EventSchemaHandler {

    public Object handleSchemaEvolution(EventEnvelope envelope) {
        String version = envelope.getSchemaVersion();

        switch (version) {
            case "v1.0":
                return handleV1(envelope);
            case "v2.0":
                return handleV2(envelope);
            default:
                throw new UnsupportedSchemaVersionException(version);
        }
    }

    private Object handleV1(EventEnvelope envelope) {
        // v1.0 스키마 처리
        return envelope.getPayload();
    }

    private Object handleV2(EventEnvelope envelope) {
        // v2.0 스키마 처리 (필드 추가/변경 등)
        Map<String, Object> payload = (Map<String, Object>) envelope.getPayload();

        // 신규 필드 기본값 설정
        if (!payload.containsKey("newField")) {
            payload.put("newField", "defaultValue");
        }

        return payload;
    }
}
```

### 에러 처리 및 재시도 전략

**Dead Letter Queue 설정:**
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
    public ErrorHandler seekToCurrentErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new SeekToCurrentErrorHandler(recoverer,
            new FixedBackOff(1000L, 3) // 1초 간격으로 3회 재시도
        );
    }
}
```

---

## 모니터링 및 검증

### 핵심 메트릭 모니터링

**1. 처리량 메트릭:**
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

**2. 성능 대시보드 설정:**
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

### 일관성 검증

**비교 테스트 도구:**
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

        if (!extraInKafka.isEmpty()) {
            log.warn("Kafka에 추가로 있는 이벤트: {}", extraInKafka);
        }

        double consistencyRate = calculateConsistencyRate(redisSet, kafkaSet);
        Metrics.gauge("event.consistency.rate", consistencyRate);

        if (consistencyRate < 0.99) { // 99% 미만
            alertingService.sendAlert("이벤트 일관성 임계값 미달", consistencyRate);
        }
    }
}
```

---

## 리스크 관리

### 주요 리스크와 대응 방안

| 리스크 | 발생 확률 | 영향도 | 대응 방안 | 모니터링 지표 |
|--------|-----------|--------|-----------|---------------|
| 메시지 중복/손실 | 중간 | 높음 | 이중 처리, 멱등성 보장 | 일관성 비율 |
| 처리 지연 증가 | 높음 | 중간 | 파티션 확장, 컨슈머 튜닝 | 응답시간, Consumer Lag |
| 재고 일관성 문제 | 낮음 | 높음 | Kafka Streams 상태 검증 | 재고 불일치 카운트 |
| Kafka 클러스터 장애 | 낮음 | 높음 | Redis 유지, 자동 롤백 | 클러스터 상태 |
| 메모리 사용량 증가 | 중간 | 중간 | Consumer 배치 크기 조정 | JVM 힙 사용량 |

### 알림 및 대응 절차

**1. 자동 알림 설정:**
```java
@Component
public class KafkaAlertingService {

    @EventListener
    public void handleKafkaDown(KafkaDownEvent event) {
        // Kafka 장애 시 자동 롤백
        configurationService.setProperty("kafka.enabled", false);

        notificationService.sendUrgentAlert(
            "Kafka 클러스터 장애 감지 - 자동 롤백 실행",
            "Redis 단독 모드로 전환됨"
        );
    }

    @EventListener
    public void handleHighConsumerLag(HighConsumerLagEvent event) {
        if (event.getLagMs() > 10000) { // 10초 초과
            // Consumer 인스턴스 확장
            kafkaConsumerManager.scaleUp(event.getTopic());

            notificationService.sendAlert(
                "Kafka Consumer Lag 임계값 초과",
                String.format("Topic: %s, Lag: %dms", event.getTopic(), event.getLagMs())
            );
        }
    }
}
```

**2. 대응 절차:**
```yaml
# 장애 대응 플레이북
procedures:
  kafka_down:
    1. 자동 롤백 확인
    2. Redis 상태 점검
    3. 서비스 영향도 확인
    4. Kafka 복구 후 재전환

  high_lag:
    1. Consumer 확장
    2. 파티션 밸런싱
    3. 배치 크기 조정
    4. 지연 원인 분석

  consistency_issue:
    1. 이중 처리 중단
    2. 데이터 정합성 검증
    3. 불일치 데이터 복구
    4. 원인 분석 및 개선
```

---

## 롤백 계획

### 롤백 트리거 조건

**자동 롤백 조건:**
- Kafka 클러스터 완전 다운 (30초 이상)
- Consumer Lag 30초 초과
- 에러율 5% 초과 (5분 연속)
- 메모리 사용량 90% 초과

**수동 롤백 고려 조건:**
- 처리량 70% 이하로 감소 (10분 연속)
- 응답시간 200ms 초과 (95th percentile, 10분 연속)
- 일관성 비율 95% 미만 (30분 연속)

### 롤백 절차

**1. 긴급 롤백 (자동):**
```java
@Component
public class EmergencyRollbackService {

    @EventListener(condition = "#event.severity == 'CRITICAL'")
    public void executeEmergencyRollback(SystemAlertEvent event) {
        log.error("긴급 롤백 실행: {}", event.getMessage());

        // 1. Kafka 프로듀서 즉시 중단
        configurationService.setProperty("kafka.enabled", false);

        // 2. 이중 발행 모드 중단
        configurationService.setProperty("migration.dual-publish", false);

        // 3. Kafka 컨슈머 graceful shutdown
        kafkaConsumerManager.shutdownGracefully();

        // 4. Redis 단독 모드 활성화
        redisEventProcessingService.enableStandaloneMode();

        // 5. 알림 발송
        notificationService.sendUrgentAlert(
            "긴급 롤백 완료",
            "시스템이 Redis 단독 모드로 전환되었습니다."
        );
    }
}
```

**2. 계획된 롤백 (수동):**
```yaml
# 단계별 롤백 스크립트
rollback_steps:
  step1:
    description: "Kafka Producer 중단"
    command: "kubectl set env deployment/order-service KAFKA_ENABLED=false"
    validation: "신규 Kafka 메시지 발행 중단 확인"

  step2:
    description: "기존 Kafka 메시지 처리 완료 대기"
    command: "wait_for_consumer_lag_zero.sh"
    timeout: "300s"

  step3:
    description: "Kafka Consumer 중단"
    command: "kubectl scale deployment/kafka-consumer --replicas=0"
    validation: "모든 Kafka Consumer 종료 확인"

  step4:
    description: "Redis 전용 설정 적용"
    command: "kubectl apply -f redis-only-config.yaml"
    validation: "Redis 이벤트 처리 정상 확인"
```

**3. 데이터 정합성 복구:**
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

## 전환 일정 및 마일스톤

| 단계 | 기간 | 주요 활동 | 완료 기준 | 책임자 |
|------|------|----------|-----------|---------|
| **Phase 0** | 1주 (1-7일) | • Kafka 클러스터 구축<br>• 공통 라이브러리 설정<br>• 토픽 생성 및 설정<br>• 모니터링 도구 설정 | • Kafka 클러스터 정상 동작<br>• 모든 토픽 생성 완료<br>• Kafka UI 접근 가능<br>• 기본 메트릭 수집 | DevOps팀 |
| **Phase 1** | 2주 (8-21일) | • order/payment 이벤트 이중 발행<br>• Kafka Consumer 구현<br>• 멱등성 검증<br>• 성능 비교 테스트 | • 이중 발행 성공률 99%<br>• Kafka Consumer 정상 동작<br>• 성능 차이 10% 이내 | 백엔드팀 |
| **Phase 2** | 2주 (22-35일) | • 재고 이벤트 Streams 구현<br>• Lua 스크립트 → Streams 전환<br>• 재고 일관성 검증<br>• 부하 테스트 | • 재고 일관성 99.9%<br>• Streams 처리 안정화<br>• 부하 테스트 통과 | 백엔드팀 |
| **Phase 3** | 1주 (36-42일) | • 조회 이벤트 전환<br>• 응답시간 최적화<br>• 전체 시스템 통합 테스트 | • 모든 이벤트 Kafka 처리<br>• 응답시간 목표 달성<br>• 통합 테스트 통과 | 전체팀 |
| **Phase 4** | 1주 (43-49일) | • 성능 벤치마크<br>• 안정성 검증<br>• Redis Stream 제거<br>• 문서화 완료 | • 벤치마크 목표 달성<br>• 7일간 무장애 운영<br>• Redis Stream 완전 제거 | 전체팀 |

**총 소요 기간: 7주**

### 성공 기준 및 KPI

**성능 목표:**
- **처리량**: Redis 대비 95% 이상 (목표: 동일 수준)
- **지연시간**: 95th percentile 100ms 이하
- **가용성**: 99.9% 이상 (기존 수준 유지)
- **에러율**: 0.1% 이하

**품질 목표:**
- **메시지 일관성**: 99.9% 이상
- **순서 보장**: 파티션 내 100%
- **중복 처리**: 멱등성 통해 방지
- **데이터 손실**: 0%

이 전환 전략을 통해 안전하고 효율적으로 Redis에서 Kafka로 전환하여 더 나은 확장성과 안정성을 확보할 수 있습니다.