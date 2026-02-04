# Kafka + Debezium CDC 전환 가이드

## 🎯 전환 목표

Redis Stream → Kafka + Debezium CDC로 완전 전환하여 표준적이고 안정적인 이벤트 아키텍처 구축

## 📋 전환 단계별 실행

### 1단계: 인프라 구성

```bash
# Kafka + Debezium 인프라 시작
./scripts/kafka-migration.sh setup

# 상태 확인
./scripts/kafka-migration.sh status
```

### 2단계: 데이터베이스 설정

- ✅ Outbox 테이블 생성 완료 (`V1__add_outbox_tables.sql`)
- ✅ Debezium 사용자 및 권한 설정 완료
- ✅ Publication 및 Replication Slot 준비 완료

### 3단계: Debezium Connector 등록

- ✅ Orders Outbox Connector
- ✅ Stores Outbox Connector
- ✅ Payments Outbox Connector (향후 확장)

### 4단계: 애플리케이션 코드 전환

#### 현재 Redis Stream 기반 → Kafka + Outbox 패턴으로 변경 필요

**Before (Redis Stream):**
```java
// Redis Stream 직접 발행
redisEventPublisher.publishEvent("order-events", "order-created", orderEvent);
```

**After (Outbox + CDC):**
```java
// Outbox 테이블에 INSERT만 (Debezium이 자동으로 Kafka에 발행)
outboxEventPublisher.publishOrderEvent(orderId, "ORDER-CREATED", orderEvent);
```

## 🔧 인프라 구성 파일들

### Docker Compose 설정
- `kafka-debezium-compose.yml` - Kafka Connect, Schema Registry, Kafka UI
- `debezium-connectors/` - Connector 설정 파일들

### 모니터링 URL
- **Kafka UI**: http://localhost:8090
- **Kafka Connect**: http://localhost:8083
- **Schema Registry**: http://localhost:8081

## 🚀 실행 방법

```bash
# 1. 인프라 시작 및 설정
./scripts/kafka-migration.sh setup

# 2. 테스트 이벤트 발행
./scripts/kafka-migration.sh test

# 3. 상태 확인
./scripts/kafka-migration.sh status

# 4. 정리 (필요시)
./scripts/kafka-migration.sh cleanup
```

## 📊 토픽 구조

| 토픽명 | 설명 | 파티션 키 |
|--------|------|-----------|
| `order-events` | 주문 관련 이벤트 | orderId |
| `popup-events` | 팝업스토어 이벤트 | popupId |
| `schedule-events` | 스케줄 예약 이벤트 | scheduleId |
| `goods-events` | 상품 재고 이벤트 | goodsId |
| `payment-events` | 결제 이벤트 | orderId |

## ✅ 전환 체크리스트

### 인프라
- [x] Kafka 클러스터 구성
- [x] Debezium Connect 설정
- [x] Schema Registry 구성
- [x] Kafka UI 모니터링

### 데이터베이스
- [x] Outbox 테이블 생성
- [x] Debezium 사용자 권한 설정
- [x] WAL 레벨 logical 설정 확인
- [x] Publication 생성

### 애플리케이션
- [ ] OutboxEventPublisher 구현
- [ ] Kafka Consumer 구현
- [ ] 기존 Redis Stream 코드 제거
- [ ] 설정 파일 업데이트
- [ ] 통합 테스트

### 운영
- [ ] 메트릭 수집 설정
- [ ] 알람 설정
- [ ] 백업 전략 수립
- [ ] 장애 복구 절차 문서화

## 🎯 다음 단계

1. **애플리케이션 코드 전환** - OutboxEventPublisher 및 Kafka Consumer 구현
2. **점진적 배포** - Redis Stream과 Kafka 병행 운영 후 전환
3. **성능 모니터링** - 처리량, 지연시간 모니터링
4. **운영 최적화** - 파티션 조정, 배치 사이즈 튜닝

---

**⚠️ 주의사항:**
- PostgreSQL WAL 레벨이 `logical`로 설정되어 있는지 확인 필수
- Outbox 테이블의 크기 증가 모니터링 필요 (주기적 정리)
- Kafka 클러스터 디스크 용량 모니터링 필요