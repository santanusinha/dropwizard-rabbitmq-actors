package io.appform.dropwizard.actors.failurehandler.handlers;

import io.appform.dropwizard.actors.failurehandler.config.DropConfig;
import io.appform.dropwizard.actors.failurehandler.config.FailureHandlerConfig;
import io.appform.dropwizard.actors.failurehandler.config.FailureHandlerConfigVisitor;
import io.appform.dropwizard.actors.failurehandler.config.SidelineConfig;

public class FailureHandlingFactory {

    public FailureHandler create(FailureHandlerConfig config) {
        if (config == null) {
            return new MessageSidelineHandler(new SidelineConfig());
        }
        return config.accept(new FailureHandlerConfigVisitor<>() {
            @Override
            public FailureHandler visit(DropConfig config) {
                return new MessageDropHandler(config);
            }

            @Override
            public FailureHandler visit(SidelineConfig config) {
                return new MessageSidelineHandler(config);
            }
        });
    }
}
