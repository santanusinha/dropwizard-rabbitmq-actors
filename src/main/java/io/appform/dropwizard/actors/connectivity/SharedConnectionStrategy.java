package io.appform.dropwizard.actors.connectivity;

import io.appform.dropwizard.actors.actor.ConnectionIsolationLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SharedConnectionStrategy extends ConnectionIsolationStrategy {

    @Builder
    public SharedConnectionStrategy() {
        super(ConnectionIsolationLevel.SHARED);
    }

    @Override
    public <T> T accept(ConnectionIsolationStrategyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
