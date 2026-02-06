SET search_path TO store;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'store'
          AND table_name = 'outbox_events'
          AND table_type = 'BASE TABLE'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = 'store'
              AND table_name = 'outbox_events_v1'
              AND table_type = 'BASE TABLE'
        ) THEN
            ALTER TABLE store.outbox_events RENAME TO outbox_events_v1;
        END IF;
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS store.outbox_events (
  id             BIGSERIAL PRIMARY KEY,
  event_id       UUID        NOT NULL,
  aggregate_type VARCHAR(100) NOT NULL,
  aggregate_id   VARCHAR(255) NOT NULL,
  event_type     VARCHAR(100) NOT NULL,
  partition_key  VARCHAR(255) NOT NULL,
  schema_version INTEGER     NOT NULL DEFAULT 1,
  event_data     JSONB       NOT NULL,
  headers        JSONB,
  occurred_at    TIMESTAMPTZ NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_created_at    ON store.outbox_events (created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate     ON store.outbox_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type    ON store.outbox_events (event_type);
CREATE INDEX IF NOT EXISTS idx_outbox_partition_key ON store.outbox_events (partition_key);
