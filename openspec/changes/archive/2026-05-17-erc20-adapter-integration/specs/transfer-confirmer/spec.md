## ADDED Requirements

### Requirement: TransferConfirmer three-layer verification

`TransferConfirmer` SHALL implement a `confirm(String txHash, String contract, BigInteger expectedAmount, String toAddress)` method that performs three-layer verification and returns a `TransferResult`.

Layer 1 — Receipt status:
- Fetch transaction receipt via `eth_getTransactionReceipt`
- No receipt → return `TransferResult.pending(txHash)`
- receipt.status != "0x1" → return `TransferResult.failed(txHash, "receipt status failed")`

Layer 2 — Transfer event:
- Parse Transfer events from receipt for the given contract
- No Transfer events found → return `TransferResult.failed(txHash, "Transfer event not found")`

Layer 3 — Amount consistency:
- Sum Transfer event values as actualAmount
- Compare actualAmount with expectedAmount
- Match → return TransferResult with outcome=SUCCESS
- Mismatch → return TransferResult with outcome=ANOMALY and anomalyReason

#### Scenario: Successful three-layer verification

- **WHEN** `confirm` is called with a txHash that has receipt status "0x1", contains a Transfer event with value=1000, and expectedAmount=1000
- **THEN** the returned `TransferResult.getOutcome()` SHALL be `SUCCESS`
- **THEN** `TransferResult.getActualAmount()` SHALL equal BigInteger 1000
- **THEN** `TransferResult.isAmountConsistent()` SHALL be `true`

#### Scenario: Transaction not yet mined

- **WHEN** `confirm` is called with a txHash that has no receipt
- **THEN** the returned `TransferResult.getOutcome()` SHALL be `PENDING`
- **THEN** `TransferResult.getBlockNumber()` SHALL be `null`

#### Scenario: Receipt status failed

- **WHEN** `confirm` is called with a txHash that has receipt status "0x0"
- **THEN** the returned `TransferResult.getOutcome()` SHALL be `FAILED`

#### Scenario: Receipt success but no Transfer event

- **WHEN** `confirm` is called with a txHash that has receipt status "0x1" but no Transfer events for the contract
- **THEN** the returned `TransferResult.getOutcome()` SHALL be `FAILED`
- **THEN** `TransferResult.getAnomalyReason()` SHALL contain "Transfer event not found"

#### Scenario: Amount mismatch detected

- **WHEN** `confirm` is called with expectedAmount=1000 and the Transfer event value=990
- **THEN** the returned `TransferResult.getOutcome()` SHALL be `ANOMALY`
- **THEN** `TransferResult.isAmountConsistent()` SHALL be `false`
- **THEN** `TransferResult.getAnomalyReason()` SHALL describe the mismatch

#### Scenario: Multiple Transfer events summed

- **WHEN** `confirm` is called and the receipt contains two Transfer events for the same contract with values 600 and 400, expectedAmount=1000
- **THEN** `TransferResult.getActualAmount()` SHALL be BigInteger 1000
- **THEN** `TransferResult.getOutcome()` SHALL be `SUCCESS`

### Requirement: TransferConfirmer populates TransferResult fields

`TransferConfirmer` SHALL populate `blockNumber`, `txHash`, `actualAmount`, `expectedAmount`, and `events` fields in the returned `TransferResult`.

#### Scenario: Block number populated on success

- **WHEN** `confirm` returns SUCCESS with receipt at block 12345
- **THEN** `TransferResult.getBlockNumber()` SHALL be 12345L

#### Scenario: Events list populated

- **WHEN** `confirm` processes a receipt with Transfer events
- **THEN** `TransferResult.getEvents()` SHALL contain the parsed TransferEvent objects
