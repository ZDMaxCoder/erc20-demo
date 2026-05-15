## Why

All balance changes (deposits, withdrawals, freezes) must go through a single service that guarantees: no negative balances, every change has a corresponding flow record, idempotent operations, and replay-verifiable consistency.

## What Changes

- Implement AccountService with atomic balance operations
- Implement account flow recording with before/after snapshots
- Implement optimistic locking with retry
- Implement reconciliation job

## Capabilities

### New Capabilities

- `account-service`: Atomic balance operations (increase, freeze, unfreeze, decrease-frozen)
- `account-flow-service`: Flow recording and querying
- `account-reconcile`: Periodic balance verification via flow replay

## Impact

- erc20-platform-service module
- Core dependency for deposit-service, withdraw-service, collection-service
- Depends on: 001-project-foundation
