package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RMQShovelMessages {

    @Default
    @JsonProperty("component")
    private String component = "shovel";

    @NotEmpty
    @JsonProperty("name")
    private String shovelRequestName;

    @Default
    @JsonProperty("vhost")
    private String virtualHost = "/";

    @JsonProperty("value")
    private RmqShovelRequestValue value;
}