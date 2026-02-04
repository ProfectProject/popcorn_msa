-- QR 관련 테이블 스키마
-- checkIns 모듈 전용 QR 도메인

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qr_migrator') THEN
        CREATE ROLE qr_migrator LOGIN PASSWORD '${QR_MIGRATOR_PASSWORD}';
    END IF;
END $$;

CREATE SCHEMA IF NOT EXISTS qr AUTHORIZATION qr_migrator;
GRANT ALL PRIVILEGES ON SCHEMA qr TO qr_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA qr TO qr_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA qr TO qr_migrator;

-- QR 코드 테이블
CREATE TABLE IF NOT EXISTS qr.qr_order_qr_codes (
    qr_id       UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    qr_code     VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  BIGINT
);

-- 체크인 테이블
CREATE TABLE IF NOT EXISTS qr.qr_checkins (
    checkin_id       UUID PRIMARY KEY,
    order_id         UUID NOT NULL,
    order_qr_code_id UUID NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    created_by       BIGINT
);

-- Foreign Key 제약조건
DO $$ BEGIN
    ALTER TABLE qr.qr_checkins
        ADD CONSTRAINT fk_checkins_qr
        FOREIGN KEY (order_qr_code_id) REFERENCES qr.qr_order_qr_codes(qr_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_qr_codes_order_id ON qr.qr_order_qr_codes(order_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_expires_at ON qr.qr_order_qr_codes(expires_at);
CREATE INDEX IF NOT EXISTS idx_checkins_order_id ON qr.qr_checkins(order_id);
CREATE INDEX IF NOT EXISTS idx_checkins_created_at ON qr.qr_checkins(created_at);
