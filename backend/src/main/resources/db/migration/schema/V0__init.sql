-- Domain schema DDL for logical DB separation (consolidated)
-- Baseline schema based on each module's latest migrations

-- 0) roles (idempotent)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_app') THEN
        CREATE ROLE payment_app LOGIN PASSWORD 'payment123';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_migrator') THEN
        CREATE ROLE payment_migrator LOGIN PASSWORD 'payment321';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_app') THEN
        CREATE ROLE order_app LOGIN PASSWORD 'order123';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_migrator') THEN
        CREATE ROLE order_migrator LOGIN PASSWORD 'order321';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'store_app') THEN
        CREATE ROLE store_app LOGIN PASSWORD 'store123';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'store_migrator') THEN
        CREATE ROLE store_migrator LOGIN PASSWORD 'store123';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_auth_app') THEN
        CREATE ROLE user_auth_app LOGIN PASSWORD 'user_auth123';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_auth_migrator') THEN
        CREATE ROLE user_auth_migrator LOGIN PASSWORD 'user321';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qr_app') THEN
        CREATE ROLE qr_app LOGIN PASSWORD 'qr123';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qr_migrator') THEN
        CREATE ROLE qr_migrator LOGIN PASSWORD 'qr321';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_query_app') THEN
        CREATE ROLE order_query_app LOGIN PASSWORD 'quary123';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_query_migrator') THEN
        CREATE ROLE order_query_migrator LOGIN PASSWORD 'quary321';
    END IF;
END $$;

-- 0) user_auth
SET ROLE user_auth_migrator;

CREATE SCHEMA IF NOT EXISTS user_auth AUTHORIZATION user_auth_migrator;

DO $$ BEGIN
    CREATE TYPE user_auth.user_role AS ENUM ('CUSTOMER','OWNER','MANAGER');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS user_auth.users (
    user_id     BIGSERIAL PRIMARY KEY,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    phone       VARCHAR(11),
    email       VARCHAR(255) NOT NULL UNIQUE,
    role        user_auth.user_role NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT
);

CREATE TABLE IF NOT EXISTS user_auth.customer_addresses (
    addr_id      UUID PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    addr_name    VARCHAR(50) NOT NULL,
    address1     VARCHAR(255) NOT NULL,
    address2     VARCHAR(255),
    postal_code  VARCHAR(10),
    is_default   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMP,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT
);

DO $$ BEGIN
    ALTER TABLE user_auth.customer_addresses
        ADD CONSTRAINT fk_customer_addresses_user
            FOREIGN KEY (user_id) REFERENCES user_auth.users(user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- user_auth privileges
GRANT ALL PRIVILEGES ON SCHEMA user_auth TO user_auth_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA user_auth TO user_auth_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA user_auth TO user_auth_migrator;

GRANT USAGE ON SCHEMA user_auth TO user_auth_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA user_auth TO user_auth_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA user_auth TO user_auth_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA user_auth GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO user_auth_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA user_auth GRANT USAGE, SELECT ON SEQUENCES TO user_auth_app;

-- 1) store
RESET ROLE;
SET ROLE store_migrator;

CREATE SCHEMA IF NOT EXISTS store AUTHORIZATION store_migrator;

DO $$ BEGIN
    CREATE TYPE store.store_status AS ENUM ('DRAFT','PENDING','ACTIVE','SUSPENDED','CLOSED','HIDDEN');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE store.popup_status AS ENUM ('DRAFT','REQUEST','APPROVED','OPEN','CLOSED','CANCELLED','HIDDEN');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE store.popup_category AS ENUM
        ('FOOD','IDOL','EXHIBITION','WORKSHOP','FASHION','BEAUTY','LIFESTYLE','ART','GAME','TECH','SPORTS','BOOK','PET','ETC');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS store.stores (
    store_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT NOT NULL,
    store_name  VARCHAR(100) NOT NULL,
    status      store.store_status NOT NULL DEFAULT 'DRAFT',
    reason      VARCHAR(500),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT
);

