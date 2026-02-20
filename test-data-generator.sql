-- 🚀 대량 주문 테스트 데이터 생성 스크립트
-- 스토어별 대시보드 성능 및 기능 검증용

-- 기존 테스트 데이터 정리 (선택사항)
-- DELETE FROM popup_order_items_view WHERE order_no LIKE 'TEST_%';

-- 📊 대량 데이터 생성 (10,000개 주문)
WITH
-- 테스트 스토어 생성 (5개)
test_stores AS (
    SELECT * FROM (VALUES
        ('11111111-1111-1111-1111-111111111111'::uuid, '팝콘 강남점'),
        ('22222222-2222-2222-2222-222222222222'::uuid, '팝콘 홍대점'),
        ('33333333-3333-3333-3333-333333333333'::uuid, '팝콘 신촌점'),
        ('44444444-4444-4444-4444-444444444444'::uuid, '팝콘 이태원점'),
        ('55555555-5555-5555-5555-555555555555'::uuid, '팝콘 명동점')
    ) AS t(store_id, store_name)
),
-- 테스트 팝업 생성 (각 스토어당 3개씩, 총 15개)
test_popups AS (
    SELECT
        (store_id::text || '-POPUP-' || popup_num)::uuid as popup_id,
        store_id,
        store_name || ' 팝업' || popup_num as popup_name
    FROM test_stores
    CROSS JOIN generate_series(1, 3) as popup_num
),
-- 테스트 상품 생성
test_goods AS (
    SELECT * FROM (VALUES
        ('a1111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, '아메리카노', 4500, '개'),
        ('b2222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, '카페라떼', 5000, '개'),
        ('c3333333-cccc-cccc-cccc-cccccccccccc'::uuid, '카푸치노', 5500, '개'),
        ('d4444444-dddd-dddd-dddd-dddddddddddd'::uuid, '에스프레소', 3500, '개'),
        ('e5555555-eeee-eeee-eeee-eeeeeeeeeeee'::uuid, '모카', 6000, '개'),
        ('f6666666-ffff-ffff-ffff-ffffffffffff'::uuid, '바닐라라떼', 5500, '개'),
        ('a7777777-aaaa-bbbb-cccc-dddddddddddd'::uuid, '치즈케이크', 7000, '조각'),
        ('b8888888-bbbb-cccc-dddd-eeeeeeeeeeee'::uuid, '티라미수', 8000, '조각'),
        ('c9999999-cccc-dddd-eeee-ffffffffffff'::uuid, '크루아상', 3000, '개'),
        ('da123456-dddd-eeee-ffff-aaaaaaaaaaaa'::uuid, '마카롱세트', 12000, 'set')
    ) AS g(goods_id, goods_name, unit_price, stock_unit)
),
-- 시간 패턴 생성 (최근 30일, 다양한 시간대)
time_patterns AS (
    SELECT
        current_date - interval '30 days' +
        (random() * interval '30 days')::interval +
        -- 영업시간 패턴 (7:00-22:00, 피크타임 강화)
        (CASE
            WHEN random() < 0.3 THEN interval '8 hours' + (random() * interval '2 hours')::interval  -- 아침 피크 (8-10시)
            WHEN random() < 0.5 THEN interval '12 hours' + (random() * interval '2 hours')::interval -- 점심 피크 (12-14시)
            WHEN random() < 0.7 THEN interval '18 hours' + (random() * interval '3 hours')::interval -- 저녁 피크 (18-21시)
            ELSE interval '7 hours' + (random() * interval '15 hours')::interval -- 기타 시간
        END) as ordered_at,
        -- 요일별 패턴 적용
        CASE
            WHEN EXTRACT(dow FROM current_date - interval '30 days' + (random() * interval '30 days')::interval) IN (0,6)
            THEN 1.3 -- 주말 가중치
            ELSE 1.0 -- 평일
        END as day_weight
    FROM generate_series(1, 10000) -- 10,000개 주문 생성
),
-- 랜덤 주문 데이터 생성
random_orders AS (
    SELECT
        row_number() over() as seq,
        tp.popup_id,
        tp.store_id,
        tg.goods_id,
        tg.goods_name,
        tg.unit_price,
        tg.stock_unit,
        tim.ordered_at,
        -- 수량 (1-5개 사이)
        (random() * 4 + 1)::int as qty,
        -- 주문 상태 (현실적인 분포)
        CASE
            WHEN random() < 0.60 THEN 'COMPLETED'
            WHEN random() < 0.80 THEN 'PAID'
            WHEN random() < 0.90 THEN 'CONFIRMED'
            WHEN random() < 0.95 THEN 'PENDING'
            ELSE 'CANCELLED'
        END as order_status,
        -- 결제 상태
        CASE
            WHEN random() < 0.85 THEN 'PAID'
            WHEN random() < 0.95 THEN 'PENDING'
            ELSE 'FAILED'
        END as payment_status,
        -- 체크인 여부 (완료/결제된 주문의 70%만)
        random() < 0.7 as checked_in
    FROM test_popups tp
    CROSS JOIN test_goods tg
    CROSS JOIN time_patterns tim
    WHERE random() < 0.1 -- 전체 조합의 10%만 실제 생성 (데이터 크기 조절)
)
-- 실제 INSERT 실행
INSERT INTO popup_order_items_view (
    popup_id, order_goods_id, order_id, store_id, user_id, order_no,
    order_status, ordered_at, item_type,
    goods_variant_id, goods_name, stock_unit, qty, unit_price, line_price,
    payment_status, payment_approved_at, checked_in, checkin_at,
    created_at, updated_at
)
SELECT
    -- Primary Key (복합키)
    ro.popup_id,
    gen_random_uuid() as order_goods_id,

    -- Order 정보
    gen_random_uuid() as order_id,
    ro.store_id,
    (random() * 1000 + 1000)::bigint as user_id, -- 1000-2000 사이 사용자
    'TEST_' || lpad(seq::text, 8, '0') as order_no,

    -- 주문 상태 및 시간
    ro.order_status::order_status_enum,
    ro.ordered_at,
    'GOODS'::item_type_enum as item_type,

    -- 상품 정보
    ro.goods_id,
    ro.goods_name,
    ro.stock_unit,
    ro.qty,
    ro.unit_price,
    (ro.unit_price * ro.qty) as line_price, -- 총 가격

    -- 결제 정보
    ro.payment_status::payment_status_enum,
    CASE
        WHEN ro.payment_status = 'PAID'
        THEN ro.ordered_at + interval '5 minutes' + (random() * interval '30 minutes')::interval
        ELSE NULL
    END as payment_approved_at,

    -- 체크인 정보
    CASE
        WHEN ro.order_status IN ('COMPLETED', 'PAID') AND ro.checked_in
        THEN true
        ELSE false
    END as checked_in,
    CASE
        WHEN ro.order_status IN ('COMPLETED', 'PAID') AND ro.checked_in
        THEN ro.ordered_at + interval '10 minutes' + (random() * interval '2 hours')::interval
        ELSE NULL
    END as checkin_at,

    -- 감사 필드
    ro.ordered_at - interval '1 minute' as created_at,
    ro.ordered_at as updated_at

FROM random_orders ro
ORDER BY ro.ordered_at DESC;

-- 📊 생성된 데이터 통계 출력
DO $$
DECLARE
    total_orders INTEGER;
    store_count INTEGER;
    date_range TEXT;
BEGIN
    -- 총 주문 수
    SELECT COUNT(*) INTO total_orders FROM popup_order_items_view WHERE order_no LIKE 'TEST_%';

    -- 스토어 수
    SELECT COUNT(DISTINCT store_id) INTO store_count FROM popup_order_items_view WHERE order_no LIKE 'TEST_%';

    -- 날짜 범위
    SELECT
        TO_CHAR(MIN(ordered_at), 'YYYY-MM-DD') || ' ~ ' || TO_CHAR(MAX(ordered_at), 'YYYY-MM-DD')
    INTO date_range
    FROM popup_order_items_view WHERE order_no LIKE 'TEST_%';

    RAISE NOTICE '🚀 테스트 데이터 생성 완료!';
    RAISE NOTICE '📊 총 주문 수: %', total_orders;
    RAISE NOTICE '🏪 스토어 수: %', store_count;
    RAISE NOTICE '📅 기간: %', date_range;
    RAISE NOTICE '';
    RAISE NOTICE '✅ 이제 다음 URL로 대시보드를 확인해보세요:';
    RAISE NOTICE '   http://localhost:3000/manager/dashboard';
    RAISE NOTICE '';
END $$;

-- 스토어별 주문 수 확인 쿼리
SELECT
    store_id,
    COUNT(*) as order_count,
    SUM(line_price) as total_revenue,
    AVG(line_price)::integer as avg_order_amount,
    COUNT(DISTINCT user_id) as unique_customers
FROM popup_order_items_view
WHERE order_no LIKE 'TEST_%'
GROUP BY store_id
ORDER BY order_count DESC;

-- 상태별 분포 확인
SELECT
    order_status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 1) as percentage
FROM popup_order_items_view
WHERE order_no LIKE 'TEST_%'
GROUP BY order_status
ORDER BY count DESC;

-- 시간대별 주문 분포 확인 (피크 타임 검증)
SELECT
    EXTRACT(hour FROM ordered_at) as hour,
    COUNT(*) as orders,
    '█' || REPEAT('▌', (COUNT(*) * 50 / MAX(COUNT(*)) OVER ())::int) as chart
FROM popup_order_items_view
WHERE order_no LIKE 'TEST_%'
GROUP BY EXTRACT(hour FROM ordered_at)
ORDER BY hour;