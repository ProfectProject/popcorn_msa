# Outbox CDC (Debezium + Kafka Connect) - CheckIns & Payment

## 1) Outbox 테이블 (Flyway)
- CheckIns: `checkIns/src/main/resources/db/migration/schema/V2__outbox_events.sql`
- Payment: `payment/src/main/resources/db/migration/V16__outbox_events.sql`

테이블 컬럼: `event_id`, `aggregate_type`, `aggregate_id`, `event_type`, `partition_key`, `schema_version`, `event_data`, `headers`, `occurred_at`, `created_at`

## 2) App 코드 (Outbox Writer)
- CheckIns
  - `checkIns/src/main/java/com/popcorn/checkIns/outbox/OutboxEvent.java`
  - `checkIns/src/main/java/com/popcorn/checkIns/outbox/OutboxEventRepository.java`
  - `checkIns/src/main/java/com/popcorn/checkIns/outbox/OutboxWriter.java`
  - 이벤트 기록: `checkIns/src/main/java/com/popcorn/checkIns/service/QrCodeService.java` → `publishEventToBothSystems()`
- Payment
  - `payment/src/main/kotlin/com/popcorn/payment/outbox/OutboxEvent.kt`
  - `payment/src/main/kotlin/com/popcorn/payment/outbox/OutboxEventRepository.kt`
  - `payment/src/main/kotlin/com/popcorn/payment/outbox/OutboxWriter.kt`
  - 이벤트 기록: `payment/src/main/kotlin/com/popcorn/payment/event/publisher/PaymentEventPublisher.kt` → `publish()`

**중요:** 이벤트 생성(INSERT)은 서비스가 하고, Kafka로 전송하는 건 Debezium 커넥터가 함.

## 3) Debezium 커넥터 JSON
- CheckIns: `checkIns/debezium/checkins-outbox-connector.json`
- Payment: `payment/debezium/payment-outbox-connector.json`

중요 포인트:
- **schema 대소문자**: 실제 스키마가 `checkIns`(대문자 I)이면
  - `schema.include.list`: `checkIns`
  - `table.include.list`: `checkIns.outbox_events`
- `publication.autocreate.mode = disabled`
- `publication.name` 명시
- `transforms.outbox.table.field.event.payload = event_data`
- `occurred_at`는 timestamptz라서 SMT `timestamp` 매핑하면 오류가 날 수 있음. (INT64 필요)
  - 해결: 커넥터에서 `transforms.outbox.table.field.event.timestamp` 제거

## 4) DB 권한 및 Replication
### 4-1) replication 권한
```bash
# DB 컨테이너
ALTER ROLE qr_app WITH REPLICATION;
ALTER ROLE payment_app WITH REPLICATION;
```

### 4-2) DB/스키마 접근 권한
```bash
GRANT CONNECT ON DATABASE popcorn_db TO qr_app, payment_app;

GRANT USAGE ON SCHEMA "checkIns" TO qr_app;
GRANT USAGE ON SCHEMA payment TO payment_app;

GRANT INSERT, SELECT, UPDATE, DELETE ON TABLE "checkIns".outbox_events TO qr_app;
GRANT INSERT, SELECT, UPDATE, DELETE ON TABLE payment.outbox_events TO payment_app;

GRANT USAGE, SELECT ON SEQUENCE "checkIns".outbox_events_id_seq TO qr_app;
GRANT USAGE, SELECT ON SEQUENCE payment.outbox_events_id_seq TO payment_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA "checkIns"
  GRANT INSERT, SELECT, UPDATE, DELETE ON TABLES TO qr_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment
  GRANT INSERT, SELECT, UPDATE, DELETE ON TABLES TO payment_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA "checkIns"
  GRANT USAGE, SELECT ON SEQUENCES TO qr_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment
  GRANT USAGE, SELECT ON SEQUENCES TO payment_app;
```

## 5) Publication 생성 (Postgres)
```bash
DROP PUBLICATION IF EXISTS checkins_outbox_pub;
CREATE PUBLICATION checkins_outbox_pub FOR TABLE "checkIns".outbox_events;

DROP PUBLICATION IF EXISTS payment_outbox_pub;
CREATE PUBLICATION payment_outbox_pub FOR TABLE payment.outbox_events;
```

## 6) 커넥터 등록
```bash
# checkins
curl -X POST http://localhost:8088/connectors \
  -H 'Content-Type: application/json' \
  -d @checkIns/debezium/checkins-outbox-connector.json

# payment
curl -X POST http://localhost:8088/connectors \
  -H 'Content-Type: application/json' \
  -d @payment/debezium/payment-outbox-connector.json
```

### 6-1) 커넥터 업데이트 (config만 PUT)
커넥터가 이미 존재하면 config만 갱신:
```bash
# checkins
jq '.config' checkIns/debezium/checkins-outbox-connector.json | \
  curl -X PUT http://localhost:8088/connectors/checkins-outbox-connector/config \
    -H 'Content-Type: application/json' \
    -d @-

# payment
jq '.config' payment/debezium/payment-outbox-connector.json | \
  curl -X PUT http://localhost:8088/connectors/payment-outbox-connector/config \
    -H 'Content-Type: application/json' \
    -d @-
```

## 7) 상태 확인
```bash
curl -s http://localhost:8088/connectors/checkins-outbox-connector/status | jq
curl -s http://localhost:8088/connectors/payment-outbox-connector/status | jq
```

정상 예시:
- connector: `RUNNING`
- tasks: `RUNNING`

## 8) 이벤트 수신 확인
```bash
# checkin-events
docker exec kafka-1 kafka-console-consumer \
  --bootstrap-server kafka-1:9092 \
  --topic checkin-events \
  --from-beginning --max-messages 1

# payment-events
docker exec kafka-1 kafka-console-consumer \
  --bootstrap-server kafka-1:9092 \
  --topic payment-events \
  --from-beginning --max-messages 1
```

## 9) 자주 발생한 에러 & 해결
### 9-1) replication slot 생성 실패
```
FATAL: must be superuser or replication role to start walsender
```
- 해결: `ALTER ROLE <user> WITH REPLICATION;`

### 9-2) publication 생성 실패 / DB 권한 문제
```
ERROR: permission denied for database popcorn_db
```
- 해결: `GRANT CONNECT ON DATABASE ...` 및 스키마/테이블 권한 부여

### 9-3) SMT timestamp 오류
```
Field 'occurred_at' is not of type INT64
```
- 해결: 커넥터 JSON에서 `transforms.outbox.table.field.event.timestamp` 제거
  - 또는 view로 `occurred_at`을 BIGINT(ms) 컬럼으로 변환해서 사용

---

## 참고
- Kafka Connect REST: `http://localhost:8088`
- 등록된 커넥터 목록: `curl http://localhost:8088/connectors`
