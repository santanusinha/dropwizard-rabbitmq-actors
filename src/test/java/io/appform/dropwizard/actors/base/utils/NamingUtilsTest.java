package io.appform.dropwizard.actors.base.utils;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NamingUtilsTest {
    @Test
    public void shouldReturnSidelineQueueName() {
        Assertions.assertEquals("app.TEST_QUEUE_SIDELINE", NamingUtils.getSideline("app" + ".TEST_QUEUE"));
    }


    @Test
    public void shouldReturnSidelineProcessorQueueName() {
        Assertions.assertEquals("app.TEST_QUEUE_SIDELINE_PROCESSOR", NamingUtils.getSidelineProcessor("app" + ".TEST_QUEUE"));
    }
}