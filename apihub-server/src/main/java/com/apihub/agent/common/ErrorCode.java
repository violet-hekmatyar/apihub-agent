package com.apihub.agent.common;

public enum ErrorCode {

    SUCCESS(200, "success"),
    INVALID_ARGUMENT(400, "invalid argument"),
    UNAUTHORIZED(401, "unauthorized"),
    PERMISSION_DENIED(403, "permission denied"),
    NOT_FOUND(404, "not found"),
    INTERNAL_ERROR(500, "internal error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
