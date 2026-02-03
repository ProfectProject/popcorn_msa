package com.popcorn.order.client.dto;

/**
 * Store 서비스 API 공통 응답 래퍼
 * Store 서비스는 항상 {code, message, data} 구조로 응답합니다.
 */
public class StoreApiResponse<T> {
    private Integer code;
    private String message;
    private T data;

    // 기본 생성자
    public StoreApiResponse() {}

    // Getters and Setters
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "StoreApiResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}