package io.appform.dropwizard.actors.actor;

import lombok.Data;

@Data
public final class MessageMetadata {

    private boolean redelivered;

    public MessageMetadata(final boolean redelivered) {
        this.redelivered = redelivered;
    }
}
