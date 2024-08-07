package io.appform.dropwizard.actors.failurehandler.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.dropwizard.actors.failurehandler.handlers.FailureHandlerType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
        defaultImpl = SidelineConfig.class)
@JsonSubTypes({
        @JsonSubTypes.Type(name = "SIDELINE", value = SidelineConfig.class),
        @JsonSubTypes.Type(name = "DROP", value = DropConfig.class)
})
@Data
@EqualsAndHashCode
@ToString
public abstract class FailureHandlerConfig {

    private final FailureHandlerType type;

    public FailureHandlerConfig(FailureHandlerType type) {
        this.type = type;
    }

    abstract public <R> R accept(FailureHandlerConfigVisitor<R> visitor);
}
