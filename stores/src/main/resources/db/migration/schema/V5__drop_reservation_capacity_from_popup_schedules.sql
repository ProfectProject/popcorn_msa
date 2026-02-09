SET search_path TO store;

ALTER TABLE popup_schedules
    DROP COLUMN IF EXISTS reservation_capacity;
