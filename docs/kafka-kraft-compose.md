# Kafka KRaft Docker Compose (No ZooKeeper)

아래는 주키퍼 없이 KRaft 모드로 동작하는 `docker-compose.yml` 예시입니다.
기존 구성과 동일하게 `kafka-ui`를 포함했습니다.

```yaml
version: '3.8'
services:
  kafka:
    image: confluentinc/cp-kafka:7.4.0
    hostname: broker
    container_name: broker
    ports:
      - "29092:29092"
      - "9092:9092"
      - "9101:9101"
    environment:
      # KRaft 기본 설정
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@broker:29093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092,CONTROLLER://0.0.0.0:29093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://broker:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT

      # 단일 노드용 복제 설정
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0

      # JMX
      KAFKA_JMX_PORT: 9101
      KAFKA_JMX_HOSTNAME: localhost

      # 성능 최적화
      KAFKA_NUM_NETWORK_THREADS: 8
      KAFKA_NUM_IO_THREADS: 8
      KAFKA_SOCKET_SEND_BUFFER_BYTES: 102400
      KAFKA_SOCKET_RECEIVE_BUFFER_BYTES: 102400
      KAFKA_SOCKET_REQUEST_MAX_BYTES: 104857600

      # KRaft 스토리지 초기화 필요 (첫 실행 시)
      KAFKA_CLUSTER_ID: "0000000000000000000000"
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
```

## 주의사항
- `KAFKA_CLUSTER_ID`는 최초 포맷 시 사용하는 고유 ID입니다. 실제 운영에서는 `kafka-storage.sh random-uuid`로 생성한 값을 넣는 것을 권장합니다.
- 이미 주키퍼 모드 데이터가 남아있는 볼륨을 KRaft로 재사용하면 문제가 발생할 수 있습니다. 필요 시 볼륨을 새로 생성하세요.
- 단일 노드 기준 설정입니다. 멀티 노드 구성 시 `KAFKA_CONTROLLER_QUORUM_VOTERS`와 `KAFKA_NODE_ID`를 확장해야 합니다.
