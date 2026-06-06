package com.simpledouyin.api.common;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String requestId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "ok", data, RequestContext.requestId());
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(0, "ok", data, requestId);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.message());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(
                errorCode.code(),
                message,
                null,
                RequestContext.requestId()
        );
    }
}
