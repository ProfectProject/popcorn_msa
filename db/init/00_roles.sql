-- Create service roles for local development (idempotent)
-- Note: Postgres does not support CREATE USER IF NOT EXISTS.

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

-- Grant database privileges to migrators for schema management
GRANT ALL PRIVILEGES ON DATABASE popcorn_db TO
    payment_migrator,
    order_migrator,
    store_migrator,
    user_auth_migrator,
    qr_migrator,
    order_query_migrator;
