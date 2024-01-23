package io.appform.dropwizard.actors.connectivity.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import java.io.IOException;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TagsBasedConsumerTest {

    private static final String TEST_CONSUMER_TAG = "test-tag";
    @Mock
    private ActorConfig actorConfig;

    @Mock
    private RMQConnection rmqConnection;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RetryStrategyFactory retryStrategyFactory;

    @Mock
    private RetryStrategy retryStrategy;

    @Mock
    private ExceptionHandlingFactory exceptionHandlingFactory;

    @Mock
    private ExceptionHandler exceptionHandler;

    @Mock
    private MessageHandlingFunction<String, Boolean> handlerFunction;

    @Mock
    private MessageHandlingFunction<String, Boolean> expiredMessageHandlingFunction;

    @Mock
    private Function<Throwable, Boolean> errorCheckFunction;

    @Mock
    private Channel channel;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testStartWithConfiguredTag() throws Exception {
        final UnmanagedConsumer<String> consumer = createConsumer(TEST_CONSUMER_TAG);
        consumer.start();
        ArgumentCaptor<String> consumerTagArgCaptor = ArgumentCaptor.forClass(String.class);
        verify(channel, Mockito.times(1)).basicConsume(anyString(), anyBoolean(), consumerTagArgCaptor.capture(),
                any(Consumer.class));
        String actualTag = consumerTagArgCaptor.getValue();
        assertNotNull(actualTag);
        assertEquals(TEST_CONSUMER_TAG + "_1", actualTag, "Expected consumer tag not generated");
    }

    @Test
    public void testStartWithoutTag() throws Exception {
        final UnmanagedConsumer<String> consumer = createConsumer(null);
        consumer.start();
        ArgumentCaptor<String> consumerTagArgCaptor = ArgumentCaptor.forClass(String.class);
        verify(channel, Mockito.times(1)).basicConsume(anyString(), anyBoolean(), consumerTagArgCaptor.capture(),
                any(Consumer.class));
        String actualTag = consumerTagArgCaptor.getValue();
        assertNotNull(actualTag);
        assertEquals(StringUtils.EMPTY, actualTag, "Expected consumer tag not generated");
    }

    @Test
    public void testStartWithEmptyNotNullTag() throws Exception {
        final UnmanagedConsumer<String> consumer = createConsumer(StringUtils.EMPTY);
        consumer.start();
        ArgumentCaptor<String> consumerTagArgCaptor = ArgumentCaptor.forClass(String.class);
        verify(channel, Mockito.times(1)).basicConsume(anyString(), anyBoolean(), consumerTagArgCaptor.capture(),
                any(Consumer.class));
        String actualTag = consumerTagArgCaptor.getValue();
        assertNotNull(actualTag);
        assertEquals(StringUtils.EMPTY, actualTag, "Expected consumer tag not generated");
    }

    private UnmanagedConsumer<String> createConsumer(String tagPrefix) throws IOException {
        when(actorConfig.getConcurrency()).thenReturn(1);
        when(actorConfig.getPrefix()).thenReturn("test-prefix");
        when(actorConfig.isSharded()).thenReturn(false);
        when(actorConfig.getShardCount()).thenReturn(1);
        when(actorConfig.getConsumer()).thenReturn(ConsumerConfig.builder().tagPrefix(tagPrefix).build());
        when(rmqConnection.newChannel()).thenReturn(channel);

        when(retryStrategyFactory.create(any())).thenReturn(retryStrategy);
        when(exceptionHandlingFactory.create(any())).thenReturn(exceptionHandler);

        final UnmanagedConsumer<String> consumer = new UnmanagedConsumer<>("test-name", actorConfig, rmqConnection,
                objectMapper, retryStrategyFactory, exceptionHandlingFactory, String.class, handlerFunction,
                expiredMessageHandlingFunction, errorCheckFunction);

        return consumer;
    }
}