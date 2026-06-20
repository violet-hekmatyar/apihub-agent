package com.apihub.agent.common;

public final class ResultUtils {

    private ResultUtils() {
    }

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(),
                data,
                TraceContext.getTraceId()
        );
    }

    public static BaseResponse<Object> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    public static BaseResponse<Object> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), message, null, TraceContext.getTraceId());
    }
}
