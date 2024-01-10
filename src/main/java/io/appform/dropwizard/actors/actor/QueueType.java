package io.appform.dropwizard.actors.actor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map;

public enum QueueType {
    CLASSIC {
        @Override
        public Map<String, Object> handleConfig(ActorConfig actorConfig) {
            Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                    .put(X_QUEUE_TYPE, this.toString()
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
    CLASSIC_V2 {
        @Override
        public Map<String, Object> handleConfig(ActorConfig actorConfig) {
            Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                    .put(X_QUEUE_TYPE, CLASSIC.name()
                            .toLowerCase())
                    .put("x-queue-version", 2);
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
                    .put(X_QUEUE_TYPE, this.toString()
                            .toLowerCase())
                    .put("x-quorum-initial-group-size", actorConfig.getQuorumInitialGroupSize())
                    .build();
        }
    };

    public static final String X_QUEUE_TYPE = "x-queue-type";

    public abstract Map<String, Object> handleConfig(ActorConfig config);
}
