# 이벤트 처리 로직 상세 (OrderRedisStreamListener)

## 1) 역할
- Redis Stream 이벤트 수신
- EventType 정규화/파싱
- 도메인 서비스 호출
- 멱등성/재시도/오류 핸들링

---

## 2) 주요 핸들러
- schedule-reservation-success / failed
- goods-reserved / goods-reservation-failed
- stock-deduction-success / failed
- payment-approved / completed / cancelled / failed
- user-address-lookup-response
- price-lookup-response
- popup-info-lookup-response

---

## 3) 재시도 처리
- 주문 생성 트랜잭션 이전 이벤트 도착 가능
- 비동기 재시도 스케줄러 사용
  - 일정 횟수(10회)까지 200ms 간격 재시도
  - 블로킹 제거

---

## 4) 멱등성
- 처리 완료 이벤트는 Redis/DB 멱등성 키로 중복 방지
- 처리 완료 후 키 무효화 또는 TTL 관리

---

## 5) 오류 처리
- 필수 필드 누락 시 warn 로그 후 skip
- 예외 발생 시 error 로그 + 이벤트 처리 실패 기록

