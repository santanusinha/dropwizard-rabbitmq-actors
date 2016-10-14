package io.dropwizard.actors.connectivity.retry.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.actors.connectivity.retry.RetryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configure a retry strategy
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@Data
@EqualsAndHashCode
@ToString
public abstract class RetryConfig {
    private final RetryType type;

    protected RetryConfig(RetryType type) {
        this.type = type;
    }
}
