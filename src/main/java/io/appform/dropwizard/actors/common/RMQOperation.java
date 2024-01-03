package io.appform.dropwizard.actors.common;

public enum RMQOperation {
    CONSUME,
    PUBLISH,
    PUBLISH_WITH_DELAY,
    PUBLISH_WITH_EXPIRY
}
