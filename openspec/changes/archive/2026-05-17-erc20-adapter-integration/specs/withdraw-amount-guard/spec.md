## ADDED Requirements

### Requirement: WithdrawStatus ANOMALY state

`WithdrawStatus` enum SHALL include an `ANOMALY` value representing a confirmed-on-chain withdrawal where the actual transfer amount does not match the expected amount.

#### Scenario: ANOMALY status exists

- **WHEN** accessing `WithdrawStatus.ANOMALY`
- **THEN** it SHALL have code "ANOMALY"
- **THEN** it SHALL have description "Amount mismatch detected"

### Requirement: Withdraw confirmation blocks on amount mismatch

When `WithdrawService.doConfirmWithdraw()` processes a confirmed withdrawal and `actualAmount != expectedChainAmount`, the system SHALL set status to `WithdrawStatus.ANOMALY` instead of `WithdrawStatus.SUCCESS`, SHALL NOT call `accountService.decreaseFrozen()`, and SHALL fire a CRITICAL alert.

#### Scenario: Amount matches — normal SUCCESS flow

- **WHEN** a withdrawal is confirmed with actualAmount=1000 and expectedChainAmount=1000
- **THEN** the withdrawal status SHALL be set to `SUCCESS`
- **THEN** `accountService.decreaseFrozen()` SHALL be called

#### Scenario: Amount mismatch — ANOMALY flow

- **WHEN** a withdrawal is confirmed with actualAmount=990 and expectedChainAmount=1000
- **THEN** the withdrawal status SHALL be set to `ANOMALY`
- **THEN** `accountService.decreaseFrozen()` SHALL NOT be called
- **THEN** a CRITICAL alert SHALL be fired with type "WITHDRAW_AMOUNT_MISMATCH"
- **THEN** the alert content SHALL include withdrawId, expectedAmount, actualAmount, and txHash

#### Scenario: Null actualAmount — treat as ANOMALY

- **WHEN** a withdrawal is confirmed but actualAmount is null (Transfer event missing should already be FAILED, but as defense)
- **THEN** the withdrawal status SHALL be set to `ANOMALY`
- **THEN** `accountService.decreaseFrozen()` SHALL NOT be called

### Requirement: State machine allows PENDING_CONFIRM to ANOMALY transition

The withdraw state machine SHALL allow the transition `PENDING_CONFIRM → ANOMALY`.

#### Scenario: Valid transition to ANOMALY

- **WHEN** the current status is `PENDING_CONFIRM` and target is `ANOMALY`
- **THEN** `stateMachine.canTransition()` SHALL return `true`
