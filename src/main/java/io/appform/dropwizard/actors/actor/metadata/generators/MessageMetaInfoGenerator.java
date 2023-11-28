package io.appform.dropwizard.actors.actor.metadata.generators;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;

public class MessageMetaInfoGenerator implements MessageMetadataGenerator {

    @Override
    public void generate(final MessageMetaContext messageMetaContext, MessageMetadata messageMetadata) {
        messageMetadata.setContentType(messageMetaContext.getContentType());
        messageMetadata.setContentEncoding(messageMetaContext.getContentEncoding());
        messageMetadata.setDeliveryMode(messageMetaContext.getDeliveryMode());
        messageMetadata.setPriority(messageMetaContext.getPriority());
        messageMetadata.setCorrelationId(messageMetaContext.getCorrelationId());
        messageMetadata.setReplyTo(messageMetaContext.getReplyTo());
        messageMetadata.setExpiration(messageMetaContext.getExpiration());
        messageMetadata.setMessageId(messageMetaContext.getMessageId());
        messageMetadata.setMsgSentTimestamp(messageMetaContext.getTimestamp());
        messageMetadata.setType(messageMetaContext.getType());
        messageMetadata.setUserId(messageMetaContext.getUserId());
        messageMetadata.setAppId(messageMetaContext.getAppId());
        messageMetadata.setClusterId(messageMetaContext.getClusterId());
    }

}