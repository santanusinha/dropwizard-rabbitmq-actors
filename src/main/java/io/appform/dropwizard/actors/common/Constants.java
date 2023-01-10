package io.appform.dropwizard.actors.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {

    public static final String DEFAULT_CONNECTION_NAME = "default";

    public static final int DEFAULT_THREADS_PER_CONNECTION = 10;

    public static final int MAX_THREADS_PER_CONNECTION = 300;

    public static final String MESSAGE_EXPIRY_TEXT = "x-expire-at";

}
