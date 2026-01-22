package com.mingyu.app.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mingyu.app.auth.domain.VerificationCodeStore;
import com.mingyu.app.auth.service.dto.LoginCommand;
import com.mingyu.app.auth.service.dto.LoginResult;
import com.mingyu.app.dal.entity.User;
import com.mingyu.app.dal.entity.UserDevice;
import com.mingyu.app.dal.mapper.UserDeviceMapper;
import com.mingyu.app.dal.mapper.UserMapper;
import com.mingyu.app.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private VerificationCodeStore verificationCodeStore;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserDeviceMapper userDeviceMapper;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_Success() {
        LoginCommand command = new LoginCommand("13800000000", "123456", "LOGIN", "D1", "IOS", "iPhone");
        
        when(verificationCodeStore.validateAndConsume(anyString(), anyString(), anyString())).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userDeviceMapper.selectOne(any())).thenReturn(null);
        when(jwtService.generateToken(any())).thenReturn("mock-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResult result = authService.login(command);

        assertNotNull(result);
        assertEquals("mock-token", result.token());
        verify(userMapper).insert(any(User.class));
        verify(userDeviceMapper).insert(any(UserDevice.class));
    }
}
