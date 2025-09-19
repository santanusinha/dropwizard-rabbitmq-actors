package io.appform.dropwizard.actors.actor;

import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerConfig {

    @NotNull
    @Valid
    private ConnectionIsolationStrategy connectionIsolationStrategy;

    @Size(max = 250)
    private String tagPrefix;

}
