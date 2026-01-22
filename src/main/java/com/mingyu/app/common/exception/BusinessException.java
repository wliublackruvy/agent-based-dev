// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.common.exception;

import com.mingyu.app.common.api.ErrorCode;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
