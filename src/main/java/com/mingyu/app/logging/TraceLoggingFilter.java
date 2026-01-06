package com.mingyu.app.logging;

// Implements System

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String traceId = resolveTraceId(request);
        try {
            applyContext(LoggingConstants.MDC_TRACE_ID, traceId);
            applyContext(LoggingConstants.MDC_USER_ID, request.getHeader(LoggingConstants.USER_ID_HEADER));
            applyContext(LoggingConstants.MDC_DEVICE_ID, request.getHeader(LoggingConstants.DEVICE_ID_HEADER));
            response.setHeader(LoggingConstants.TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            restoreContext(previousContext);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String headerValue = request.getHeader(LoggingConstants.TRACE_ID_HEADER);
        if (StringUtils.hasText(headerValue)) {
            return headerValue.trim();
        }
        String current = MDC.get(LoggingConstants.MDC_TRACE_ID);
        if (StringUtils.hasText(current)) {
            return current;
        }
        return UUID.randomUUID().toString();
    }

    private void applyContext(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, value.trim());
        } else {
            MDC.remove(key);
        }
    }

    private void restoreContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}