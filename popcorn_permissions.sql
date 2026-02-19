-- ===========================================
-- Popcorn MSA 전체 서비스 데이터베이스 권한 설정
-- ===========================================
-- 실행 방법: docker exec -i popcorn-db psql -U postgres -d popcorn_db < popcorn_permissions.sql

-- 1. User Auth 서비스 권한 설정
-- --------------------------
-- User auth 서비스용 마이그레이션 사용자 생성
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_auth_migrator') THEN
        CREATE ROLE user_auth_migrator LOGIN PASSWORD 'user321';
    END IF;
END $$;

-- User auth 서비스용 애플리케이션 사용자 생성 (user_auth 스키마용)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_auth_app') THEN
        CREATE ROLE user_auth_app LOGIN PASSWORD 'user123';
    END IF;
END $$;

-- User_auth 스키마 생성 및 소유권 설정
CREATE SCHEMA IF NOT EXISTS user_auth AUTHORIZATION user_auth_migrator;

-- User_auth 스키마에 대한 migrator 권한 (DDL 작업용)
GRANT ALL PRIVILEGES ON SCHEMA user_auth TO user_auth_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA user_auth TO user_auth_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA user_auth TO user_auth_migrator;

-- User_auth 스키마에 대한 user_auth_app 권한 (CRUD 작업용)
GRANT USAGE ON SCHEMA user_auth TO user_auth_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA user_auth TO user_auth_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA user_auth TO user_auth_app;

-- User_auth app 사용자의 기본 스키마 경로 설정
ALTER ROLE user_auth_app SET search_path TO user_auth, public;

-- 향후 생성될 테이블에 대한 기본 권한 설정
ALTER DEFAULT PRIVILEGES IN SCHEMA user_auth GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO user_auth_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA user_auth GRANT USAGE, SELECT ON SEQUENCES TO user_auth_app;


-- 2. Store 서비스 권한 재설정
-- ---------------------------
-- Store 스키마에 대한 migrator 권한 재설정
GRANT ALL PRIVILEGES ON SCHEMA store TO store_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA store TO store_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA store TO store_migrator;

-- Store 스키마에 대한 app 권한 재설정
GRANT USAGE ON SCHEMA store TO store_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA store TO store_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA store TO store_app;

-- Store app 사용자의 기본 스키마 경로 설정
ALTER ROLE store_app SET search_path TO store, public;

-- 향후 생성될 테이블에 대한 기본 권한 설정
ALTER DEFAULT PRIVILEGES IN SCHEMA store GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO store_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA store GRANT USAGE, SELECT ON SEQUENCES TO store_app;


-- 3. CheckIns 서비스 권한 설정 (QR 스키마 사용)
-- ----------------------------------------------
-- CheckIns 서비스용 애플리케이션 사용자 생성 (migrator는 이미 존재)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qr_app') THEN
        CREATE ROLE qr_app LOGIN PASSWORD 'qr123';
    END IF;
END $$;

-- checkIns 스키마에 대한 migrator 권한 재설정
GRANT ALL PRIVILEGES ON SCHEMA checkIns TO qr_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA checkIns TO qr_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA checkIns TO qr_migrator;

-- checkIns 스키마에 대한 app 권한 설정
GRANT USAGE ON SCHEMA checkIns TO qr_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA checkIns TO qr_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA checkIns TO qr_app;

-- QR app 사용자의 기본 스키마 경로 설정
ALTER ROLE qr_app SET search_path TO checkIns, public;

-- 향후 생성될 테이블에 대한 기본 권한 설정
ALTER DEFAULT PRIVILEGES IN SCHEMA checkIns GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO qr_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA checkIns GRANT USAGE, SELECT ON SEQUENCES TO qr_app;


-- 4. Orders 서비스 권한 재설정
-- ---------------------------
-- Orders 스키마에 대한 migrator 권한 재설정
GRANT ALL PRIVILEGES ON SCHEMA orders TO order_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA orders TO order_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA orders TO order_migrator;

