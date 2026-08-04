package io.appform.dropwizard.actors.retry.config;

import io.dropwizard.util.Duration;

public interface MaxWaitBounded {
    Duration getMaxTimeBetweenRetries();
}
