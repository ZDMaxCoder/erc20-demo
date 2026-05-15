## Why

A financial platform operating on blockchain needs comprehensive monitoring. Operators must know: is block sync keeping up? Are withdrawals stuck? Is the hot wallet running low? Are there reconciliation mismatches? Without monitoring, problems are discovered by users — too late.

## What Changes

- Implement health checks for all external dependencies
- Implement business metrics (Micrometer + Prometheus endpoint)
- Implement AlertService with deduplication and severity levels
- Define all alert scenarios across the platform

## Capabilities

### New Capabilities

- `health-checks`: Custom health indicators for Ethereum node, block sync, etc.
- `business-metrics`: Deposit/withdrawal/collection counters, wallet balances, sync delay
- `alert-service`: Centralized alerting with dedup and persistence

## Impact

- erc20-platform-service module (AlertService)
- erc20-platform-api module (actuator endpoints)
- Depends on: all previous changes (comprehensive monitoring)
