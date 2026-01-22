// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.security;

public class UserPrincipal {

    private final Long userId;
    private final String deviceId;

    public UserPrincipal(Long userId, String deviceId) {
        this.userId = userId;
        this.deviceId = deviceId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
