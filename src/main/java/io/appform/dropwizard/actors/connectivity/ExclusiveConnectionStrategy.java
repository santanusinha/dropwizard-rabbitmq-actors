package io.appform.dropwizard.actors.connectivity;

import io.appform.dropwizard.actors.actor.ConnectionIsolationLevel;
import io.appform.dropwizard.actors.common.Constants;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExclusiveConnectionStrategy extends ConnectionIsolationStrategy {

    @Min(1)
    @Max(Constants.MAX_THREADS_PER_CONNECTION)
    @Builder.Default
    private int threadPoolSize = 10;

    public ExclusiveConnectionStrategy() {
        super(ConnectionIsolationLevel.EXCLUSIVE);
    }

    @Builder
    public ExclusiveConnectionStrategy(int threadPoolSize) {
        super(ConnectionIsolationLevel.EXCLUSIVE);
        this.threadPoolSize = threadPoolSize;
    }

    @Override
    public <T> T accept(ConnectionIsolationStrategyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
