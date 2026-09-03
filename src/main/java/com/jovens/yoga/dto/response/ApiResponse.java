package com.jovens.yoga.dto.response;

public record ApiResponse<T>(boolean success, String message, String code, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, "OK", data);
    }

    public static <T> ApiResponse<T> success(String message, String code, T data) {
        return new ApiResponse<>(true, message, code, data);
    }

    public static <T> ApiResponse<T> failure(String message, String code) {
        return new ApiResponse<>(false, message, code, null);
    }
}