-- Orders 스키마에 대한 app 권한 재설정
GRANT USAGE ON SCHEMA orders TO order_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA orders TO order_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA orders TO order_app;

-- Orders app 사용자의 기본 스키마 경로 설정
ALTER ROLE order_app SET search_path TO orders, public;

-- 향후 생성될 테이블에 대한 기본 권한 설정
ALTER DEFAULT PRIVILEGES IN SCHEMA orders GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO order_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA orders GRANT USAGE, SELECT ON SEQUENCES TO order_app;


-- 5. Payment 서비스 권한 설정
-- ---------------------------
-- Payment 서비스용 마이그레이션 사용자 생성
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_migrator') THEN
        CREATE ROLE payment_migrator LOGIN PASSWORD 'payment321';
    END IF;
END $$;

-- Payment 서비스용 애플리케이션 사용자 생성
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_app') THEN
        CREATE ROLE payment_app LOGIN PASSWORD 'payment123';
    END IF;
END $$;

-- Payment 스키마 생성 및 소유권 설정
CREATE SCHEMA IF NOT EXISTS payment AUTHORIZATION payment_migrator;

-- Payment 스키마에 대한 migrator 권한 (DDL 작업용)
GRANT ALL PRIVILEGES ON SCHEMA payment TO payment_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA payment TO payment_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA payment TO payment_migrator;

-- Payment 스키마에 대한 app 권한 (CRUD 작업용)
GRANT USAGE ON SCHEMA payment TO payment_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payment TO payment_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payment TO payment_app;

-- Payment app 사용자의 기본 스키마 경로 설정
ALTER ROLE payment_app SET search_path TO payment, public;

-- 향후 생성될 테이블에 대한 기본 권한 설정
ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO payment_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT USAGE, SELECT ON SEQUENCES TO payment_app;

-- 6. Coupon 서비스 권한 설정
-- --------------------------
-- Coupon 서비스용 마이그레이션 사용자 생성
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'coupon_migrator') THEN
        CREATE ROLE coupon_migrator LOGIN PASSWORD 'coupon321';
    END IF;
END $$;

-- Coupon 서비스용 애플리케이션 사용자 생성
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'coupon_app') THEN
        CREATE ROLE coupon_app LOGIN PASSWORD 'coupon123';
    END IF;
END $$;

-- coupons 스키마 생성 및 소유권 설정
CREATE SCHEMA IF NOT EXISTS coupons AUTHORIZATION coupon_migrator;

-- coupons 스키마에 대한 migrator 권한 (DDL 작업용)
GRANT ALL PRIVILEGES ON SCHEMA coupons TO coupon_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA coupons TO coupon_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA coupons TO coupon_migrator;

-- coupons 스키마에 대한 app 권한 (CRUD 작업용)
GRANT USAGE ON SCHEMA coupons TO coupon_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA coupons TO coupon_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA coupons TO coupon_app;

-- coupon_app 사용자의 기본 스키마 경로 설정
ALTER ROLE coupon_app SET search_path TO coupons, public;

-- 향후 생성될 테이블에 대한 기본 권한 설정
ALTER DEFAULT PRIVILEGES IN SCHEMA coupons GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO coupon_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA coupons GRANT USAGE, SELECT ON SEQUENCES TO coupon_app;

-- 7. Order Query 서비스 권한 설정
-- -------------------------------
-- Order Query 서비스용 마이그레이션 사용자 생성
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_query_migrator') THEN
        CREATE ROLE order_query_migrator LOGIN PASSWORD 'quary321';
    END IF;
END $$;

-- Order Query 서비스용 애플리케이션 사용자 생성
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_query_app') THEN
        CREATE ROLE order_query_app LOGIN PASSWORD 'quary123';
    END IF;
END $$;

