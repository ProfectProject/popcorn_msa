-- 🔧 테스트 데이터에 대한 Summary 레코드 생성 (수정된 버전)
-- 주문 현황 404 오류 해결용

INSERT INTO order_query.popup_order_summary (
    store_id, popup_id, owner_id, popup_title, popup_status,
    reservation_open_at, reservation_total_orders, reservation_paid_orders, reservation_cancelled_orders,
    goods_total_orders, goods_paid_orders, goods_cancelled_orders, checked_in_orders,
    created_at, updated_at
)
SELECT DISTINCT
    v.store_id,
    v.popup_id,
    16 as owner_id, -- popcorn5@popcorn.com 사용자 ID
    '테스트팝업_' || SUBSTRING(v.store_id::text, 1, 8) as popup_title,
    'OPEN' as popup_status,
    NOW() - interval '30 days' as reservation_open_at,
    0 as reservation_total_orders,
    0 as reservation_paid_orders,
    0 as reservation_cancelled_orders,
    COUNT(*) OVER (PARTITION BY v.store_id, v.popup_id) as goods_total_orders,
    COUNT(*) FILTER (WHERE v.order_status IN ('PAID', 'COMPLETED')) OVER (PARTITION BY v.store_id, v.popup_id) as goods_paid_orders,
    COUNT(*) FILTER (WHERE v.order_status = 'CANCELLED') OVER (PARTITION BY v.store_id, v.popup_id) as goods_cancelled_orders,
    COUNT(*) FILTER (WHERE v.checked_in = true) OVER (PARTITION BY v.store_id, v.popup_id) as checked_in_orders,
    NOW() - interval '30 days' as created_at,
    NOW() as updated_at
FROM order_query.popup_order_items_view v
WHERE v.order_no LIKE 'TEST_%'
ON CONFLICT (popup_id) DO UPDATE SET
    goods_total_orders = EXCLUDED.goods_total_orders,
    goods_paid_orders = EXCLUDED.goods_paid_orders,
    goods_cancelled_orders = EXCLUDED.goods_cancelled_orders,
    checked_in_orders = EXCLUDED.checked_in_orders,
    updated_at = NOW();

-- 생성 결과 확인
SELECT
    '📊 Summary 데이터 생성 결과' as title,
    COUNT(*) as summary_count,
    SUM(goods_total_orders) as total_goods_orders,
    SUM(goods_paid_orders) as total_paid_orders
FROM order_query.popup_order_summary s
WHERE EXISTS (
    SELECT 1 FROM order_query.popup_order_items_view v
    WHERE v.store_id = s.store_id AND v.popup_id = s.popup_id
    AND v.order_no LIKE 'TEST_%'
);

-- 스토어별 summary 확인
SELECT
    '🏪 스토어별 Summary' as title,
    SUBSTRING(store_id::text, 1, 8) as store_id_short,
    SUBSTRING(popup_id::text, 1, 8) as popup_id_short,
    popup_title,
    goods_total_orders,
    goods_paid_orders
FROM order_query.popup_order_summary s
WHERE EXISTS (
    SELECT 1 FROM order_query.popup_order_items_view v
    WHERE v.store_id = s.store_id AND v.popup_id = s.popup_id
    AND v.order_no LIKE 'TEST_%'
)
ORDER BY store_id;

-- 매칭 확인
SELECT
    '✅ 데이터 매칭 확인' as check_result,
    v.store_id,
    v.popup_id,
    CASE WHEN s.popup_id IS NOT NULL THEN 'HAS_SUMMARY' ELSE 'NO_SUMMARY' END as status
FROM (
    SELECT DISTINCT store_id, popup_id
    FROM order_query.popup_order_items_view
    WHERE order_no LIKE 'TEST_%'
    LIMIT 3
) v
LEFT JOIN order_query.popup_order_summary s ON v.popup_id = s.popup_id;