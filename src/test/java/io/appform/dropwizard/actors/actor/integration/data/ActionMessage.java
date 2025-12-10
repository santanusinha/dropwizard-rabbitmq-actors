package io.appform.dropwizard.actors.actor.integration.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode
@ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = FlowType.FLOW_ONE_TEXT, value = OneDataActionMessage.class),
        @JsonSubTypes.Type(name = FlowType.FLOW_TWO_TEXT, value = TwoDataActionMessage.class)
})
public abstract class ActionMessage {

    @NotNull
    private final FlowType type;

    @Setter
    private String exchangeName;

    protected ActionMessage(FlowType type) {
        this.type = type;
    }

}