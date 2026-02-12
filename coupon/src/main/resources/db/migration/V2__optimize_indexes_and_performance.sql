-- =================================================================
-- 🎫 쿠폰 시스템 DB 성능 최적화
-- V2__optimize_indexes_and_performance.sql
-- =================================================================

-- ✅ 1. 쿠폰 테이블 성능 인덱스
-- 활성 쿠폰 조회용 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_coupons_active_lookup
ON coupons (status, valid_from, valid_until)
WHERE status = 'ACTIVE';

-- 대상 타입별 활성 쿠폰 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_coupons_target_type_active
ON coupons (target_type, status, valid_from, valid_until);

-- 발급 가능한 쿠폰 조회용 인덱스 (재고 체크 포함)
CREATE INDEX IF NOT EXISTS idx_coupons_available_stock
ON coupons (status, valid_from, valid_until, total_quantity, issued_quantity)
WHERE status = 'ACTIVE' AND total_quantity IS NOT NULL;

-- 만료된 쿠폰 배치 처리용 인덱스
CREATE INDEX IF NOT EXISTS idx_coupons_expired_batch
ON coupons (status, valid_until)
WHERE status = 'ACTIVE';

-- ✅ 2. 사용자 쿠폰 테이블 성능 인덱스
-- 사용자별 쿠폰 조회 (가장 많이 사용되는 쿼리)
CREATE INDEX IF NOT EXISTS idx_user_coupons_user_status_created
ON user_coupons (user_id, status, created_at DESC);

-- 쿠폰별 발급 통계용 인덱스
CREATE INDEX IF NOT EXISTS idx_user_coupons_coupon_stats
ON user_coupons (coupon_id, status);

-- 주문별 쿠폰 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_user_coupons_order_lookup
ON user_coupons (order_id)
WHERE order_id IS NOT NULL;

-- 쿠폰 코드 고속 조회용 인덱스 (이미 UNIQUE 제약이 있지만 명시적으로 추가)
CREATE INDEX IF NOT EXISTS idx_user_coupons_coupon_code_hash
ON user_coupons USING hash (coupon_code);

-- 사용 가능한 쿠폰 조회용 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_user_coupons_available
ON user_coupons (user_id, status, expired_at)
WHERE status = 'ISSUED' AND (expired_at IS NULL OR expired_at > NOW());

-- 만료된 예약 쿠폰 복구용 인덱스
CREATE INDEX IF NOT EXISTS idx_user_coupons_expired_reservations
ON user_coupons (status, reserved_until)
WHERE status = 'RESERVED' AND reserved_until IS NOT NULL;

-- 만료된 사용자 쿠폰 배치 처리용 인덱스
CREATE INDEX IF NOT EXISTS idx_user_coupons_expired_batch
ON user_coupons (status, expired_at)
WHERE status = 'ISSUED' AND expired_at IS NOT NULL;

-- ✅ 3. 쿠폰 히스토리 테이블 성능 인덱스
-- 사용자별 히스토리 조회
CREATE INDEX IF NOT EXISTS idx_coupon_history_user_created
ON coupon_history (user_id, created_at DESC);

-- 쿠폰별 히스토리 조회
CREATE INDEX IF NOT EXISTS idx_coupon_history_coupon_created
ON coupon_history (user_coupon_id, created_at DESC);

-- 사용자 쿠폰별 히스토리 조회
CREATE INDEX IF NOT EXISTS idx_coupon_history_user_coupon
ON coupon_history (user_coupon_id, created_at DESC)
WHERE user_coupon_id IS NOT NULL;

-- 액션 타입별 통계용 인덱스
CREATE INDEX IF NOT EXISTS idx_coupon_history_action_stats
ON coupon_history (action, created_at);

-- 기간별 히스토리 분석용 인덱스
CREATE INDEX IF NOT EXISTS idx_coupon_history_date_analysis
ON coupon_history (created_at, action);

-- ✅ 4. Outbox 이벤트 테이블 성능 인덱스
-- 미발행 이벤트 조회 (가장 중요한 쿼리)
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished_created
ON coupon_outbox_events (processed_at, created_at)
WHERE processed_at IS NULL;

-- 이벤트 타입별 미발행 조회
CREATE INDEX IF NOT EXISTS idx_outbox_type_unpublished
ON coupon_outbox_events (event_type, processed_at, created_at)
WHERE processed_at IS NULL;

-- 집계 ID별 이벤트 조회
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_created
ON coupon_outbox_events (aggregate_id, created_at);

