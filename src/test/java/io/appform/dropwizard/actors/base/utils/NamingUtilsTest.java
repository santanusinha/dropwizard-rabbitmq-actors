package io.appform.dropwizard.actors.base.utils;

import static org.junit.Assert.assertEquals;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class NamingUtilsTest {
    @Test
    public void shouldReturnSidelineQueueName() {
        assertEquals("app.TEST_QUEUE_SIDELINE", NamingUtils.getSideline("app.TEST_QUEUE"));
    }

    @Test
    public void shouldGenerateEmptyConsumerTagWhenInputTagIsNull() {
        assertEquals(StringUtils.EMPTY, NamingUtils.generateConsumerTag(null, 1));
    }

    @Test
    public void shouldGenerateEmptyConsumerTagWhenInputTagIsEmpty() {
        assertEquals(StringUtils.EMPTY, NamingUtils.generateConsumerTag(StringUtils.EMPTY, 1));
    }

    @Test
    public void shouldGenerateEmptyConsumerTagWhenInputTagIsProvided() {
        assertEquals("testConsumerTag_1", NamingUtils.generateConsumerTag("testConsumerTag", 1));
    }

    @Test
    public void shouldPopulateEmptyConsumerTagIfEnvVarsNotSet() {
        assertEquals(StringUtils.EMPTY, NamingUtils.populateDescriptiveConsumerTag());
    }

}