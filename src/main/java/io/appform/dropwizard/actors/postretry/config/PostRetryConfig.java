package io.appform.dropwizard.actors.postretry.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.dropwizard.actors.postretry.PostRetryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Created by kanika.khetawat on 04/02/20
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", defaultImpl = SidelineConfig.class)
@JsonSubTypes({
        @JsonSubTypes.Type(name = "SIDELINE", value = SidelineConfig.class),
        @JsonSubTypes.Type(name = "DROP", value = DropConfig.class)
})
@Data
@EqualsAndHashCode
@ToString
public abstract class PostRetryConfig {

    private final PostRetryType type;

    public PostRetryConfig(PostRetryType type) {
        this.type = type;
    }

    abstract public <T> T accept(PostRetryConfigVisitor<T> visitor);
}
