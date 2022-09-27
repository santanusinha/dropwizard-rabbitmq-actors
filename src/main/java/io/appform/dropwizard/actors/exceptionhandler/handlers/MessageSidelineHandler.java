package io.appform.dropwizard.actors.exceptionhandler.handlers;

import io.appform.dropwizard.actors.exceptionhandler.config.SidelineConfig;

/**
 * Created by kanika.khetawat on 04/02/20
 */
public class MessageSidelineHandler extends ExceptionHandler {

    public MessageSidelineHandler(SidelineConfig sidelineConfig) {
        super(sidelineConfig);
    }

    @Override
    public boolean handle() {
        return false;
    }
}