CREATE TABLE IF NOT EXISTS store.popups (
    popup_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id            UUID NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    category            store.popup_category NOT NULL,
    status              store.popup_status NOT NULL,
    reservation_open_at TIMESTAMP,
    address_road        TEXT,
    address_detail      TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    deleted_by          BIGINT
);

DO $$ BEGIN
    ALTER TABLE store.popups
        ADD CONSTRAINT fk_popups_store
            FOREIGN KEY (store_id) REFERENCES store.stores(store_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS store.popup_schedules (
    schedule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    popup_id    UUID NOT NULL,
    start_at    TIMESTAMP NOT NULL,
    end_at      TIMESTAMP NOT NULL,
    price       INT NOT NULL,
    capacity    INT NOT NULL,
    remaining_capacity INT NOT NULL,
    reservation_capacity INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT
);

DO $$ BEGIN
    ALTER TABLE store.popup_schedules
        ADD CONSTRAINT fk_schedules_popup
            FOREIGN KEY (popup_id) REFERENCES store.popups(popup_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS store.goods_variants (
    goods_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    popup_id    UUID NOT NULL,
    stock_unit  VARCHAR(64),
    goods_name  VARCHAR(100) NOT NULL,
    goods_price INT NOT NULL,
    stock       INT NOT NULL,
    reservation_stock INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT
);

DO $$ BEGIN
    ALTER TABLE store.goods_variants
        ADD CONSTRAINT fk_goods_popup
            FOREIGN KEY (popup_id) REFERENCES store.popups(popup_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS store.outbox_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id     UUID NOT NULL,
    aggregate_type   VARCHAR(100) NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    event_id         VARCHAR(255) NOT NULL UNIQUE,
    event_data       JSONB NOT NULL,
    published        BOOLEAN NOT NULL DEFAULT FALSE,
    published_at     TIMESTAMP,
    publish_attempts INT NOT NULL DEFAULT 0,
    last_error       TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS store.goods_order_reservations (
    reservation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    order_no VARCHAR(64),
    popup_id UUID,
    goods_variant_id UUID,
    schedule_id UUID,
    quantity INT NOT NULL,
    reservation_type VARCHAR(32) NOT NULL DEFAULT 'GOODS',
    status VARCHAR(32) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_goods_order_reservations_order_id
    ON store.goods_order_reservations(order_id);

CREATE INDEX IF NOT EXISTS idx_goods_order_reservations_schedule_id
    ON store.goods_order_reservations(schedule_id);

-- store privileges
GRANT ALL PRIVILEGES ON SCHEMA store TO store_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA store TO store_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA store TO store_migrator;

GRANT USAGE ON SCHEMA store TO store_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA store TO store_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA store TO store_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA store GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO store_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA store GRANT USAGE, SELECT ON SEQUENCES TO store_app;

-- 2) orders
RESET ROLE;
SET ROLE order_migrator;

CREATE SCHEMA IF NOT EXISTS orders AUTHORIZATION order_migrator;

DO $$ BEGIN
    CREATE TYPE orders.itemtype AS ENUM ('RESERVATION', 'GOODS', 'MIXED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE orders.orderstatus AS ENUM ('REQUESTED', 'RESERVED', 'PAYMENT_PENDING', 'PENDING_PAYMENT', 'PAID', 'CONFIRMED', 'PROCESSING', 'COMPLETED', 'CANCELLED', 'REFUNDED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS orders.p_orders (
    order_id UUID PRIMARY KEY,
    order_no VARCHAR(32) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    popup_id UUID,
    order_type orders.itemtype NOT NULL,
    status orders.orderstatus NOT NULL DEFAULT 'REQUESTED',
    cancelable_until TIMESTAMP,
    total_price INTEGER NOT NULL,
    paid_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    canceled_at TIMESTAMP,
    cancel_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT
);

CREATE TABLE IF NOT EXISTS orders.p_order_goods (
    order_goods_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    popup_id UUID,
    item_type orders.itemtype NOT NULL,
    schedule_id UUID,
    goods_variant_id UUID,
    qty INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,
    price INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT
);

CREATE TABLE IF NOT EXISTS orders.p_order_status_histories (
    order_status_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    from_status orders.orderstatus,
    to_status orders.orderstatus NOT NULL,
    reason VARCHAR(255),
    changed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT
);

ALTER TABLE orders.p_order_goods
    ADD CONSTRAINT fk_p_order_goods_order
        FOREIGN KEY (order_id) REFERENCES orders.p_orders(order_id);

ALTER TABLE orders.p_order_status_histories
    ADD CONSTRAINT fk_p_order_status_histories_order
        FOREIGN KEY (order_id) REFERENCES orders.p_orders(order_id);

CREATE INDEX IF NOT EXISTS idx_p_orders_user_id ON orders.p_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_p_orders_popup_id ON orders.p_orders(popup_id);
CREATE INDEX IF NOT EXISTS idx_p_orders_status ON orders.p_orders(status);
CREATE INDEX IF NOT EXISTS idx_p_orders_order_type ON orders.p_orders(order_type);
CREATE INDEX IF NOT EXISTS idx_p_orders_created_at ON orders.p_orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_p_orders_order_no ON orders.p_orders(order_no);
CREATE INDEX IF NOT EXISTS idx_p_orders_paid_at ON orders.p_orders(paid_at);
CREATE INDEX IF NOT EXISTS idx_p_orders_cancelable_until ON orders.p_orders(cancelable_until);

CREATE INDEX IF NOT EXISTS idx_p_order_goods_order_id ON orders.p_order_goods(order_id);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_popup_id ON orders.p_order_goods(popup_id);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_item_type ON orders.p_order_goods(item_type);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_schedule_id ON orders.p_order_goods(schedule_id);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_goods_variant_id ON orders.p_order_goods(goods_variant_id);
CREATE INDEX IF NOT EXISTS idx_p_order_goods_created_at ON orders.p_order_goods(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_p_order_status_histories_order_id ON orders.p_order_status_histories(order_id);
CREATE INDEX IF NOT EXISTS idx_p_order_status_histories_changed_at ON orders.p_order_status_histories(changed_at DESC);

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

-- orders privileges
GRANT ALL PRIVILEGES ON SCHEMA orders TO order_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA orders TO order_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA orders TO order_migrator;

GRANT USAGE ON SCHEMA orders TO order_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA orders TO order_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA orders TO order_app;

GRANT USAGE ON TYPE orders.itemtype TO order_app;
GRANT USAGE ON TYPE orders.orderstatus TO order_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA orders GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO order_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA orders GRANT USAGE, SELECT ON SEQUENCES TO order_app;

-- 3) payment
RESET ROLE;
SET ROLE payment_migrator;

CREATE SCHEMA IF NOT EXISTS payment AUTHORIZATION payment_migrator;

DO $$ BEGIN
    CREATE TYPE payment.payment_method AS ENUM ('CARD','TRANSFER','EASY_PAY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE payment.payment_status AS ENUM ('READY','PAID','FAILED','CANCELLED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS payment.payments (
    payment_id   UUID PRIMARY KEY,
    order_id     UUID NOT NULL,
    method       payment.payment_method NOT NULL,
    status       payment.payment_status NOT NULL,
    amount       INT NOT NULL,
    payment_key  VARCHAR(200),
    raw_payload  TEXT,
    approved_at  TIMESTAMP,
    is_deleted   BOOLEAN DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMP,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_payment_key
    ON payment.payments (payment_key);

DO $$ BEGIN
    CREATE TYPE payment.queue_status AS ENUM ('PENDING','RETRYING','SUCCESS','FAILED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS payment.payment_cancel_failure_queue (
    id            BIGSERIAL PRIMARY KEY,
    order_id      UUID NOT NULL,
    payment_id    UUID NOT NULL,
    payment_key   VARCHAR(255) NOT NULL,
    cancel_reason VARCHAR(255) NOT NULL,
    failure_reason VARCHAR(255),
    amount        INT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts  INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP,
    status        payment.queue_status NOT NULL DEFAULT 'PENDING',
    completed_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS payment.outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID          NOT NULL,
    aggregate_type VARCHAR(100)  NOT NULL,
    aggregate_id   VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    partition_key  VARCHAR(255)  NOT NULL,
    schema_version INTEGER       NOT NULL DEFAULT 1,
    event_data     JSONB         NOT NULL,
    headers        JSONB         NULL,
    occurred_at    TIMESTAMPTZ   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_outbox_created_at ON payment.outbox_events (created_at);
CREATE INDEX IF NOT EXISTS idx_payment_outbox_aggregate ON payment.outbox_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_payment_outbox_event_type ON payment.outbox_events (event_type);
CREATE INDEX IF NOT EXISTS idx_payment_outbox_partition_key ON payment.outbox_events (partition_key);

-- payment privileges
GRANT ALL PRIVILEGES ON SCHEMA payment TO payment_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA payment TO payment_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA payment TO payment_migrator;

GRANT USAGE ON SCHEMA payment TO payment_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payment TO payment_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payment TO payment_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO payment_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT USAGE, SELECT ON SEQUENCES TO payment_app;

-- 4) checkIns (QR)
RESET ROLE;
SET ROLE qr_migrator;

CREATE SCHEMA IF NOT EXISTS checkIns AUTHORIZATION qr_migrator;

CREATE TABLE IF NOT EXISTS checkIns.qr_order_qr_codes (
    qr_id       UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    qr_code     VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  BIGINT,
    store_id    UUID,
    popup_id    UUID,
    order_goods_id UUID
);

ALTER TABLE checkIns.qr_order_qr_codes
    ADD COLUMN IF NOT EXISTS popup_id UUID,
    ADD COLUMN IF NOT EXISTS order_goods_id UUID;

CREATE TABLE IF NOT EXISTS checkIns.qr_checkins (
    checkin_id       UUID PRIMARY KEY,
    order_id         UUID NOT NULL,
    order_qr_code_id UUID NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    created_by       BIGINT,
    store_id         UUID,
    popup_id         UUID,
    order_goods_id   UUID
);

DO $$ BEGIN
    ALTER TABLE checkIns.qr_checkins
        ADD CONSTRAINT fk_checkins_qr
            FOREIGN KEY (order_qr_code_id) REFERENCES checkIns.qr_order_qr_codes(qr_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE INDEX IF NOT EXISTS idx_qr_codes_order_id ON checkIns.qr_order_qr_codes(order_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_expires_at ON checkIns.qr_order_qr_codes(expires_at);
CREATE INDEX IF NOT EXISTS idx_checkins_order_id ON checkIns.qr_checkins(order_id);
CREATE INDEX IF NOT EXISTS idx_checkins_created_at ON checkIns.qr_checkins(created_at);

CREATE INDEX IF NOT EXISTS idx_qr_codes_store_id ON checkIns.qr_order_qr_codes(store_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_popup_id ON checkIns.qr_order_qr_codes(popup_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_order_goods_id ON checkIns.qr_order_qr_codes(order_goods_id);

CREATE INDEX IF NOT EXISTS idx_checkins_store_id ON checkIns.qr_checkins(store_id);
CREATE INDEX IF NOT EXISTS idx_checkins_popup_id ON checkIns.qr_checkins(popup_id);
CREATE INDEX IF NOT EXISTS idx_checkins_order_goods_id ON checkIns.qr_checkins(order_goods_id);

CREATE TABLE IF NOT EXISTS checkIns.outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID          NOT NULL,
    aggregate_type VARCHAR(100)  NOT NULL,
    aggregate_id   VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    partition_key  VARCHAR(255)  NOT NULL,
    schema_version INTEGER       NOT NULL DEFAULT 1,
    event_data     JSONB         NOT NULL,
    headers        JSONB         NULL,
    occurred_at    TIMESTAMPTZ   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    popup_id       UUID,
    order_goods_id UUID,
    CONSTRAINT uq_checkins_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON checkIns.outbox_events (created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON checkIns.outbox_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON checkIns.outbox_events (event_type);
CREATE INDEX IF NOT EXISTS idx_outbox_partition_key ON checkIns.outbox_events (partition_key);
CREATE INDEX IF NOT EXISTS idx_outbox_popup_id ON checkIns.outbox_events(popup_id);
CREATE INDEX IF NOT EXISTS idx_outbox_order_goods_id ON checkIns.outbox_events(order_goods_id);
CREATE INDEX IF NOT EXISTS idx_outbox_popup_order_goods ON checkIns.outbox_events(popup_id, order_goods_id);

-- checkIns privileges
GRANT ALL PRIVILEGES ON SCHEMA checkIns TO qr_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA checkIns TO qr_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA checkIns TO qr_migrator;

GRANT USAGE ON SCHEMA checkIns TO qr_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA checkIns TO qr_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA checkIns TO qr_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA checkIns GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO qr_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA checkIns GRANT USAGE, SELECT ON SEQUENCES TO qr_app;

-- 5) order_query (read model)
RESET ROLE;
SET ROLE order_query_migrator;

CREATE SCHEMA IF NOT EXISTS order_query AUTHORIZATION order_query_migrator;

CREATE TABLE IF NOT EXISTS order_query.popup_order_summary (
    popup_id uuid PRIMARY KEY,
    store_id uuid NOT NULL,
    popup_title varchar(200),
    popup_status varchar(30),
    address_road text,
    address_detail text,
    reservation_open_at timestamp,
    reservation_total_orders int NOT NULL DEFAULT 0,
    reservation_paid_orders int NOT NULL DEFAULT 0,
    reservation_cancelled_orders int NOT NULL DEFAULT 0,
    goods_total_orders int NOT NULL DEFAULT 0,
    goods_paid_orders int NOT NULL DEFAULT 0,
    goods_cancelled_orders int NOT NULL DEFAULT 0,
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

CREATE TABLE IF NOT EXISTS order_query.popup_order_items_view (
    popup_id uuid NOT NULL,
    order_goods_id uuid NOT NULL,
    order_id uuid NOT NULL,
    store_id uuid NOT NULL,
    user_id bigint NOT NULL,
    order_no varchar(32),
    order_status varchar(30),
    ordered_at timestamp NOT NULL,
    item_type varchar(10) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_popup_items_type_time
    ON order_query.popup_order_items_view(popup_id, item_type, ordered_at DESC);

CREATE INDEX IF NOT EXISTS idx_popup_items_status_time
    ON order_query.popup_order_items_view(popup_id, order_status, ordered_at DESC);

CREATE INDEX IF NOT EXISTS idx_popup_items_checkin
    ON order_query.popup_order_items_view(popup_id, checked_in, ordered_at DESC);

CREATE TABLE IF NOT EXISTS order_query.summary_applied_log (
    applied_id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    event_type varchar(50) NOT NULL,
    popup_id uuid NOT NULL,
    order_id uuid,
    delta_reservation_total int NOT NULL DEFAULT 0,
    delta_reservation_paid int NOT NULL DEFAULT 0,
    delta_reservation_cancelled int NOT NULL DEFAULT 0,
    delta_goods_total int NOT NULL DEFAULT 0,
    delta_goods_paid int NOT NULL DEFAULT 0,
    delta_goods_cancelled int NOT NULL DEFAULT 0,
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

-- order_query privileges
GRANT ALL PRIVILEGES ON SCHEMA order_query TO order_query_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA order_query TO order_query_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA order_query TO order_query_migrator;

GRANT USAGE ON SCHEMA order_query TO order_query_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA order_query TO order_query_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA order_query TO order_query_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA order_query GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO order_query_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_query GRANT USAGE, SELECT ON SEQUENCES TO order_query_app;

RESET ROLE;
