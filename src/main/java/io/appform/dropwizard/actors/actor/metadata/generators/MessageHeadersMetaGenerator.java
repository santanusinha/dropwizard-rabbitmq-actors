package io.appform.dropwizard.actors.actor.metadata.generators;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;

public class MessageHeadersMetaGenerator implements MessageMetadataGenerator {

    @Override
    public void generate(final MessageMetaContext messageMetaContext, MessageMetadata messageMetadata) {
        messageMetadata.setHeaders(messageMetaContext.getHeaders());
    }

}