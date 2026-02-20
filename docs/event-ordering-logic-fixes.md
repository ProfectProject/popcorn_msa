# 🔧 이벤트 순서 보장 시스템 로직 수정 사항

## 🚨 발견된 문제점들

### 1. **순환 재귀 호출 위험**
**문제**: `EventOrderingService.processInOrderEvent()` ↔ `processWaitingEvents()` 간 무한 재귀 호출 가능성

**원인**:
```java
// 기존 문제 코드
private EventProcessingResult processInOrderEvent(OrderedEvent event) {
    // ... 처리 후
    processWaitingEvents(...); // → 재귀 호출 위험
}

private void processWaitingEvents(...) {
    for (OrderedEvent waitingEvent : processableEvents) {
        processInOrderEvent(waitingEvent); // → 또 다시 재귀!
    }
}
```

**해결책**:
- `processInOrderEvent()` 오버로드로 재귀 제어 매개변수 추가
- `processWaitingEvents()` → `processWaitingEventsIterative()` 반복적 처리로 변경
- 최대 반복 횟수 제한 (100회)으로 무한 루프 방지

### 2. **이벤트 조기 제거 문제**
**문제**: 이벤트를 실제 처리하기 전에 Redis에서 제거하여 처리 실패 시 데이터 손실

**원인**:
```java
// OrderedEventStore.getProcessableEvents() 기존 문제
redisTemplate.opsForZSet().remove(pendingKey, eventJson); // 처리 전 제거!
```

**해결책**:
- `getProcessableEvents()`: 조회만 하고 제거하지 않음
- `removeProcessedEvent()`: 처리 성공 후에만 제거하는 전용 메서드
- 시퀀스 기반 안전한 제거 로직

### 3. **트랜잭션 부재**
**문제**: 시퀀스 업데이트와 이벤트 처리 간 일관성 보장 부족

**해결책**:
- `@Transactional` 어노테이션 추가
  - `processEvent()`: 메인 진입점
  - `processInOrderEvent()`: 개별 이벤트 처리

## 🎯 수정된 로직 플로우

### 새로운 이벤트 처리 플로우:
```
1. processEvent(@Transactional)
   ├─ validateSequence()
   ├─ IN_ORDER → processInOrderEvent()
   │   ├─ 실제 이벤트 처리
   │   ├─ 성공 시 시퀀스 업데이트
   │   └─ processWaitingEventsIterative() [재귀 방지]
   │       └─ 반복적으로 대기 이벤트 처리
   ├─ OUT_OF_ORDER → storePendingEvent()
   └─ DUPLICATE_OR_LATE → skip
```

### 개선된 안전성:
- ✅ **스택 오버플로우 방지**: 반복적 처리로 변경
- ✅ **데이터 무결성**: 처리 성공 후에만 이벤트 제거
- ✅ **트랜잭션 일관성**: DB 트랜잭션 내에서 모든 작업 수행
- ✅ **무한 루프 방지**: 최대 반복 횟수 제한

## 🧪 검증 포인트

### 1. **재귀 깊이 테스트**
- 대량 순차 이벤트 처리 시 스택 오버플로우 발생하지 않음
- 메모리 사용량 일정 수준 유지

### 2. **이벤트 무결성 테스트**
- 처리 실패 시 이벤트가 Redis에 남아있어야 함
- 재시도 시 동일 이벤트 재처리 가능해야 함

### 3. **성능 테스트**
- 순차 처리 성능 유지
- 대기 이벤트 배치 처리 효율성
- Redis 메모리 사용량 최적화

## 🔍 모니터링 개선

### 추가된 로그:
- 반복 처리 횟수 추적
- 배치 단위 처리 결과 추적
- 이벤트 제거 성공/실패 로그

### 성능 메트릭:
- 반복 처리 횟수 (`iteration`)
- 배치당 처리 성공률
- 대기 이벤트 수 감소 추이

## 📈 다음 단계

1. **기존 서비스 통합**: Order/Payment 서비스에 적용
2. **성능 튜닝**: Redis 연결 풀 및 배치 크기 최적화
3. **모니터링 대시보드**: 이벤트 순서 처리 현황 시각화
4. **알림 시스템**: 대기 이벤트 누적 시 알림 발송