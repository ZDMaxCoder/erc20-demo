## Why

Withdrawal operations carry financial risk. Automated risk controls prevent unauthorized withdrawals, enforce limits, and flag suspicious activity for manual review. Without risk control, compromised accounts could drain funds before anyone notices.

## What Changes

- Implement rule engine with chain-of-responsibility pattern
- Implement withdrawal limits (per-transaction, daily, monthly)
- Implement address blacklist
- Implement frequency and new-address detection rules

## Capabilities

### New Capabilities

- `risk-control-service`: Rule engine evaluating withdrawal requests
- `withdraw-limit-service`: Redis-based limit tracking and enforcement
- `address-blacklist`: Blacklist management with Redis + DB persistence

## Impact

- erc20-platform-service module
- Redis keys for limits and blacklist
- Depends on: 001-project-foundation
