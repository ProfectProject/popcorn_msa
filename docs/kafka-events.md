# 📘 Kafka 이벤트/토픽 설계 정리본 (v1)

## ✅ 공통 이벤트 Envelope (모든 토픽 공통)
```json
{
  "eventId": "uuid",
  "eventType": "order.created",
  "version": "v1",
  "occurredAt": "2026-01-30T12:34:56Z",
  "producer": "order-service",
  "traceId": "optional",
  "orderId": "uuid",
  "orderNo": "O20260130-XXXXXXX",
  "payload": { ... }
}
```

- **eventId**: 전역 유니크
- **eventType**: dot-case 통일
- **version**: v1 고정
- **orderId / orderNo**: 핵심 이벤트는 반드시 포함
- **partition key**: orderId (없으면 eventId)

---

# 📦 Order 도메인

## 🔹 `order-events`
| EventType | 발행자 | 수신자 (Consumer Group) | 주요 Payload |
| --- | --- | --- | --- |
| `ORDER_CREATED` (주문 생성) :테스트 완료 | Order | Store(store-cg) Payment(payment-cg), CheckIn(checkin-cg) | eventId, orderId, orderNo, userId, orderType, popupId, hasReservation, hasGoods, lines[], totalAmount, createdAt |
| `ORDER_PAID` (상태 변경) : service 추가 , 테스트 | Order | Store, Payment, Query | eventId, orderId, popupId, fromStatus, toStatus, hasReservation, hasGoods, updatedAt |
| `ORDER_COMPLETED` |  |  |  |

---

## 🔹 `order-requests`
| EventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `ORDER_INFO_REQUESTED` | Payment | Order(order-cg) | eventId, orderId, correlationId |
| `ORDER_QUERY_REQUESTED` | Payment | Order | eventId, orderId, correlationId |

---

## 🔹 `order-responses`
> 필요 시 추가 (현재 없음)

---

# 💳 Payment 도메인

## 🔹 `payment-events`
| EventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `PAYMENT_CREATED` | Payment | Order(order-cg) | eventId, paymentId, orderId, amount, status, createdAt |
| `PAYMENT_APPROVED` | Payment | Order, Query | eventId, paymentId, orderId, popupId, storeId, amount, approvedAt |
| `PAYMENT_FAILED` | Payment | Order, Query | eventId, paymentId, orderId, popupId, storeId, reason, failedAt |
| `PAYMENT_USER_CANCELLED` | Payment | Order, Query | eventId, paymentId, orderId, popupId, storeId, cancelReason, cancelledAt |
| `PAYMENT_CANCEL_SUCCEEDED` | Payment | Order | eventId, paymentId, orderId, cancelledAt |
| `PAYMENT_CANCEL_FAILED` | Payment | DLQ / 알람 | eventId, paymentId, orderId, lastError, retryCount, lastTriedAt |

---

## 🔹 `payment-requests`
| EventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `PAYMENT_CANCEL_REQUESTED` | Order | Payment(payment-cg) | eventId, paymentId, orderId, cancelReason, requestedAt |
| `PAYMENT_CREATE_REQUESTED` | Order | Payment | eventId, orderId, amount, orderName, successUrl, failUrl, requestedAt |

---

## 🔹 `payment-responses`
> 없음

---

# 🏪 Store / Inventory 도메인

## 🔹 `store-requests`
| eventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `goods.reservation.requested` | Order | Store(store-cg) | orderId, orderNo, goodsId, quantity |
| `stock.deduction.requested` | Order | Store | orderId, orderNo, deductionItems[] |
| `schedule.reservation.requested` | Order | Store | orderId, orderNo, sessionId, quantity |
| `schedule.confirmation.requested` | Order | Store | orderId, orderNo |
| `inventory.confirmation.requested` | Payment | Store | paymentId, orderId, orderNo, actionType |
| `inventory.restore.requested` | Payment | Store | paymentId, orderId, orderNo |
| `goods.reservation.cancel.requested` | Order | Store | orderId, orderNo, goodsId |
| `schedule.reservation.cancel.requested` | Order | Store | orderId, orderNo, sessionId |
| `price.lookup.requested` | Order | Store | correlationId, goodsId |
| `popup.info.lookup.requested` | Order | Store | correlationId, popupId |

---

## 🔹 `store-events`
| eventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `goods.reserved` | Store | Order(order-cg) | orderId, orderNo, goodsId, quantity |
| `goods.reservation.failed` | Store | Order | orderId, orderNo, goodsId, reason |
| `stock.deduction.success` | Store | Order | orderId, orderNo, stockDetails |
| `stock.deduction.failed` | Store | Order | orderId, orderNo, reason |
| `schedule.reservation.success` | Store | Order | orderId, orderNo, reservedSessions[], token |
| `schedule.reservation.failed` | Store | Order | orderId, orderNo, failedSessions[] |

---

## 🔹 `store-responses`
| eventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `price.lookup.response` | Store | Order | correlationId, price, stockQuantity |
| `popup.info.lookup.response` | Store | Order | correlationId, popupId, title, address |

---

# 📱 CheckIn / QR 도메인

## 🔹 `checkin-requests`
| eventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `qr.generation.requested` | Payment | CheckIn(checkin-cg) | orderId, orderNo |
| `qr.checkin.requested` | Client | CheckIn | qrCode |
| `qr.invalidation.requested` | Order | CheckIn | qrCode |

---

## 🔹 `checkin-events`
| eventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `qr.generated` | CheckIn | Order, User | qrCode, orderId, orderNo |
| `qr.checkin.completed` | CheckIn | Order, User | qrCode, orderId, orderNo, checkinTime |

---

# 👤 User 도메인

## 🔹 `user-requests`
| eventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `user.address.lookup.requested` | Order | User(user-cg) | userId, correlationId |

---

## 🔹 `user-responses`
| eventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `user.address.lookup.response` | User | Order | userId, addressId, address1, success |

---

# 📊 Analytics 도메인 (분리)

## 🔹 `analytics-events`
| eventType | 발행자 | 수신자 | 주요 Payload |
| --- | --- | --- | --- |
| `order.created` | Order | Analytics | orderId, orderNo, popupId, totalAmount |
| `order.status.updated` | Order | Analytics | orderId, orderNo, fromStatus, toStatus |
| `payment.created` | Payment | Analytics | paymentId, orderId, orderNo, amount |
| `payment.approved` | Payment | Analytics | paymentId, orderId, orderNo, amount |
