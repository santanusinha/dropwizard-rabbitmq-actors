package io.appform.dropwizard.actors.common;

import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {

    public static final String DEFAULT_PRODUCER_CONNECTION_NAME = "__defaultproducer";

    public static final String DEFAULT_CONSUMER_CONNECTION_NAME = "__defaultconsumer";

    public static final Set<String> DEFAULT_CONNECTIONS = Sets.newHashSet(DEFAULT_PRODUCER_CONNECTION_NAME,
            DEFAULT_CONSUMER_CONNECTION_NAME);
    public static final int DEFAULT_THREADS_PER_CONNECTION = 10;

    public static final int MAX_THREADS_PER_CONNECTION = 300;

    public static final String MESSAGE_EXPIRY_TEXT = "x-expire-at";
    public static final String MESSAGE_PUBLISHED_TEXT = "x-published-at";

}
