-- =================================================================
-- 🎫 쿠폰 시스템 스키마 수정 & 샘플 데이터 삽입 V3
-- 1. coupon_history 테이블에 updated_at 컬럼 추가 (JPA BaseEntity 호환성)
-- 2. 쿠폰 샘플 데이터 삽입
-- =================================================================

-- 현재 사용자가 coupon_migrator인지 확인
DO $$
BEGIN
    IF current_user != 'coupon_migrator' THEN
        RAISE EXCEPTION '🚫 쿠폰 테이블은 coupon_migrator 사용자만 수정할 수 있습니다. 현재: %', current_user;
    END IF;
END $$;

-- coupons 스키마 사용 설정
SET search_path TO coupons;

-- =================================================================
-- 📋 Part 1: coupon_history 테이블에 updated_at 컬럼 추가
-- =================================================================

-- coupon_history 테이블 스키마를 JPA Entity와 맞추기
DO $$
BEGIN
    -- updated_at 컬럼 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupon_history' AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE coupon_history ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
        UPDATE coupon_history SET updated_at = created_at WHERE updated_at IS NULL;
        RAISE NOTICE '✅ updated_at 컬럼 추가 완료';
    END IF;

    -- order_amount 컬럼 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupon_history' AND column_name = 'order_amount'
    ) THEN
        ALTER TABLE coupon_history ADD COLUMN order_amount DECIMAL(10,2);
        RAISE NOTICE '✅ order_amount 컬럼 추가 완료';
    END IF;

    -- reason 컬럼 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupon_history' AND column_name = 'reason'
    ) THEN
        ALTER TABLE coupon_history ADD COLUMN reason VARCHAR(500);
        RAISE NOTICE '✅ reason 컬럼 추가 완료';
    END IF;

    -- cancel_reason 컬럼 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupon_history' AND column_name = 'cancel_reason'
    ) THEN
        ALTER TABLE coupon_history ADD COLUMN cancel_reason VARCHAR(500);
        RAISE NOTICE '✅ cancel_reason 컬럼 추가 완료';
    END IF;

    -- cancel_amount 컬럼 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupon_history' AND column_name = 'cancel_amount'
    ) THEN
        ALTER TABLE coupon_history ADD COLUMN cancel_amount DECIMAL(10,2);
        RAISE NOTICE '✅ cancel_amount 컬럼 추가 완료';
    END IF;

    -- context 컬럼 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupon_history' AND column_name = 'context'
    ) THEN
        ALTER TABLE coupon_history ADD COLUMN context JSON;
        RAISE NOTICE '✅ context 컬럼 추가 완료';
    END IF;

    -- user_coupon_id 컬럼을 nullable로 변경 (JPA Entity에서 nullable = false이지만 통합 스크립트와 맞추기 위해)
    ALTER TABLE coupon_history ALTER COLUMN user_coupon_id DROP NOT NULL;

    -- 누락된 트리거 함수 생성
    IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'update_updated_at_column') THEN
        CREATE OR REPLACE FUNCTION update_updated_at_column()
        RETURNS TRIGGER AS $$
        BEGIN
            NEW.updated_at = NOW();
            RETURN NEW;
        END;
        $$ LANGUAGE 'plpgsql';
        RAISE NOTICE '✅ update_updated_at_column 함수 생성 완료';
    END IF;

    -- updated_at 자동 갱신 트리거 추가 (존재하지 않을 때만)
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_coupon_history_updated_at') THEN
        CREATE TRIGGER update_coupon_history_updated_at
            BEFORE UPDATE ON coupon_history
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
        RAISE NOTICE '✅ updated_at 자동 갱신 트리거 생성 완료';
    END IF;
END $$;

-- =================================================================
-- 📊 Part 2: 샘플 쿠폰 데이터 삽입
-- =================================================================

-- 샘플 쿠폰 템플릿 데이터
INSERT INTO coupons (
    name, description, discount_type, discount_value, min_order_amount,
    total_quantity, per_user_limit, valid_from, valid_until, target_type, created_by
) VALUES
-- 신규 가입 환영 쿠폰
('신규 가입 환영 쿠폰', '새로 가입한 회원을 위한 5,000원 할인 쿠폰', 'AMOUNT', 5000.00, 10000.00, NULL, 1, '2024-01-01 00:00:00', '2024-12-31 23:59:59', 'NEW_USERS', '1'),

-- 강남 팝업 오픈 기념
('강남 팝업 오픈 기념', '강남 팝업스토어 오픈 기념 3,000원 할인', 'AMOUNT', 3000.00, 0.00, 1000, 1, '2024-02-01 00:00:00', '2024-02-29 23:59:59', 'ALL_USERS', '1'),

-- 홍대 팝업 런칭 이벤트
('홍대 팝업 런칭 이벤트', '홍대 팝업스토어 런칭 이벤트 10% 할인', 'PERCENTAGE', 10.00, 5000.00, 500, 2, '2024-03-01 00:00:00', '2024-03-31 23:59:59', 'ALL_USERS', '1'),

-- 온라인 굿즈 할인
('온라인 굿즈 할인', '온라인몰 전용 2,000원 할인', 'AMOUNT', 2000.00, 5000.00, NULL, 3, '2024-01-01 00:00:00', '2024-12-31 23:59:59', 'ALL_USERS', '1'),

-- VIP 회원 전용 쿠폰
('VIP 회원 전용', 'VIP 회원을 위한 15% 할인 쿠폰 (최대 20,000원)', 'PERCENTAGE', 15.00, 10000.00, 100, 1, '2024-01-01 00:00:00', '2024-12-31 23:59:59', 'VIP_USERS', '1'),

-- 여름 시즌 특별 할인
('여름 시즌 특별 할인', '여름 시즌 한정 7,000원 할인 쿠폰', 'AMOUNT', 7000.00, 15000.00, 2000, 1, '2024-06-01 00:00:00', '2024-08-31 23:59:59', 'ALL_USERS', '1')

ON CONFLICT DO NOTHING;

-- =================================================================
-- 📈 최종 확인 및 결과 출력
-- =================================================================

DO $$
DECLARE
    column_exists BOOLEAN;
    table_columns TEXT;
    coupon_count INTEGER;
BEGIN
    -- updated_at 컬럼 존재 여부 확인
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'coupons'
        AND table_name = 'coupon_history'
        AND column_name = 'updated_at'
    ) INTO column_exists;

    -- 테이블의 모든 컬럼 조회
    SELECT string_agg(column_name, ', ' ORDER BY ordinal_position)
    INTO table_columns
    FROM information_schema.columns
    WHERE table_schema = 'coupons' AND table_name = 'coupon_history';

    -- 생성된 쿠폰 개수 확인
    SELECT COUNT(*) INTO coupon_count FROM coupons;

    RAISE NOTICE '🎉 쿠폰 시스템 V3 마이그레이션 완료!';
    RAISE NOTICE '';
    RAISE NOTICE '📋 스키마 수정:';
    RAISE NOTICE '   ✅ updated_at 컬럼 존재: %', column_exists;
    RAISE NOTICE '   📊 coupon_history 컬럼들: %', table_columns;
    RAISE NOTICE '   🔄 updated_at 자동 갱신 트리거 생성 완료';
    RAISE NOTICE '';
    RAISE NOTICE '📊 샘플 데이터:';
    RAISE NOTICE '   🎫 생성된 쿠폰 개수: %개', coupon_count;
    RAISE NOTICE '';
END $$;

-- search_path 원복
RESET search_path;