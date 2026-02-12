-- =================================================================
-- 🎫 쿠폰 마이크로서비스 전용 테이블 생성 V1
-- 👤 coupon_migrator 사용자만 접근 가능 (coupons 스키마 내)
-- =================================================================

-- 현재 사용자가 coupon_migrator인지 확인
DO $$
BEGIN
    IF current_user != 'coupon_migrator' THEN
        RAISE EXCEPTION '🚫 쿠폰 테이블은 coupon_migrator 사용자만 생성할 수 있습니다. 현재: %', current_user;
    END IF;
END $$;

-- coupons 스키마 사용 설정
SET search_path TO coupons;

-- =================================================================
-- 📋 테이블 생성
-- =================================================================

-- 1. 쿠폰 마스터 테이블
CREATE TABLE IF NOT EXISTS coupons (
    id BIGSERIAL PRIMARY KEY,

    -- 기본 정보
    name VARCHAR(100) NOT NULL,
    description TEXT,

    -- 할인 정보
    discount_type coupons.discount_type NOT NULL,
    discount_value DECIMAL(10,2) NOT NULL CHECK (discount_value > 0),
    min_order_amount DECIMAL(10,2) DEFAULT 0 NOT NULL,
    max_discount_amount DECIMAL(10,2),

    -- 수량 관리
    total_quantity INTEGER,
    issued_quantity INTEGER DEFAULT 0 NOT NULL CHECK (issued_quantity >= 0),
    per_user_limit INTEGER DEFAULT 1 NOT NULL CHECK (per_user_limit > 0),

    -- 유효기간
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,

    -- 상태 및 타입
    status coupons.coupon_status DEFAULT 'ACTIVE' NOT NULL,
    target_type coupons.target_type DEFAULT 'ALL_USERS' NOT NULL,

    -- 추가 조건
    conditions JSON,

    -- 메타데이터
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- 제약조건
    CONSTRAINT chk_coupons_valid_period CHECK (valid_until > valid_from),
    CONSTRAINT chk_coupons_quantities CHECK (total_quantity IS NULL OR issued_quantity <= total_quantity)
);

-- 2. 사용자 발급 쿠폰 테이블
CREATE TABLE IF NOT EXISTS user_coupons (
    id BIGSERIAL PRIMARY KEY,

    -- 기본 정보
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    coupon_code VARCHAR(50) UNIQUE NOT NULL,

    -- 상태 관리
    status coupons.user_coupon_status DEFAULT 'ISSUED' NOT NULL,

    -- 시간 정보
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reserved_at TIMESTAMP,
    used_at TIMESTAMP,
    expired_at TIMESTAMP,

    -- 사용 정보
    order_id BIGINT,
    discount_applied DECIMAL(10,2),

    -- 메타데이터
    metadata JSON,

    -- 감사 정보
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- 외래 키
    CONSTRAINT fk_user_coupons_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE,

    -- 제약조건
    CONSTRAINT chk_user_coupons_used_consistency
        CHECK ((status = 'USED' AND order_id IS NOT NULL AND used_at IS NOT NULL) OR (status != 'USED')),
    CONSTRAINT chk_user_coupons_reserved_consistency
        CHECK ((status = 'RESERVED' AND reserved_at IS NOT NULL) OR (status != 'RESERVED'))
);

-- 3. 쿠폰 히스토리 테이블
CREATE TABLE IF NOT EXISTS coupon_history (
    id BIGSERIAL PRIMARY KEY,

    -- 기본 정보
    user_coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    -- 주문 정보
    order_id BIGINT,
    order_amount DECIMAL(10,2),

    -- 액션 정보
    action VARCHAR(20) NOT NULL CHECK (action IN ('ISSUED', 'RESERVED', 'USED', 'CANCELLED', 'EXPIRED', 'RESTORED')),
    discount_amount DECIMAL(10,2),
    reason TEXT,
    cancel_reason VARCHAR(100),
    cancel_amount DECIMAL(10,2),

    -- 컨텍스트 정보
    context JSON,

    -- 시간 정보
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- 외래 키
    CONSTRAINT fk_coupon_history_user_coupon FOREIGN KEY (user_coupon_id) REFERENCES user_coupons(id) ON DELETE CASCADE
);

