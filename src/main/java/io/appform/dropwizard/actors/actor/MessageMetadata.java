package io.appform.dropwizard.actors.actor;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public final class MessageMetadata {

    private boolean redelivered;
    private long delayInMs;
    private Map<String, Object> headers;

}
