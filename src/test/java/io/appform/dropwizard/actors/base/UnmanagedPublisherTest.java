package io.appform.dropwizard.actors.base;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.DirectExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class UnmanagedPublisherTest {

    private final ActorConfig config = mock(ActorConfig.class);

    private final RMQConnection connection = mock(RMQConnection.class);

    private final ObjectMapper mapper = mock(ObjectMapper.class);

    private Channel publishChannel = mock(Channel.class);

    @Test
    public void testPublishWithConfirmListener() throws Exception {
        List<String> testMessages = new ArrayList<>();
        UnmanagedPublisher<String> unmanagedPublisher = mock(UnmanagedPublisher.class);
        testMessages.add("testing message");
        when(config.isSharded()).thenReturn(true);
        when(config.getShardCount()).thenReturn(2);
        List<String> nackedMessage = unmanagedPublisher.publishWithConfirmListener(testMessages,  MessageProperties.MINIMAL_PERSISTENT_BASIC, 2000L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(0, nackedMessage.size());

    }

    /**
     * Method under test:
     * {@link UnmanagedPublisher#publishWithConfirmListener(List, AMQP.BasicProperties, long, TimeUnit)}
     */
    @Test
    @Disabled("TODO: Complete this test")
    void testPublishWithConfirmListener2() throws Exception {
        // TODO: Complete this test.
        //   Reason: R013 No inputs found that don't throw a trivial exception.
        //   Diffblue Cover tried to run the arrange/act section, but the method under
        //   test threw
        //   java.lang.NullPointerException: Cannot invoke "com.rabbitmq.client.Channel.confirmSelect()" because "this.publishChannel" is null
        //       at io.appform.dropwizard.actors.base.UnmanagedPublisher.publishWithConfirmListener(UnmanagedPublisher.java:118)
        //   See https://diff.blue/R013 to resolve this issue.

        ActorConfig config = new ActorConfig();
        RMQConfig config1 = new RMQConfig();
        DirectExecutorService executorService = new DirectExecutorService();
        Environment environment = new Environment("Name");
        RMQConnection connection = new RMQConnection("Name", config1, executorService, environment, new TtlConfig());

        UnmanagedPublisher<Object> unmanagedPublisher = new UnmanagedPublisher<>("Name", config, connection,
                new ObjectMapper());
        ArrayList<Object> messages = new ArrayList<>();
        unmanagedPublisher.publishWithConfirmListener(messages, new AMQP.BasicProperties(), 10L, TimeUnit.MICROSECONDS);
    }
}

