## Why

All modules communicate asynchronously via RocketMQ. This change unifies message definitions, provides a consistent producer/consumer framework with built-in idempotency, and adds compensation for message loss scenarios.

## What Changes

- Define all MQ topics, tags, and message DTOs
- Implement unified MqProducer with retry
- Implement BaseConsumer with idempotency and error handling
- Implement compensation job for stuck messages

## Capabilities

### New Capabilities

- `mq-constants`: Centralized topic/tag definitions
- `mq-producer`: Unified message sending with retry
- `base-consumer`: Template method consumer with idempotency
- `mq-compensation`: Periodic scan and re-publish for lost messages

## Impact

- erc20-platform-mq module
- All modules that produce or consume messages depend on this
- Depends on: 001-project-foundation (for message DTOs)
