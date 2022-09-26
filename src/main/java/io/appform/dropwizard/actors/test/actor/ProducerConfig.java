package io.appform.dropwizard.actors.test.actor;

import io.appform.dropwizard.actors.test.connectivity.strategy.ConnectionIsolationStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProducerConfig {

    @NotNull
    @Valid
    private ConnectionIsolationStrategy connectionIsolationStrategy;

}
