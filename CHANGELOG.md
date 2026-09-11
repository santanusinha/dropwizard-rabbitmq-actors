# Changelog

All notable changes to this project will be documented in this file.

## 5.0.2-1

- Replaced retry engine: removed `com.github.rholder:guava-retrying:2.0.0`, added `dev.failsafe:failsafe:3.3.2`.
- **Breaking change:** Exhaustion exception type changed from guava's checked `RetryException` to failsafe's unchecked `FailsafeException` (or the raw original throwable). Consumers catching `RetryException` by type will get a compile error — see [MIGRATION.md](MIGRATION.md).
- `RetryStrategy.execute()` signature and `throws Exception` declaration are unchanged. Synchronous blocking behavior is preserved.
- All retry semantics (attempt counts, wait sequences, exception filtering) verified behaviorally identical via 15 parity tests on the failsafe-only tree (49 total tests green).
- See [MIGRATION.md](MIGRATION.md) for migration instructions and version references.

## 2.0.28-14

- Introduced shard ID calculator interface, with random shard id as the default implementation.

## 2.0.28-13

- Detect whether a channel is closed and automatically reopen a channel to avoid no consumers
  running for a queue.

## 2.0.28-12

- Bug fixes and support for delayed in observer

## 2.0.28-11

- Observers and operation level metrics support

## 2.0.28-10

- Support custom replication factor in classic replicated queues
- Support Lazy Queues
- Support quorum queues with custom group size
- Support Classic V2 Queues
- Remove redundant properties passed during exchange creation
- Optimize runtime of tests by introducing a singleton instance of RMQContainer

## 2.0.28-9

Added header forwarding

## 2.0.28-2

- Fixed pending count for sharded queues

## 1.3.18-2

### Added

- BlockedListener for RMQ connections. Logs added to indicate connection blockage.
- Support for separate producer/consumer connections.

### Removed

- Removed `MetricRegistry` from RabbitMQBundle constructor. Registry is now picked from the supplied
`Environment`

### Changed

- Moved `ExecutorServiceProvider` from Bundle constructor to an overridden method. This allows access to config during `ExecutorServiceProvider` construction.
