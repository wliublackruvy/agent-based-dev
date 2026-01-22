package com.mingyu.app.relation.controller.dto;

import jakarta.validation.constraints.NotNull;

public record UnbindConfirmRequest(
    @NotNull(message = "确认状态不能为空")
    Boolean confirm
) {
}
