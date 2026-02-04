#!/usr/bin/env python3

import os
import re

# Mapping of kebab-case event types to EventConstants names
event_mappings = {
    "popup-created": "POPUP_CREATED",
    "popup-status-updated": "POPUP_STATUS_UPDATED",
    "popup-info-updated": "POPUP_INFO_UPDATED",
    "schedule-released": "SCHEDULE_RELEASED",
    "schedule-confirmation-succeeded": "SCHEDULE_CONFIRMATION_SUCCEEDED",
    "schedule-confirmation-requested": "SCHEDULE_CONFIRMATION_REQUESTED",
    "schedule-reservation-cancel-requested": "SCHEDULE_RESERVATION_CANCEL_REQUESTED",
    "schedule-reservation-succeeded": "SCHEDULE_RESERVATION_SUCCEEDED",
    "schedule-release-requested": "SCHEDULE_RELEASE_REQUESTED",
    "schedule-confirmation-failed": "SCHEDULE_CONFIRMATION_FAILED",
    "schedule-reservation-requested": "SCHEDULE_RESERVATION_REQUESTED",
    "schedule-reservation-failed": "SCHEDULE_RESERVATION_FAILED",
    "reservation-expired": "RESERVATION_EXPIRED",
    "goods-reservation-requested": "GOODS_RESERVATION_REQUESTED",
    "goods-reservation-succeeded": "GOODS_RESERVATION_SUCCEEDED",
    "goods-reservation-failed": "GOODS_RESERVATION_FAILED",
    "goods-reservation-cancel-requested": "GOODS_RESERVATION_CANCEL_REQUESTED",
    "stock-released": "STOCK_RELEASED"
}

# Base directory
base_dir = "/Users/suhwonji/Desktop/SideProject/popcorn_msa/order/src/main/java/com/popcorn/order/event"

def update_file(file_path, old_event, new_constant):
    try:
        with open(file_path, 'r') as f:
            content = f.read()

        # Add import if not present
        if 'import com.popcorn.order.constants.EventConstants;' not in content:
            # Find a good place to insert the import
            if 'import com.popcorn.order.event.order.BaseOrderEvent;' in content:
                content = content.replace(
                    'import com.popcorn.order.event.order.BaseOrderEvent;',
                    'import com.popcorn.order.constants.EventConstants;\nimport com.popcorn.order.event.order.BaseOrderEvent;'
                )
            elif 'import com.popcorn.order.event.popup.BasePopupEvent;' in content:
                content = content.replace(
                    'import com.popcorn.order.event.popup.BasePopupEvent;',
                    'import com.popcorn.order.constants.EventConstants;\nimport com.popcorn.order.event.popup.BasePopupEvent;'
                )

        # Replace the super call
        old_pattern = f'super\\([^,]+,\\s*"{old_event}"'
        new_replacement = f'super(\\1, EventConstants.EventTypes.{new_constant}'
        content = re.sub(old_pattern, new_replacement, content)

        # Handle 3-parameter super calls (with userId)
        old_pattern3 = f'super\\(([^,]+),\\s*"{old_event}",\\s*([^)]+)\\)'
        new_replacement3 = f'super(\\1, EventConstants.EventTypes.{new_constant}, \\2)'
        content = re.sub(old_pattern3, new_replacement3, content)

        with open(file_path, 'w') as f:
            f.write(content)

        print(f"Updated {file_path}: {old_event} -> EventConstants.EventTypes.{new_constant}")

    except Exception as e:
        print(f"Error updating {file_path}: {e}")

# Find all Java files in the event directory
for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith('.java'):
            file_path = os.path.join(root, file)

            try:
                with open(file_path, 'r') as f:
                    content = f.read()

                # Check for hardcoded event types
                for old_event, new_constant in event_mappings.items():
                    if f'"{old_event}"' in content and 'EventConstants.EventTypes' not in content:
                        update_file(file_path, old_event, new_constant)
                        break

            except Exception as e:
                print(f"Error reading {file_path}: {e}")

print("All updates completed!")