-- =================================================================
-- 🎫 쿠폰 할인 컬럼 스키마 정합성 보정 V4
-- - legacy discount_value NOT NULL 제약 완화
-- - discount_amount / discount_percentage 컬럼과 데이터 정렬
-- =================================================================

SET search_path TO coupons;

DO $$
BEGIN
    -- 신규 컬럼이 누락된 환경을 대비해 보강
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupons' AND column_name = 'discount_amount'
    ) THEN
        ALTER TABLE coupons ADD COLUMN discount_amount DECIMAL(10,2);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupons' AND column_name = 'discount_percentage'
    ) THEN
        ALTER TABLE coupons ADD COLUMN discount_percentage DECIMAL(5,2);
    END IF;

    -- 기존 discount_value 데이터를 신규 컬럼으로 이관
    UPDATE coupons
    SET discount_amount = discount_value
    WHERE discount_type = 'AMOUNT'
      AND discount_amount IS NULL
      AND discount_value IS NOT NULL;

    UPDATE coupons
    SET discount_percentage = discount_value
    WHERE discount_type = 'PERCENTAGE'
      AND discount_percentage IS NULL
      AND discount_value IS NOT NULL;

    -- legacy 컬럼은 호환성 유지를 위해 남기되, 생성 시 NULL 허용
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupons' AND column_name = 'discount_value'
    ) THEN
        ALTER TABLE coupons ALTER COLUMN discount_value DROP NOT NULL;
    END IF;
END $$;

RESET search_path;
