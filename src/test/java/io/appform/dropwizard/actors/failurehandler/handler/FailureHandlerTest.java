package io.appform.dropwizard.actors.failurehandler.handler;

import io.appform.dropwizard.actors.failurehandler.config.DropConfig;
import io.appform.dropwizard.actors.failurehandler.config.SidelineConfig;
import io.appform.dropwizard.actors.failurehandler.handlers.MessageDropHandler;
import io.appform.dropwizard.actors.failurehandler.handlers.MessageSidelineHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FailureHandlerTest {

    @Test
    void testMessageSidelineHandler() {
        Assertions.assertFalse(new MessageSidelineHandler(new SidelineConfig()).handle());
    }

    @Test
    void testMessageDropHandler() {
        Assertions.assertTrue(new MessageDropHandler(new DropConfig()).handle());
    }
}
