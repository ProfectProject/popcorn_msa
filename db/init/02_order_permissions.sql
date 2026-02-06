-- Order service permissions setup
-- Grant necessary permissions to order_app user for orders schema

-- Grant usage on orders schema to order_app
GRANT USAGE ON SCHEMA orders TO order_app;

-- Grant permissions on all existing tables in orders schema
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA orders TO order_app;

-- Grant permissions on all existing sequences in orders schema
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA orders TO order_app;

-- Grant permissions on all future tables and sequences (for when Flyway creates them)
ALTER DEFAULT PRIVILEGES IN SCHEMA orders GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO order_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA orders GRANT USAGE, SELECT ON SEQUENCES TO order_app;

-- Grant permissions on custom types in orders schema
GRANT USAGE ON TYPE orders.itemtype TO order_app;
GRANT USAGE ON TYPE orders.orderstatus TO order_app;