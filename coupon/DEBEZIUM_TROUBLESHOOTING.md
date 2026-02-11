# 🔧 Debezium 커넥터 문제 해결 가이드

## 📋 **문제 진단 체크리스트**

### 1️⃣ **PostgreSQL 설정 확인**

```bash
# WAL 레벨 확인 (logical이어야 함)
docker exec coupon-postgresql psql -U postgres -d popcorn_db -c "SHOW wal_level;"

# Replication 슬롯 확인
docker exec coupon-postgresql psql -U postgres -d popcorn_db -c "SELECT * FROM pg_replication_slots;"

# Publication 확인
docker exec coupon-postgresql psql -U postgres -d popcorn_db -c "SELECT * FROM pg_publication;"

# 테이블 존재 확인
docker exec coupon-postgresql psql -U postgres -d popcorn_db -c "\dt coupon_outbox_events"
```

### 2️⃣ **Kafka Connect 상태 확인**

```bash
# Kafka Connect 서비스 상태
curl http://localhost:8084/connectors

# 특정 커넥터 상태
curl http://localhost:8084/connectors/coupon-outbox-connector/status | jq

# 커넥터 로그
docker logs coupon-kafka-connect --tail 100
```

### 3️⃣ **Kafka 토픽 및 메시지 확인**

```bash
# 토픽 목록
docker exec coupon-kafka kafka-topics --bootstrap-server localhost:9092 --list

# 특정 토픽 메시지 확인
docker exec coupon-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic coupon-events --from-beginning --max-messages 5
```

---

## 🚨 **자주 발생하는 문제 및 해결책**

### **Problem 1: WAL Level 설정 오류**
```
Error: wal_level is not set to 'logical'
```

**해결책:**
1. PostgreSQL 설정 파일 확인: `/docker/postgres/postgresql.conf`
2. `wal_level = logical` 설정 확인
3. 컨테이너 재시작: `docker-compose restart coupon-db`

### **Problem 2: Replication Slot 충돌**
```
Error: replication slot "coupon_outbox_slot" already exists
```

**해결책:**
```sql
-- 기존 슬롯 삭제
SELECT pg_drop_replication_slot('coupon_outbox_slot');

-- 또는 Docker 컨테이너 완전 초기화
docker-compose down -v
docker-compose up -d
```

### **Problem 3: 테이블 권한 문제**
```
Error: permission denied for table coupon_outbox_events
```

**해결책:**
```sql
-- postgres 사용자에게 권한 부여
GRANT ALL PRIVILEGES ON TABLE public.coupon_outbox_events TO postgres;
ALTER USER postgres WITH REPLICATION;
```

### **Problem 4: 필드 매핑 오류**
```
Error: Column 'published' does not exist
```

**해결책:**
커넥터 설정에서 필드 매핑 수정:
- `published` → `processed_at`
- `publishedAt` → `processed_at`

### **Problem 5: JSON 역직렬화 오류**
```
Error: Cannot deserialize value of type java.time.LocalDateTime from String
```

**해결책:**
1. 커넥터 설정에서 시간 형식 지정
2. `time.precision.mode = connect` 설정

---

## 🔄 **커넥터 재배포 프로세스**

### **방법 1: 자동 스크립트 사용**
```bash
./scripts/deploy-connector.sh
```

### **방법 2: 수동 재배포**
```bash
# 1. 기존 커넥터 삭제
curl -X DELETE http://localhost:8084/connectors/coupon-outbox-connector

# 2. 새 커넥터 생성
curl -X POST -H "Content-Type: application/json" \
  --data @docker/debezium/coupon-outbox-connector-fixed.json \
  http://localhost:8084/connectors

# 3. 상태 확인
curl http://localhost:8084/connectors/coupon-outbox-connector/status | jq
```

---

## 📊 **모니터링 명령어**

### **실시간 로그 모니터링**
```bash
# Kafka Connect 로그
docker logs -f coupon-kafka-connect

# PostgreSQL 로그
docker logs -f coupon-postgresql

# 쿠폰 서비스 로그
docker logs -f coupon-service
```

### **메트릭 확인**
```bash
# 커넥터 태스크 상태
curl http://localhost:8084/connectors/coupon-outbox-connector/tasks | jq

# 토픽별 메시지 수
docker exec coupon-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 --topic coupon-events
```

---

## 🧪 **테스트 이벤트 생성**

```sql
-- PostgreSQL에서 테스트 이벤트 생성
INSERT INTO public.coupon_outbox_events (
    aggregate_type,
    aggregate_id,
    event_type,
    event_data,
    created_at
) VALUES (
    'COUPON',
    'test-' || extract(epoch from now()),
    'COUPON_TEST',
    '{"test": true, "timestamp": "' || now() || '"}',
    now()
);
```

---

## 🆘 **완전 초기화 (마지막 수단)**

```bash
# 모든 데이터 삭제 후 재시작
docker-compose down -v
docker system prune -f
docker-compose up -d

# 초기화 후 커넥터 재배포
./scripts/deploy-connector.sh
```

---

## 📞 **추가 지원**

문제가 지속되면 다음 정보를 포함하여 리포트하세요:

1. **환경 정보:**
   ```bash
   docker --version
   docker-compose --version
   ```

2. **서비스 상태:**
   ```bash
   docker-compose ps
   ```

3. **로그 스냅샷:**
   ```bash
   docker logs coupon-kafka-connect --tail 50 > connector-logs.txt
   docker logs coupon-postgresql --tail 50 > postgres-logs.txt
   ```

4. **커넥터 설정:**
   ```bash
   curl http://localhost:8084/connectors/coupon-outbox-connector/config | jq
   ```