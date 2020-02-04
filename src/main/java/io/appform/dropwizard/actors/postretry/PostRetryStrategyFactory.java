package io.appform.dropwizard.actors.postretry;

import io.appform.dropwizard.actors.postretry.config.PostRetryConfig;
import io.appform.dropwizard.actors.postretry.config.PostRetryConfigVisitor;
import io.appform.dropwizard.actors.postretry.impl.MessageDropHandler;
import io.appform.dropwizard.actors.postretry.impl.MessageSidelineHandler;

/**
 * Created by kanika.khetawat on 04/02/20
 */
public class PostRetryStrategyFactory {

    public PostRetryHandler create(PostRetryConfig config) {

        return config.accept(new PostRetryConfigVisitor<PostRetryHandler>() {
            @Override
            public PostRetryHandler visitDrop() {
                return new MessageDropHandler(config);
            }

            @Override
            public PostRetryHandler visitSideline() {
                return new MessageSidelineHandler(config);
            }
        });
    }
}
