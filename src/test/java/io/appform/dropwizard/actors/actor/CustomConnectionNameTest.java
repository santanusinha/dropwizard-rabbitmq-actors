package io.appform.dropwizard.actors.actor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.connectivity.strategy.SharedConnectionStrategy;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

public class CustomConnectionNameTest {

    @Test
    public void testSharedConnectionStrategyValidationFailedForInvalidConnectionNames()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        final var sharedConnectionStrategy = SharedConnectionStrategy.builder()
                .name(Constants.DEFAULT_CONSUMER_CONNECTION_NAME)
                .build();

        final var method = SharedConnectionStrategy.class.getDeclaredMethod("isCustomConnectionNamesValid");
        boolean valid = (boolean) method.invoke(sharedConnectionStrategy);
        assertFalse(valid);
    }


    @Test
    public void testSharedConnectionStrategyValidationSuccess()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        final var sharedConnectionStrategy = SharedConnectionStrategy.builder()
                .name("random-connection-name")
                .build();

        final var method = SharedConnectionStrategy.class.getDeclaredMethod("isCustomConnectionNamesValid");
        boolean valid = (boolean) method.invoke(sharedConnectionStrategy);
        assertTrue(valid);
    }

}
