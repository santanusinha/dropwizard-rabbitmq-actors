package io.appform.dropwizard.actors.actor;

import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.connectivity.strategy.SharedConnectionStrategy;
import io.appform.dropwizard.actors.utils.AsyncOperationHelper;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActorConfigTest {

    @Test
    public void testActorConfigValidationFailedForInvalidConnectionNames()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ActorConfig actorConfig = AsyncOperationHelper.buildActorConfig();
        ((SharedConnectionStrategy) actorConfig.getProducer().getConnectionIsolationStrategy())
                .setName(Constants.DEFAULT_CONSUMER_CONNECTION_NAME);
        Method method = ActorConfig.class.getDeclaredMethod("isCustomConnectionNamesValid");
        boolean valid = (boolean) method.invoke(actorConfig);
        assertFalse(valid);
    }

    @Test
    public void testActorConfigValidationSuccess()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ActorConfig actorConfig = AsyncOperationHelper.buildActorConfig();
        Method method = ActorConfig.class.getDeclaredMethod("isCustomConnectionNamesValid");
        boolean valid = (boolean) method.invoke(actorConfig);
        assertTrue(valid);
    }

}
