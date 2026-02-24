-- V6__add_missing_popup_columns.sql
-- 🎯 Java Architect 진단: PopupQueryRepository 500 에러 해결
-- 누락된 event_start_at, event_end_at 컬럼 추가

DO $$
BEGIN
    -- 🚀 PopupQueryRepository에서 참조하는 누락된 컬럼들 추가
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'store'
                   AND table_name = 'popups'
                   AND column_name = 'event_start_at') THEN
        ALTER TABLE store.popups ADD COLUMN event_start_at TIMESTAMP;
        RAISE NOTICE '✅ Added event_start_at column to popups table';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'store'
                   AND table_name = 'popups'
                   AND column_name = 'event_end_at') THEN
        ALTER TABLE store.popups ADD COLUMN event_end_at TIMESTAMP;
        RAISE NOTICE '✅ Added event_end_at column to popups table';
    END IF;
END $$;

-- 📊 기존 데이터에 기본값 설정 (PopupQueryRepository 쿼리 호환성 확보)
UPDATE store.popups
SET event_start_at = COALESCE(reservation_open_at, created_at),
    event_end_at = COALESCE(reservation_open_at, created_at) + INTERVAL '30 days'
WHERE event_start_at IS NULL OR event_end_at IS NULL;

-- 🚀 성능 최적화 인덱스 추가
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popups_active_events
ON store.popups (event_end_at, status)
WHERE deleted_at IS NULL;

-- ✅ 완료 확인
-- PopupQueryRepository 쿼리: p.event_end_at > CURRENT_TIMESTAMP 조건 지원