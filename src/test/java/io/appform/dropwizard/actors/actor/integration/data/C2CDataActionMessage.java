package io.appform.dropwizard.actors.actor.integration.data;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class C2CDataActionMessage extends ActionMessage {
    private String data;

    public C2CDataActionMessage() {
        super(FlowType.C2C_AUTH_FLOW);
    }

    @Builder
    public C2CDataActionMessage(String data) {
        this();
        this.data = data;
    }
}