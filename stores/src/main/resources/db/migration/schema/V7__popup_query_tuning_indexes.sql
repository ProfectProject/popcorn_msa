-- V7__popup_query_tuning_indexes.sql
-- Keep existing query logic and tune read-path performance with supporting indexes.

-- popups list/detail ordering/filter support
CREATE INDEX IF NOT EXISTS idx_popups_active_created_at
ON store.popups (created_at DESC)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_popups_active_status_created_at
ON store.popups (status, created_at DESC)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_popups_active_store_created_at
ON store.popups (store_id, created_at DESC)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_popups_active_category_created_at
ON store.popups (category, created_at DESC)
WHERE deleted_at IS NULL;

-- popup detail schedule fetch support
CREATE INDEX IF NOT EXISTS idx_popup_schedules_active_popup_start_at
ON store.popup_schedules (popup_id, start_at)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_popup_schedules_active_popup_end_at
ON store.popup_schedules (popup_id, end_at)
WHERE deleted_at IS NULL;
