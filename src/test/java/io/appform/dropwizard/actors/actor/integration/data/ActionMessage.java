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
        @JsonSubTypes.Type(name = FlowType.C2M_AUTH_FLOW_TEXT, value = C2MDataActionMessage.class),
        @JsonSubTypes.Type(name = FlowType.C2C_AUTH_FLOW_TEXT, value = C2CDataActionMessage.class)
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