// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.controller;

import com.mingyu.app.auth.controller.dto.LoginRequest;
import com.mingyu.app.auth.controller.dto.LoginResponse;
import com.mingyu.app.auth.controller.dto.LoginUserView;
import com.mingyu.app.auth.controller.dto.SmsSendRequest;
import com.mingyu.app.auth.controller.dto.SmsSendResponse;
import com.mingyu.app.auth.service.AuthService;
import com.mingyu.app.auth.service.dto.LoginCommand;
import com.mingyu.app.auth.service.dto.LoginResult;
import com.mingyu.app.auth.service.dto.SmsSendResult;
import com.mingyu.app.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String DEFAULT_SCENE = "LOGIN";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sms/send")
    public ApiResponse<SmsSendResponse> sendSms(@Valid @RequestBody SmsSendRequest request) {
        SmsSendResult result = authService.sendSmsCode(request.phone(), request.scene());
        return ApiResponse.success(new SmsSendResponse(result.cooldownSeconds()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(
                new LoginCommand(request.phone(), request.smsCode(), DEFAULT_SCENE,
                        request.deviceId(), request.deviceModel(), request.platform()));
        LoginResponse response = new LoginResponse(
                result.token(),
                result.expiresInSeconds(),
                new LoginUserView(result.userId(), result.phoneMasked())
        );
        return ApiResponse.success(response);
    }
}
