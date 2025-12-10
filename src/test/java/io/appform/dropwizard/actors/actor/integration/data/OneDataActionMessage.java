package io.appform.dropwizard.actors.actor.integration.data;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OneDataActionMessage extends ActionMessage {
    private String data;

    public OneDataActionMessage() {
        super(FlowType.FLOW_ONE);
    }

    @Builder
    public OneDataActionMessage(String data) {
        this();
        this.data = data;
    }
}