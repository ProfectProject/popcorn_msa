-- QR 테이블에 store_id, popup_id, order_goods_id 컬럼 추가
-- V3 마이그레이션

-- qr_order_qr_codes 테이블에 컬럼 추가
ALTER TABLE checkIns.qr_order_qr_codes
    ADD COLUMN IF NOT EXISTS store_id uuid,
    ADD COLUMN IF NOT EXISTS popup_id uuid,
    ADD COLUMN IF NOT EXISTS order_goods_id uuid;

-- qr_checkins 테이블에 컬럼 추가
ALTER TABLE checkIns.qr_checkins
    ADD COLUMN IF NOT EXISTS store_id uuid,
    ADD COLUMN IF NOT EXISTS popup_id uuid,
    ADD COLUMN IF NOT EXISTS order_goods_id uuid;

-- 새로 추가된 컬럼들에 대한 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_qr_codes_store_id ON checkIns.qr_order_qr_codes(store_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_popup_id ON checkIns.qr_order_qr_codes(popup_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_order_goods_id ON checkIns.qr_order_qr_codes(order_goods_id);

CREATE INDEX IF NOT EXISTS idx_checkins_store_id ON checkIns.qr_checkins(store_id);
CREATE INDEX IF NOT EXISTS idx_checkins_popup_id ON checkIns.qr_checkins(popup_id);
CREATE INDEX IF NOT EXISTS idx_checkins_order_goods_id ON checkIns.qr_checkins(order_goods_id);