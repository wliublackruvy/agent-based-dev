package com.mingyu.app.logging;

// Implements System

import java.util.Map;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Runnable delegate = Objects.requireNonNull(runnable, "runnable is required");
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (context == null || context.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                delegate.run();
            } finally {
                if (previous == null || previous.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}