-- Order Query 스키마 생성 및 소유권 설정
CREATE SCHEMA IF NOT EXISTS order_query AUTHORIZATION order_query_migrator;

-- Order Query 스키마에 대한 migrator 권한 (DDL 작업용)
GRANT ALL PRIVILEGES ON SCHEMA order_query TO order_query_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA order_query TO order_query_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA order_query TO order_query_migrator;

-- Order Query 스키마에 대한 app 권한 (CRUD 작업용)
GRANT USAGE ON SCHEMA order_query TO order_query_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA order_query TO order_query_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA order_query TO order_query_app;

-- Order Query app 사용자의 기본 스키마 경로 설정
ALTER ROLE order_query_app SET search_path TO order_query, public;

-- 향후 생성될 테이블에 대한 기본 권한 설정
ALTER DEFAULT PRIVILEGES IN SCHEMA order_query GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO order_query_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_query GRANT USAGE, SELECT ON SEQUENCES TO order_query_app;


-- 8. 기존 테이블 소유권 정리
-- -------------------------

-- Store 테이블들의 소유권을 store_migrator로 변경
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'store' LOOP
        EXECUTE 'ALTER TABLE store.' || quote_ident(r.tablename) || ' OWNER TO store_migrator';
    END LOOP;
END $$;

-- QR 테이블들의 소유권을 qr_migrator로 변경
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'checkIns' LOOP
        EXECUTE 'ALTER TABLE checkIns.' || quote_ident(r.tablename) || ' OWNER TO qr_migrator';
    END LOOP;
END $$;

-- Orders 테이블들의 소유권을 order_migrator로 변경
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'orders' LOOP
        EXECUTE 'ALTER TABLE orders.' || quote_ident(r.tablename) || ' OWNER TO order_migrator';
    END LOOP;
END $$;

-- Payment 테이블들의 소유권을 payment_migrator로 변경
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'payment' LOOP
        EXECUTE 'ALTER TABLE payment.' || quote_ident(r.tablename) || ' OWNER TO payment_migrator';
    END LOOP;
END $$;

-- User_auth 테이블들의 소유권을 user_auth_migrator로 변경
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'user_auth' LOOP
        EXECUTE 'ALTER TABLE user_auth.' || quote_ident(r.tablename) || ' OWNER TO user_auth_migrator';
    END LOOP;
END $$;


-- 9. 권한 설정 완료 확인
-- ---------------------
\echo '=== 권한 설정 완료 ==='

-- 각 스키마별 테이블 수 확인
SELECT
    schemaname as "스키마",
    count(*) as "테이블 수"
FROM pg_tables
WHERE schemaname IN ('store', 'checkIns', 'orders', 'payment', 'user_auth', 'order_query', 'coupons')
GROUP BY schemaname
ORDER BY schemaname;

\echo '=== 생성된 사용자 확인 ==='

-- 생성된 서비스별 사용자 확인
SELECT rolname as "사용자명"
FROM pg_roles
WHERE rolname LIKE '%_app' OR rolname LIKE '%_migrator'
ORDER BY rolname;

\echo '=== 권한 설정 요약 ==='
\echo '각 서비스별 사용자:'
\echo '- User Auth: user_auth_app (CRUD), user_auth_migrator (DDL)'
\echo '- Store: store_app (CRUD), store_migrator (DDL)'
\echo '- CheckIns: qr_app (CRUD), qr_migrator (DDL)'
\echo '- Orders: order_app (CRUD), order_migrator (DDL)'
\echo '- Payment: payment_app (CRUD), payment_migrator (DDL)'
\echo '- Coupon: coupon_app (CRUD), coupon_migrator (DDL)'
\echo '- Order Query: order_query_app (CRUD), order_query_migrator (DDL)'
\echo ''
\echo '각 app 사용자는 해당 스키마를 기본 경로로 설정됨'
\echo '모든 권한 설정이 완료되었습니다!'
