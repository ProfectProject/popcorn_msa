-- QR 테이블에 store_id, popup_id, order_goods_id 컬럼 추가
-- V3 마이그레이션

-- qr_order_qr_codes / qr_checkins가 없는 환경에서도 마이그레이션이 죽지 않도록 방어
DO $$
BEGIN
    IF to_regclass('checkins.qr_order_qr_codes') IS NOT NULL THEN
        ALTER TABLE checkins.qr_order_qr_codes
            ADD COLUMN IF NOT EXISTS store_id uuid,
            ADD COLUMN IF NOT EXISTS popup_id uuid,
            ADD COLUMN IF NOT EXISTS order_goods_id uuid;

        CREATE INDEX IF NOT EXISTS idx_qr_codes_store_id ON checkins.qr_order_qr_codes(store_id);
        CREATE INDEX IF NOT EXISTS idx_qr_codes_popup_id ON checkins.qr_order_qr_codes(popup_id);
        CREATE INDEX IF NOT EXISTS idx_qr_codes_order_goods_id ON checkins.qr_order_qr_codes(order_goods_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('checkins.qr_checkins') IS NOT NULL THEN
        ALTER TABLE checkins.qr_checkins
            ADD COLUMN IF NOT EXISTS store_id uuid,
            ADD COLUMN IF NOT EXISTS popup_id uuid,
            ADD COLUMN IF NOT EXISTS order_goods_id uuid;

        CREATE INDEX IF NOT EXISTS idx_checkins_store_id ON checkins.qr_checkins(store_id);
        CREATE INDEX IF NOT EXISTS idx_checkins_popup_id ON checkins.qr_checkins(popup_id);
        CREATE INDEX IF NOT EXISTS idx_checkins_order_goods_id ON checkins.qr_checkins(order_goods_id);
    END IF;
END $$;
