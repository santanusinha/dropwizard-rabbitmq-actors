package io.appform.dropwizard.actors.retry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by kanika.khetawat on 04/02/20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostRetryStrategy {
    private boolean ackAfterRetriesExhausted;
}
