# Order API 요약

## 주문 생성
- Endpoint: `POST /api/orders/v1`
- 인증: Bearer 토큰 필요

### 요청 바디 (예시: MIXED)
```json
{
  "orderType": "MIXED",
  "popupId": "00000000-0000-0000-0000-000000000101",
  "reservationId": "00000000-0000-0000-0000-000000000201",
  "paymentMethod": "CARD",
  "items": [
    {
      "orderItemType": "RESERVATION",
      "qty": 1,
      "unitPrice": 15000,
      "sessionId": "00000000-0000-0000-0000-000000000201"
    },
    {
      "orderItemType": "GOODS",
      "qty": 2,
      "unitPrice": 5000,
      "goodsId": "00000000-0000-0000-0000-000000000301"
    }
  ]
}
```

### 응답
- 201 Created
- 주문ID/주문번호 포함 (BaseResponse 형태)

### 검증
- orderType에 따라 items 구성이 일치해야 함
- RESERVATION: sessionId 필수
- GOODS: goodsId 필수
- MIXED: 두 타입 모두 포함

