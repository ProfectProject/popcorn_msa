-- QR 관련 테이블 스키마
-- checkIns 모듈 전용 QR 도메인

-- qr_migrator 사용자는 이미 /db/init/00_roles.sql에서 생성됨

CREATE SCHEMA IF NOT EXISTS checkIns AUTHORIZATION qr_migrator;
GRANT ALL PRIVILEGES ON SCHEMA checkIns TO qr_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA checkIns TO qr_migrator;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA checkIns TO qr_migrator;

-- QR 코드 테이블
CREATE TABLE IF NOT EXISTS checkIns.qr_order_qr_codes (
    qr_id       UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    qr_code     VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  BIGINT
);

-- 체크인 테이블
CREATE TABLE IF NOT EXISTS checkIns.qr_checkins (
    checkin_id       UUID PRIMARY KEY,
    order_id         UUID NOT NULL,
    order_qr_code_id UUID NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    created_by       BIGINT
);

-- Foreign Key 제약조건
DO $$ BEGIN
    ALTER TABLE checkIns.qr_checkins
        ADD CONSTRAINT fk_checkins_qr
        FOREIGN KEY (order_qr_code_id) REFERENCES checkIns.qr_order_qr_codes(qr_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_qr_codes_order_id ON checkIns.qr_order_qr_codes(order_id);
CREATE INDEX IF NOT EXISTS idx_qr_codes_expires_at ON checkIns.qr_order_qr_codes(expires_at);
CREATE INDEX IF NOT EXISTS idx_checkins_order_id ON checkIns.qr_checkins(order_id);
CREATE INDEX IF NOT EXISTS idx_checkins_created_at ON checkIns.qr_checkins(created_at);