-- 4. 쿠폰 아웃박스 이벤트 테이블
CREATE TABLE IF NOT EXISTS coupon_outbox_events (
    id BIGSERIAL PRIMARY KEY,

    -- 기본 정보
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,

    -- 이벤트 정보
    event_type VARCHAR(100) NOT NULL,
    event_data JSON NOT NULL,

    -- 처리 상태
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP
);

-- =================================================================
-- 📊 인덱스 생성
-- =================================================================

-- 쿠폰 인덱스
CREATE INDEX IF NOT EXISTS idx_coupons_status_valid ON coupons(status, valid_from, valid_until);
CREATE INDEX IF NOT EXISTS idx_coupons_created_at ON coupons(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_coupons_target_type ON coupons(target_type);
CREATE INDEX IF NOT EXISTS idx_coupons_created_by ON coupons(created_by);

-- 사용자 쿠폰 인덱스
CREATE INDEX IF NOT EXISTS idx_user_coupons_user_id ON user_coupons(user_id);
CREATE INDEX IF NOT EXISTS idx_user_coupons_coupon_id ON user_coupons(coupon_id);
CREATE INDEX IF NOT EXISTS idx_user_coupons_code ON user_coupons(coupon_code);
CREATE INDEX IF NOT EXISTS idx_user_coupons_status ON user_coupons(status);
CREATE INDEX IF NOT EXISTS idx_user_coupons_user_status ON user_coupons(user_id, status);
CREATE INDEX IF NOT EXISTS idx_user_coupons_expired_at ON user_coupons(expired_at);
CREATE INDEX IF NOT EXISTS idx_user_coupons_order_id ON user_coupons(order_id);

-- 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_user_coupons_user_status_valid ON user_coupons(user_id, status, expired_at);
CREATE INDEX IF NOT EXISTS idx_user_coupons_available ON user_coupons(user_id, coupon_id, status);

-- 쿠폰 히스토리 인덱스
CREATE INDEX IF NOT EXISTS idx_coupon_history_user_coupon_id ON coupon_history(user_coupon_id);
CREATE INDEX IF NOT EXISTS idx_coupon_history_user_id ON coupon_history(user_id);
CREATE INDEX IF NOT EXISTS idx_coupon_history_order_id ON coupon_history(order_id);
CREATE INDEX IF NOT EXISTS idx_coupon_history_action ON coupon_history(action);
CREATE INDEX IF NOT EXISTS idx_coupon_history_created_at ON coupon_history(created_at DESC);

-- 아웃박스 이벤트 인덱스
CREATE INDEX IF NOT EXISTS idx_coupon_outbox_unprocessed ON coupon_outbox_events(created_at)
    WHERE processed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_coupon_outbox_aggregate ON coupon_outbox_events(aggregate_type, aggregate_id);

-- =================================================================
-- 🔄 자동 업데이트 트리거 생성
-- =================================================================

-- 쿠폰 테이블 updated_at 자동 갱신
CREATE TRIGGER update_coupons_updated_at
    BEFORE UPDATE ON coupons
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 사용자 쿠폰 테이블 updated_at 자동 갱신
CREATE TRIGGER update_user_coupons_updated_at
    BEFORE UPDATE ON user_coupons
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =================================================================
-- 📈 권한 확인 및 최종 설정
-- =================================================================

-- 현재 사용자(coupon_migrator)의 권한 확인
DO $$
DECLARE
    schema_privileges TEXT;
    table_count INTEGER;
BEGIN
    -- 스키마 권한 확인
    SELECT array_to_string(array_agg(privilege_type), ', ')
    INTO schema_privileges
    FROM information_schema.usage_privileges
    WHERE grantee = 'coupon_migrator' AND object_name = 'coupons';

    -- 생성된 테이블 수 확인
    SELECT count(*)
    INTO table_count
    FROM information_schema.tables
    WHERE table_schema = 'coupons' AND table_type = 'BASE TABLE';

    RAISE NOTICE '🎉 쿠폰 스키마 생성 완료!';
    RAISE NOTICE '👤 현재 사용자: %', current_user;
    RAISE NOTICE '🔐 스키마 권한: %', COALESCE(schema_privileges, 'FULL ACCESS');
    RAISE NOTICE '📊 생성된 테이블: %개', table_count;
    RAISE NOTICE '🚨 보안: coupon_migrator만 쿠폰 데이터 접근 가능';
    RAISE NOTICE '🔍 다른 서비스는 SELECT만 허용 (V0에서 설정됨)';
END $$;

-- search_path 원복
RESET search_path;