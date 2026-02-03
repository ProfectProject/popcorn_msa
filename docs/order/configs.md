# 주요 설정/타임아웃

## 주문 예약 대기
- `order.reservation.wait-timeout-ms`

## 가격 조회
- `order.price-lookup.timeout-ms`
- 캐시 miss 시 기본값 사용

## 사용자 주소 조회
- 캐시 hit만 사용
- miss 시 비동기 보강

## Redis Stream
- Stream pollTimeout, batchSize (서비스별 설정)
- Redis timeout/connection-timeout 상향

