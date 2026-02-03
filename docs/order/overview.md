# Order 서비스 개요 (상세)

## 목적
Order 서비스는 주문 생성/상태 관리/예약 및 재고 요청/결제 흐름 연결을 담당합니다.

## 핵심 로직 위치
- 주문 생성/분기/예외: `OrderCommandService`
- 이벤트 수신/정규화/재시도: `OrderRedisStreamListener`
- 상태 전이/도메인 규칙: `OrderDomainService`
- 예약 응답 대기/완료 처리: `OrderReservationAwaiter`

## 외부 연동
- Store: 스케줄/굿즈 예약 및 재고 확정
- Payment: 결제 생성/승인/취소/보상
- User: 기본 주소 조회
- CheckIn: QR 발급/체크인

