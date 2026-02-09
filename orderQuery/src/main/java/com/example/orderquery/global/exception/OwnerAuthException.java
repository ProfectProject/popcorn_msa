package com.example.orderquery.global.exception;

import com.popcorn.common.dto.CommonResponseCode;
import com.popcorn.common.dto.ResponseCode;
import com.popcorn.common.exception.BaseException;

public class OwnerAuthException extends BaseException {

    private final String detail;

    public OwnerAuthException(ResponseCode responseCode, String detail) {
        super(responseCode, detail);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }

    public static OwnerAuthException unauthenticated() {
        return new OwnerAuthException(CommonResponseCode.FORBIDDEN, "unauthenticated");
    }

    public static OwnerAuthException userIdRequired() {
        return new OwnerAuthException(CommonResponseCode.FORBIDDEN, "user id required");
    }

    public static OwnerAuthException invalidRole() {
        return new OwnerAuthException(CommonResponseCode.FORBIDDEN, "invalid role");
    }

    public static OwnerAuthException invalidPrincipal() {
        return new OwnerAuthException(CommonResponseCode.FORBIDDEN, "invalid principal");
    }

    public static OwnerAuthException notOwner() {
        return new OwnerAuthException(CommonResponseCode.FORBIDDEN, "not owner");
    }

    public static OwnerAuthException notPopupOwner() {
        return new OwnerAuthException(CommonResponseCode.FORBIDDEN, "해당 팝업의 오너가 아닙니다.");
    }
}
