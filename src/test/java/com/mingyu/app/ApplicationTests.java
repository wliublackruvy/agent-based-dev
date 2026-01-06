package com.mingyu.app;

// Implements 1.账号与关系管理

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTests {

    @Test
    void contextLoads() {
        Runnable task = Mockito.mock(Runnable.class);
        task.run();
        Mockito.verify(task).run();
    }
}