package io.appform.dropwizard.actors.common;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {

    public static final String DEFAULT_CONNECTION_NAME = "default";

    public static final int MAX_THREADS_PER_CONNECTION = 300;


}
