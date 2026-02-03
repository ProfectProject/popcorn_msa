# 결제 연동 상세 (Payment)

## 1) 결제 생성 흐름
- 예약 성공 후 payment.create.requested 발행
- payment.created 수신 → 결제 대기 상태 유지

## 2) 결제 승인 흐름
- payment.approved 수신
- 주문 상태 PAID 전환
- inventory.confirmation.requested 발행
- QR 생성 요청(qr.generation.requested) 발행

## 3) 결제 실패/취소
- payment.failed / payment.cancelled 수신
- 주문 CANCELLED 처리
- 보상 트랜잭션 이벤트 발행 가능

## 4) 보상 트랜잭션
- 재고 확정 실패 시 결제 취소 요청
- 결제 취소 실패 시 재시도 큐로 이동

