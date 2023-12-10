package io.appform.dropwizard.actors.actor.metadata.generators;


import static io.appform.dropwizard.actors.common.Constants.MESSAGE_PUBLISHED_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.Test;

public class MessageDelayMetaGeneratorTest {

    @Test
    void shouldReturnCorrectDelay() {
        val publishedTimestamp = Instant.now().minusSeconds(60).toEpochMilli();
        Map<String, Object> headers = Collections.singletonMap(MESSAGE_PUBLISHED_TEXT, publishedTimestamp);
        val messageMetaContext = MessageMetaContext.builder().headers(headers).build();

        val messageMetadata = MessageMetadata.builder().build();
        val messageDelayMetaGenerator = new MessageDelayMetaGenerator();
        messageDelayMetaGenerator.generate(messageMetaContext, messageMetadata);

        val result = messageMetadata.getDelayInMs();
        assertEquals(60000L, result, "Delay should be about 60 seconds after publishing");
    }

    @Test
    void shouldReturnNegativeIfHeaderNotPresent() {
        Map<String, Object> headers = new HashMap<>();
        val messageMetaContext = MessageMetaContext.builder().headers(headers).build();

        val messageMetadata = MessageMetadata.builder().build();
        val messageDelayMetaGenerator = new MessageDelayMetaGenerator();
        messageDelayMetaGenerator.generate(messageMetaContext, messageMetadata);

        assertEquals(0L, messageMetadata.getDelayInMs(), "Delay should be negative if the header is not present");
    }

    @Test
    void shouldReturnZeroIfTimestampInTheFuture() {
        val publishedTimestamp = Instant.now().plusSeconds(60).toEpochMilli();
        Map<String, Object> headers = Collections.singletonMap(MESSAGE_PUBLISHED_TEXT, publishedTimestamp);
        val messageMetaContext = MessageMetaContext.builder().headers(headers).build();

        val messageMetadata = MessageMetadata.builder().build();
        val messageDelayMetaGenerator = new MessageDelayMetaGenerator();
        messageDelayMetaGenerator.generate(messageMetaContext, messageMetadata);

        assertEquals(0L, messageMetadata.getDelayInMs(), "Delay should be zero if the timestamp is in the future");
    }
}