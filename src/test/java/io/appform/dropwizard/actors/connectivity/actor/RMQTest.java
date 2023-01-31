package io.appform.dropwizard.actors.connectivity.actor;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.utils.ActorType;
import io.appform.dropwizard.actors.utils.AsyncOperationHelper;
import io.appform.dropwizard.actors.utils.EmbeddedRMQServer;
import io.appform.dropwizard.actors.utils.SidelineTestActor;
import io.appform.dropwizard.actors.utils.TestMessage;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.dropwizard.Configuration;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.validation.Validation;
import java.util.concurrent.Executors;

public class RMQTest {
    private static ConnectionRegistry connectionRegistry;
    private SidelineTestActor actor;

    private RMQConnection rmqConnection;

    @BeforeClass
    public static void startAll() {
        EmbeddedRMQServer.start();
        Environment environment = new Environment("testing",
                Jackson.newObjectMapper(),
                Validation.buildDefaultValidatorFactory(),
                new MetricRegistry(),
                Thread.currentThread().getContextClassLoader(),
                new HealthCheckRegistry(),
                new Configuration());

        RMQConfig rmqConfig = EmbeddedRMQServer.getConfig();

        connectionRegistry = new ConnectionRegistry(environment,
                (name, coreSize) -> Executors.newFixedThreadPool(1),
                rmqConfig, TtlConfig.builder().build());
    }

    @AfterClass
    public static void stopAll() {
        EmbeddedRMQServer.stop();
    }

    @Before
    @SneakyThrows
    public void setUp() {
        rmqConnection = connectionRegistry.createOrGet("default");
        AsyncOperationHelper helper = new AsyncOperationHelper(rmqConnection,
                connectionRegistry, new RetryStrategyFactory(), new ExceptionHandlingFactory());
        ActorConfig actorConfig = helper.buildActorConfig();
        actor = new SidelineTestActor(actorConfig, helper.getConnRegistry(), new ObjectMapper(),
                helper.getRetryStrategyFactory(), helper.getExceptionHandlingFactory());
        actor.start();
    }

    @Test
    public void failureToConsumerMessageShouldMoveToSidelineInSharedQueue() throws Exception {
        TestMessage message = TestMessage.builder()
                .actorType(ActorType.ALWAYS_FAIL_ACTOR)
                .name("test_message")
                .build();
        actor.publish(message);
        Thread.sleep(5000);

        Channel channel = rmqConnection.newChannel();
        GetResponse response = channel.basicGet("test.ALWAYS_FAIL_ACTOR_SIDELINE", true);
        Assert.assertNotNull(response);
        ObjectMapper mapper = new ObjectMapper();
        TestMessage sidelinedMessage = mapper.readValue(new String(response.getBody()), TestMessage.class);
        Assert.assertEquals(sidelinedMessage, message);
    }

    @After
    public void tearDown() throws Exception {
        actor.stop();
    }
}
