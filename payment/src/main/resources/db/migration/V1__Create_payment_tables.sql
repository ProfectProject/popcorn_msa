-- Initial payment schema for payment-service
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_migrator') THEN
        CREATE ROLE payment_migrator LOGIN PASSWORD '${PAYMENT_MIGRATOR_PASSWORD}';
    END IF;
END $$;

CREATE SCHEMA IF NOT EXISTS payment AUTHORIZATION payment_migrator;
GRANT ALL PRIVILEGES ON SCHEMA payment TO payment_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA payment TO payment_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA payment TO payment_migrator;

DO $$ BEGIN
    CREATE TYPE payment.payment_method AS ENUM ('CARD','TRANSFER','EASY_PAY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE payment.payment_status AS ENUM ('READY','PAID','FAILED','CANCELLED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS payment.payments (
    payment_id UUID PRIMARY KEY,
    order_id   UUID NOT NULL,
    method     payment.payment_method NOT NULL,
    status     payment.payment_status NOT NULL,
    amount     INT NOT NULL,
    payment_key VARCHAR(200),
    raw_payload TEXT,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT
);
