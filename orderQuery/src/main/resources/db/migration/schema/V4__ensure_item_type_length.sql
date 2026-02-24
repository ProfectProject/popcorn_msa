DO $$
DECLARE
    current_len integer;
BEGIN
    SELECT character_maximum_length
      INTO current_len
      FROM information_schema.columns
     WHERE table_schema = 'order_query'
       AND table_name = 'popup_order_items_view'
       AND column_name = 'item_type';

    IF current_len IS NOT NULL AND current_len < 20 THEN
        ALTER TABLE order_query.popup_order_items_view
            ALTER COLUMN item_type TYPE varchar(20);
    END IF;
END $$;
