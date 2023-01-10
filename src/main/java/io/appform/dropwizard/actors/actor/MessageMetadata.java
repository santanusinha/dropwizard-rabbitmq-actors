package io.appform.dropwizard.actors.actor;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class MessageMetadata {

    private boolean redelivered;
    private long validTill;

}
