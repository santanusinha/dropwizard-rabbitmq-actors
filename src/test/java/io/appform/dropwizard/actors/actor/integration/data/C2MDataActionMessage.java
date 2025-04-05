package io.appform.dropwizard.actors.actor.integration.data;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class C2MDataActionMessage extends ActionMessage {
    private String data;

    public C2MDataActionMessage() {
        super(FlowType.C2M_AUTH_FLOW);
    }

    @Builder
    public C2MDataActionMessage(String data) {
        this();
        this.data = data;
    }
}