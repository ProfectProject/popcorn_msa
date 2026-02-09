-- 5) order_query (read model)
RESET ROLE;
SET ROLE order_query_migrator;

-- =========================
-- 5-1) popup_order_summary
-- =========================
CREATE TABLE IF NOT EXISTS order_query.popup_order_summary (
    popup_id uuid PRIMARY KEY,
    store_id uuid NOT NULL,
    popup_title varchar(200),
    popup_status varchar(30),
    address_road text,
    address_detail text,
    reservation_open_at timestamp,

    -- 예약(회차) 기준 KPI (주문 단위)
    reservation_total_orders int NOT NULL DEFAULT 0,
    reservation_paid_orders int NOT NULL DEFAULT 0,
    reservation_cancelled_orders int NOT NULL DEFAULT 0,

    -- 굿즈 기준 KPI (주문 단위)
    goods_total_orders int NOT NULL DEFAULT 0,
    goods_paid_orders int NOT NULL DEFAULT 0,
    goods_cancelled_orders int NOT NULL DEFAULT 0,

    -- 체크인은 예약 기준
    checked_in_orders int NOT NULL DEFAULT 0,

    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,

    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT
    );

CREATE INDEX IF NOT EXISTS idx_popup_summary_store
    ON order_query.popup_order_summary(store_id, popup_id);

-- =========================
-- 5-2) popup_order_items_view
-- =========================
CREATE TABLE IF NOT EXISTS order_query.popup_order_items_view (
    popup_id uuid NOT NULL,
    order_goods_id uuid NOT NULL,
    order_id uuid NOT NULL,
    store_id uuid NOT NULL,
    user_id bigint NOT NULL,
    order_no varchar(32),

    order_status varchar(30),
    ordered_at timestamp NOT NULL,

    item_type varchar(10) NOT NULL, -- SCHEDULE / GOODS

    schedule_id uuid,
    schedule_start_at timestamp,
    schedule_end_at timestamp,

    goods_variant_id uuid,
    goods_name varchar(100),
    stock_unit varchar(64),

    qty int NOT NULL,
    unit_price int NOT NULL,
    line_price int NOT NULL,

    payment_status varchar(20),
    payment_approved_at timestamp,

    checked_in boolean NOT NULL DEFAULT false,
    checkin_at timestamp,

    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,

    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT,

    PRIMARY KEY (popup_id, order_goods_id)
    );

-- 기본 조회 인덱스
CREATE INDEX IF NOT EXISTS idx_popup_items_type_time
    ON order_query.popup_order_items_view(popup_id, item_type, ordered_at DESC);

CREATE INDEX IF NOT EXISTS idx_popup_items_status_time
    ON order_query.popup_order_items_view(popup_id, order_status, ordered_at DESC);

CREATE INDEX IF NOT EXISTS idx_popup_items_checkin
    ON order_query.popup_order_items_view(popup_id, checked_in, ordered_at DESC);

-- =========================
-- 5-3) summary_applied_log
-- =========================
CREATE TABLE IF NOT EXISTS order_query.summary_applied_log (
    applied_id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    event_type varchar(50) NOT NULL,

    popup_id uuid NOT NULL,
    order_id uuid,

    -- 예약 KPI 증감
    delta_reservation_total int NOT NULL DEFAULT 0,
    delta_reservation_paid int NOT NULL DEFAULT 0,
    delta_reservation_cancelled int NOT NULL DEFAULT 0,

    -- 굿즈 KPI 증감
    delta_goods_total int NOT NULL DEFAULT 0,
    delta_goods_paid int NOT NULL DEFAULT 0,
    delta_goods_cancelled int NOT NULL DEFAULT 0,

    -- 체크인 증감
    delta_checked_in int NOT NULL DEFAULT 0,

    applied_at timestamp NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),

    CONSTRAINT uq_summary_applied_log_event UNIQUE (event_id, event_type)
    );

CREATE INDEX IF NOT EXISTS idx_summary_applied_log_popup
    ON order_query.summary_applied_log(popup_id);

CREATE INDEX IF NOT EXISTS idx_summary_applied_log_popup_time
    ON order_query.summary_applied_log(popup_id, applied_at DESC);

CREATE INDEX IF NOT EXISTS idx_summary_applied_log_order_time
    ON order_query.summary_applied_log(order_id, applied_at DESC);

RESET ROLE;
