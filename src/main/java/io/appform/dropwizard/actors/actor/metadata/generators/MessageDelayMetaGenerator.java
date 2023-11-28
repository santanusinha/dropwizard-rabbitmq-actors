package io.appform.dropwizard.actors.actor.metadata.generators;

import static io.appform.dropwizard.actors.common.Constants.MESSAGE_PUBLISHED_TEXT;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import io.appform.dropwizard.actors.utils.CommonUtils;
import java.time.Instant;

public class MessageDelayMetaGenerator implements MessageMetadataGenerator {

    @Override
    public void generate(final MessageMetaContext messageMetaContext, MessageMetadata messageMetadata) {
        messageMetadata.setDelayInMs(calculateDelayInMs(messageMetaContext));
    }

    private long calculateDelayInMs(MessageMetaContext messageMetaContext) {
        return CommonUtils.extractMessagePropertiesHeader(messageMetaContext.getHeaders(),
                        MESSAGE_PUBLISHED_TEXT, Long.class)
                .map(publishedAt -> Math.max(Instant.now().toEpochMilli() - publishedAt, 0))
                .orElse(0L);
    }
}
