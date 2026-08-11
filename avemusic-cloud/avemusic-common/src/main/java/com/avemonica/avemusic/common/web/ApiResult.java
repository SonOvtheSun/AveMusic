package com.avemonica.avemusic.common.web;

/**
 * 前端统一响应结构。
 */
public record ApiResult<T>(
        String code,
        String message,
        T data
) {

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>("0", "success", data);
    }

    public static ApiResult<Void> success() {
        return new ApiResult<>("0", "success", null);
    }

    public static <T> ApiResult<T> failure(
            String code,
            String message
    ) {
        return new ApiResult<>(code, message, null);
    }
}