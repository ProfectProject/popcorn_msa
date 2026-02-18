-- ===============================================
-- 📈 OrderQuery 서비스 성능 최적화 인덱스 추가
-- 작성일: 2026-02-18
-- 목적: 자주 사용되는 쿼리 패턴에 대한 인덱스 최적화
-- ===============================================

-- 🔍 기본 조회용 복합 인덱스
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_store_popup_status
ON popup_order_items_view (store_id, popup_id, order_status);

-- 📅 날짜 범위 쿼리 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_ordered_at_btree
ON popup_order_items_view (ordered_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_created_at_btree
ON popup_order_items_view (created_at DESC);

-- 🏪 스토어별 조회 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_store_ordered_at
ON popup_order_items_view (store_id, ordered_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_store_created_at
ON popup_order_items_view (store_id, created_at DESC);

-- 📊 상태별 집계 쿼리 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_order_status_ordered_at
ON popup_order_items_view (order_status, ordered_at DESC);

-- 💳 결제 상태별 조회 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_payment_status
ON popup_order_items_view (payment_status, order_status);

-- 🎯 사용자별 주문 조회 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_user_ordered_at
ON popup_order_items_view (user_id, ordered_at DESC);

-- 🛍️ 상품별 통계 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_goods_name_status
ON popup_order_items_view (goods_name, order_status)
WHERE goods_name IS NOT NULL AND order_status IN ('COMPLETED', 'PAID');

-- ⏰ 시간대별 분석용 함수 기반 인덱스
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_hour_extract
ON popup_order_items_view (extract(hour from ordered_at));

-- 📈 매출 집계 최적화 (복합 인덱스)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_revenue_analysis
ON popup_order_items_view (order_status, ordered_at, line_price)
WHERE order_status IN ('COMPLETED', 'PAID');

-- 🏪 스토어별 매출 분석 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_store_revenue_analysis
ON popup_order_items_view (store_id, order_status, ordered_at, line_price)
WHERE order_status IN ('COMPLETED', 'PAID');

-- 🚨 긴급 처리 대상 조회 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_urgent_orders
ON popup_order_items_view (order_status, ordered_at)
WHERE order_status IN ('PENDING', 'CONFIRMED', 'PAYMENT_PENDING');

-- 📋 체크인 관련 조회 최적화
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_popup_order_items_view_checkin_analysis
ON popup_order_items_view (store_id, popup_id, checked_in, checkin_at);

-- ===============================================
-- 📊 통계 정보 업데이트 (인덱스 생성 후 실행)
-- ===============================================
ANALYZE popup_order_items_view;

-- ===============================================
-- 💡 인덱스 사용 가이드 (주석)
-- ===============================================
/*
주요 쿼리 패턴별 최적화된 인덱스:

1. 스토어별 주문 조회:
   - idx_popup_order_items_view_store_ordered_at
   - idx_popup_order_items_view_store_created_at

2. 상태별 필터링:
   - idx_popup_order_items_view_order_status_ordered_at
   - idx_popup_order_items_view_payment_status

3. 날짜 범위 쿼리:
   - idx_popup_order_items_view_ordered_at_btree
   - idx_popup_order_items_view_created_at_btree

4. 매출 집계:
   - idx_popup_order_items_view_revenue_analysis
   - idx_popup_order_items_view_store_revenue_analysis

5. 인기 상품 분석:
   - idx_popup_order_items_view_goods_name_status

6. 시간대별 분포:
   - idx_popup_order_items_view_hour_extract
*/