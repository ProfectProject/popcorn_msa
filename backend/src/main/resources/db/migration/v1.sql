-- Domain schema DDL for logical DB separation

-- 0) user_auth
SET ROLE user_auth_migrator;

DO $$ BEGIN
CREATE TYPE user_auth.user_role AS ENUM ('CUSTOMER','OWNER','MANAGER','ADMIN');
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

-- 1) store
RESET ROLE;
SET ROLE store_migrator;

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
                                            store_id    UUID PRIMARY KEY,
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
                                            popup_id            UUID PRIMARY KEY,
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
                                                     schedule_id UUID PRIMARY KEY,
                                                     popup_id    UUID NOT NULL,
                                                     start_at    TIMESTAMP NOT NULL,
                                                     end_at      TIMESTAMP NOT NULL,
                                                     price       INT NOT NULL,
                                                     capacity    INT NOT NULL,
                                                     remaining_capacity INT NOT NULL,
                                                     reservation_capacity INT DEFAULT 0,
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
                                                    goods_id    UUID PRIMARY KEY,
                                                    popup_id    UUID NOT NULL,
                                                    stock_unit  VARCHAR(64),
    goods_name  VARCHAR(100) NOT NULL,
    goods_price INT NOT NULL,
    stock       INT NOT NULL,
    reservation_stock INT DEFAULT 0,
    is_active   BOOLEAN DEFAULT TRUE,
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

-- 2) order
RESET ROLE;
SET ROLE order_migrator;

DO $$ BEGIN
CREATE TYPE "order".order_status AS ENUM
    ('REQUESTED','ACCEPTED','REJECTED','RESERVED','PAYMENT_PENDING','PAID','COMPLETED','CANCELLED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS "order".orders (
                                              order_id     UUID PRIMARY KEY,
                                              order_no     VARCHAR(32) UNIQUE,
    user_id      BIGINT NOT NULL,
    store_id     UUID NOT NULL,
    status       "order".order_status NOT NULL,
    cancelable_until TIMESTAMP,
    total_price  INT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMP,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT
    );

CREATE TABLE IF NOT EXISTS "order".order_goods (
                                                   order_goods_id UUID PRIMARY KEY,
                                                   order_id       UUID NOT NULL,
                                                   popup_id       UUID NOT NULL,
                                                   item_type      VARCHAR(10) NOT NULL,
    schedule_id    UUID,
    goods_variant_id UUID,
    qty           INT NOT NULL,
    unit_price    INT NOT NULL,
    price         INT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMP,
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT
    );

DO $$ BEGIN
ALTER TABLE "order".order_goods
    ADD CONSTRAINT fk_order_goods_order
        FOREIGN KEY (order_id) REFERENCES "order".orders(order_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS "order".order_status_histories (
                                                              order_status_id UUID PRIMARY KEY,
                                                              order_id        UUID NOT NULL,
                                                              from_status     "order".order_status,
                                                              to_status       "order".order_status NOT NULL,
                                                              reason          VARCHAR(255),
    changed_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT,
    deleted_by      BIGINT
    );

DO $$ BEGIN
ALTER TABLE "order".order_status_histories
    ADD CONSTRAINT fk_order_status_histories_order
        FOREIGN KEY (order_id) REFERENCES "order".orders(order_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 3) payment
RESET ROLE;
SET ROLE payment_migrator;

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
                                                raw_payload  TEXT,
                                                approved_at  TIMESTAMP,
                                                created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMP,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT
    );

-- payment schema cancel failure queue table
RESET ROLE;
SET ROLE payment_migrator;

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

RESET ROLE;

-- 4) qr
RESET ROLE;
SET ROLE qr_migrator;

CREATE TABLE IF NOT EXISTS qr.order_qr_codes (
                                                 qr_id       UUID PRIMARY KEY,
                                                 order_id    UUID NOT NULL,
                                                 qr_code     VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  BIGINT
    );

CREATE TABLE IF NOT EXISTS qr.checkins (
                                           checkin_id       UUID PRIMARY KEY,
                                           order_id         UUID NOT NULL,
                                           order_qr_code_id UUID NOT NULL,
                                           created_at       TIMESTAMP NOT NULL DEFAULT now(),
    created_by       BIGINT
    );

DO $$ BEGIN
ALTER TABLE qr.checkins
    ADD CONSTRAINT fk_checkins_qr
        FOREIGN KEY (order_qr_code_id) REFERENCES qr.order_qr_codes(qr_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 5) order_query (read model)
RESET ROLE;
SET ROLE order_query_migrator;

CREATE TABLE IF NOT EXISTS order_query.popup_order_summary (
                                                               popup_id uuid PRIMARY KEY,
                                                               store_id uuid NOT NULL,
                                                               popup_title varchar(200),
    popup_status varchar(30),
    address_road text,
    address_detail text,
    reservation_open_at timestamp,
    first_schedule_start_at timestamp,
    last_schedule_end_at timestamp,
    total_orders int NOT NULL DEFAULT 0,
    paid_orders int NOT NULL DEFAULT 0,
    cancelled_orders int NOT NULL DEFAULT 0,
    checked_in_orders int NOT NULL DEFAULT 0,
    updated_at timestamp NOT NULL DEFAULT now()
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
    updated_at timestamp NOT NULL DEFAULT now(),
    PRIMARY KEY (popup_id, order_goods_id)
    );

CREATE INDEX IF NOT EXISTS idx_popup_items_popup_time
    ON order_query.popup_order_items_view(popup_id, ordered_at DESC);

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
    delta_paid int NOT NULL DEFAULT 0,
    delta_cancelled int NOT NULL DEFAULT 0,
    delta_checked_in int NOT NULL DEFAULT 0,
    applied_at timestamp NOT NULL,
    created_at timestamp NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_summary_applied_log_popup
    ON order_query.summary_applied_log(popup_id);

RESET ROLE;