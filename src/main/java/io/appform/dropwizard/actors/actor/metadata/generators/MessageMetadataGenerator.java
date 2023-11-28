package io.appform.dropwizard.actors.actor.metadata.generators;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;

@FunctionalInterface
public interface MessageMetadataGenerator {

    void generate(final MessageMetaContext messageMetaContext, final MessageMetadata messageMetadata);

}