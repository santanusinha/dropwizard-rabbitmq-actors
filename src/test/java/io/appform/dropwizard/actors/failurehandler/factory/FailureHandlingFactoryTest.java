package io.appform.dropwizard.actors.failurehandler.factory;

import io.appform.dropwizard.actors.failurehandler.config.DropConfig;
import io.appform.dropwizard.actors.failurehandler.config.SidelineConfig;
import io.appform.dropwizard.actors.failurehandler.handlers.FailureHandlerType;
import io.appform.dropwizard.actors.failurehandler.handlers.FailureHandlingFactory;
import io.appform.dropwizard.actors.failurehandler.handlers.MessageDropHandler;
import io.appform.dropwizard.actors.failurehandler.handlers.MessageSidelineHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class FailureHandlingFactoryTest {

    @Test
    void testWhenFailureHandlerConfigIsNull() {
        Assertions.assertInstanceOf(MessageSidelineHandler.class,
                new FailureHandlingFactory().create(null));
    }

    @Test
    void testWhenFailureHandlerConfigIsNonNull() {
        Arrays.stream(FailureHandlerType.values())
                .forEach(failureHandlerType -> {
                    switch (failureHandlerType) {

                        case SIDELINE -> {
                            Assertions.assertInstanceOf(MessageSidelineHandler.class,
                                    new FailureHandlingFactory().create(new SidelineConfig()));
                        }
                        case DROP -> {
                            Assertions.assertInstanceOf(MessageDropHandler.class,
                                    new FailureHandlingFactory().create(new DropConfig()));
                        }
                        default -> throw new RuntimeException("Not a supported FailureHandlerType");
                    }
                });
    }
}
