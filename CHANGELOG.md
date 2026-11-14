# Changelog
All notable changes to this project will be documented in this file.

## 2.0.28-19

- Added exchange-centric messaging (`io.appform.dropwizard.actors.messaging`), opt-in and independent of actors:
  publish to DIRECT, TOPIC or FANOUT exchanges with `MessagePublisher`; consume each configured queue with a
  `QueueHandler`. Queue bindings come from config, so which queues receive a message is decided by the broker,
  not by the publisher.
- Publisher confirms by default (5 s timeout); a message no binding matches fails the publish with
  `UnroutableMessageException` (except on delayed exchanges, where the plugin can't report it).
- Per-queue sideline queue and an optional per-queue delayed retry exchange (`MessageContext.retryLater`), so a
  retry is redelivered only to the queue that asked for it.
- The whole config is validated up front and all errors are reported together.
- Binding drift checks, on by default: startup fails on binding drift (changed binding keys, stale exchange bindings,
  concurrent creators with a different config) instead of silently keeping stale bindings. They read bindings through
  the Management API (`rabbitmq_management`, a user with a management tag); opt out with
  `messaging.skipBindingDriftCheck: true`. The API is tried on `managementConfig.host` or each
  broker in turn, https trusts `serverCertStorePath` when set, and the client is released once topology is declared.
- Survives connection loss: topology is redeclared by automatic recovery; publishes fail fast until the connection
  is back, and channels closed by channel-level errors are reopened on the next publish.
- Handlers only run while the whole application is up: messaging consumers start once the server has started (after
  every managed object) and stop as soon as it begins stopping (before any managed object). Topology and publishers
  start before the application's managed objects and stop after them, so the application can publish throughout.
- `retryLater` keeps the message's expiry (`x-expire-at`) and goes through the observer chain (metrics, tracing),
  observed under the queue's retry exchange name (`<queue>_RETRY`).
- Messaging rejects `retryConfig.waitMode: ASYNC`: async retries are dead-lettered back through the exchange with the
  queue name as routing key, so on FANOUT they would reach every bound queue and on TOPIC usually no queue. Use `SYNC`
  or `MessageContext.retryLater`.
- Actors: on top of the existing stop guard, `UnmanagedConsumer` no longer re-creates a consumer channel closed by the
  application (e.g. its connection being closed) or by a connection failure (automatic recovery restores it, so a
  reconnect no longer leaves an extra consumer), and cancels a channel re-created while `stop()` runs. Actor topology
  is unchanged.

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
