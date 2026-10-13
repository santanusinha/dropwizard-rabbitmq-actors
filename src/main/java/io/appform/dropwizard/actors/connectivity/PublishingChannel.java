package io.appform.dropwizard.actors.connectivity;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

@Slf4j
public final class PublishingChannel implements AutoCloseable {

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static final class ObservedPublishFailure extends RuntimeException {
        private ObservedPublishFailure(final Exception cause) {
            super(cause);
        }
    }

    private final RMQConnection connection;
    private final RMQObserver observer;
    private final long confirmTimeoutMs;
    private Channel channel;
    private AtomicBoolean returned;
    private boolean closed;

    public PublishingChannel(final RMQConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.observer = Objects.requireNonNull(connection.getRootObserver(), "root observer");
        this.confirmTimeoutMs = 0L;
    }

    public PublishingChannel(final RMQConnection connection, final long confirmTimeoutMs) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.observer = Objects.requireNonNull(connection.getRootObserver(), "root observer");
        if (confirmTimeoutMs <= 0) {
            throw new IllegalArgumentException("Confirm timeout must be positive");
        }
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    public synchronized void start() throws Exception {
        closed = false;
        if (null != channel && channel.isOpen()) {
            return;
        }
        if (confirmTimeoutMs == 0) {
            channel = connection.newChannel();
        }
    }

    @Override
    public synchronized void close() throws Exception {
        closed = true;
        closeChannel();
    }

    public synchronized void publish(final String queueName,
                        final String exchange,
                        final String routingKey,
                        final AMQP.BasicProperties properties,
                        final UnaryOperator<AMQP.BasicProperties> propertiesTransform,
                        final CheckedSupplier<byte[]> body) throws Exception {
        val context = PublishObserverContext.builder()
                .queueName(queueName)
                .messageProperties(properties)
                .build();
        var confirmed = new AtomicBoolean();
        try {
            observer.executePublish(context, details -> {
                try {
                    dispatch(exchange, routingKey,
                            propertiesTransform.apply(details.getMessageProperties()), body.get());
                    confirmed.set(confirmTimeoutMs > 0);
                } catch (final Exception error) {
                    throw new ObservedPublishFailure(error);
                }
                return null;
            });
            if (confirmTimeoutMs > 0 && !confirmed.get()) {
                throw new IOException("Publish observer did not complete the confirmed handoff");
            }
        } catch (final Exception failure) {
            // Once confirmed, observer post-processing must not cause the original delivery to be retried.
            if (confirmed.get()) {
                log.error("Observer failed after confirmed publish to exchange {}", exchange, failure);
                return;
            }
            var error = failure instanceof ObservedPublishFailure
                    ? (Exception) failure.getCause() : failure;
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (confirmTimeoutMs > 0) {
                discardChannel(error);
            }
            throw error;
        }
    }

    public void publish(final String queueName,
                        final String exchange,
                        final String routingKey,
                        final AMQP.BasicProperties properties,
                        final byte[] body) throws Exception {
        publish(queueName, exchange, routingKey, properties, UnaryOperator.identity(), () -> body);
    }

    public synchronized long messageCount(final String queueName) throws IOException {
        if (null == channel || !channel.isOpen()) {
            throw new IOException("Publisher channel is not open for queue [" + queueName + "]");
        }
        return channel.messageCount(queueName);
    }

    private void dispatch(final String exchange,
                          final String routingKey,
                          final AMQP.BasicProperties properties,
                          final byte[] body) throws Exception {
        if (confirmTimeoutMs > 0) {
            if (closed) {
                throw new IllegalStateException("Publisher channel is closed; publishing is not allowed until start()");
            }
            if (channel == null || !channel.isOpen()) {
                openConfirmedChannel();
            }
            returned.set(false);
            channel.basicPublish(exchange, routingKey, true, properties, body);
            channel.waitForConfirmsOrDie(confirmTimeoutMs);
            if (returned.get()) {
                throw new IOException("Message to exchange [" + exchange + "] was returned as unroutable");
            }
        } else {
            requireOpenChannel().basicPublish(exchange, routingKey, properties, body);
        }
    }

    private Channel requireOpenChannel() {
        if (null == channel) {
            throw new IllegalStateException("Publisher channel is not open; publishing is not allowed until start()");
        }
        return channel;
    }

    private void openConfirmedChannel() throws IOException {
        val channelReturnedState = new AtomicBoolean();
        this.channel = connection.newChannel();
        this.returned = channelReturnedState;
        channel.confirmSelect();
        channel.addReturnListener(message -> channelReturnedState.set(true));
    }

    private void discardChannel(final Throwable error) {
        try {
            closeChannel();
        } catch (final Exception closeError) {
            error.addSuppressed(closeError);
        }
    }

    private void closeChannel() throws Exception {
        val openChannel = channel;
        channel = null;
        returned = null;
        if (null != openChannel && openChannel.isOpen()) {
            openChannel.close();
        }
    }
}
