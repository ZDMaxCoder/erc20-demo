## MODIFIED Requirements

### Requirement: Transaction confirmation uses TransferConfirmer

`TransactionConfirmTracker.checkConfirmation()` SHALL delegate confirmation logic to `TransferConfirmer.confirm()` instead of implementing inline receipt/event verification. The tracker remains responsible for: scheduling (polling), DB updates, nonce confirmation, and MQ publishing.

#### Scenario: TransferConfirmer returns SUCCESS

- **WHEN** `TransferConfirmer.confirm()` returns a `TransferResult` with outcome `SUCCESS`
- **THEN** `TransactionConfirmTracker` SHALL set `TransactionRecord.status` to `TxStatus.CONFIRMED`
- **THEN** it SHALL call `nonceManager.confirmNonce()`
- **THEN** it SHALL publish a `TxStatusChangedMessage` with the actualAmount from TransferResult

#### Scenario: TransferConfirmer returns FAILED

- **WHEN** `TransferConfirmer.confirm()` returns a `TransferResult` with outcome `FAILED`
- **THEN** `TransactionConfirmTracker` SHALL set `TransactionRecord.status` to `TxStatus.FAILED`
- **THEN** it SHALL set `errorMessage` from `TransferResult.getAnomalyReason()`

#### Scenario: TransferConfirmer returns PENDING

- **WHEN** `TransferConfirmer.confirm()` returns a `TransferResult` with outcome `PENDING`
- **THEN** `TransactionConfirmTracker` SHALL NOT update the `TransactionRecord`
- **THEN** the transaction remains in PENDING state for next poll cycle

#### Scenario: TransferConfirmer returns ANOMALY

- **WHEN** `TransferConfirmer.confirm()` returns a `TransferResult` with outcome `ANOMALY`
- **THEN** `TransactionConfirmTracker` SHALL set `TransactionRecord.status` to `TxStatus.CONFIRMED`
- **THEN** it SHALL publish a `TxStatusChangedMessage` with actualAmount from TransferResult
- **THEN** the message SHALL include an anomaly flag so downstream (WithdrawService) can distinguish

### Requirement: TxStatusChangedMessage carries anomaly information

`TxStatusChangedMessage` SHALL include a `boolean anomaly` field and an `anomalyReason` field, so downstream consumers can determine whether the confirmation has an amount discrepancy.

#### Scenario: Normal confirmation message

- **WHEN** TransferResult outcome is SUCCESS
- **THEN** `TxStatusChangedMessage.anomaly` SHALL be `false`

#### Scenario: Anomaly confirmation message

- **WHEN** TransferResult outcome is ANOMALY
- **THEN** `TxStatusChangedMessage.anomaly` SHALL be `true`
- **THEN** `TxStatusChangedMessage.anomalyReason` SHALL contain the reason from TransferResult
