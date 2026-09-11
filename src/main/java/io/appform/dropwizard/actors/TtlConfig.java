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
public class TtlConfig {

    private boolean ttlEnabled;

    @Builder.Default
    private Duration ttl = Duration.ofSeconds(1800);

}
