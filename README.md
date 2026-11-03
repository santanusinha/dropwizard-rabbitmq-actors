# Dropwizard RabbitMQ Actors Bundle
Provides actor abstraction on RabbitMQ for dropwizard based projects.

## Dependency

```
<dependency>
    <groupId>io.appform.dropwizard.actors</groupId>
    <artifactId>dropwizard-rabbitmq-actors</artifactId>
    <version>2.0.28-18</version>
</dependency>
```

## License
Apache-2.0

## Actors vs messaging

* **Actors** (`Actor`, `BaseActor`): point-to-point. One DIRECT exchange, one queue, one handler.
  `actor.publish(msg)` always reaches exactly that actor's queue.
* **Messaging** (`io.appform.dropwizard.actors.messaging`): publish/subscribe over DIRECT, TOPIC and FANOUT
  exchanges. Code publishes a message *to an exchange* through a `MessagePublisher`; the configured queue bindings
  alone decide which queues receive it. Each queue is consumed by a `QueueHandler`, which has no way to publish to
  the exchange it consumes from.

A queue belongs either to an actor or to messaging, never both. Messaging queues use their own prefix
(`rabbitmq.messaging` by default), so they don't collide with actor queues by default. Don't reuse an actor's exchange
or queue name in the messaging config.

Set a `prefix` per service (e.g. `myservice.messaging`). With the shared default, two services configuring the same
queue name would share one broker queue: their consumers would compete for its messages, and with different exchanges
the queue would be bound to both.

Exchange names in the config are logical names used in code. On the broker, exchanges and queues are prefixed with
the namespace (`NAMESPACE_ENV_NAME` / `rmq.actors.namespace`) when one is set, so feature environments sharing a
broker don't receive each other's messages.

### Messaging config

```yaml
messaging:                             # anywhere in your app config; returned from getMessagingConfig
  prefix: myservice.messaging          # default: rabbitmq.messaging; set one per service
  exchanges:
    order_events:
      type: TOPIC                      # DIRECT | TOPIC | FANOUT
      delayed: false                   # x-delayed-message exchange, enables publishWithDelay (see the risk below)
      publisherConfirms: true          # default; publish waits for the broker's confirm
      queues:
        shipping:
          bindingKeys: ["order.*.PLACED"]
          concurrency: 5
        audit:
          bindingKeys: ["order.#"]
          retryLaterEnabled: true      # MessageContext.retryLater redelivers to this queue only
```

#### Binding drift checks (on by default)
RabbitMQ never removes a binding by itself, and AMQP can't list a queue's bindings. So at startup messaging reads each
queue's bindings through the **Management API** and fails if they drifted from the config: changed `bindingKeys`, a
binding to another exchange, or another instance creating the same queue with a different config. Without the checks,
such a queue silently keeps its old bindings and receives messages it should not.

The checks need the `rabbitmq_management` plugin, network access to its port, and a RabbitMQ user (the AMQP
credentials) with a management tag such as `monitoring`. If the API can't be read, startup fails with a message
explaining what is missing. Connection settings are optional, inside your RabbitMQ (`RMQConfig`) section:

```yaml
rabbit:                                # your RMQConfig section
  managementConfig:
    host: rmq-mgmt.internal            # optional; default: each broker host, tried in order until one answers
    port: 15672                        # default
    secure: false                      # https; verified against serverCertStorePath if set, else the JVM trust store
```

If the Management API is not available to your service, opt out explicitly (changed bindings then go undetected):

```yaml
messaging:
  skipBindingDriftCheck: true         # default: false
```

The Management API is only called while messaging starts; its HTTP client is released right after.

### Usage

Enable messaging by overriding `RabbitmqActorBundle.getMessagingConfig`, then register publishers and handlers in
`Application.run`. Registering from a managed object's `start()` is too late: messaging is managed by the bundle and
starts first.

