-- QR/CheckIns service permissions setup
-- Grant necessary permissions to qr_app user for checkIns schema

-- Grant usage on checkIns schema to qr_app
GRANT USAGE ON SCHEMA checkIns TO qr_app;

-- Grant permissions on all existing tables in checkIns schema
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA checkIns TO qr_app;

-- Grant permissions on all existing sequences in checkIns schema
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA checkIns TO qr_app;

-- Grant permissions on all future tables and sequences (for when Flyway creates them)
ALTER DEFAULT PRIVILEGES IN SCHEMA checkIns GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO qr_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA checkIns GRANT USAGE, SELECT ON SEQUENCES TO qr_app;