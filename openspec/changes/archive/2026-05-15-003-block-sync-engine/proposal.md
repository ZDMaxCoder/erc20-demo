## Why

The platform needs to continuously monitor the Ethereum blockchain for ERC-20 Transfer events to detect user deposits. Block synchronization must handle chain reorganizations to prevent crediting deposits on orphaned blocks.

## What Changes

- Implement sequential block sync engine with configurable polling
- Implement chain reorg detection via parent hash verification
- Implement reorg rollback logic (mark affected deposits as REORGED)
- Extract Transfer events from synced blocks and publish to MQ

## Capabilities

### New Capabilities

- `block-sync-engine`: Sequential block synchronization with progress tracking
- `reorg-handler`: Chain reorganization detection and rollback
- `transfer-event-extractor`: Extract ERC-20 events from blocks and publish to MQ

## Impact

- erc20-platform-blockchain module
- Publishes messages to RocketMQ (BLOCK_TRANSFER_EVENT topic)
- Depends on: 001-project-foundation, 002-erc20-compatibility
