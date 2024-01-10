package io.appform.dropwizard.actors.actor;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

public enum HaMode {
    EXACTLY {
        @Override
        public Map<String, Object> handleConfig(ActorConfig actorConfig) {
            return ImmutableMap.<String, Object>builder()
                    .put("ha-params", actorConfig.getHaParams())
                    .put("ha-mode", EXACTLY.name()
                            .toLowerCase())
                    .build();
        }
    },
    ALL {
        @Override
        public Map<String, Object> handleConfig(ActorConfig config) {
            return ImmutableMap.<String, Object>builder()
                    .put("ha-mode", ALL.name()
                            .toLowerCase())
                    .build();
        }
    };

    public abstract Map<String, Object> handleConfig(ActorConfig config);
}
