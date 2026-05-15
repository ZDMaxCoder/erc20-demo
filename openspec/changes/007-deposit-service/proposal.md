## Why

Users deposit ERC-20 tokens by sending them to their assigned platform addresses. The platform must detect these deposits, wait for sufficient block confirmations, then credit the user's account. This is the core revenue-generating flow.

## What Changes

- Implement user address allocation (HD wallet derivation)
- Implement deposit detection from MQ Transfer events
- Implement confirmation counting and deposit crediting
- Implement minimum amount filtering and reorg handling

## Capabilities

### New Capabilities

- `address-service`: User deposit address allocation and lookup
- `deposit-service`: Deposit detection, confirmation, and crediting
- `deposit-confirm-job`: Periodic confirmation count updates

## Impact

- erc20-platform-service module
- Consumes MQ messages from block-sync-engine (BLOCK_TRANSFER_EVENT:DEPOSIT)
- Calls account-service for balance updates
- Depends on: 001-project-foundation, 003-block-sync-engine
