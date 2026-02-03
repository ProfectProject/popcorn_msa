# 예약/재고 연동 상세 (Store)

## 1) 예약 전용
- schedule.reservation.requested 발행
- schedule.reservation.success 수신 → 상태 RESERVED
- 실패 시 CANCELLED

## 2) 굿즈 전용
- goods.reservation.requested 발행
- goods.reserved 수신 → 상태 RESERVED
- 실패 시 CANCELLED

## 3) MIXED
1. 스케줄 예약 요청
2. 스케줄 성공 후 굿즈 예약 요청
3. 둘 다 성공 시 RESERVED → PAYMENT_PENDING

## 4) 결제 승인 후 재고 확정
- payment.approved 수신
- inventory.confirmation.requested 발행
- stock-deduction-success 수신 시 COMPLETED 전환

