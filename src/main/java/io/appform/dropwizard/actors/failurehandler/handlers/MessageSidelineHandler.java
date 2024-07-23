package io.appform.dropwizard.actors.failurehandler.handlers;


import io.appform.dropwizard.actors.failurehandler.config.SidelineConfig;

public class MessageSidelineHandler extends FailureHandler {

    public MessageSidelineHandler(SidelineConfig sidelineConfig) {
        super(sidelineConfig);
    }

    @Override
    public boolean handle() {
        return false;
    }
}
