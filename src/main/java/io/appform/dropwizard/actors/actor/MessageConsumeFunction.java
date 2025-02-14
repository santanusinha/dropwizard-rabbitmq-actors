package io.appform.dropwizard.actors.actor;

@FunctionalInterface
public interface MessageConsumeFunction<T> {
    T apply(MessageMetadata messageMetadata);
}
