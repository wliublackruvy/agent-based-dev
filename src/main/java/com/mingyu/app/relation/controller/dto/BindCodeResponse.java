// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.relation.controller.dto;

public record BindCodeResponse(String bindCode, int ttlSeconds) {
}
