package io.appform.dropwizard.actors.actor.metadata.generators;


import static io.appform.dropwizard.actors.common.Constants.MESSAGE_EXPIRY_TEXT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.Test;

public class MessageExpiredMetaGeneratorTest {

    @Test
    void shouldReturnTrueIfExpired() {
        val publishedTimestamp = Instant.now().minusSeconds(60).toEpochMilli();
        Map<String, Object> headers = Collections.singletonMap(MESSAGE_EXPIRY_TEXT, publishedTimestamp);
        val messageMetaContext = MessageMetaContext.builder().headers(headers).build();

        val messageMetadata = MessageMetadata.builder().build();
        val generator = new MessageExpiredMetaGenerator();
        generator.generate(messageMetaContext, messageMetadata);

        assertTrue(messageMetadata.isExpired(), "Message should be expired if expiry is in the past");
    }

    @Test
    void shouldReturnFalseIfHeaderNotPresent() {
        Map<String, Object> headers = new HashMap<>();
        val messageMetaContext = MessageMetaContext.builder().headers(headers).build();

        val messageMetadata = MessageMetadata.builder().build();
        val generator = new MessageExpiredMetaGenerator();
        generator.generate(messageMetaContext, messageMetadata);

        assertFalse(messageMetadata.isExpired(), "Message should not be expired if header is not present");
    }

    @Test
    void shouldReturnFalseIfNotExpired() {
        val publishedTimestamp = Instant.now().plusSeconds(60).toEpochMilli();
        Map<String, Object> headers = Collections.singletonMap(MESSAGE_EXPIRY_TEXT, publishedTimestamp);
        val messageMetaContext = MessageMetaContext.builder().headers(headers).build();

        val messageMetadata = MessageMetadata.builder().build();
        val generator = new MessageExpiredMetaGenerator();
        generator.generate(messageMetaContext, messageMetadata);

        assertFalse(messageMetadata.isExpired(), "Message should not be expired if expiry is in the future");
    }
}