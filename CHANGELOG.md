# Changelog
All notable changes to this project will be documented in this file.
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
