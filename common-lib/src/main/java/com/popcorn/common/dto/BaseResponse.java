package com.popcorn.common.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BaseResponse<T> {

    private int code;
    private String message;
    private T data;

    /**
     * 공통 응답 포맷 생성
     */
    public static <T> BaseResponse<T> of(int code, String message, T data) {
        return new BaseResponse<>(code, message, data);
    }

    public static <T> BaseResponse<T> from(ResponseCode responseCode, T data) {
        return of(responseCode.getCode(), responseCode.getMessage(), data);
    }

    public static <T> BaseResponse<T> success(T data) {
        return from(CommonResponseCode.SUCCESS, data);
    }

    public static BaseResponse<Void> error(ResponseCode responseCode) {
        return from(responseCode, null);
    }

    public static BaseResponse<BaseError> error(ResponseCode responseCode, String detail) {
        return new BaseResponse<>(responseCode.getCode(), responseCode.getMessage(),
                BaseError.of(responseCode, detail));
    }

    public static BaseResponse<BaseError> error(BaseError error) {
        return new BaseResponse<>(error.getCode(), error.getMessage(), error);
    }
}
