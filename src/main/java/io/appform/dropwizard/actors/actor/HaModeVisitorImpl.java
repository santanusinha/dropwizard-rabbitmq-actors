package io.appform.dropwizard.actors.actor;

import static io.appform.dropwizard.actors.actor.HaMode.ALL;
import static io.appform.dropwizard.actors.actor.HaMode.EXACTLY;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HaModeVisitorImpl implements HaModeVisitor<Map<String, Object>> {

    private ActorConfig actorConfig;

    @Override
    public Map<String, Object> visitAll() {
        return ImmutableMap.<String, Object>builder()
                .put("ha-mode", ALL.name()
                        .toLowerCase())
                .build();
    }

    @Override
    public Map<String, Object> visitExactly() {
        return ImmutableMap.<String, Object>builder()
                .put("ha-params", actorConfig.getHaParams())
                .put("ha-mode", EXACTLY.name()
                        .toLowerCase())
                .build();
    }
}
