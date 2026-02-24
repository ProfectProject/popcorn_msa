-- ===============================================
-- OrderQuery /dashboard/orders 고빈도 조회 인덱스
-- ===============================================

-- popupId 단독/정렬(createdAt) 필터
CREATE INDEX IF NOT EXISTS idx_oq_items_popup_created_at
    ON order_query.popup_order_items_view (popup_id, created_at DESC);

-- popupId 단독/정렬(orderedAt) 필터
CREATE INDEX IF NOT EXISTS idx_oq_items_popup_ordered_at
    ON order_query.popup_order_items_view (popup_id, ordered_at DESC);

-- userId + createdAt (마이페이지/유저 필터)
CREATE INDEX IF NOT EXISTS idx_oq_items_user_created_at
    ON order_query.popup_order_items_view (user_id, created_at DESC);

-- status + createdAt (상태 탭 조회)
CREATE INDEX IF NOT EXISTS idx_oq_items_status_created_at
    ON order_query.popup_order_items_view (order_status, created_at DESC);

-- popupId + status + createdAt (핫팝업 상태 필터)
CREATE INDEX IF NOT EXISTS idx_oq_items_popup_status_created_at
    ON order_query.popup_order_items_view (popup_id, order_status, created_at DESC);

ANALYZE order_query.popup_order_items_view;
