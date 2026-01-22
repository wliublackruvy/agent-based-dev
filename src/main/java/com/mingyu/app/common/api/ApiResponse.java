// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.common.api;

import com.mingyu.app.common.api.ErrorCode;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        String finalMessage = (message == null || message.isBlank()) ? errorCode.getMessage() : message;
        return new ApiResponse<>(errorCode.getCode(), finalMessage, null);
    }
}
