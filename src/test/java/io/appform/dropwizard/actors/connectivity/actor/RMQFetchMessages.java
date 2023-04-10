package io.appform.dropwizard.actors.connectivity.actor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RMQFetchMessages {
    private int count = 5;
    private String ackmode = "ack_requeue_true";
    private String encoding = "auto";
    private int truncate = 50000;
}
