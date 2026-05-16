## ADDED Requirements

### Requirement: TransactionConfirmTracker verifies Transfer event in receipt

When a transaction receipt has status `"0x1"`, the system SHALL parse the receipt logs for a Transfer event matching the expected token contract. If no Transfer event is found, the transaction SHALL be marked as FAILED with reason "Transfer event not found".

#### Scenario: Receipt success with Transfer event present
- **WHEN** a transaction receipt has status "0x1" and contains a Transfer event log from the expected contract
- **THEN** the transaction is marked CONFIRMED and the actualAmount from the Transfer event is included in the status change message

#### Scenario: Receipt success but no Transfer event
- **WHEN** a transaction receipt has status "0x1" but contains no Transfer event log from the expected contract
- **THEN** the transaction is marked FAILED with reason "Transfer event not found in receipt"

#### Scenario: Receipt failed (status 0x0)
- **WHEN** a transaction receipt has status "0x0"
- **THEN** the transaction is marked FAILED (existing behavior, unchanged)

### Requirement: TxStatusChangedMessage carries actualAmount

The `TxStatusChangedMessage` SHALL include a nullable `actualAmount` field (BigInteger) representing the value parsed from the Transfer event. This field is null when the transaction failed or when no Transfer event was found.

#### Scenario: Confirmed transaction includes actualAmount
- **WHEN** a transaction is confirmed with a Transfer event containing value 1000000
- **THEN** the TxStatusChangedMessage published to MQ has actualAmount=1000000

#### Scenario: Failed transaction has null actualAmount
- **WHEN** a transaction is marked FAILED
- **THEN** the TxStatusChangedMessage published to MQ has actualAmount=null

### Requirement: Withdrawal confirmation verifies actualAmount

When confirming a withdrawal, if actualAmount is provided in the status message, the system SHALL compare it against the expected withdrawal chain amount. If they differ, the system SHALL raise a WITHDRAW_AMOUNT_MISMATCH alert but still confirm the withdrawal (since fee-on-transfer tokens are rejected, any mismatch is anomalous but not expected).

#### Scenario: Withdrawal amount matches
- **WHEN** confirmWithdraw is called with actualAmount matching expected chain amount
- **THEN** the withdrawal is confirmed normally with no alert

#### Scenario: Withdrawal amount differs
- **WHEN** confirmWithdraw is called with actualAmount differing from expected chain amount
- **THEN** the withdrawal is confirmed AND a WITHDRAW_AMOUNT_MISMATCH CRITICAL alert is raised

#### Scenario: Withdrawal confirmation without actualAmount (backward compatibility)
- **WHEN** confirmWithdraw is called with actualAmount=null
- **THEN** the withdrawal is confirmed normally (no comparison possible)
