# Dropwizard RabbitMQ Actors Bundle
Provides actor abstraction on RabbitMQ for dropwizard based projects.

## Dependency

```
<dependency>
    <groupId>io.appform.dropwizard.actors</groupId>
    <artifactId>dropwizard-rabbitmq-actors</artifactId>
    <version>2.0.28-15</version>
</dependency>
```

## License
Apache-2.0

## ASYNC retry mode

The retry machinery was refactored to decouple it from Guava's `RetryerBuilder`:

- **RetryPolicy** abstracts Guava's `RetryerBuilder`, providing full access to the individual Stop, Wait and other internal strategies. All implementations of `RetryStrategy` now only build a `RetryPolicy`; `RetryBuilder` is deprecated.
- **ActorRetryer** is a new interface used by `RetryStrategy` to invoke the consumer handler function.
- **WaitMode** `[SYNC, ASYNC]` indicates whether waiting for the next retry attempt is synchronous or asynchronous.

### WaitMode::SYNC

Current behaviour, works as is. Uses the same Guava `Retryer<>` and blocks the consumer thread till the next retry.

### WaitMode::ASYNC

The call goes through a special implementation of `ActorRetryer` called **AsyncRetryer**:

- Stands as the default ASYNC implementation for `ActorRetryer`. Takes in an implementation of `AsyncRetryScheduler`. If a handler process attempt fails, it evaluates the current `RetryPolicy` to check whether another attempt is allowed. It then schedules another attempt with the help of `AsyncRetryScheduler` and ACKs the current message to free up the consumer.
- **TtlRequeueRetryScheduler** is the provided implementation of `AsyncRetryScheduler`. It provides detached attempt scheduling via DeadLetterExchange based DELAY queues, each of which dead-letters into the original main queue.
- **PublishingChannel** is used by `TtlRequeueRetryScheduler` to perform raw but observed queue publishes. The observer and publishing flows were extracted from `UnmanagedBasePublisher` to keep the commonality in one class. `UnmanagedBasePublisher` could not be used directly since it is mainly meant for Actor based flows.

All the above changes are directly overrideable just from extending a single entry point: `RetryStrategyFactory`. The consumer API of the Actor remains intact.

# NOTE
Package and group id has changed from `io.dropwizard.actors` to `io.appfrom.dropwizard.actors` from 1.3.12-1.
