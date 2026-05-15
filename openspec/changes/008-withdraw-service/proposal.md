## Why

Users need to withdraw their ERC-20 tokens from the platform to external addresses. This is the highest-risk flow — it moves real assets off-platform. It requires strict state machine control, risk review, idempotent execution, and robust failure recovery.

## What Changes

- Implement withdrawal request creation with balance freeze
- Implement approval/rejection flow
- Implement automated withdrawal execution (sign + broadcast)
- Implement retry and stuck transaction handling
- Enforce strict state machine transitions

## Capabilities

### New Capabilities

- `withdraw-service`: Complete withdrawal lifecycle management
- `withdraw-state-machine`: Strict state transition enforcement
- `withdraw-retry-job`: Automatic retry for stuck/failed withdrawals

## Impact

- erc20-platform-service module
- Consumes MQ messages (WITHDRAW_EXECUTE:APPROVED, TX_STATUS_CHANGED)
- Depends on: 006-wallet-service, 009-account-service, 010-risk-control
