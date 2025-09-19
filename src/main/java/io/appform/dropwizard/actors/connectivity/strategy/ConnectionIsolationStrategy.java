package io.appform.dropwizard.actors.connectivity.strategy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.dropwizard.actors.actor.ConnectionIsolationLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "isolationLevel"
)
@JsonSubTypes({
        @JsonSubTypes.Type(
                name = "DEFAULT",
                value = DefaultConnectionStrategy.class
        ),
        @JsonSubTypes.Type(
                name = "SHARED",
                value = SharedConnectionStrategy.class
        )})
@Data
@ToString
public abstract class ConnectionIsolationStrategy {

    @NotNull
    private final ConnectionIsolationLevel isolationLevel;

    protected ConnectionIsolationStrategy(ConnectionIsolationLevel isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public abstract <T> T accept(ConnectionIsolationStrategyVisitor<T> visitor);
}
