## ADDED Requirements

### Requirement: Monitor token contract admin events

The system SHALL monitor enabled token contracts for administrative events including Paused(), Unpaused(), and Upgraded(). When detected, a CRITICAL alert is raised.

#### Scenario: Token contract paused
- **WHEN** a Paused() event is emitted by a monitored token contract
- **THEN** a TOKEN_ADMIN_EVENT CRITICAL alert is raised with details "Token {symbol} paused at block {N}"

#### Scenario: Token contract upgraded (proxy)
- **WHEN** an Upgraded(address) event is emitted by a monitored token contract
- **THEN** a TOKEN_ADMIN_EVENT CRITICAL alert is raised with details "Token {symbol} upgraded to {newImpl} at block {N}"

#### Scenario: Unmonitored contract event
- **WHEN** a Paused() event is emitted by a contract not in the enabled token list
- **THEN** no alert is raised

### Requirement: AlertService supports fine-grained dedup with bizId

`AlertService.alert()` SHALL accept an optional `bizId` parameter. The dedup key becomes `alert:dedup:{type}:{level}:{bizId}`. When bizId is not provided, behavior is unchanged (dedup by type+level only).

#### Scenario: Two alerts same type, different bizId
- **WHEN** alert("STUCK_TX", WARN, "tx1 stuck", "tx1") is called followed by alert("STUCK_TX", WARN, "tx2 stuck", "tx2")
- **THEN** both alerts are created (different bizId means no dedup)

#### Scenario: Duplicate alert same bizId within dedup window
- **WHEN** alert("STUCK_TX", WARN, "tx1 stuck", "tx1") is called twice within the dedup interval
- **THEN** only the first alert is created, the second is deduplicated

#### Scenario: Alert without bizId (backward compatible)
- **WHEN** alert("REORG", CRITICAL, "reorg at block 100") is called (no bizId)
- **THEN** dedup key uses empty bizId (same as current behavior: dedup by type+level)
