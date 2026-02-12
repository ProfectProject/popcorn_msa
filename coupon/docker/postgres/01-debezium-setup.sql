-- Debezium CDC 설정 스크립트 for 쿠폰 서비스

-- 1. WAL 레벨 확인 (이미 postgresql.conf에서 설정되어 있어야 함)
-- wal_level = logical
-- max_replication_slots >= 4
-- max_wal_senders >= 4

-- 2. 쿠폰 Outbox 테이블 생성 (이미 존재한다면 스킵)
CREATE TABLE IF NOT EXISTS public.coupon_outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 인덱스 생성 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_coupon_outbox_events_processed_at
ON public.coupon_outbox_events(processed_at);

CREATE INDEX IF NOT EXISTS idx_coupon_outbox_events_created_at
ON public.coupon_outbox_events(created_at);

CREATE INDEX IF NOT EXISTS idx_coupon_outbox_events_event_type
ON public.coupon_outbox_events(event_type);

CREATE INDEX IF NOT EXISTS idx_coupon_outbox_events_aggregate
ON public.coupon_outbox_events(aggregate_type, aggregate_id);

-- 4. Debezium 사용자 권한 설정
-- 기존 postgres 사용자에게 replication 권한 부여
ALTER USER postgres WITH REPLICATION;

-- 5. Publication 생성 (Debezium이 자동으로 할 수도 있지만 명시적으로 생성)
DO $$
BEGIN
    -- Publication이 이미 존재하면 삭제 후 재생성
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'coupon_outbox_pub') THEN
        DROP PUBLICATION coupon_outbox_pub;
    END IF;

    -- Publication 생성
    CREATE PUBLICATION coupon_outbox_pub FOR TABLE public.coupon_outbox_events;

    RAISE NOTICE 'Publication coupon_outbox_pub created successfully';
EXCEPTION
    WHEN others THEN
        RAISE NOTICE 'Error creating publication: %', SQLERRM;
END $$;

-- 6. Replication Slot 정리 (기존 슬롯이 있다면 삭제)
DO $$
BEGIN
    -- 기존 슬롯 삭제 (존재한다면)
    IF EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'coupon_outbox_slot') THEN
        PERFORM pg_drop_replication_slot('coupon_outbox_slot');
        RAISE NOTICE 'Existing replication slot coupon_outbox_slot dropped';
    END IF;
EXCEPTION
    WHEN others THEN
        RAISE NOTICE 'Error managing replication slot: %', SQLERRM;
END $$;

-- 7. 현재 상태 확인
SELECT
    'WAL Level' as setting,
    setting as value
FROM pg_settings
WHERE name = 'wal_level'

UNION ALL

SELECT
    'Max Replication Slots' as setting,
    setting as value
FROM pg_settings
WHERE name = 'max_replication_slots'

UNION ALL

SELECT
    'Max WAL Senders' as setting,
    setting as value
FROM pg_settings
WHERE name = 'max_wal_senders';

-- 8. Publications 확인
SELECT 'Publications' as type, pubname as name FROM pg_publication;

-- 9. 테이블 권한 확인
SELECT
    'Table Permissions' as type,
    schemaname,
    tablename,
    tableowner
FROM pg_tables
WHERE tablename = 'coupon_outbox_events';

-- 10. 샘플 이벤트 데이터 (테스트용)
INSERT INTO public.coupon_outbox_events (
    aggregate_type,
    aggregate_id,
    event_type,
    event_data
) VALUES (
    'COUPON',
    'test-coupon-1',
    'COUPON_CREATED',
    '{"couponId": "test-coupon-1", "name": "Test Coupon", "status": "ACTIVE", "timestamp": "2026-02-10T12:00:00"}'::jsonb
) ON CONFLICT DO NOTHING;

-- 완료 메시지
DO $$
BEGIN
    RAISE NOTICE '=================================';
    RAISE NOTICE 'Debezium CDC 설정이 완료되었습니다!';
    RAISE NOTICE '=================================';
    RAISE NOTICE '1. WAL Level: logical';
    RAISE NOTICE '2. Publication: coupon_outbox_pub';
    RAISE NOTICE '3. Table: coupon_outbox_events';
    RAISE NOTICE '4. User: postgres (replication 권한)';
    RAISE NOTICE '=================================';
END $$;