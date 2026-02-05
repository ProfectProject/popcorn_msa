-- Outbox 이벤트 테이블 (Transactional Outbox Pattern)
-- checkIns 스키마 기본 사용 (default_schema=checkIns)

CREATE TABLE IF NOT EXISTS outbox_events (
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

    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox_events (created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON outbox_events (event_type);
CREATE INDEX IF NOT EXISTS idx_outbox_partition_key ON outbox_events (partition_key);
