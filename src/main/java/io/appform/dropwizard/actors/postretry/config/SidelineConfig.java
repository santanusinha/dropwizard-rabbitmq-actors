package io.appform.dropwizard.actors.postretry.config;

import io.appform.dropwizard.actors.postretry.PostRetryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Created by kanika.khetawat on 04/02/20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SidelineConfig extends PostRetryConfig {

    public SidelineConfig() {
        super(PostRetryType.SIDELINE);
    }

    @Override
    public <T> T accept(PostRetryConfigVisitor<T> visitor) {
        return visitor.visitSideline();
    }
}
