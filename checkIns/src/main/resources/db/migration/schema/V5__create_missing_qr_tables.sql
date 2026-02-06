-- 누락된 QR 테이블들 생성
-- V5 마이그레이션

-- QR 코드 테이블 생성
CREATE TABLE IF NOT EXISTS checkIns.qr_order_qr_codes (
    qr_id       UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    qr_code     VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  BIGINT,
    store_id    UUID,
    popup_id    UUID,
    order_goods_id UUID
);

-- 체크인 테이블 생성
CREATE TABLE IF NOT EXISTS checkIns.qr_checkins (
    checkin_id       UUID PRIMARY KEY,
    order_id         UUID NOT NULL,
    order_qr_code_id UUID NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    created_by       BIGINT,
    store_id         UUID,
    popup_id         UUID,
    order_goods_id   UUID
);

-- Foreign Key 제약조건
DO $$ BEGIN
    ALTER TABLE checkIns.qr_checkins
        ADD CONSTRAINT fk_checkins_qr
        FOREIGN KEY (order_qr_code_id) REFERENCES checkIns.qr_order_qr_codes(qr_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 기본 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_qr_codes_order_id ON checkIns.qr_order_qr_codes(order_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_expires_at ON checkIns.qr_order_qr_codes(expires_at);
CREATE INDEX IF NOT EXISTS idx_checkins_order_id ON checkIns.qr_checkins(order_id);
CREATE INDEX IF NOT EXISTS idx_checkins_created_at ON checkIns.qr_checkins(created_at);

-- 추가 컬럼 인덱스
CREATE INDEX IF NOT EXISTS idx_qr_codes_store_id ON checkIns.qr_order_qr_codes(store_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_popup_id ON checkIns.qr_order_qr_codes(popup_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_order_goods_id ON checkIns.qr_order_qr_codes(order_goods_id);

CREATE INDEX IF NOT EXISTS idx_checkins_store_id ON checkIns.qr_checkins(store_id);
CREATE INDEX IF NOT EXISTS idx_checkins_popup_id ON checkIns.qr_checkins(popup_id);
CREATE INDEX IF NOT EXISTS idx_checkins_order_goods_id ON checkIns.qr_checkins(order_goods_id);