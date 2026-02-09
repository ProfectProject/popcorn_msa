package com.popcorn.store.domain.popup.dto;

import com.popcorn.common.dto.ResponseCode;

import lombok.Getter;

@Getter
public enum PopupResponseCode implements ResponseCode {

	INVALID_REQUEST(2100, 400, "잘못된 요청입니다."),
	POPUP_NOT_FOUND(2101, 404, "팝업 정보를 찾을 수 없습니다."),
	Quantity_is_Null(2101, 400, "수량이 null 입니다."),
	Positive_Quantity(2101, 400, "수량이 양수가 아닙니다."),
	INSUFFICIENT_RESERVATION_CAPACITY(2102, 400, "예약 가능 수량이 부족합니다."),
	MISSING_ORDER_ID(2103, 400, "주문 ID가 필요합니다."),
	INVALID_SCHEDULE_TIME(2104, 400, "스케줄 시작/종료 시간이 올바르지 않습니다.");

	private final int code;
	private final int httpStatus;
	private final String message;

	PopupResponseCode(int code, int httpStatus, String message) {
		this.code = code;
		this.httpStatus = httpStatus;
		this.message = message;
	}
}
