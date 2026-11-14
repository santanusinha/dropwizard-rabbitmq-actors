package io.appform.dropwizard.actors.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.messaging.config.MessagingConfig;
import io.appform.dropwizard.actors.connectivity.RmqManagementClient;
import io.appform.dropwizard.actors.connectivity.RmqManagementClient.Binding;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BindingGuardTest {

    private static final String QUEUE = "ns.p.Q";
    private static final String EXCHANGE = "ns.ex";
    private static final Set<String> ALLOWED = Set.of("ns.p.Q_RETRY");

    private final List<String> events = new ArrayList<>();
    private RmqManagementClient client;
    private BindingGuard guard;

    @BeforeEach
    void setUp() {
        client = mock(RmqManagementClient.class);
        guard = new BindingGuard(RMQConfig.builder().virtualHost("vh").build(), client);
    }

    private void ensure(final List<String> keys) throws Exception {
        guard.ensureBindings(QUEUE, EXCHANGE, keys, ALLOWED,
                () -> events.add("declare"), key -> events.add("bind:" + key));
    }

    private static Optional<List<Binding>> bindings(final Binding... bindings) {
        return Optional.of(List.of(bindings));
    }

    @Test
    void withoutManagementApiDeclaresAndBindsAdditively() throws Exception {
        guard = new BindingGuard(RMQConfig.builder().build(), (RmqManagementClient) null);
        ensure(List.of("a", "b"));
        assertEquals(List.of("declare", "bind:a", "bind:b"), events);
    }

    @Test
    void newQueueIsDeclaredBoundAndReverified() throws Exception {
        when(client.fetchBindings("vh", QUEUE))
                .thenReturn(Optional.empty())
                .thenReturn(bindings(new Binding(EXCHANGE, "a")));
        ensure(List.of("a"));
        assertEquals(List.of("declare", "bind:a"), events);
    }

    @Test
    void existingQueueWithSameKeysIsNotRebound() throws Exception {
        when(client.fetchBindings("vh", QUEUE)).thenReturn(bindings(
                new Binding(EXCHANGE, "b"), new Binding(EXCHANGE, "a"), new Binding("ns.p.Q_RETRY", "ns.p.Q")));
        ensure(List.of("a", "b"));
        assertEquals(List.of("declare"), events);
    }

    @Test
    void changedKeysOnExistingQueueFail() {
        when(client.fetchBindings("vh", QUEUE)).thenReturn(bindings(new Binding(EXCHANGE, "a")));
        final List<String> keys = List.of("a", "c");
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> ensure(keys));
        assertTrue(e.getMessage().contains("immutable"), e.getMessage());
        assertEquals(List.of("declare"), events);
    }

    @Test
    void leftoverOwnRetryBindingIsNotStale() throws Exception {
        // retryLater was turned off: the queue is still bound to its own retry exchange, which is always allowed.
        when(client.fetchBindings("vh", QUEUE)).thenReturn(bindings(
                new Binding(EXCHANGE, "a"), new Binding("ns.p.Q_RETRY", QUEUE)));
        ensure(List.of("a"));
        assertEquals(List.of("declare"), events);
    }

    @Test
    void staleExchangeFailsBeforeAnythingIsDeclared() {
        when(client.fetchBindings("vh", QUEUE)).thenReturn(bindings(new Binding("ns.other", "a")));
        final List<String> keys = List.of("a");
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> ensure(keys));
        assertTrue(e.getMessage().contains("unexpected exchange"), e.getMessage());
        assertEquals(List.of(), events);
    }

    @Test
    void concurrentCreatorWithOtherKeysFails() {
        when(client.fetchBindings("vh", QUEUE))
                .thenReturn(Optional.empty())
                .thenReturn(bindings(new Binding(EXCHANGE, "a"), new Binding(EXCHANGE, "z")));
        final List<String> keys = List.of("a");
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> ensure(keys));
        assertTrue(e.getMessage().contains("concurrent creator"), e.getMessage());
    }

    @Test
    void concurrentCreatorOnOtherExchangeFails() {
        when(client.fetchBindings("vh", QUEUE))
                .thenReturn(Optional.empty())
                .thenReturn(bindings(new Binding(EXCHANGE, "a"), new Binding("ns.other", "a")));
        final List<String> keys = List.of("a");
        assertThrows(IllegalStateException.class, () -> ensure(keys));
    }

    @Test
    void queueDeletedBeforeReReadIsAccepted() throws Exception {
        when(client.fetchBindings("vh", QUEUE)).thenReturn(Optional.empty());
        ensure(List.of("a"));
        assertEquals(List.of("declare", "bind:a"), events);
    }

    @Test
    void unreadableManagementApiFailsStartup() {
        when(client.fetchBindings(anyString(), eq(QUEUE))).thenThrow(new RuntimeException("connection refused"));
        final List<String> keys = List.of("a");
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> ensure(keys));
        assertTrue(e.getMessage().contains("Unable to read bindings"), e.getMessage());
        assertEquals(List.of(), events);
    }

    @Test
    void defaultsVhostToSlash() throws Exception {
        guard = new BindingGuard(RMQConfig.builder().build(), client);
        when(client.fetchBindings("/", QUEUE)).thenReturn(bindings(new Binding(EXCHANGE, "a")));
        ensure(List.of("a"));
        assertEquals(List.of("declare"), events);
    }

    @Test
    void closeReleasesTheClient() {
        guard.close();
        verify(client).close();
    }

    @Test
    void closeWithoutClientIsNoOp() {
        new BindingGuard(RMQConfig.builder().build(), (RmqManagementClient) null).close();
    }

    @Test
    void disabledDriftCheckMakesNoManagementCalls() throws Exception {
        guard = new BindingGuard(RMQConfig.builder().build(), new ObjectMapper(), false);
        ensure(List.of("a"));
        assertEquals(List.of("declare", "bind:a"), events);
    }

    @Test
    void driftCheckIsOnByDefault() {
        assertFalse(MessagingConfig.builder().build().isSkipBindingDriftCheck());
        assertFalse(new MessagingConfig().isSkipBindingDriftCheck());
    }

    @Test
    void unreadableManagementApiExplainsHowToFixOrOptOut() {
        when(client.fetchBindings(anyString(), eq(QUEUE))).thenThrow(new RuntimeException("connection refused"));
        final List<String> keys = List.of("a");
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> ensure(keys));
        assertTrue(e.getMessage().contains("skipBindingDriftCheck: true"), e.getMessage());
        assertTrue(e.getMessage().contains("connection refused"), e.getMessage());
    }
}
