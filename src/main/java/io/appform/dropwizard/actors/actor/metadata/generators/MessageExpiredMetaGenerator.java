package io.appform.dropwizard.actors.actor.metadata.generators;

import static io.appform.dropwizard.actors.common.Constants.MESSAGE_EXPIRY_TEXT;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import io.appform.dropwizard.actors.utils.CommonUtils;
import java.time.Instant;

public class MessageExpiredMetaGenerator implements MessageMetadataGenerator {

    @Override
    public void generate(MessageMetaContext messageMetaContext, MessageMetadata messageMetadata) {
        messageMetadata.setExpired(isExpired(messageMetaContext));
    }

    private boolean isExpired(MessageMetaContext messageMetaContext) {
        return CommonUtils.extractMessagePropertiesHeader(messageMetaContext.getHeaders(),
                MESSAGE_EXPIRY_TEXT, Long.class)
                .filter(expiry -> Instant.now().toEpochMilli() >= expiry)
                .isPresent();
    }

}