```java
Messaging messaging = bundle.getMessaging();

MessagePublisher<OrderMessage> publisher = messaging.publisher("order_events", OrderMessage::routingKey);

messaging.consumer("order_events", "shipping", OrderMessage.class, (message, context) -> ship(message));
messaging.consumer("order_events", "audit", OrderMessage.class, (message, context) -> {
    if (!ready(message)) {
        context.retryLater(message, Duration.ofMinutes(1));   // this queue only
        return true;
    }
    return audit(message);
});

publisher.publish(message);
```

FANOUT exchanges take no routing key: `messaging.publisher("exchange")`.

`publish`, `publishWithDelay` and `publishWithExpiry` each have an overload taking extra headers (e.g. a
feature-environment header); every queue that receives the message gets them.

Each `publisher(...)` call opens its own channel: register one publisher per exchange (or per message type) and reuse it.
`bundle.getMessaging()` throws if messaging is not configured.

When using `retryLater`, call it as the last step and then `return true`. Calling it and then throwing or returning
`false` produces duplicates (the handler is retried, or the original is also sidelined). Handlers should be idempotent.
A retried message keeps its expiry (`publishWithExpiry`), and retries go through observers like normal publishes.
Observers see a retry under the queue's retry exchange name (`<queue>_RETRY`): with `metricConfig.enabledForAll: false`,
list that name too to get retry metrics.

### Lifecycle
The bundle ties messaging to the server lifecycle, so handlers only run while the whole application is up:

| Phase | Messaging |
|---|---|
| Managed objects start | topology is declared and publishers start **before** the application's managed objects, so they can publish while starting |
| Server started | consumers subscribe, **after** every managed object has started. A failure fails startup |
| Server begins stopping | consumers stop (in-flight messages finish), **before** any managed object stops |
| Managed objects stop | publishers stop **after** the application's managed objects, so they can publish while stopping |

Consumers start on the Jetty server's "started" event, so the HTTP port is already open when handlers start
receiving messages; messages published before that wait in their queues.

When using `Messaging` without the bundle, call `startConsumers()` after `start()`, and `stopConsumers()` before
stopping the resources handlers use. The same applies to commands that run without the Jetty server (e.g. a custom
`EnvironmentCommand`): there is no "server started" event, so consumers don't start unless `startConsumers()` is
called.

Messages are deserialized with Dropwizard's `ObjectMapper`. To let publishers add fields before consumers are updated,
make message classes tolerate unknown properties (`@JsonIgnoreProperties(ignoreUnknown = true)`).

### Rules and guarantees
* **Startup validation.** Startup fails if:
  * no exchange is configured, or an exchange has no queues. Publishing to an exchange without consuming from it in
    the same service is not supported;
  * a DIRECT/TOPIC queue has no binding keys, a FANOUT queue has binding keys, or a binding key is blank, has
    surrounding whitespace or (TOPIC) an empty word;
  * a queue name ends with `_SIDELINE` or `_RETRY`, or an exchange name ends with `_SIDELINE` (reserved for derived
    names);
  * a configured queue has no handler;
  * a publisher or handler refers to an unknown exchange or queue.
