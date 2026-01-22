package com.mingyu.app.relation.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record UnbindRequest(
    @NotBlank(message = "解绑原因不能为空")
    String reason
) {
}
