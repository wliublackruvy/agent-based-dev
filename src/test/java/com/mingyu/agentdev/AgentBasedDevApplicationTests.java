package com.mingyu.agentdev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentBasedDevApplicationTests {

    @Test
    void agentAppInitializes() {
        AgentBasedDevApplication application = new AgentBasedDevApplication();
        assertNotNull(application);
    }
}
