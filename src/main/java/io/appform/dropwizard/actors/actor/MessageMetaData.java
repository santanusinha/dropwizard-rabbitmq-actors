package io.appform.dropwizard.actors.actor;

import lombok.Data;

@Data
public final class MessageMetaData {

    private boolean redelivered;

    public MessageMetaData(final boolean redelivered) {
        this.redelivered = redelivered;
    }
}
