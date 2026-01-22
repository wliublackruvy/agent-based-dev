// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mingyu.app.auth.domain.VerificationCodeStore;
import com.mingyu.app.auth.service.dto.LoginCommand;
import com.mingyu.app.auth.service.dto.LoginResult;
import com.mingyu.app.auth.service.dto.SmsSendResult;
import com.mingyu.app.common.api.ErrorCode;
import com.mingyu.app.common.exception.BusinessException;
import com.mingyu.app.common.util.PhoneMasker;
import com.mingyu.app.dal.entity.User;
import com.mingyu.app.dal.entity.UserDevice;
import com.mingyu.app.dal.mapper.UserDeviceMapper;
import com.mingyu.app.dal.mapper.UserMapper;
import com.mingyu.app.security.JwtPayload;
import com.mingyu.app.security.JwtService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final int COOLDOWN_SECONDS = 60;

    private final VerificationCodeStore verificationCodeStore;
    private final UserMapper userMapper;
    private final UserDeviceMapper userDeviceMapper;
    private final JwtService jwtService;

    public AuthService(VerificationCodeStore verificationCodeStore,
                       UserMapper userMapper,
                       UserDeviceMapper userDeviceMapper,
                       JwtService jwtService) {
        this.verificationCodeStore = verificationCodeStore;
        this.userMapper = userMapper;
        this.userDeviceMapper = userDeviceMapper;
        this.jwtService = jwtService;
    }

    public SmsSendResult sendSmsCode(String phone, String scene) {
        String code = "%06d".formatted(ThreadLocalRandom.current().nextInt(0, 1_000_000));
        verificationCodeStore.saveCode(phone, scene, code, CODE_TTL);
        return new SmsSendResult(COOLDOWN_SECONDS);
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        boolean verified = verificationCodeStore.validateAndConsume(command.phone(), command.scene(), command.smsCode());
        if (!verified) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "短信验证码错误");
        }
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, command.phone()));
        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            user = new User();
            user.setPhone(command.phone());
            user.setPhoneHash(DigestUtils.sha256Hex(command.phone()));
            user.setStatus(1);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            userMapper.insert(user);
        }
        UserDevice userDevice = userDeviceMapper.selectOne(
                Wrappers.<UserDevice>lambdaQuery().eq(UserDevice::getUserId, user.getId()).last("LIMIT 1"));
        if (userDevice == null) {
            userDevice = new UserDevice();
            userDevice.setUserId(user.getId());
            userDevice.setDeviceId(command.deviceId());
            userDevice.setPlatform(command.platform());
            userDevice.setDeviceModel(command.deviceModel());
            userDevice.setTokenVersion(1);
            userDevice.setIsActive(true);
            userDevice.setCreatedAt(now);
            userDevice.setUpdatedAt(now);
            userDevice.setLastLoginAt(now);
            userDeviceMapper.insert(userDevice);
        } else {
            boolean sameDevice = command.deviceId().equals(userDevice.getDeviceId());
            if (sameDevice) {
                userDevice.setPlatform(command.platform());
                userDevice.setDeviceModel(command.deviceModel());
            } else {
                userDevice.setDeviceId(command.deviceId());
                userDevice.setPlatform(command.platform());
                userDevice.setDeviceModel(command.deviceModel());
                userDevice.setTokenVersion(userDevice.getTokenVersion() + 1);
            }
            userDevice.setIsActive(true);
            userDevice.setLastLoginAt(now);
            userDevice.setUpdatedAt(now);
            userDeviceMapper.updateById(userDevice);
        }
        JwtPayload payload = new JwtPayload(user.getId(), command.deviceId(), userDevice.getTokenVersion());
        String token = jwtService.generateToken(payload);
        return new LoginResult(token, jwtService.getExpirationSeconds(), user.getId(), PhoneMasker.mask(user.getPhone()));
    }
}
