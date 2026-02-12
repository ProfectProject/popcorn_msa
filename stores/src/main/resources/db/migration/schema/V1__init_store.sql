-- DO $$ BEGIN
--     IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'store_migrator') THEN
--         CREATE ROLE store_migrator LOGIN PASSWORD 'password_here';
--         CREATE ROLE store_migrator LOGIN PASSWORD 'password_here';
--     END IF;
-- END $$;

CREATE SCHEMA IF NOT EXISTS store AUTHORIZATION store_migrator;
GRANT ALL PRIVILEGES ON SCHEMA store TO store_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA store TO store_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA store TO store_migrator;

SET search_path TO store;

-- ENUM
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

-- stores (user_id는 user_auth 참조지만 FK 없음)
CREATE TABLE IF NOT EXISTS store.stores (
                                            store_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT NOT NULL, -- 논리 REF user_auth.users.user_id (no FK)

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

-- popups (store 내부 FK는 허용 가능)
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

-- popup_schedules
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

-- goods_variants
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

-- outbox_events
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
