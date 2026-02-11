-- Outbox 이벤트 테이블 생성 (Order 서비스)

CREATE TABLE IF NOT EXISTS orders.outbox_events (
    id BIGSERIAL PRIMARY KEY,

    event_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,

    partition_key VARCHAR(255) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    topic VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    headers JSONB NULL,

    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON orders.outbox_events (created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON orders.outbox_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON orders.outbox_events (event_type);
CREATE INDEX IF NOT EXISTS idx_outbox_partition_key ON orders.outbox_events (partition_key);

-- 2) 권한 최소화
-- order_app: INSERT만 가능 (원칙적으로 SELECT/UPDATE/DELETE 불필요)
GRANT USAGE ON SCHEMA orders TO order_app;

REVOKE ALL PRIVILEGES ON TABLE orders.outbox_events FROM order_app;
GRANT INSERT ON TABLE orders.outbox_events TO order_app;



