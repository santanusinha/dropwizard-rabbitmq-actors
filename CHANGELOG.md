# Changelog
All notable changes to this project will be documented in this file.

## [1.3.18-2]
### Added
- BlockedListener for RMQ connections. Logs added to indicate connection blockage.
- Support for separate producer/consumer connections. 

### Removed
- Removed `MetricRegistry` from RabbitMQBundle constructor. Registry is now picked from the supplied
`Environment`

### Changed
- Moved `ExecutorServiceProvider` from Bundle constructor to an overridden method. This allows access to config during `ExecutorServiceProvider` construction.