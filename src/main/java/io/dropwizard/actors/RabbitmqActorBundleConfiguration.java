package io.dropwizard.actors;

import io.dropwizard.actors.config.RMQConfig;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by santanu on 13/10/16.
 */
@Data
public class RabbitmqActorBundleConfiguration {
    @NotNull
    @Valid
    private RMQConfig rabbitmq = new RMQConfig();

}
