package io.appform.dropwizard.actors.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.router.data.ActionMessage;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Getter
public class HierarchicalRouterTestHelper {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RetryStrategyFactory retryStrategyFactory = new RetryStrategyFactory();
    private final ExceptionHandlingFactory exceptionHandlingFactory = new ExceptionHandlingFactory();
    public final Set<Class<?>> droppedExceptionTypes = Set.of();

    @SneakyThrows
    private RMQConnection getRmqConnections() {
        RMQConnection rmqConnection = mock(RMQConnection.class);
        doReturn(new RMQConfig()).when(rmqConnection).getConfig();
        val mockChannel = mock(Channel.class);
        doReturn(mockChannel).when(rmqConnection).newChannel();
        doReturn(mockChannel).when(rmqConnection).channel();
        doNothing().when(rmqConnection).ensure(anyString(), anyString(), anyMap());
        doNothing().when(rmqConnection).ensure(anyString(), anyString(), anyString(), anyMap());
        doNothing().when(rmqConnection).ensure(anyString(), anyString(), any(Map.class));
        doAnswer(invocation -> {
            byte[] string = invocation.getArgument(3, byte[].class);
            val exchangeName = invocation.getArgument(0, String.class);
            // val worker = HierarchicalOperationRouter.workerStore.getRouter(exchangeName);
            val actionMessage = new ObjectMapper().readValue(string, ActionMessage.class);
            actionMessage.setExchangeName(exchangeName);
            // worker.processIT(actionMessage);
            return null;
        }).when(mockChannel).basicPublish(anyString(), anyString(), any(), any());
        doReturn(new TerminalRMQObserver()).when(rmqConnection).getRootObserver();
        return rmqConnection;
    }

    public ConnectionRegistry getConnectionRegistry() {
        val rmqConnection = getRmqConnections();
        val connectionRegistry = Mockito.mock(ConnectionRegistry.class);
        when(connectionRegistry.createOrGet(any())).thenReturn(rmqConnection);
        return connectionRegistry;
    }


}
