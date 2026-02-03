# Order 이벤트 요약

> 상세 토픽 설계는 `docs/kafka-events.md` 참고

## 발행 이벤트 (Order → 외부)
- order.created
- order.paid
- order.status.updated
- goods.reservation.requested
- schedule.reservation.requested
- stock.deduction.requested
- payment.create.requested
- user.address.lookup.requested
- price.lookup.requested
- popup.info.lookup.requested

## 수신 이벤트 (외부 → Order)
- schedule.reservation.success / failed
- goods.reserved / goods.reservation.failed
- stock.deduction.success / failed
- payment.created / approved / failed / cancelled / completed
- user.address.lookup.response
- price.lookup.response
- popup.info.lookup.response

