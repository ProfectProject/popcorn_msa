SET search_path TO store;

ALTER TABLE store.outbox_events
  ADD COLUMN IF NOT EXISTS topic VARCHAR(255) NOT NULL DEFAULT 'store-events';

ALTER TABLE store.outbox_events
  ALTER COLUMN schema_version SET DEFAULT 4;
