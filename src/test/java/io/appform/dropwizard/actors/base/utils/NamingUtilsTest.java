package io.appform.dropwizard.actors.base.utils;

import org.junit.Assert;
import org.junit.Test;

public class NamingUtilsTest {
    @Test
    public void shouldReturnSidelineQueueName() {
        Assert.assertEquals("app.TEST_QUEUE_SIDELINE", NamingUtils.getSideline("app.TEST_QUEUE"));
    }
}