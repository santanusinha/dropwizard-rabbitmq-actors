package io.appform.dropwizard.actors.failurehandler.strategy;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.ProducerConfig;
import io.appform.dropwizard.actors.base.Handler;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.failurehandler.config.DropConfig;
import io.appform.dropwizard.actors.failurehandler.config.SidelineConfig;
import io.appform.dropwizard.actors.failurehandler.handlers.FailureHandlingFactory;
import io.appform.dropwizard.actors.failurehandler.handlers.MessageSidelineHandler;
import io.appform.dropwizard.actors.failurehandler.strategy.testactor.TestActor1;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.impl.NoRetryStrategy;
import io.appform.dropwizard.actors.utils.TestMessage;
import io.dropwizard.jackson.Jackson;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;

class FailureHandlingStrategyTest {

    private ObjectMapper objectMapper;
    private RetryStrategyFactory retryStrategyFactory;
    private TtlConfig ttlConfig;
    private MetricRegistry metricRegistry;
    private ConnectionRegistry connectionRegistry;
    private RMQConnection connection;
    private Channel publishChannel;
    private Channel consumeChannel;
    private ProducerConfig producerConfig;
    private ConsumerConfig consumerConfig;

    @BeforeEach
    void setup() throws IOException {
        this.objectMapper = Jackson.newObjectMapper();
        this.retryStrategyFactory = new RetryStrategyFactory();
        this.ttlConfig = TtlConfig.builder().build();
        this.metricRegistry = new MetricRegistry();
        this.connectionRegistry = Mockito.mock(ConnectionRegistry.class);
        this.connection = Mockito.mock(RMQConnection.class);
        this.publishChannel = Mockito.spy(Channel.class);
        this.consumeChannel = Mockito.spy(Channel.class);

        this.producerConfig = ProducerConfig.builder().build();
        this.consumerConfig = ConsumerConfig.builder().build();


        Mockito.doReturn(connection).when(connectionRegistry)
                .createOrGet(Constants.DEFAULT_PRODUCER_CONNECTION_NAME);
        Mockito.doReturn(connection).when(connectionRegistry)
                .createOrGet(Constants.DEFAULT_CONSUMER_CONNECTION_NAME);
        Mockito.doReturn(publishChannel).when(connection).channel();

        Mockito.doNothing().when(publishChannel)
                .basicPublish(ArgumentMatchers.any(), ArgumentMatchers.any(),
                        ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.doReturn(publishChannel).when(connection).newChannel();

//        val handler = new Handler<>()
    }

    @Test
    void testWhenFailureHandlingFactoryIsNotPassedByClient() {

    }

    @Test
    void testWhenClientWantsToSendMessageToSidelineOnFailure2() throws Exception {
        val actor = TestActor1.builder()
                .actorConfig(ActorConfig.builder()
                        .producer(producerConfig)
                        .consumer(consumerConfig)
                        .exchange("test-exchange")
                        .failureHandlerConfig(new SidelineConfig())
                        .build())
                .connectionRegistry(connectionRegistry)
                .mapper(objectMapper)
                .retryStrategyFactory(retryStrategyFactory)
                .exceptionHandlingFactory(new ExceptionHandlingFactory())
                .failureHandlingFactory(new FailureHandlingFactory())
                .clazz(TestMessage.class)
                .build();

        actor.start();
    }

    @Test
    void testWhenClientWantsToSendMessageToSidelineOnFailure() throws Exception {
        val exchange = "testExchange";
        val routingKey = "routingKey";
        val queueName = "testQueue";
        val actor = TestActor1.builder()
                .actorConfig(ActorConfig.builder()
                        .producer(producerConfig)
                        .consumer(consumerConfig)
                        .exchange(exchange)
                        .failureHandlerConfig(new SidelineConfig())
                        .build())
                .connectionRegistry(connectionRegistry)
                .mapper(objectMapper)
                .retryStrategyFactory(retryStrategyFactory)
                .exceptionHandlingFactory(new ExceptionHandlingFactory())
                .failureHandlingFactory(new FailureHandlingFactory())
                .clazz(TestMessage.class)
                .build();

        val testMessage = TestMessage.builder().build();

        actor.start();
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
        val actor = TestActor1.builder()
                .actorConfig(ActorConfig.builder()
                        .producer(producerConfig)
                        .consumer(consumerConfig)
                        .exchange(exchange)
                        .failureHandlerConfig(new DropConfig())
                        .build())
                .connectionRegistry(connectionRegistry)
                .mapper(objectMapper)
                .retryStrategyFactory(retryStrategyFactory)
                .exceptionHandlingFactory(new ExceptionHandlingFactory())
                .failureHandlingFactory(new FailureHandlingFactory())
                .clazz(TestMessage.class)
                .build();

        val testMessage = TestMessage.builder().build();

        actor.start();
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
