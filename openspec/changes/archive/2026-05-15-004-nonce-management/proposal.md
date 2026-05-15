## Why

Ethereum transactions require strictly incrementing nonces with no gaps. Under concurrent withdrawal load, multiple transactions must be broadcast simultaneously, each needing a unique nonce. Without centralized nonce management, we get nonce collisions (rejected transactions) or gaps (stuck transactions blocking all subsequent ones).

## What Changes

- Implement atomic nonce allocation with Redis + distributed lock
- Implement nonce confirmation and release mechanisms
- Implement nonce gap detection and auto-repair
- Implement timeout-based nonce reclamation

## Capabilities

### New Capabilities

- `nonce-manager`: Atomic nonce allocation, confirmation, release, and reset
- `nonce-gap-detector`: Periodic detection and repair of nonce gaps

## Impact

- erc20-platform-blockchain module
- Redis keys: nonce:pending:*, nonce:gaps:*, nonce:allocated:*
- Depends on: 001-project-foundation (DistributedLock, t_nonce_record table)
