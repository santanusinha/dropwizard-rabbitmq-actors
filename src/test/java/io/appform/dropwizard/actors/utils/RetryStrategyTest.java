package io.appform.dropwizard.actors.utils;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.WaitStrategies;
import com.github.rholder.retry.WaitStrategy;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

@Slf4j
public class RetryStrategyTest {

    private static @NotNull Attempt getAttempt(int number) {
        return new Attempt() {
            @Override
            public Object get() throws ExecutionException {
                return null;
            }

            @Override
            public boolean hasResult() {
                return false;
            }

            @Override
            public boolean hasException() {
                return false;
            }

            @Override
            public Object getResult() throws IllegalStateException {
                return null;
            }

            @Override
            public Throwable getExceptionCause() throws IllegalStateException {
                return null;
            }

            @Override
            public long getAttemptNumber() {
                return number;
            }

            @Override
            public long getDelaySinceFirstAttempt() {
                return 0;
            }
        };
    }

    @Test
    public void test() {
        WaitStrategy exponentialWait = WaitStrategies.exponentialWait(50, 30, TimeUnit.SECONDS);
        long sleepTime = exponentialWait.computeSleepTime(getAttempt(1));
        log.info("first {}", sleepTime);

        sleepTime = exponentialWait.computeSleepTime(getAttempt(2));
        log.info("second {}", sleepTime);
        sleepTime = exponentialWait.computeSleepTime(getAttempt(3));
        log.info("third {}", sleepTime);
        sleepTime = exponentialWait.computeSleepTime(getAttempt(4));
        log.info("fourth {}", sleepTime);
        sleepTime = exponentialWait.computeSleepTime(getAttempt(5));
        log.info("fifth {}", sleepTime);
    }
}
