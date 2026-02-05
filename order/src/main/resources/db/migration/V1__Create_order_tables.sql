-- Order Entity 구조에 맞춘 주문 관련 테이블 생성 (V1 - 완전 재작성)
-- Hibernate Entity와 100% 호환되는 구조

-- Users and schema already created manually
-- CREATE SCHEMA IF NOT EXISTS orders AUTHORIZATION order_migrator;

SET search_path TO orders;

-- 1. PostgreSQL enum 타입 생성 (존재하지 않는 경우에만)
DO $$ BEGIN
    CREATE TYPE orders.itemtype AS ENUM ('RESERVATION', 'GOODS', 'MIXED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE orders.orderstatus AS ENUM ('REQUESTED', 'RESERVED', 'PAYMENT_PENDING', 'PENDING_PAYMENT', 'PAID', 'CONFIRMED', 'PROCESSING', 'COMPLETED', 'CANCELLED', 'REFUNDED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 2. p_orders 테이블 생성 (Order Entity와 완전 일치)
CREATE TABLE IF NOT EXISTS orders.p_orders (
    order_id UUID PRIMARY KEY,                     -- Order.id (@Id)
    order_no VARCHAR(32) UNIQUE NOT NULL,          -- Order.orderNo
    user_id BIGINT NOT NULL,                       -- Order.customerId
    popup_id UUID,                                 -- Order.popupId (이제 저장됨)
    order_type orders.itemtype NOT NULL,           -- Order.orderType (RESERVATION/GOODS/MIXED)
    status orders.orderstatus NOT NULL DEFAULT 'REQUESTED', -- Order.status (@JdbcTypeCode)
    cancelable_until TIMESTAMP,                    -- Order.cancelableUntil
    total_price INTEGER NOT NULL,                  -- Order.totalAmount
    paid_at TIMESTAMP,                             -- Order.paidAt
    confirmed_at TIMESTAMP,                        -- Order.confirmedAt
    canceled_at TIMESTAMP,                         -- Order.canceledAt
    cancel_reason VARCHAR(500),                    -- Order.cancelReason

    -- BaseEntity 필드들
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT
);

-- 5. p_order_goods 테이블 생성 (OrderItem Entity와 완전 일치)
CREATE TABLE IF NOT EXISTS orders.p_order_goods (
    order_goods_id UUID PRIMARY KEY,                   -- OrderItem.id (@Id)
    order_id UUID NOT NULL,                            -- OrderItem.orderId
    popup_id UUID,                                      -- OrderItem.popupId
    item_type orders.itemtype NOT NULL,                -- OrderItem.orderItemType (@Enumerated, RESERVATION/GOODS만 사용)
    schedule_id UUID,                                   -- OrderItem.sessionOptionId (예약 항목용)
    goods_variant_id UUID,                              -- OrderItem.goodsId (굿즈 항목용)
    qty INTEGER NOT NULL,                               -- OrderItem.qty
    unit_price INTEGER NOT NULL,                        -- OrderItem.unitPrice
    price INTEGER NOT NULL,                             -- OrderItem.lineAmount

    -- BaseEntity 필드들
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT
);

-- 6. p_order_status_histories 테이블 생성 (OrderStatusHistory Entity와 완전 일치)
CREATE TABLE IF NOT EXISTS orders.p_order_status_histories (
    order_status_id UUID PRIMARY KEY,              -- OrderStatusHistory.id (@Id)
    order_id UUID NOT NULL,                        -- OrderStatusHistory.orderId
    from_status orders.orderstatus,                -- OrderStatusHistory.fromStatus (@JdbcTypeCode)
    to_status orders.orderstatus NOT NULL,         -- OrderStatusHistory.toStatus (@JdbcTypeCode)
    reason VARCHAR(255),                           -- OrderStatusHistory.reason
    changed_at TIMESTAMP NOT NULL,                 -- OrderStatusHistory.changedAt

    -- BaseEntity 필드들
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT
);

-- 7. 외래 키 제약 조건
ALTER TABLE orders.p_order_goods
    ADD CONSTRAINT fk_p_order_goods_order
        FOREIGN KEY (order_id) REFERENCES orders.p_orders(order_id);

ALTER TABLE orders.p_order_status_histories
    ADD CONSTRAINT fk_p_order_status_histories_order
        FOREIGN KEY (order_id) REFERENCES orders.p_orders(order_id);

-- 8. 인덱스 생성 (성능 최적화 + Repository 쿼리 지원)
CREATE INDEX IF NOT EXISTS idx_p_orders_user_id ON orders.p_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_p_orders_popup_id ON orders.p_orders(popup_id);
CREATE INDEX IF NOT EXISTS idx_p_orders_status ON orders.p_orders(status);
CREATE INDEX IF NOT EXISTS idx_p_orders_order_type ON orders.p_orders(order_type);
CREATE INDEX IF NOT EXISTS idx_p_orders_created_at ON orders.p_orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_p_orders_order_no ON orders.p_orders(order_no);
CREATE INDEX IF NOT EXISTS idx_p_orders_paid_at ON orders.p_orders(paid_at);
CREATE INDEX IF NOT EXISTS idx_p_orders_cancelable_until ON orders.p_orders(cancelable_until);

-- 주문 상품 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_p_order_goods_order_id ON orders.p_order_goods(order_id);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_popup_id ON orders.p_order_goods(popup_id);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_item_type ON orders.p_order_goods(item_type);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_schedule_id ON orders.p_order_goods(schedule_id);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_goods_variant_id ON orders.p_order_goods(goods_variant_id);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_created_at ON orders.p_order_goods(created_at DESC);

-- 상태 이력 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_p_order_status_histories_order_id ON orders.p_order_status_histories(order_id);
CREATE INDEX IF NOT EXISTS idx_p_order_status_histories_changed_at ON orders.p_order_status_histories(changed_at DESC);

-- 9. 체크 제약 조건
ALTER TABLE orders.p_orders
    ADD CONSTRAINT chk_p_orders_total_price
        CHECK (total_price > 0);

ALTER TABLE orders.p_order_goods
    ADD CONSTRAINT chk_p_order_goods_qty
        CHECK (qty > 0);

ALTER TABLE orders.p_order_goods
    ADD CONSTRAINT chk_p_order_goods_unit_price
        CHECK (unit_price > 0);

ALTER TABLE orders.p_order_goods
    ADD CONSTRAINT chk_p_order_goods_price
        CHECK (price > 0);

-- 10. 테이블 및 컬럼 코멘트
COMMENT ON TABLE orders.p_orders IS '주문 정보 테이블 (Order Entity와 완전 일치)';
COMMENT ON COLUMN orders.p_orders.order_id IS '주문 고유 ID (UUID) - Order.id';
COMMENT ON COLUMN orders.p_orders.order_no IS '주문 번호 (사용자 표시용) - Order.orderNo';
COMMENT ON COLUMN orders.p_orders.user_id IS '주문한 사용자 ID - Order.customerId';
COMMENT ON COLUMN orders.p_orders.popup_id IS '팝업 ID - Order.popupId';
COMMENT ON COLUMN orders.p_orders.order_type IS '주문 타입 (RESERVATION/GOODS/MIXED) - Order.orderType';
COMMENT ON COLUMN orders.p_orders.status IS '주문 상태 - Order.status';
COMMENT ON COLUMN orders.p_orders.cancelable_until IS '취소 가능 시한 - Order.cancelableUntil';
COMMENT ON COLUMN orders.p_orders.total_price IS '총 주문 금액 (원) - Order.totalAmount';
COMMENT ON COLUMN orders.p_orders.paid_at IS '결제 완료 시간 - Order.paidAt';
COMMENT ON COLUMN orders.p_orders.confirmed_at IS '주문 확정 시간 - Order.confirmedAt';
COMMENT ON COLUMN orders.p_orders.canceled_at IS '주문 취소 시간 - Order.canceledAt';
COMMENT ON COLUMN orders.p_orders.cancel_reason IS '취소 사유 - Order.cancelReason';

COMMENT ON TABLE orders.p_order_goods IS '주문 상품 정보 테이블 (OrderItem Entity와 완전 일치)';
COMMENT ON COLUMN orders.p_order_goods.order_goods_id IS '주문 상품 고유 ID (UUID) - OrderItem.id';
COMMENT ON COLUMN orders.p_order_goods.order_id IS '주문 ID - OrderItem.orderId';
COMMENT ON COLUMN orders.p_order_goods.popup_id IS '팝업 ID - OrderItem.popupId';
COMMENT ON COLUMN orders.p_order_goods.item_type IS '주문 항목 타입 (RESERVATION/GOODS, MIXED 제외) - OrderItem.orderItemType';
COMMENT ON COLUMN orders.p_order_goods.schedule_id IS '세션 옵션 ID (예약형 상품용) - OrderItem.sessionOptionId';
COMMENT ON COLUMN orders.p_order_goods.goods_variant_id IS '굿즈 변형 ID (굿즈형 상품용) - OrderItem.goodsId';
COMMENT ON COLUMN orders.p_order_goods.qty IS '주문 수량 - OrderItem.qty';
COMMENT ON COLUMN orders.p_order_goods.unit_price IS '단가 (원) - OrderItem.unitPrice';
COMMENT ON COLUMN orders.p_order_goods.price IS '라인 금액 (원) - OrderItem.lineAmount';

COMMENT ON TABLE orders.p_order_status_histories IS '주문 상태 변경 이력 테이블 (OrderStatusHistory Entity와 완전 일치)';
COMMENT ON COLUMN orders.p_order_status_histories.order_status_id IS '상태 변경 이력 고유 ID - OrderStatusHistory.id';
COMMENT ON COLUMN orders.p_order_status_histories.order_id IS '주문 ID - OrderStatusHistory.orderId';
COMMENT ON COLUMN orders.p_order_status_histories.from_status IS '변경 전 주문 상태 - OrderStatusHistory.fromStatus';
COMMENT ON COLUMN orders.p_order_status_histories.to_status IS '변경 후 주문 상태 - OrderStatusHistory.toStatus';
COMMENT ON COLUMN orders.p_order_status_histories.reason IS '상태 변경 사유 - OrderStatusHistory.reason';
COMMENT ON COLUMN orders.p_order_status_histories.changed_at IS '상태 변경 일시 - OrderStatusHistory.changedAt';
