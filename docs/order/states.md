# 주문 상태 전이 규칙 (상세)

## 상태 정의
- REQUESTED: 주문 생성됨
- RESERVED: 예약/재고 예약 완료
- PAYMENT_PENDING: 결제 대기
- PAID: 결제 승인 완료
- COMPLETED: 후속 처리 완료
- CANCELLED: 취소됨
- REJECTED: 거절됨

---

## 정상 흐름
- REQUESTED → RESERVED
- RESERVED → PAYMENT_PENDING
- PAYMENT_PENDING → PAID
- PAID → COMPLETED

---

## 실패/취소 흐름
- REQUESTED → CANCELLED
  - 예약 실패/타임아웃
- RESERVED → CANCELLED
  - 결제 실패/취소
- PAYMENT_PENDING → CANCELLED
  - 결제 실패/취소/보상

---

## 이벤트 기반 전이 규칙
- schedule-reservation-success → RESERVED
- goods-reserved → RESERVED
- payment-approved → PAID
- payment-cancelled → CANCELLED
- stock-deduction-failed → CANCELLED

