-- Add owner reference to popup_order_summary for owner-based authorization
ALTER TABLE order_query.popup_order_summary
    ADD COLUMN IF NOT EXISTS owner_id bigint;

CREATE INDEX IF NOT EXISTS idx_popup_summary_owner
    ON order_query.popup_order_summary(owner_id);
