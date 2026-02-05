# CheckIns Outbox CDC (Debezium + Kafka Connect)

## 목표
- CheckIns 서비스 트랜잭션 내에서 Outbox 기록
- Debezium이 WAL에서 outbox_events 변경을 감지
- Kafka로 이벤트를 자동 발행

## 구성 요소
- PostgreSQL (WAL)
- Kafka Connect (Debezium PostgreSQL)
- Kafka

## Outbox 테이블
- `checkIns.outbox_events` (Flyway V2로 생성)

## 커넥터 등록

```bash
curl -X POST http://localhost:8088/connectors \
  -H 'Content-Type: application/json' \
  -d @checkIns/debezium/checkins-outbox-connector.json
```

```bash
curl -X POST http://localhost:8088/connectors \
  -H 'Content-Type: application/json' \
  -d @payment/debezium/payment-outbox-connector.json
```

## 커넥터 상태 확인

```bash
curl -s http://localhost:8088/connectors/checkins-outbox-connector/status | jq
```

## 토픽
- `checkin-events`
- `payment-events`

## 주의 사항
- 스키마가 소문자(`checkins`)로 생성되어 있는 경우, 커넥터 JSON의
  `schema.include.list` / `table.include.list` 값을 소문자로 수정해야 합니다.
  - 예: `checkins`, `checkins.outbox_events`
