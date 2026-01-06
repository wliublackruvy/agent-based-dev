package com.mingyu.app.notification.support;

// Implements 2.权限引导与存活看板

@FunctionalInterface
public interface Sleeper {

    void sleep(long millis) throws InterruptedException;
}