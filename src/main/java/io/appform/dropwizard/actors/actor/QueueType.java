package io.appform.dropwizard.actors.actor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map;

public enum QueueType {
    CLASSIC {
        @Override
        public Map<String, Object> handleConfig(ActorConfig actorConfig) {
            Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                    .put("x-queue-type", this.toString()
                            .toLowerCase());
            Map<String, Object> haModeParams = actorConfig.getHaMode()
                    .handleConfig(actorConfig);
            builder.putAll(haModeParams);
            if (actorConfig.isLazyMode()) {
                builder.put("x-queue-mode", "lazy");
            }
            return builder.build();
        }
    },
    QUORUM {
        @Override
        public Map<String, Object> handleConfig(ActorConfig actorConfig) {
            return ImmutableMap.<String, Object>builder()
                    .put("x-queue-type", this.toString()
                            .toLowerCase())
                    .put("x-quorum-initial-group-size", actorConfig.getQuorumInitialGroupSize())
                    .build();
        }
    };

    public abstract Map<String, Object> handleConfig(ActorConfig config);
}
