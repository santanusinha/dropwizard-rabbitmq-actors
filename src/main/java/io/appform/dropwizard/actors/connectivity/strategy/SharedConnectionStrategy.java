package io.appform.dropwizard.actors.connectivity.strategy;

import io.appform.dropwizard.actors.actor.ConnectionIsolationLevel;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;


@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SharedConnectionStrategy extends ConnectionIsolationStrategy {

    @NotNull
    @NotEmpty
    private String name;

    public SharedConnectionStrategy() {
        super(ConnectionIsolationLevel.SHARED);
    }

    @Builder
    public SharedConnectionStrategy(String name) {
        super(ConnectionIsolationLevel.SHARED);
        this.name = name;
    }

    @Override
    public <T> T accept(ConnectionIsolationStrategyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