* **Topology first.** All exchanges, queues and bindings are declared before any publisher or consumer starts.
* **No silent loss on normal exchanges.** Publishes use `mandatory`. With publisher confirms (the default), a message
  no binding matches fails with `UnroutableMessageException`, and a publish the broker did not accept fails too.
  * Confirms cost a broker round trip per publish, and publishes on one publisher are serialized: see
    [Publish throughput](#publish-throughput). Set `publisherConfirms: false` for fire-and-forget throughput;
    unroutable messages are then only logged and dropped.
  * A confirm timeout can leave the caller unsure whether the broker got the message, so retries can duplicate it.
    Consumers should be idempotent.
  * **Risk with delayed exchanges:** the delayed-message plugin does not support `mandatory`, so a message that no
    binding matches is *not* detected, even with confirms, and is dropped silently.
  * A publisher whose channel gets closed (confirm timeout, broker error) reopens it on the next publish.
  * Routing keys are limited to 255 bytes.
* **Retries stay local.** `retryLater` goes through a private per-queue delayed exchange (`<queue>_RETRY`), so a
  retry never reaches the other queues of the exchange.
* **Sideline.** Each queue dead-letters rejected messages to its own `<queue>_SIDELINE` queue.
* **Consumption** runs on the same consumer as actors: concurrency, prefetch, retry config, exception handling,
  expiry (`publishWithExpiry` / `handleExpired`) and metrics behave identically.
* **Queue arguments** (queue type, lazy mode, quorum group size, auto-delete `ttlConfig`) are built as for actors.
  `ttlConfig` deletes an unused queue (`x-expires`); it is not a message TTL. Priority queues are not supported.
  `haMode`/`haParams` are not supported either: as queue arguments they don't enable mirroring. For replicated queues
  use `queueType: QUORUM`, or a mirroring policy matching the messaging queue names.

### Publish throughput
Publisher confirms reduce publish throughput: each publisher has one publish in flight at a time and waits for the
broker's confirm before the next one. To publish faster:
* **Turn confirms off** for that exchange (`publisherConfirms: false`), accepting that unroutable messages are only
  logged.
* **Use several publishers** for the exchange: each `publisher(...)` call has its own channel, so their publishes
  overlap. Register them in `Application.run` and spread publishes across them:

  ```java
  final List<MessagePublisher<OrderMessage>> publishers = new ArrayList<>();
  for (int i = 0; i < 4; i++) {
      publishers.add(messaging.publisher("order_events", OrderMessage::routingKey));
  }
  // Same key -> same publisher, so messages for one order stay in order.
  publishers.get(Math.floorMod(message.getOrderId().hashCode(), publishers.size())).publish(message);
  ```

  Messages sent through different publishers can arrive out of order; pick the publisher by a key when order matters.

### Changing topology
* Adding queues or exchanges is always safe.
* A queue's binding keys and exchange are fixed once it exists. Changing them fails startup (binding drift checks);
  with `skipBindingDriftCheck: true`, old bindings silently remain. Use a new queue instead.
* Queue arguments (queue type, lazy mode, TTL) and exchange settings (`type`, `delayed`)
  can't be changed on an existing queue/exchange (the broker rejects the redeclare with `406 PRECONDITION_FAILED`).
* Removing a queue from the config does not delete it on the broker; it stays bound and keeps collecting messages.
  Delete or unbind it manually.
* Turning `retryLaterEnabled` off on an existing queue is safe: its binding to `<queue>_RETRY` stays behind (the
  library never unbinds) but nothing publishes to that exchange any more, and the drift check allows it.

### Not supported (yet)
These actor features have no messaging equivalent; use an actor if you need them.
* **Sharding** (`shardCount`): a messaging queue is a single broker queue, scaled with `concurrency`, so ordering is
  not guaranteed across consumers.
* **Sideline processor** (`sidelineProcessorConfig`): rejected messages stay in `<queue>_SIDELINE` until moved back
  manually; nothing reprocesses them automatically.
* **TTL-based delay** (`DelayType.TTL`): delays need the delayed-message plugin (`delayed: true`).
* **Per-message priority** and priority queues.
* **Publishing with both a delay and an expiry** (actors' `publishWithDelayAndExpiry`).
* **Publishing to an exchange without consuming from it** in the same service.

### Requirements
`delayed` and `retryLaterEnabled` need the `rabbitmq_delayed_message_exchange` plugin. The binding drift checks (on by
default) need the `rabbitmq_management` plugin, network access to it and a user with a management tag; see
[Binding drift checks](#binding-drift-checks-on-by-default).

# NOTE
Package and group id has changed from `io.dropwizard.actors` to `io.appfrom.dropwizard.actors` from 1.3.12-1.
