-- Cross-service permissions for microservices communication
-- Allow payment service to read order information directly from DB

-- Grant payment_app read access to orders schema
GRANT USAGE ON SCHEMA orders TO payment_app;

-- Grant SELECT permission on orders tables to payment_app
GRANT SELECT ON ALL TABLES IN SCHEMA orders TO payment_app;

-- Grant permissions on future tables (for when new tables are created)
ALTER DEFAULT PRIVILEGES IN SCHEMA orders GRANT SELECT ON TABLES TO payment_app;

-- Grant permissions on custom types in orders schema
GRANT USAGE ON TYPE orders.itemtype TO payment_app;
GRANT USAGE ON TYPE orders.orderstatus TO payment_app;

-- Comment: This allows payment service to directly query order information
-- instead of relying on event-based communication which can timeout