## MODIFIED Requirements

### Requirement: TransactionConfirmTracker verifies Transfer event in receipt

When a transaction receipt has status `"0x1"`, the system SHALL parse the receipt logs for a Transfer event matching the expected token contract. If no Transfer event is found, the transaction SHALL be marked as FAILED with reason "Transfer event not found".

Additionally, the system SHALL verify that the transaction has reached a minimum number of block confirmations before marking it as confirmed. If the current block number minus the receipt's block number is less than the token's configured `depositConfirmBlocks`, the transaction SHALL remain in PENDING status.

#### Scenario: Receipt success with Transfer event present and sufficient confirmations
- **WHEN** a transaction receipt has status "0x1" and contains a Transfer event log from the expected contract
- **AND** the current block number minus receipt block number >= token's depositConfirmBlocks
- **THEN** the transaction is marked CONFIRMED and the actualAmount from the Transfer event is included in the status change message

#### Scenario: Receipt success but no Transfer event
- **WHEN** a transaction receipt has status "0x1" but contains no Transfer event log from the expected contract
- **THEN** the transaction is marked FAILED with reason "Transfer event not found in receipt"

#### Scenario: Receipt failed (status 0x0)
- **WHEN** a transaction receipt has status "0x0"
- **THEN** the transaction is marked FAILED (existing behavior, unchanged)

#### Scenario: Receipt success with Transfer event but insufficient confirmations
- **WHEN** a transaction receipt has status "0x1" and contains a Transfer event
- **AND** the current block number minus receipt block number < token's depositConfirmBlocks
- **THEN** the transaction remains in PENDING status (not yet confirmed)

#### Scenario: Token with zero depositConfirmBlocks skips confirmation check
- **WHEN** a token has depositConfirmBlocks = 0 or null
- **THEN** the confirmation count check is skipped and the transaction is confirmed immediately upon receipt success + event presence

### Requirement: TxStatusChangedMessage carries actualAmount

The `TxStatusChangedMessage` SHALL include a nullable `actualAmount` field (BigInteger) representing the value parsed from the Transfer event. This field is null when the transaction failed or when no Transfer event was found.

#### Scenario: Confirmed transaction includes actualAmount
- **WHEN** a transaction is confirmed with a Transfer event containing value 1000000
- **THEN** the TxStatusChangedMessage published to MQ has actualAmount=1000000

#### Scenario: Failed transaction has null actualAmount
- **WHEN** a transaction is marked FAILED
- **THEN** the TxStatusChangedMessage published to MQ has actualAmount=null

### Requirement: Withdrawal confirmation verifies actualAmount

When confirming a withdrawal, if actualAmount is provided in the status message, the system SHALL compare it against the expected withdrawal chain amount. If they differ, the withdrawal SHALL transition to ANOMALY status and a WITHDRAW_AMOUNT_MISMATCH CRITICAL alert SHALL be raised.

#### Scenario: Withdrawal amount matches
- **WHEN** confirmWithdraw is called with actualAmount matching expected chain amount
- **THEN** the withdrawal is confirmed normally with no alert

#### Scenario: Withdrawal amount differs
- **WHEN** confirmWithdraw is called with actualAmount differing from expected chain amount
- **THEN** the withdrawal transitions to ANOMALY status AND a WITHDRAW_AMOUNT_MISMATCH CRITICAL alert is raised

#### Scenario: Withdrawal confirmation without actualAmount
- **WHEN** confirmWithdraw is called with actualAmount=null
- **THEN** the withdrawal transitions to ANOMALY status (null actualAmount is treated as anomalous)