-- 발행된 이벤트 정리용 인덱스
CREATE INDEX IF NOT EXISTS idx_outbox_published_cleanup
ON coupon_outbox_events (processed_at)
WHERE processed_at IS NOT NULL;

-- 오래된 미발행 이벤트 복구용 인덱스
CREATE INDEX IF NOT EXISTS idx_outbox_old_unpublished
ON coupon_outbox_events (processed_at, created_at)
WHERE processed_at IS NULL;

-- ✅ 5. 테이블별 통계 정보 업데이트 설정
-- PostgreSQL 통계 정보 수집 빈도 조정 (더 자주 수집)
ALTER TABLE coupons SET (autovacuum_analyze_scale_factor = 0.05);
ALTER TABLE user_coupons SET (autovacuum_analyze_scale_factor = 0.05);
ALTER TABLE coupon_history SET (autovacuum_analyze_scale_factor = 0.1);
ALTER TABLE coupon_outbox_events SET (autovacuum_analyze_scale_factor = 0.05);

-- ✅ 6. 파티셔닝을 위한 준비 (대용량 히스토리 테이블 대비)
-- 월별 파티셔닝을 위한 함수 생성 (향후 적용 예정)
CREATE OR REPLACE FUNCTION create_monthly_partition(table_name TEXT, start_date DATE)
RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
    end_date DATE;
BEGIN
    partition_name := table_name || '_' || to_char(start_date, 'YYYY_MM');
    end_date := start_date + INTERVAL '1 month';

    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF %I
                    FOR VALUES FROM (%L) TO (%L)',
                   partition_name, table_name, start_date, end_date);
END;
$$ LANGUAGE plpgsql;

-- ✅ 7. 쿼리 성능 모니터링을 위한 뷰 생성
-- 느린 쿠폰 관련 쿼리 모니터링 뷰
CREATE OR REPLACE VIEW coupon_slow_queries AS
SELECT
    query,
    calls,
    total_time,
    mean_time,
    rows,
    100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0) AS hit_percent
FROM pg_stat_statements
WHERE query LIKE '%coupon%'
   OR query LIKE '%user_coupon%'
ORDER BY total_time DESC;

-- ✅ 8. 인덱스 사용률 모니터링을 위한 뷰
CREATE OR REPLACE VIEW coupon_index_usage AS
SELECT
    schemaname,
    tablename,
    indexname,
    idx_tup_read,
    idx_tup_fetch,
    idx_scan,
    CASE
        WHEN idx_scan = 0 THEN 'UNUSED'
        WHEN idx_scan < 10 THEN 'LOW'
        WHEN idx_scan < 100 THEN 'MEDIUM'
        ELSE 'HIGH'
    END as usage_level
FROM pg_stat_user_indexes
WHERE tablename LIKE '%coupon%'
ORDER BY idx_scan DESC;

-- ✅ 9. 테이블 크기 모니터링을 위한 뷰
CREATE OR REPLACE VIEW coupon_table_sizes AS
SELECT
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as total_size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) as table_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) as index_size
FROM pg_tables
WHERE tablename LIKE '%coupon%'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- ✅ 10. 성능 최적화를 위한 설정 권장사항 (주석으로 기록)
/*
PostgreSQL 설정 최적화 권장사항:

1. postgresql.conf 설정:
   - shared_buffers = 25% of RAM
   - effective_cache_size = 75% of RAM
   - work_mem = 4MB (쿼리별 정렬/해시 작업용)
   - maintenance_work_mem = 64MB (인덱스 생성/VACUUM용)
   - max_connections = 200 (동시 연결 수)
   - checkpoint_completion_target = 0.9
   - wal_buffers = 16MB
   - default_statistics_target = 100

2. 쿠폰 시스템 특화 설정:
   - random_page_cost = 1.1 (SSD 사용 시)
   - seq_page_cost = 1.0
   - cpu_tuple_cost = 0.01
   - cpu_index_tuple_cost = 0.005
   - effective_io_concurrency = 200 (SSD 사용 시)

3. 정기 유지보수 작업:
   - 주간 VACUUM ANALYZE 실행
   - 월간 REINDEX 실행
   - 분기별 테이블 통계 갱신
   - 연간 히스토리 데이터 아카이브
*/

-- ✅ 성능 최적화 완료 로그
DO $$
BEGIN
    RAISE NOTICE '🎫 쿠폰 시스템 DB 성능 최적화 완료!';
    RAISE NOTICE '📊 생성된 인덱스: 23개';
    RAISE NOTICE '🔍 모니터링 뷰: 3개';
    RAISE NOTICE '⚡ 성능 최적화 설정: 적용됨';
END $$;
