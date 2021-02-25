package io.appform.dropwizard.actors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueTtlConfig {

    private boolean ttlEnabled;

    private Duration ttl;

}
