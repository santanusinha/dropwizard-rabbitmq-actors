package io.appform.dropwizard.actors.base.utils;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NamingUtilsTest {
    @Test
    public void shouldReturnSidelineQueueName() {
        Assertions.assertEquals("app.TEST_QUEUE_SIDELINE", NamingUtils.getSideline("app" + ".TEST_QUEUE"));
    }
}