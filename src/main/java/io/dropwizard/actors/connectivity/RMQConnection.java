package io.dropwizard.actors.connectivity;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.dropwizard.actors.connectivity.config.RMQConfig;
import io.dropwizard.lifecycle.Managed;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
public class RMQConnection implements Managed {
    @Getter
    private final RMQConfig config;
    private Connection connection;
    private Channel channel;
    private static String QUEUE_PREFIX="promotions." ;

    public RMQConnection(RMQConfig config) {
        this.config = config;
    }


    @Override
    public void start() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(config.getUserName());
        factory.setPassword(config.getPassword());
        if(config.isSslEnabled()) {
            factory.useSslProtocol();
        }
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(3000);
        factory.setRequestedHeartbeat(60);
        List<Address> addresses = config.getBrokers()
                .stream()
                .map(broker -> new Address(broker.getHost()/*, broker.getPort()*/))
                .collect(Collectors.toList());

        connection = factory.newConnection(Executors.newFixedThreadPool(config.getThreadPoolSize())
                ,addresses.toArray(new Address[addresses.size()]));
        channel = connection.createChannel();
    }

    private<T extends Enum<T>> String deriveQueueName(T type, boolean sideline) {
        return QUEUE_PREFIX + (sideline ? getSideline(type.name()) : type.name());
    }

    public void ensure(final String queueName, String exchange,Map<String, Object> rmqOpts) throws Exception {
        ensure(queueName, queueName, exchange, rmqOpts);    
    }
    
    public void ensure(final String queueName, final String routingQueue, String exchange,Map<String, Object> rmqOpts) throws Exception {
        channel.queueDeclare(queueName, true, false, false, rmqOpts);
        channel.queueBind(queueName, exchange, routingQueue);
        log.info("Created queue: {}", queueName);
    }

    public Map<String, Object> rmqOpts() {
        return ImmutableMap.<String, Object>builder()
                .put("x-ha-policy", "all")
                .put("ha-mode", "all")
                .build();
    }

    public Map<String, Object> rmqOpts(String deadLetterExchange) {
        return ImmutableMap.<String, Object>builder()
                .put("x-ha-policy", "all")
                .put("ha-mode", "all")
                .put("x-dead-letter-exchange", deadLetterExchange)
                .build();
    }

    @Override
    public void stop() throws Exception {

    }

    public Channel channel() throws IOException {
        return channel;
    }

    public Channel newChannel() throws IOException {
        return connection.createChannel();
    }
    
    private String getSideline(String name){
        return String.format("%s_%s", name, "SIDELINE");
    }
}
