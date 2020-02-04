package io.appform.dropwizard.actors.postretry;

import io.appform.dropwizard.actors.postretry.config.PostRetryConfig;


/**
 * Created by kanika.khetawat on 04/02/20
 */
public abstract class PostRetryHandler {

    private final PostRetryConfig postRetryConfig;

    public PostRetryHandler(PostRetryConfig postRetryConfig) {
        this.postRetryConfig = postRetryConfig;
    }

    abstract public boolean handle();
}
