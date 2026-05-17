## MODIFIED Requirements

### Requirement: Admin event detection auto-disables token

When `AdminEventMonitor` detects a Paused or Upgraded event from a monitored token contract, it SHALL immediately update the token's `enabled` status to `0` (disabled) in the database AND fire a CRITICAL alert. Previously, only the alert was fired.

#### Scenario: Paused event detected — alert AND disable

- **WHEN** a `Paused` event is detected for token with id=5 and symbol="USDT"
- **THEN** `tokenConfigMapper.updateById()` SHALL be called with `enabled=0`
- **THEN** `alertService.alert("TOKEN_ADMIN_EVENT", CRITICAL, ...)` SHALL be called
- **THEN** the alert content SHALL include "auto-disabled"

#### Scenario: Upgraded event detected — alert AND disable

- **WHEN** an `Upgraded` event is detected for token with id=3 and symbol="USDC"
- **THEN** `tokenConfigMapper.updateById()` SHALL be called with `enabled=0`
- **THEN** `alertService.alert("TOKEN_ADMIN_EVENT", CRITICAL, ...)` SHALL be called
- **THEN** the alert content SHALL include the new implementation address and "auto-disabled"

#### Scenario: Multiple events in same block for same token

- **WHEN** both Paused and Upgraded events are detected for the same token in the same block range
- **THEN** the token SHALL be disabled (idempotent — already 0 on second call)
- **THEN** both alerts SHALL be fired independently
