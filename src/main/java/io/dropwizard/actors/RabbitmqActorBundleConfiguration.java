package io.dropwizard.actors;

import io.dropwizard.actors.connectivity.config.RMQConfig;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Created by santanu on 13/10/16.
 */
@Data
public class RabbitmqActorBundleConfiguration {
    @NotNull
    @Valid
    private RMQConfig rabbitmq = new RMQConfig();

}
