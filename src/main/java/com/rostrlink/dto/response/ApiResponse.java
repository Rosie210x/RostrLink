package com.rostrlink.dto.response;

import lombok.Data;

/** Standard API envelope */
@Data
public class ApiResponse<T> {
    private String status;
    private String message;
    private T data;
    private String errorCode;
    private Object errorData;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.status = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = ok(data);
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.status = "error";
        r.errorCode = errorCode;
        r.message = message;
        return r;
    }
}