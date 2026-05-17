## ADDED Requirements

### Requirement: Auto-disable token on Paused event

When `AdminEventMonitor` detects a `Paused` event from a monitored token contract, it SHALL immediately set `TokenConfig.enabled = 0` (disabled) for that token, in addition to firing a CRITICAL alert.

#### Scenario: Paused event triggers auto-disable

- **WHEN** a `Paused` event (topic0 = 0x62e78cea01bee320cd4e420270b5ea74000d11b0c9f74754ebdbfc544b05a258) is detected for token "USDT"
- **THEN** the token's `enabled` field SHALL be updated to `0` in the database
- **THEN** a CRITICAL alert SHALL be fired with type "TOKEN_ADMIN_EVENT"
- **THEN** the alert content SHALL include "auto-disabled"

#### Scenario: Already disabled token receives Paused event

- **WHEN** a `Paused` event is detected for a token that is already disabled (enabled=0)
- **THEN** the system SHALL still fire the alert (idempotent)
- **THEN** no error SHALL be thrown

### Requirement: Auto-disable token on Upgraded event

When `AdminEventMonitor` detects an `Upgraded` event from a monitored token contract, it SHALL immediately set `TokenConfig.enabled = 0` (disabled) for that token, in addition to firing a CRITICAL alert.

#### Scenario: Upgraded event triggers auto-disable

- **WHEN** an `Upgraded` event (topic0 = 0xbc7cd75a20ee27fd9adebab32041f755214dbc6bffa90cc0225b39da2e5c2d3b) is detected for token "USDC"
- **THEN** the token's `enabled` field SHALL be updated to `0` in the database
- **THEN** a CRITICAL alert SHALL be fired with content including "auto-disabled" and the new implementation address

### Requirement: Disabled token blocks new withdrawals

When a token has `enabled = 0`, `WithdrawService` SHALL reject new withdrawal requests for that token with error code `TOKEN_DISABLED`.

#### Scenario: Withdrawal rejected for disabled token

- **WHEN** a user submits a withdrawal request for a token with enabled=0
- **THEN** the system SHALL throw `BizException(ErrorCode.TOKEN_DISABLED)`
- **THEN** no WithdrawRecord SHALL be created
