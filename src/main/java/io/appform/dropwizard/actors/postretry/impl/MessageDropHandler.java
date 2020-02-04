package io.appform.dropwizard.actors.postretry.impl;

import io.appform.dropwizard.actors.postretry.PostRetryHandler;
import io.appform.dropwizard.actors.postretry.config.PostRetryConfig;

/**
 * Created by kanika.khetawat on 04/02/20
 */
public class MessageDropHandler extends PostRetryHandler {

    public MessageDropHandler(PostRetryConfig postRetryConfig) {
        super(postRetryConfig);
    }

    @Override
    public boolean handle() {
        return true;
    }
}
