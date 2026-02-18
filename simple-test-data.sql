-- 🚀 간단한 테스트 데이터 생성 (바로 실행 가능)

-- 기존 테스트 데이터 정리
DELETE FROM order_query.popup_order_items_view WHERE order_no LIKE 'TEST_%';

-- 🏪 5개 스토어별로 각각 500개씩 총 2,500개 주문 데이터 생성
DO $$
DECLARE
    store_ids uuid[] := ARRAY[
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '33333333-3333-3333-3333-333333333333',
        '44444444-4444-4444-4444-444444444444',
        '55555555-5555-5555-5555-555555555555'
    ];
    goods_data TEXT[][] := ARRAY[
        ['a1111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '아메리카노', '4500'],
        ['b2222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '카페라떼', '5000'],
        ['c3333333-cccc-cccc-cccc-cccccccccccc', '카푸치노', '5500'],
        ['d4444444-dddd-dddd-dddd-dddddddddddd', '에스프레소', '3500'],
        ['e5555555-eeee-eeee-eeee-eeeeeeeeeeee', '모카', '6000'],
        ['f6666666-ffff-ffff-ffff-ffffffffffff', '바닐라라떼', '5500'],
        ['a7777777-aaaa-bbbb-cccc-dddddddddddd', '치즈케이크', '7000'],
        ['b8888888-bbbb-cccc-dddd-eeeeeeeeeeee', '티라미수', '8000'],
        ['c9999999-cccc-dddd-eeee-ffffffffffff', '크루아상', '3000'],
        ['da123456-dddd-eeee-ffff-aaaaaaaaaaaa', '마카롱세트', '12000']
    ];

    i INTEGER;
    j INTEGER;
    store_id uuid;
    popup_id uuid;
    goods_id uuid;
    goods_name TEXT;
    unit_price INTEGER;
    qty INTEGER;
    order_time TIMESTAMP;
    order_status TEXT;
    payment_status TEXT;
    checked_in BOOLEAN;
    counter INTEGER := 1;
BEGIN
    -- 각 스토어별로 데이터 생성
    FOR i IN 1..5 LOOP
        store_id := store_ids[i];
        popup_id := gen_random_uuid(); -- 랜덤 팝업 ID

        RAISE NOTICE '🏪 스토어 % 데이터 생성 중...', i;

        -- 각 스토어당 500개 주문
        FOR j IN 1..500 LOOP
            -- 랜덤 상품 선택
            goods_id := goods_data[1 + (random() * 9)::int][1]::uuid;
            goods_name := goods_data[1 + (random() * 9)::int][2];
            unit_price := goods_data[1 + (random() * 9)::int][3]::int;

            -- 수량 (1-3개)
            qty := 1 + (random() * 2)::int;

            -- 주문 시간 (최근 30일 랜덤)
            order_time := NOW() - (random() * interval '30 days') +
                         (random() * interval '14 hours' + interval '7 hours'); -- 7시-21시

            -- 주문 상태 분포
            IF random() < 0.6 THEN
                order_status := 'COMPLETED';
                payment_status := 'PAID';
                checked_in := random() < 0.8;
            ELSIF random() < 0.8 THEN
                order_status := 'PAID';
                payment_status := 'PAID';
                checked_in := random() < 0.7;
            ELSIF random() < 0.9 THEN
                order_status := 'CONFIRMED';
                payment_status := 'PENDING';
                checked_in := false;
            ELSIF random() < 0.95 THEN
                order_status := 'PENDING';
                payment_status := 'PENDING';
                checked_in := false;
            ELSE
                order_status := 'CANCELLED';
                payment_status := 'FAILED';
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
                popup_id,
                gen_random_uuid(),
                gen_random_uuid(),
                store_id,
                1000 + (random() * 500)::bigint, -- 사용자 ID 1000-1500
                'TEST_' || LPAD(counter::text, 6, '0'),
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

            counter := counter + 1;

            -- 진행상황 표시
            IF j % 100 = 0 THEN
                RAISE NOTICE '  - % / 500 완료', j;
            END IF;
        END LOOP;
    END LOOP;

    RAISE NOTICE '✅ 총 % 개의 테스트 주문 생성 완료!', counter - 1;
END $$;

-- 📊 생성 결과 통계 확인
SELECT
    '🏪 스토어별 주문 분포' as title,
    store_id,
    COUNT(*) as orders,
    SUM(line_price) as revenue,
    ROUND(AVG(line_price)::numeric, 0) as avg_amount
FROM order_query.popup_order_items_view
WHERE order_no LIKE 'TEST_%'
GROUP BY store_id
ORDER BY store_id;

SELECT
    '📊 주문 상태별 분포' as title,
    order_status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 1) as percentage
FROM order_query.popup_order_items_view
WHERE order_no LIKE 'TEST_%'
GROUP BY order_status
ORDER BY count DESC;

SELECT
    '⏰ 시간대별 주문 분포' as title,
    EXTRACT(hour FROM ordered_at)::int as hour,
    COUNT(*) as orders,
    LPAD('█', (COUNT(*) * 30 / MAX(COUNT(*)) OVER ())::int, '█') as chart
FROM order_query.popup_order_items_view
WHERE order_no LIKE 'TEST_%'
GROUP BY EXTRACT(hour FROM ordered_at)
HAVING COUNT(*) > 0
ORDER BY hour;

-- 최종 요약
SELECT
    '🎯 테스트 데이터 요약' as summary,
    COUNT(*) as total_orders,
    COUNT(DISTINCT store_id) as stores,
    COUNT(DISTINCT goods_name) as products,
    TO_CHAR(MIN(ordered_at), 'MM-DD') || ' ~ ' || TO_CHAR(MAX(ordered_at), 'MM-DD') as date_range
FROM order_query.popup_order_items_view
WHERE order_no LIKE 'TEST_%';