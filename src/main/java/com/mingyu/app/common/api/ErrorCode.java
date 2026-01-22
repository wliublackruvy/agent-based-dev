// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.common.api;

public enum ErrorCode {
    OK(0, "OK"),
    PARAM_INVALID(40001, "参数错误"),
    UNAUTHORIZED(40100, "未登录或Token无效"),
    DEVICE_MISMATCH(40101, "设备不匹配"),
    FORBIDDEN(40300, "无权限"),
    RELATION_CONFLICT(40901, "关系冲突"),
    BIND_CODE_INVALID(40902, "绑定码无效"),
    TOO_FREQUENT(42900, "请求过频"),
    SYSTEM_ERROR(50000, "系统异常");

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
