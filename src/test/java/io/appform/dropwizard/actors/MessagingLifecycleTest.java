package io.appform.dropwizard.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.messaging.Messaging;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;

/**
 * Runs the bundle's messaging wiring on a real Dropwizard lifecycle and Jetty server, with an application managed
 * object registered after it (as {@code Application.run} or Guice would).
 */
class MessagingLifecycleTest {

    private final List<String> events = new ArrayList<>();

    private Messaging recordingMessaging() throws Exception {
        final Messaging messaging = mock(Messaging.class);
        doAnswer(i -> events.add("messaging.start")).when(messaging).start();
        doAnswer(i -> events.add("messaging.startConsumers")).when(messaging).startConsumers();
        doAnswer(i -> events.add("messaging.stopConsumers")).when(messaging).stopConsumers();
        doAnswer(i -> events.add("messaging.stop")).when(messaging).stop();
        return messaging;
    }

    private Managed appObject() {
        return new Managed() {
            @Override
            public void start() {
                events.add("app.start");
            }

            @Override
            public void stop() {
                events.add("app.stop");
            }
        };
    }

    private Server server(final Messaging messaging) {
        final LifecycleEnvironment lifecycle = new LifecycleEnvironment(new MetricRegistry());
        RabbitmqActorBundle.manageMessaging(lifecycle, messaging);
        lifecycle.manage(appObject());
        final Server server = new Server();
        lifecycle.attach(server);
        return server;
    }

    @Test
    void consumersRunOnlyWhileTheWholeApplicationIsUp() throws Exception {
        final Server server = server(recordingMessaging());

        server.start();
        server.stop();

        assertEquals(List.of(
                "messaging.start",          // topology + publishers, before the application starts
                "app.start",
                "messaging.startConsumers", // after every managed object has started
                "messaging.stopConsumers",  // before any managed object stops
                "app.stop",
                "messaging.stop"            // publishers stay usable while the application stops
        ), events);
    }

    @Test
    void consumerStartFailureFailsServerStartup() throws Exception {
        final Messaging messaging = recordingMessaging();
        doThrow(new IOException("404 NOT_FOUND")).when(messaging).startConsumers();
        final Server server = server(messaging);

        final Exception e = assertThrows(Exception.class, server::start);
        assertEquals("Unable to start messaging consumers", e.getMessage());
        server.stop();
    }

    @Test
    void consumerStopFailureDoesNotBlockShutdown() throws Exception {
        final Messaging messaging = recordingMessaging();
        doThrow(new IllegalStateException("boom")).when(messaging).stopConsumers();
        final Server server = server(messaging);

        server.start();
        server.stop();

        assertEquals(List.of("messaging.start", "app.start", "messaging.startConsumers", "app.stop",
                "messaging.stop"), events);
    }
}
