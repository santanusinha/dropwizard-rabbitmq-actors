package io.appform.dropwizard.actors.base;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.DirectExecutorService;
import java.util.ArrayList;

import java.util.Collections;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

@Slf4j
public class UnmanagedPublisherTest {

    private Channel publishChannel = mock(Channel.class);

    private UnmanagedPublisher<String> messagePublisher;

    private ObjectMapper objectMapper = new ObjectMapper();

    private RMQConnection rmqConnection = mock(RMQConnection.class);

    @Test
    public void testConstructor() {
        ActorConfig config = new ActorConfig();
        RMQConfig config1 = new RMQConfig();
        DirectExecutorService executorService = new DirectExecutorService();
        Environment environment = new Environment("Name");
        RMQConnection rmqConnection = new RMQConnection("Name", config1, executorService, environment, new TtlConfig());

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        UnmanagedPublisher<Object> actualUnmanagedPublisher = new UnmanagedPublisher<>("Name", config, rmqConnection,
                objectMapper);

        assertSame(rmqConnection, actualUnmanagedPublisher.connection());
        assertSame(objectMapper, actualUnmanagedPublisher.mapper());
    }

    @Test
    public void testPublishWithConfirmListener() throws Exception {
        // Arrange
        String message = "test message";
        List<String> messages = Collections.singletonList(message);
        AMQP.BasicProperties properties = MessageProperties.PERSISTENT_TEXT_PLAIN;
        long timeout = 1;
        TimeUnit unit = TimeUnit.SECONDS;

        // Mock confirm listener
        ConfirmCallback ackConfirmListener = mock(ConfirmCallback.class);
        doAnswer(invocation -> {
            long sequenceNumber = invocation.getArgument(0);
            boolean multiple = invocation.getArgument(1);
            if (multiple) {
                confirmMultiple(sequenceNumber);
            } else {
                confirmSingle(sequenceNumber);
            }
            return null;
        }).when(ackConfirmListener).handle(anyLong(), anyBoolean());

        ConfirmCallback nackConfirmListener = mock(ConfirmCallback.class);
        doAnswer(invocation -> {
            long sequenceNumber = invocation.getArgument(0);
            boolean multiple = invocation.getArgument(1);
            nack(sequenceNumber, multiple);
            return null;
        }).when(nackConfirmListener).handle(anyLong(), anyBoolean());

        // Set up confirm select and confirm listener on publish channel
        when(publishChannel.getNextPublishSeqNo()).thenReturn(1L, 2L);
        when(publishChannel.isOpen()).thenReturn(true);
        when(rmqConnection.newChannel()).thenReturn(publishChannel);
        doAnswer(invocation -> {
            ConfirmListener listener = invocation.getArgument(0);
            listener.handleAck(1, false);
            return null;
        }).when(publishChannel).addConfirmListener(ackConfirmListener, nackConfirmListener);

        // Act
        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        messagePublisher = new UnmanagedPublisher<>("Name", actorConfig, rmqConnection, new ObjectMapper());
        assertSame(rmqConnection, messagePublisher.connection());
        messagePublisher.setPublishChannel(publishChannel);
        List<String> nackedMessages = messagePublisher.publishWithConfirmListener(messages, properties, timeout, unit);
        verify(publishChannel, times(1)).getNextPublishSeqNo();
        publishChannel.basicPublish(eq(""), eq(""), same(properties), any());
        verify(publishChannel).confirmSelect();
        verify(publishChannel, times(1)).getNextPublishSeqNo();
        verify(publishChannel, times(1)).basicPublish(anyString(), anyString(), eq(properties), any(byte[].class));
        assertEquals(1, nackedMessages.size());
    }


    @Test
    public void testMultiplePublishWithConfirmListener() throws Exception {
        // Arrange
        String message = "test message";
        int numberOfMessage = 2;
        List<String> messages = new ArrayList<>();
        for(int i=0;i<numberOfMessage;i++)
        {
            messages.add(message+i);
        }
        AMQP.BasicProperties properties = MessageProperties.PERSISTENT_TEXT_PLAIN;
        long timeout = 1;
        TimeUnit unit = TimeUnit.SECONDS;

        // Mock confirm listener
        ConfirmCallback ackConfirmListener = mock(ConfirmCallback.class);
        doAnswer(invocation -> {
            long sequenceNumber = invocation.getArgument(0);
            boolean multiple = invocation.getArgument(1);
            if (multiple) {
                confirmMultiple(sequenceNumber);
            } else {
                confirmSingle(sequenceNumber);
            }
            return null;
        }).when(ackConfirmListener).handle(anyLong(), anyBoolean());

        ConfirmCallback nackConfirmListener = mock(ConfirmCallback.class);
        doAnswer(invocation -> {
            long sequenceNumber = invocation.getArgument(0);
            boolean multiple = invocation.getArgument(1);
            nack(sequenceNumber, multiple);
            return null;
        }).when(nackConfirmListener).handle(anyLong(), anyBoolean());

        // Set up confirm select and confirm listener on publish channel
        when(publishChannel.getNextPublishSeqNo()).thenReturn(1L, 2L);
        when(publishChannel.isOpen()).thenReturn(true);
        when(rmqConnection.newChannel()).thenReturn(publishChannel);
        doAnswer(invocation -> {
            ConfirmListener listener = invocation.getArgument(0);
            listener.handleAck(1, false);
            return null;
        }).when(publishChannel).addConfirmListener(ackConfirmListener, nackConfirmListener);

        // Act
        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        messagePublisher = new UnmanagedPublisher<>("Name", actorConfig, rmqConnection, new ObjectMapper());
        assertSame(rmqConnection, messagePublisher.connection());
        messagePublisher.setPublishChannel(publishChannel);
        List<String> nackedMessages = messagePublisher.publishWithConfirmListener(messages, properties, timeout, unit);
        verify(publishChannel, times(numberOfMessage)).getNextPublishSeqNo();
        publishChannel.basicPublish(eq(""), eq(""), same(properties), any());
        verify(publishChannel).confirmSelect();
        verify(publishChannel, times(numberOfMessage)).getNextPublishSeqNo();
        verify(publishChannel, times(numberOfMessage)).basicPublish(anyString(), anyString(), eq(properties), any(byte[].class));
        assertEquals(numberOfMessage, nackedMessages.size());
    }


    @Test
    public void testConnection() {
        ActorConfig config = new ActorConfig();
        RMQConfig config1 = new RMQConfig();
        DirectExecutorService executorService = new DirectExecutorService();
        Environment environment = new Environment("Name");
        RMQConnection rmqConnection = new RMQConnection("Name", config1, executorService, environment, new TtlConfig());

        assertSame(rmqConnection,
                (new UnmanagedPublisher<>("Name", config, rmqConnection,
                        new com.fasterxml.jackson.databind.ObjectMapper()))
                        .connection());
    }


    @Test
    public void testMapper() {
        ActorConfig config = new ActorConfig();
        RMQConfig config1 = new RMQConfig();
        DirectExecutorService executorService = new DirectExecutorService();
        Environment environment = new Environment("Name");
        RMQConnection connection = new RMQConnection("Name", config1, executorService, environment, new TtlConfig());

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertSame(objectMapper, (new UnmanagedPublisher<>("Name", config, connection, objectMapper)).mapper());
    }

    private void confirmSingle(long sequenceNumber) {
        // Do nothing
    }

    private void confirmMultiple(long sequenceNumber) {
        // Do nothing
    }

    private void nack(long sequenceNumber, boolean multiple) {
        // Do nothing
    }
}
