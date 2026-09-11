# Migration: Retry Engine Swap (guava-retrying → failsafe)

This release replaces the unmaintained `com.github.rholder:guava-retrying:2.0.0` with `dev.failsafe:failsafe:3.3.2` as the retry engine. All retry semantics (attempt counts, wait sequences, exception filtering, synchronous blocking) are behaviorally identical — verified by 15 parity tests on the failsafe-only tree (49 total tests green).

## What changed

- The exhaustion exception type changed from guava's checked `com.github.rholder.guava.retrying.RetryException` to failsafe's unchecked `dev.failsafe.FailsafeException` (which extends `RuntimeException`), or the raw original throwable if it's an `Error`.
- `RetryException` is no longer on the classpath — `catch (RetryException e)` will not compile.

## What did NOT change

- `RetryStrategy.execute(Callable<Boolean>)` retains its `throws Exception` declaration. The signature is byte-identical to the pre-migration API.
- Synchronous blocking behavior is unchanged — `Failsafe.with(policy).get()` blocks the calling thread, same as guava's `Retryer.call()`.
- All retry semantics (attempt counts, sleep sequences, exception filtering) are behaviorally identical, proven by 15 parity tests on the failsafe-only tree (49 total tests green).

## What to do

If your code catches `RetryException` by type:

```java
// Before (broken — RetryException no longer exists)
try {
    strategy.execute(callable);
} catch (RetryException e) {
    // handle exhaustion
}

// After — execute() already declares throws Exception
try {
    strategy.execute(callable);
} catch (Exception e) {
    // handle exhaustion (FailsafeException wraps the last failure)
}
```

If you already catch `Exception` or `Throwable` around `execute()`, no change needed — your existing handler covers `FailsafeException`.

> **Note:** `Handler.handleDelivery()` (the internal call site) catches `Throwable`, so internal behavior is unaffected — only consumer-side typed catches break.

## Version references

- Removed: `com.github.rholder:guava-retrying:2.0.0`
- Added: `dev.failsafe:failsafe:3.3.2`
