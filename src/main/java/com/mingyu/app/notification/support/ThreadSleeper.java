package com.mingyu.app.notification.support;

// Implements 2.权限引导与存活看板

import org.springframework.stereotype.Component;

@Component
public class ThreadSleeper implements Sleeper {

    @Override
    public void sleep(long millis) throws InterruptedException {
        if (millis <= 0) {
            return;
        }
        Thread.sleep(millis);
    }
}