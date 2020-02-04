package io.appform.dropwizard.actors.exceptionhandler.handlers;

import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfig;


/**
 * Created by kanika.khetawat on 04/02/20
 */
public abstract class ExceptionHandler {

    private final ExceptionHandlerConfig exceptionHandlerConfig;

    public ExceptionHandler(ExceptionHandlerConfig exceptionHandlerConfig) {
        this.exceptionHandlerConfig = exceptionHandlerConfig;
    }

    abstract public boolean handle();
}
