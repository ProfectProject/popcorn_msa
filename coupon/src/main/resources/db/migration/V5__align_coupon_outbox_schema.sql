-- =================================================================
-- 🎫 쿠폰 Outbox 스키마 정합성 보정 V5
-- - payload/event_data 공존 환경 정리
-- - legacy CHECK 제약으로 인한 이벤트 적재 실패 방지
-- =================================================================

SET search_path TO coupons;

DO $$
BEGIN
    -- payload 컬럼 보강
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupon_outbox_events' AND column_name = 'payload'
    ) THEN
        ALTER TABLE coupon_outbox_events ADD COLUMN payload JSON;
    END IF;

    -- event_data 데이터가 있고 payload가 비어있으면 이관
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'coupons' AND table_name = 'coupon_outbox_events' AND column_name = 'event_data'
    ) THEN
        UPDATE coupon_outbox_events
        SET payload = event_data
        WHERE payload IS NULL
          AND event_data IS NOT NULL;
    END IF;

    -- payload 기본값/NOT NULL 보장
    UPDATE coupon_outbox_events
    SET payload = '{}'::json
    WHERE payload IS NULL;

    ALTER TABLE coupon_outbox_events
        ALTER COLUMN payload SET NOT NULL;

    -- 애플리케이션 이벤트 타입 확장을 위해 legacy CHECK 제약 제거
    ALTER TABLE coupon_outbox_events DROP CONSTRAINT IF EXISTS valid_event_type;
    ALTER TABLE coupon_outbox_events DROP CONSTRAINT IF EXISTS valid_aggregate_type;
END $$;

RESET search_path;
