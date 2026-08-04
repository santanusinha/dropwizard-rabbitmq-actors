/*
 * Copyright (c) 2019 Santanu Sinha <santanu.sinha@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.appform.dropwizard.actors.retry.waitstrategies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rholder.retry.WaitStrategy;
import org.junit.jupiter.api.Test;

class WaitStrategyFactoryTest {

    private static final long BASE_WAIT = 1000L;
    // Delegate ignores the attempt and always returns a fixed base wait, so passing null is safe.
    private static final WaitStrategy FIXED_WAIT = attempt -> BASE_WAIT;

    @Test
    void boundedJitterStaysWithinFactorBounds() {
        final WaitStrategy strategy = WaitStrategyFactory.withBoundedJitter(FIXED_WAIT, 0.3);
        for (int i = 0; i < 1000; i++) {
            final long wait = strategy.computeSleepTime(null);
            assertTrue(wait >= 700 && wait <= 1300, "wait out of bounds: " + wait);
        }
    }

    @Test
    void boundedJitterWithZeroFactorReturnsBaseWait() {
        assertEquals(BASE_WAIT, WaitStrategyFactory.withBoundedJitter(FIXED_WAIT, 0.0).computeSleepTime(null));
    }

    @Test
    void boundedJitterNeverExceedsMaxWaitMillis() {
        final long maxWaitMillis = 900L;
        final WaitStrategy strategy = WaitStrategyFactory.withBoundedJitter(FIXED_WAIT, 0.5, maxWaitMillis);
        for (int i = 0; i < 1000; i++) {
            final long wait = strategy.computeSleepTime(null);
            assertTrue(wait >= 0 && wait <= maxWaitMillis, "wait exceeded maxWaitMillis: " + wait);
        }
    }

    @Test
    void boundedJitterCapsUpwardJitterAtMaxWaitMillis() {
        // maxWaitMillis is below the lowest possible jittered value (base 1000 - 20% = 800),
        // so every computed wait must be clamped exactly to the cap.
        final long maxWaitMillis = 500L;
        final WaitStrategy strategy = WaitStrategyFactory.withBoundedJitter(FIXED_WAIT, 0.2, maxWaitMillis);
        for (int i = 0; i < 1000; i++) {
            assertEquals(maxWaitMillis, strategy.computeSleepTime(null),
                    "jittered wait should be capped at maxWaitMillis");
        }
    }

    @Test
    void fullJitterStaysWithinZeroAndBaseWait() {
        final WaitStrategy strategy = WaitStrategyFactory.withFullJitter(FIXED_WAIT);
        for (int i = 0; i < 1000; i++) {
            final long wait = strategy.computeSleepTime(null);
            assertTrue(wait >= 0 && wait <= BASE_WAIT, "wait out of bounds: " + wait);
        }
    }

    @Test
    void jitterIsNoOpForNonPositiveBaseWait() {
        final WaitStrategy zeroWait = attempt -> 0L;
        assertEquals(0L, WaitStrategyFactory.withBoundedJitter(zeroWait, 0.5).computeSleepTime(null));
        assertEquals(0L, WaitStrategyFactory.withFullJitter(zeroWait).computeSleepTime(null));
    }
}

