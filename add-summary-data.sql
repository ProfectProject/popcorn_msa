-- 🔧 테스트 데이터에 대한 Summary 레코드 생성
-- 주문 현황 404 오류 해결용

INSERT INTO order_query.popup_order_summary (
    store_id, popup_id, owner_id, popup_name, popup_description, popup_status,
    operation_start_at, operation_end_at, opening_at, closing_at,
    total_orders, total_revenue, last_order_at, created_at, updated_at
)
SELECT DISTINCT
    v.store_id,
    v.popup_id,
    16 as owner_id, -- popcorn5@popcorn.com 사용자 ID
    '팝업스토어 ' || SUBSTRING(v.store_id::text, 1, 8) as popup_name,
    '테스트 팝업 스토어입니다' as popup_description,
    'OPEN' as popup_status,
    NOW() - interval '30 days' as operation_start_at,
    NOW() + interval '30 days' as operation_end_at,
    NOW() - interval '30 days' as opening_at,
    NOW() + interval '30 days' as closing_at,
    COUNT(*) OVER (PARTITION BY v.store_id, v.popup_id) as total_orders,
    SUM(v.line_price) OVER (PARTITION BY v.store_id, v.popup_id) as total_revenue,
    MAX(v.ordered_at) OVER (PARTITION BY v.store_id, v.popup_id) as last_order_at,
    NOW() - interval '30 days' as created_at,
    NOW() as updated_at
FROM order_query.popup_order_items_view v
WHERE v.order_no LIKE 'TEST_%'
ON CONFLICT (store_id, popup_id) DO UPDATE SET
    total_orders = EXCLUDED.total_orders,
    total_revenue = EXCLUDED.total_revenue,
    last_order_at = EXCLUDED.last_order_at,
    updated_at = NOW();

-- 생성 결과 확인
SELECT
    '📊 Summary 데이터 생성 결과' as title,
    COUNT(*) as summary_count,
    SUM(total_orders) as total_test_orders,
    SUM(total_revenue) as total_test_revenue
FROM order_query.popup_order_summary s
WHERE EXISTS (
    SELECT 1 FROM order_query.popup_order_items_view v
    WHERE v.store_id = s.store_id AND v.popup_id = s.popup_id
    AND v.order_no LIKE 'TEST_%'
);

-- 스토어별 summary 확인
SELECT
    '🏪 스토어별 Summary' as title,
    store_id,
    popup_id,
    popup_name,
    total_orders,
    total_revenue
FROM order_query.popup_order_summary s
WHERE EXISTS (
    SELECT 1 FROM order_query.popup_order_items_view v
    WHERE v.store_id = s.store_id AND v.popup_id = s.popup_id
    AND v.order_no LIKE 'TEST_%'
)
ORDER BY store_id;