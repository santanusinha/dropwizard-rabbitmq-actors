package io.appform.dropwizard.actors.connectivity.actor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestBundleMessage {

    private String name;

    public enum Type {
        TEST_MESSAGE
    }
}
