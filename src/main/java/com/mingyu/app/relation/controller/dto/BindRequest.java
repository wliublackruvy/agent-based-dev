package com.mingyu.app.relation.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BindRequest(
    @NotBlank(message = "绑定码不能为空")
    @Size(min = 6, max = 6, message = "绑定码长度必须为6位")
    String bindCode
) {
}
