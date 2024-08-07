package io.appform.dropwizard.actors.failurehandler.handlers;

import io.appform.dropwizard.actors.failurehandler.config.FailureHandlerConfig;

public abstract class FailureHandler {
    private final FailureHandlerConfig failureHandlerConfig;

    protected FailureHandler(FailureHandlerConfig failureHandlerConfig) {
        this.failureHandlerConfig = failureHandlerConfig;
    }

    public abstract boolean handle();
}
