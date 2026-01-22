// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingyu.app.common.api.ApiResponse;
import com.mingyu.app.common.api.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mingyu.app.dal.entity.UserDevice;
import com.mingyu.app.dal.mapper.UserDeviceMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/api/v1/auth/sms/send",
            "/api/v1/auth/login", "/actuator/health");

    private final JwtService jwtService;
    private final UserDeviceMapper userDeviceMapper;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDeviceMapper userDeviceMapper,
                                   ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userDeviceMapper = userDeviceMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (!StringUtils.startsWith(header, "Bearer ")) {
            writeUnauthorized(response, ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }
        String token = header.substring(7);
        try {
            JwtPayload payload = jwtService.parseToken(token);
            UserDevice device = userDeviceMapper.selectOne(
                    Wrappers.<UserDevice>lambdaQuery().eq(UserDevice::getUserId, payload.userId()).last("LIMIT 1"));
            if (device == null) {
                writeUnauthorized(response, ErrorCode.UNAUTHORIZED, "User device not found");
                return;
            }
            if (!payload.deviceId().equals(device.getDeviceId()) || payload.tokenVersion() != device.getTokenVersion()) {
                writeUnauthorized(response, ErrorCode.DEVICE_MISMATCH, "Token device mismatch");
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(new UserPrincipal(payload.userId(), payload.deviceId()),
                            null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response, ErrorCode.UNAUTHORIZED, "Invalid token");
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return PUBLIC_PATHS.contains(path);
    }

    private void writeUnauthorized(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Object> body = ApiResponse.error(errorCode, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
