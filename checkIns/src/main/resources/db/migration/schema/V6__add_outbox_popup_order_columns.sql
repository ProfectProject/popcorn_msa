-- outbox_events 테이블에 popup_id, order_goods_id 컬럼 추가
-- V6 마이그레이션

-- outbox_events 테이블에 컬럼 추가
ALTER TABLE checkIns.outbox_events
    ADD COLUMN IF NOT EXISTS popup_id uuid,
    ADD COLUMN IF NOT EXISTS order_goods_id uuid;

-- 새로 추가된 컬럼들에 대한 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_outbox_popup_id ON checkIns.outbox_events(popup_id);
CREATE INDEX IF NOT EXISTS idx_outbox_order_goods_id ON checkIns.outbox_events(order_goods_id);

-- 복합 인덱스도 생성 (popup과 order_goods 함께 검색할 때 유용)
CREATE INDEX IF NOT EXISTS idx_outbox_popup_order_goods ON checkIns.outbox_events(popup_id, order_goods_id);