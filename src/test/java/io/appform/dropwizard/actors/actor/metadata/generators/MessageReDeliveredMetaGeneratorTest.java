package io.appform.dropwizard.actors.actor.metadata.generators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import lombok.val;
import org.junit.jupiter.api.Test;

class MessageReDeliveredMetaGeneratorTest {

    @Test
    void shouldGenerateMetadata() {
        val messageMetaContext = MessageMetaContext.builder()
                .redelivered(true)
                .build();
        val messageMetadata = MessageMetadata.builder().build();
        val generator = new MessageReDeliveredMetaGenerator();
        generator.generate(messageMetaContext, messageMetadata);
        assertTrue(messageMetadata.isRedelivered(), "Redelivered flag should match");
    }

    @Test
    void shouldGenerateDefaultMetadata() {
        val messageMetaContext = MessageMetaContext.builder().build();
        val messageMetadata = MessageMetadata.builder().build();
        val generator = new MessageReDeliveredMetaGenerator();
        generator.generate(messageMetaContext, messageMetadata);
        assertFalse(messageMetadata.isRedelivered(), "Redelivered flag should be false");
    }

}