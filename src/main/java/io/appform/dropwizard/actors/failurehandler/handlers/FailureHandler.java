package io.appform.dropwizard.actors.failurehandler.handlers;

import io.appform.dropwizard.actors.failurehandler.config.FailureHandlerConfig;

public abstract class FailureHandler {
    private final FailureHandlerConfig failureHandlerConfig;

    public FailureHandler(FailureHandlerConfig failureHandlerConfig) {
        this.failureHandlerConfig = failureHandlerConfig;
    }

    abstract public boolean handle();
}
