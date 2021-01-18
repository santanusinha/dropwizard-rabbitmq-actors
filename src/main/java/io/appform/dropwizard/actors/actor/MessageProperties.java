package io.appform.dropwizard.actors.actor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageProperties {

    private boolean isRedelivered;
}
