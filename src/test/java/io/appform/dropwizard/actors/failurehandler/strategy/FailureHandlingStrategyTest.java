package io.appform.dropwizard.actors.failurehandler.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.base.Handler;
import io.appform.dropwizard.actors.failurehandler.config.SidelineConfig;
import io.appform.dropwizard.actors.failurehandler.handlers.MessageSidelineHandler;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.impl.NoRetryStrategy;
import io.appform.dropwizard.actors.utils.TestMessage;
import io.dropwizard.jackson.Jackson;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


class FailureHandlingStrategyTest {

    private ObjectMapper objectMapper;
    private Channel consumeChannel;

    @BeforeEach
    void setup() {
        this.objectMapper = Jackson.newObjectMapper();
        this.consumeChannel = Mockito.spy(Channel.class);
    }

    @Test
    void testWhenClientWantsToSendMessageToSidelineOnFailure() throws Exception {
        val exchange = "testExchange";
        val routingKey = "routingKey";
        val queueName = "testQueue";
        val testMessage = TestMessage.builder().build();

        val handler = new Handler<>(consumeChannel, objectMapper, TestMessage.class, 1,
                this:: isExceptionIgnorable,
                new NoRetryStrategy(new NoRetryConfig()),
                new io.appform.dropwizard.actors.exceptionhandler.handlers.MessageSidelineHandler(
                        new io.appform.dropwizard.actors.exceptionhandler.config.SidelineConfig()),
                new MessageSidelineHandler(new SidelineConfig()),
                this::failure,
                this::handleExpiredMessages,
                new TerminalRMQObserver(), queueName);
        handler.handleDelivery("tag", new Envelope(0, false, exchange, routingKey),
                new AMQP.BasicProperties().builder().build(), objectMapper.writeValueAsBytes(testMessage));

        Mockito.verify(consumeChannel, Mockito.times(0))
                .basicAck(0, false);
        Mockito.verify(consumeChannel, Mockito.times(1))
                .basicReject(0, false);
    }

    @Test
    void testWhenClientWantsToDropMessageOnFailure() throws Exception {
        val exchange = "testExchange";
        val routingKey = "routingKey";
        val queueName = "testQueue";
        val testMessage = TestMessage.builder().build();

        val handler = new Handler<>(consumeChannel, objectMapper, TestMessage.class, 1,
                this:: isExceptionIgnorable,
                new NoRetryStrategy(new NoRetryConfig()),
                new io.appform.dropwizard.actors.exceptionhandler.handlers.MessageSidelineHandler(
                        new io.appform.dropwizard.actors.exceptionhandler.config.SidelineConfig()),
                new MessageSidelineHandler(new SidelineConfig()),
                this::success,
                this::handleExpiredMessages,
                new TerminalRMQObserver(), queueName);

        handler.handleDelivery("tag", new Envelope(0, false, exchange, routingKey),
                new AMQP.BasicProperties().builder().build(), objectMapper.writeValueAsBytes(testMessage));

        Mockito.verify(consumeChannel, Mockito.times(1))
                .basicAck(0, false);
        Mockito.verify(consumeChannel, Mockito.times(0))
                .basicReject(0, false);
    }

    private boolean isExceptionIgnorable(Throwable t) {
        return true;
    }

    private boolean failure(final TestMessage testMessage,
                            final MessageMetadata messageMetadata) {
         return false;
    }

    private boolean success(final TestMessage testMessage,
                            final MessageMetadata messageMetadata) {
        return true;
    }

    private boolean handleExpiredMessages(final TestMessage testMessage,
                                          final MessageMetadata messageMetadata) {
        return false;
    }
}
