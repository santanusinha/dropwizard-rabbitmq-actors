package io.appform.dropwizard.actors.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.messaging.config.ExchangeSpec;
import io.appform.dropwizard.actors.messaging.config.QueueSpec;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.ClassUtils;

/**
 * Runs a {@link QueueHandler} on one configured queue, on top of the actors' {@link UnmanagedConsumer} (concurrency,
 * prefetch, retry strategy, exception handling, expiry and observers behave exactly as for actors).
 */
class QueueConsumer<M> {

    private final String queue;
    private final UnmanagedConsumer<M> consumer;
    private final QueueRetryPublisher retryPublisher;

    QueueConsumer(final String prefix,
                  final String brokerExchange,
                  final String queue,
                  final ExchangeSpec exchangeSpec,
                  final QueueSpec spec,
                  final Class<? extends M> messageType,
                  final QueueHandler<M> handler,
                  final RMQConnection consumerConnection,
                  final RMQConnection producerConnection,
                  final ObjectMapper mapper,
                  final RetryStrategyFactory retryStrategyFactory,
                  final ExceptionHandlingFactory exceptionHandlingFactory) {
        this.queue = queue;
        final String brokerQueue = MessagingNames.queue(prefix, queue);
        this.retryPublisher = spec.isRetryLaterEnabled()
                ? new QueueRetryPublisher(brokerQueue, producerConnection, mapper,
                        exchangeSpec.isPublisherConfirms(), exchangeSpec.getConfirmTimeoutMs())
                : null;
        this.consumer = new UnmanagedConsumer<>(brokerQueue,
                spec.getPrefetchCount(),
                spec.getConcurrency(),
                false,
                0,
                spec.getConsumer(),
                consumerConnection,
                mapper,
                // SYNC retries only (ASYNC is rejected by MessagingConfigValidator), so the queue arguments, connection
                // and names passed here are not used to declare anything.
                retryStrategyFactory.create(spec.getRetryConfig(), spec.toQueueArgumentsConfig(brokerExchange),
                        producerConnection, brokerExchange, brokerQueue),
                exceptionHandlingFactory.create(spec.getExceptionHandlerConfig()),
                messageType,
                (message, metadata) -> handler.handle(message, context(metadata)),
                (message, metadata) -> handler.handleExpired(message, context(metadata)),
                t -> isDropped(t, handler.droppedExceptionTypes()));
    }

    /**
     * Starts the queue's {@code retryLater} publisher, if enabled (no-op otherwise). Called with the publishers, before
     * consuming starts.
     */
    void startRetryPublisher() throws Exception {
        if (retryPublisher != null) {
            retryPublisher.start();
        }
    }

    /**
     * Subscribes to the queue: from here on, the handler receives messages.
     */
    void startConsuming() throws Exception {
        consumer.start();
    }

    /**
     * Cancels the subscription and waits for in-flight messages, so the handler is no longer called afterwards.
     */
    void stopConsuming() {
        consumer.stop();
    }

    /**
     * Stops the queue's {@code retryLater} publisher, if enabled (no-op otherwise).
     */
    void stopRetryPublisher() throws Exception {
        if (retryPublisher != null) {
            retryPublisher.stop();
        }
    }

    /**
     * The consumer wraps handler exceptions, so the whole cause chain is checked.
     */
    private static boolean isDropped(final Throwable error, final Set<Class<? extends Throwable>> dropped) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            final Class<?> errorType = t.getClass();
            if (dropped.stream().anyMatch(type -> ClassUtils.isAssignable(errorType, type))) {
                return true;
            }
        }
        return false;
    }

    private MessageContext<M> context(final MessageMetadata metadata) {
        return new MessageContext<>() {
            @Override
            public String queue() {
                return queue;
            }

            @Override
            public MessageMetadata metadata() {
                return metadata;
            }

            @Override
            public void retryLater(final M message, final Duration delay) {
                if (retryPublisher == null) {
                    throw new IllegalStateException(String.format(
                            "retryLater is not enabled for queue '%s'; set retryLaterEnabled: true", queue));
                }
                final Map<String, Object> headers = metadata == null ? null : metadata.getHeaders();
                retryPublisher.publish(message, headers, delay);
            }
        };
    }
}
