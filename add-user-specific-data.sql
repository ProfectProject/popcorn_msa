-- 🎯 사용자가 사용하는 storeId/popupId 조합에 대한 데이터 생성

-- 1️⃣ 주문 아이템 데이터 생성 (100개)
DO $$
DECLARE
    target_store_id uuid := '72bc0453-0404-4de1-a54d-34d78ad16e55';
    target_popup_id uuid := '7ac19fc7-36aa-47b7-a283-f42d21e5a47d';
    goods_data TEXT[][] := ARRAY[
        ['a1111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '아메리카노', '4500'],
        ['b2222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '카페라떼', '5000'],
        ['c3333333-cccc-cccc-cccc-cccccccccccc', '카푸치노', '5500'],
        ['d4444444-dddd-dddd-dddd-dddddddddddd', '에스프레소', '3500'],
        ['e5555555-eeee-eeee-eeee-eeeeeeeeeeee', '모카', '6000']
    ];

    i INTEGER;
    goods_id uuid;
    goods_name TEXT;
    unit_price INTEGER;
    qty INTEGER;
    order_time TIMESTAMP;
    order_status TEXT;
    payment_status TEXT;
    checked_in BOOLEAN;
BEGIN
    RAISE NOTICE '📍 사용자 지정 storeId/popupId 조합 데이터 생성 중...';

    FOR i IN 1..100 LOOP
        -- 랜덤 상품 선택
        goods_id := goods_data[1 + (random() * 4)::int][1]::uuid;
        goods_name := goods_data[1 + (random() * 4)::int][2];
        unit_price := goods_data[1 + (random() * 4)::int][3]::int;

        qty := 1 + (random() * 2)::int;
        order_time := NOW() - (random() * interval '7 days');

        -- 주문 상태 분포
        IF random() < 0.7 THEN
            order_status := 'COMPLETED';
            payment_status := 'PAID';
            checked_in := random() < 0.8;
        ELSIF random() < 0.85 THEN
            order_status := 'PAID';
            payment_status := 'PAID';
            checked_in := random() < 0.6;
        ELSIF random() < 0.95 THEN
            order_status := 'CONFIRMED';
            payment_status := 'PENDING';
            checked_in := false;
        ELSE
            order_status := 'PENDING';
            payment_status := 'PENDING';
            checked_in := false;
        END IF;

        -- 데이터 INSERT
        INSERT INTO order_query.popup_order_items_view (
            popup_id, order_goods_id, order_id, store_id, user_id, order_no,
            order_status, ordered_at, item_type,
            goods_variant_id, goods_name, stock_unit, qty, unit_price, line_price,
            payment_status, payment_approved_at, checked_in, checkin_at,
            created_at, updated_at
        ) VALUES (
            target_popup_id,
            gen_random_uuid(),
            gen_random_uuid(),
            target_store_id,
            1600 + (random() * 100)::bigint, -- 사용자 ID 1600-1700
            'USER_' || LPAD(i::text, 6, '0'),
            order_status,
            order_time,
            'GOODS',
            goods_id,
            goods_name,
            '개',
            qty,
            unit_price,
            unit_price * qty,
            payment_status,
            CASE WHEN payment_status = 'PAID' THEN order_time + interval '5 minutes' ELSE NULL END,
            checked_in,
            CASE WHEN checked_in THEN order_time + interval '30 minutes' ELSE NULL END,
            order_time - interval '1 minute',
            order_time
        );
    END LOOP;

    RAISE NOTICE '✅ 100개 주문 데이터 생성 완료';
END $$;

-- 2️⃣ Summary 데이터 생성
INSERT INTO order_query.popup_order_summary (
    store_id, popup_id, owner_id, popup_title, popup_status,
    reservation_open_at, reservation_total_orders, reservation_paid_orders, reservation_cancelled_orders,
    goods_total_orders, goods_paid_orders, goods_cancelled_orders, checked_in_orders,
    created_at, updated_at
)
SELECT
    '72bc0453-0404-4de1-a54d-34d78ad16e55'::uuid as store_id,
    '7ac19fc7-36aa-47b7-a283-f42d21e5a47d'::uuid as popup_id,
    16 as owner_id, -- popcorn5@popcorn.com
    '사용자 팝업스토어' as popup_title,
    'OPEN' as popup_status,
    NOW() - interval '7 days' as reservation_open_at,
    0 as reservation_total_orders,
    0 as reservation_paid_orders,
    0 as reservation_cancelled_orders,
    COUNT(*) as goods_total_orders,
    COUNT(*) FILTER (WHERE order_status IN ('PAID', 'COMPLETED')) as goods_paid_orders,
    COUNT(*) FILTER (WHERE order_status = 'CANCELLED') as goods_cancelled_orders,
    COUNT(*) FILTER (WHERE checked_in = true) as checked_in_orders,
    NOW() - interval '7 days' as created_at,
    NOW() as updated_at
FROM order_query.popup_order_items_view
WHERE store_id = '72bc0453-0404-4de1-a54d-34d78ad16e55'
  AND popup_id = '7ac19fc7-36aa-47b7-a283-f42d21e5a47d'
ON CONFLICT (popup_id) DO UPDATE SET
    goods_total_orders = EXCLUDED.goods_total_orders,
    goods_paid_orders = EXCLUDED.goods_paid_orders,
    goods_cancelled_orders = EXCLUDED.goods_cancelled_orders,
    checked_in_orders = EXCLUDED.checked_in_orders,
    updated_at = NOW();

-- 3️⃣ 생성 결과 확인
SELECT
    '🎯 사용자 지정 데이터 생성 결과' as result,
    COUNT(*) as order_count,
    SUM(line_price) as total_revenue,
    ROUND(AVG(line_price)::numeric, 0) as avg_amount
FROM order_query.popup_order_items_view
WHERE store_id = '72bc0453-0404-4de1-a54d-34d78ad16e55'
  AND popup_id = '7ac19fc7-36aa-47b7-a283-f42d21e5a47d';

SELECT
    '✅ Summary 데이터 확인' as check_result,
    popup_title,
    goods_total_orders,
    goods_paid_orders
FROM order_query.popup_order_summary
WHERE store_id = '72bc0453-0404-4de1-a54d-34d78ad16e55'
  AND popup_id = '7ac19fc7-36aa-47b7-a283-f42d21e5a47d';