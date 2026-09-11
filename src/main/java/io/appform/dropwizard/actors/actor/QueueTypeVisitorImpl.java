package io.appform.dropwizard.actors.actor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import lombok.AllArgsConstructor;

import java.util.Map;

import static io.appform.dropwizard.actors.actor.QueueType.*;

@AllArgsConstructor
public class QueueTypeVisitorImpl implements QueueTypeVisitor<Map<String, Object>> {
    ActorConfig actorConfig;

    @Override
    public Map<String, Object> visitClassicQueue() {
        Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                    .put(X_QUEUE_TYPE, CLASSIC.name()
                            .toLowerCase());
            Map<String, Object> haModeParams = actorConfig.getHaMode()
                    .handleConfig(new HaModeVisitorImpl(actorConfig));
            builder.putAll(haModeParams);
            if (actorConfig.isLazyMode()) {
                builder.put("x-queue-mode", "lazy");
            }
            return builder.build();
    }

    @Override
    public Map<String, Object> visitQuorumQueue() {
        return ImmutableMap.<String, Object>builder()
                    .put(X_QUEUE_TYPE, QUORUM.name()
                            .toLowerCase())
                .put("x-quorum-initial-group-size", actorConfig.getQuorumInitialGroupSize())
                .build();
    }

    @Override
    public Map<String, Object> visitClassicV2Queue() {
        Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                .put(X_QUEUE_TYPE, CLASSIC.name()
                        .toLowerCase())
                .put("x-queue-version", 2);
        Map<String, Object> haModeParams = actorConfig.getHaMode()
                .handleConfig(new HaModeVisitorImpl(actorConfig));
        builder.putAll(haModeParams);
        if (actorConfig.isLazyMode()) {
            builder.put("x-queue-mode", "lazy");
        }
        return builder.build();
    }
}
