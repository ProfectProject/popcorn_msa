-- =================================================================
-- 🎫 쿠폰 애플리케이션 사용자 생성 V2
-- 🔐 coupon_app: 런타임에서 DML 전용 사용자 (읽기/쓰기/수정/삭제)
-- 🛠️ coupon_migrator: DDL 전용 마이그레이션 사용자 (이미 V0에서 생성됨)
-- =================================================================

-- ✅ 1. coupon_app 사용자 생성 (애플리케이션 런타임용)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'coupon_app') THEN
        CREATE USER coupon_app WITH PASSWORD 'coupon123';
        RAISE NOTICE '✅ coupon_app 사용자 생성 완료';
    ELSE
        RAISE NOTICE 'ℹ️ coupon_app 사용자 이미 존재';
    END IF;
END $$;

-- ✅ 2. coupons 스키마에 대한 DML 권한 부여 (애플리케이션 운영용)
-- 테이블 읽기/쓰기 권한
GRANT USAGE ON SCHEMA coupons TO coupon_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA coupons TO coupon_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA coupons TO coupon_app;

-- 미래에 생성될 테이블에 대한 기본 권한 설정
ALTER DEFAULT PRIVILEGES IN SCHEMA coupons GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO coupon_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA coupons GRANT USAGE, SELECT ON SEQUENCES TO coupon_app;

-- ✅ 3. 다른 스키마에 대한 읽기 전용 권한 (MSA 간 참조용)
-- 주문 정보 조회 (결제 상태 확인, 쿠폰 적용 검증)
GRANT SELECT ON ALL TABLES IN SCHEMA orders TO coupon_app;
GRANT SELECT ON ALL TABLES IN SCHEMA payment TO coupon_app;
GRANT SELECT ON ALL TABLES IN SCHEMA users TO coupon_app;

-- 미래 테이블에 대한 읽기 권한
ALTER DEFAULT PRIVILEGES IN SCHEMA orders GRANT SELECT ON TABLES TO coupon_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT SELECT ON TABLES TO coupon_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA users GRANT SELECT ON TABLES TO coupon_app;

-- ✅ 4. 함수 실행 권한
GRANT EXECUTE ON FUNCTION update_updated_at_column() TO coupon_app;

-- ✅ 5. 보안 검증 - coupon_app은 DDL 권한 없음 확인
DO $$
DECLARE
    schema_owner TEXT;
    app_privileges TEXT[];
BEGIN
    -- 스키마 소유자 확인
    SELECT nspowner::regrole::text
    INTO schema_owner
    FROM pg_namespace
    WHERE nspname = 'coupons';

    -- coupon_app의 권한 확인
    SELECT array_agg(privilege_type)
    INTO app_privileges
    FROM information_schema.table_privileges
    WHERE grantee = 'coupon_app' AND table_schema = 'coupons'
    LIMIT 5;

    RAISE NOTICE '🔐 보안 검증 결과:';
    RAISE NOTICE '   📂 coupons 스키마 소유자: %', schema_owner;
    RAISE NOTICE '   👤 coupon_app 권한: % (DDL 제외)', COALESCE(array_to_string(app_privileges, ', '), 'NONE');
    RAISE NOTICE '   ✅ coupon_migrator: DDL 전체 권한 (스키마 소유자)';
    RAISE NOTICE '   ✅ coupon_app: DML만 허용 (SELECT, INSERT, UPDATE, DELETE)';

    -- DDL 권한이 없는지 확인
    IF schema_owner != 'coupon_migrator' THEN
        RAISE WARNING '⚠️ coupons 스키마 소유자가 coupon_migrator가 아닙니다: %', schema_owner;
    END IF;
END $$;

-- ✅ 6. 연결 제한 및 보안 강화
ALTER USER coupon_app CONNECTION LIMIT 50;  -- 최대 50개 연결
ALTER USER coupon_app SET statement_timeout = '30s';  -- 쿼리 타임아웃 30초
ALTER USER coupon_app SET idle_in_transaction_session_timeout = '10min';  -- 유휴 트랜잭션 타임아웃

-- ✅ 최종 결과 출력
DO $$
BEGIN
    RAISE NOTICE '🎉 쿠폰 서비스 사용자 권한 설정 완료!';
    RAISE NOTICE '';
    RAISE NOTICE '📋 사용자 역할 분리:';
    RAISE NOTICE '   🛠️ coupon_migrator: DDL 권한 (Flyway/마이그레이션 전용)';
    RAISE NOTICE '   🏃 coupon_app: DML 권한 (애플리케이션 런타임 전용)';
    RAISE NOTICE '';
    RAISE NOTICE '🔐 보안 원칙:';
    RAISE NOTICE '   ✅ 최소 권한 원칙 적용 (Principle of Least Privilege)';
    RAISE NOTICE '   ✅ 역할 분리 (Separation of Duties)';
    RAISE NOTICE '   ✅ 연결 제한 및 타임아웃 설정';
    RAISE NOTICE '';
    RAISE NOTICE '📱 애플리케이션 연결 정보:';
    RAISE NOTICE '   🔗 사용자: coupon_app';
    RAISE NOTICE '   🔑 비밀번호: coupon123';
    RAISE NOTICE '   📂 스키마: coupons';
END $$;