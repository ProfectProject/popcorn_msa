#!/bin/bash

# Array of files and their event types to update
declare -A event_files=(
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/stock/StockDeductionSucceededEvent.java"]="stock-deduction-succeeded:STOCK_DEDUCTION_SUCCEEDED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/stock/StockDeductionFailedEvent.java"]="stock-deduction-failed:STOCK_DEDUCTION_FAILED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/stock/StockReleaseRequestedEvent.java"]="stock-release-requested:STOCK_RELEASE_REQUESTED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/stock/StockReleasedEvent.java"]="stock-released:STOCK_RELEASED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/stock/GoodsReservationCancelRequestedEvent.java"]="goods-reservation-cancel-requested:GOODS_RESERVATION_CANCEL_REQUESTED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleReservationRequestedEvent.java"]="schedule-reservation-requested:SCHEDULE_RESERVATION_REQUESTED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleReservationSucceededEvent.java"]="schedule-reservation-succeeded:SCHEDULE_RESERVATION_SUCCEEDED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleReservationFailedEvent.java"]="schedule-reservation-failed:SCHEDULE_RESERVATION_FAILED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleReservationCancelRequestedEvent.java"]="schedule-reservation-cancel-requested:SCHEDULE_RESERVATION_CANCEL_REQUESTED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleConfirmationRequestedEvent.java"]="schedule-confirmation-requested:SCHEDULE_CONFIRMATION_REQUESTED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleConfirmationSucceededEvent.java"]="schedule-confirmation-succeeded:SCHEDULE_CONFIRMATION_SUCCEEDED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleConfirmationFailedEvent.java"]="schedule-confirmation-failed:SCHEDULE_CONFIRMATION_FAILED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleReleaseRequestedEvent.java"]="schedule-release-requested:SCHEDULE_RELEASE_REQUESTED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/schedule/ScheduleReleasedEvent.java"]="schedule-released:SCHEDULE_RELEASED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/goods/GoodsReservationRequestedEvent.java"]="goods-reservation-requested:GOODS_RESERVATION_REQUESTED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/goods/GoodsReservationSucceededEvent.java"]="goods-reservation-succeeded:GOODS_RESERVATION_SUCCEEDED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/goods/GoodsReservationFailedEvent.java"]="goods-reservation-failed:GOODS_RESERVATION_FAILED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/reservation/ReservationExpiredEvent.java"]="reservation-expired:RESERVATION_EXPIRED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/popup/PopupCreatedEvent.java"]="popup-created:POPUP_CREATED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/popup/PopupStatusUpdatedEvent.java"]="popup-status-updated:POPUP_STATUS_UPDATED"
    ["/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event/popup/PopupInfoUpdatedEvent.java"]="popup-info-updated:POPUP_INFO_UPDATED"
)

for file in "${!event_files[@]}"; do
    event_info=${event_files[$file]}
    old_type=$(echo $event_info | cut -d: -f1)
    new_constant=$(echo $event_info | cut -d: -f2)

    echo "Updating $file..."

    # Add import if not exists
    if ! grep -q "import com.popcorn.order.constants.EventConstants;" "$file"; then
        # Find the last import line and add after it
        sed -i '/^import.*$/a\
import com.popcorn.order.constants.EventConstants;' "$file"
    fi

    # Replace the super call
    sed -i "s/super(\([^,]*\), \"$old_type\"/super(\1, EventConstants.EventTypes.$new_constant/" "$file"

    echo "Updated $file: $old_type -> EventConstants.EventTypes.$new_constant"
done

echo "All event files updated successfully!"