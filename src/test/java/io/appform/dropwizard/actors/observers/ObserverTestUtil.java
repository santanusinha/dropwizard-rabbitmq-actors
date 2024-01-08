package io.appform.dropwizard.actors.observers;

import lombok.experimental.UtilityClass;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;

@UtilityClass
public class ObserverTestUtil {

    public static final String PUBLISH_START = "publish.start";
    public static final String PUBLISH_END = "publish.end";

    public void validateThreadLocal(final String queueName) {
        assertEquals(queueName, MDC.get(PUBLISH_START));
        assertEquals(queueName, MDC.get(PUBLISH_END));
    }
}
