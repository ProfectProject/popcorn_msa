package com.popcorn.store.domain.popup.exception;

import com.popcorn.common.exception.BaseException;
import com.popcorn.store.domain.popup.dto.PopupResponseCode;

public class PopupException extends BaseException {

    public PopupException(PopupResponseCode responseCode) {
        super(responseCode, responseCode.getMessage());
    }

    public static PopupException popupNotFound() {
        return new PopupException(PopupResponseCode.POPUP_NOT_FOUND);
    }

    public static PopupException isNullQuantity() {
        return new PopupException(PopupResponseCode.Quantity_is_Null);
    }

    public static PopupException isNotPositiveQuantity() {
        return new PopupException(PopupResponseCode.Positive_Quantity);
    }

    public static PopupException insufficientReservationCapacity() {
        return new PopupException(PopupResponseCode.INSUFFICIENT_RESERVATION_CAPACITY);
    }

    public static PopupException missingOrderId() {
        return new PopupException(PopupResponseCode.MISSING_ORDER_ID);
    }

    public static PopupException invalidScheduleTime() {
        return new PopupException(PopupResponseCode.INVALID_SCHEDULE_TIME);
    }
}
