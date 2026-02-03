# Order 코드 맵 (흐름 ↔ 코드 위치)

## 주문 생성 흐름
- 엔트리: `OrderCommandController#createOrder`
- 핵심 로직: `OrderCommandService#createOrder`
- 도메인 검증/생성: `OrderDomainService`
- 상태 변경: `OrderCommandService#updateOrderStatus`

## 예약/재고 요청 발행
- 스케줄 예약 요청: `OrderEventPublisher#publishScheduleReservationRequested`
- 굿즈 예약 요청: `OrderEventPublisher#publishGoodsReservationRequested`
- 재고 차감 요청: `OrderEventPublisher#publishStockDeductionRequested`

## 이벤트 수신 처리
- Redis Stream 수신: `OrderRedisStreamListener#onMessage`
- 스케줄 성공/실패: `handleScheduleReservationSuccess/Failed`
- 굿즈 성공/실패: `handleGoodsReserved/handleGoodsReservationFailed`
- 결제 이벤트: `handlePaymentApproved/Completed/Cancelled/Failed`
- 주소/가격/팝업 응답: `handleUserAddressLookupResponse`, `handlePriceLookupResponse`, `handlePopupInfoLookupResponse`

## 결제 연동
- 결제 생성 요청: `OrderCommandService#publishPaymentCreateRequestedEvent`
- 결제 URL 생성: `OrderCommandService#generatePaymentUrlAfterReservation`

## 대기/응답
- 예약 응답 대기: `OrderReservationAwaiter#await`
- 예약 완료 처리: `OrderReservationAwaiter#completeSuccess/completeFailure`

