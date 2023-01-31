package io.appform.dropwizard.actors.utils;

import io.appform.dropwizard.actors.utils.ActorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestMessage {
    private String name;
    private ActorType actorType;
}
