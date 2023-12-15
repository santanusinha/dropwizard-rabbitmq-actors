package io.appform.dropwizard.actors.actor;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class MessageMetadata {

    private boolean redelivered;
    private long delayInMs;
    private Map<String, Object> headers;

}
