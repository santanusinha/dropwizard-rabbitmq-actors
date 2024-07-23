package io.appform.dropwizard.actors.failurehandler.handlers;


import io.appform.dropwizard.actors.failurehandler.config.DropConfig;

public class MessageDropHandler extends FailureHandler {

    public MessageDropHandler(DropConfig dropConfig) {
        super(dropConfig);
    }

    @Override
    public boolean handle() {
        return true;
    }
}
