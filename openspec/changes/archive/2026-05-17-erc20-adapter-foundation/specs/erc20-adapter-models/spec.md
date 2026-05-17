## ADDED Requirements

### Requirement: ERC20AdapterException hierarchy

The adapter layer SHALL define a root exception `ERC20AdapterException` extending `RuntimeException`, with the following semantic subclasses:
- `TransferRevertedException`: contract execution reverted (with optional revert reason)
- `TokenPausedException`: operation failed because token contract is paused
- `TokenBlacklistedException`: operation failed because address is blacklisted by the token contract
- `AmountMismatchException`: confirmed transfer amount differs from expected amount
- `TransferEventMissingException`: transaction receipt succeeded (status=0x1) but no Transfer event found

#### Scenario: ERC20AdapterException carries message and cause

- **WHEN** an `ERC20AdapterException` is constructed with message "balanceOf failed" and a cause IOException
- **THEN** `getMessage()` SHALL return "balanceOf failed"
- **THEN** `getCause()` SHALL return the IOException

#### Scenario: TransferRevertedException carries contract address and revert reason

- **WHEN** a `TransferRevertedException` is constructed with contract "0xabc" and reason "Pausable: paused"
- **THEN** `getContractAddress()` SHALL return "0xabc"
- **THEN** `getRevertReason()` SHALL return "Pausable: paused"

#### Scenario: AmountMismatchException carries expected and actual amounts

- **WHEN** an `AmountMismatchException` is constructed with expected=1000 and actual=990
- **THEN** `getExpectedAmount()` SHALL return BigInteger 1000
- **THEN** `getActualAmount()` SHALL return BigInteger 990

#### Scenario: TransferEventMissingException carries txHash

- **WHEN** a `TransferEventMissingException` is constructed with txHash "0xdef"
- **THEN** `getTxHash()` SHALL return "0xdef"

### Requirement: TransferOutcome four-state model

`TransferOutcome` SHALL be an enum with exactly four values representing the outcome of a transfer confirmation:
- `SUCCESS`: all verification layers passed, amounts consistent
- `FAILED`: receipt failed or Transfer event missing
- `PENDING`: transaction not yet mined (no receipt)
- `ANOMALY`: receipt succeeded and events found, but amounts inconsistent or other anomaly detected

#### Scenario: All four states are distinct

- **WHEN** iterating `TransferOutcome.values()`
- **THEN** there SHALL be exactly 4 values: SUCCESS, FAILED, PENDING, ANOMALY

### Requirement: TransferResult carries verification evidence

`TransferResult` SHALL be an immutable value object carrying:
- `outcome` (TransferOutcome): the final determination
- `txHash` (String): transaction hash
- `blockNumber` (Long, nullable): block number if mined
- `actualAmount` (BigInteger, nullable): amount from Transfer event
- `expectedAmount` (BigInteger, nullable): expected transfer amount
- `balanceDiff` (BigInteger, nullable): observed balance change
- `anomalyReason` (String, nullable): explanation when outcome=ANOMALY
- `events` (List of TransferEvent, nullable): raw Transfer events from receipt

#### Scenario: SUCCESS result has consistent amounts

- **WHEN** a TransferResult is built with outcome=SUCCESS, actualAmount=1000, expectedAmount=1000
- **THEN** `isAmountConsistent()` SHALL return `true`
- **THEN** `getOutcome()` SHALL return `SUCCESS`

#### Scenario: ANOMALY result with amount mismatch

- **WHEN** a TransferResult is built with outcome=ANOMALY, actualAmount=990, expectedAmount=1000, anomalyReason="fee detected"
- **THEN** `isAmountConsistent()` SHALL return `false`
- **THEN** `getAnomalyReason()` SHALL return "fee detected"

#### Scenario: PENDING result has null blockNumber

- **WHEN** a TransferResult is built with outcome=PENDING, txHash="0xabc"
- **THEN** `getBlockNumber()` SHALL return `null`
- **THEN** `getActualAmount()` SHALL return `null`

#### Scenario: FAILED result from missing event

- **WHEN** `TransferResult.failed(txHash, reason)` is called with txHash="0xdef" and reason="Transfer event not found"
- **THEN** outcome SHALL be FAILED
- **THEN** `getAnomalyReason()` SHALL return "Transfer event not found"

#### Scenario: Balance diff anomaly detection

- **WHEN** a TransferResult has actualAmount=1000 and balanceDiff=990
- **THEN** `hasBalanceDiffAnomaly()` SHALL return `true`

#### Scenario: Null balanceDiff means no anomaly detectable

- **WHEN** a TransferResult has actualAmount=1000 and balanceDiff=null
- **THEN** `hasBalanceDiffAnomaly()` SHALL return `false`

### Requirement: TokenCapability enum

`TokenCapability` SHALL be an enum in `erc20-platform-common` defining token behavior capabilities. It SHALL include at minimum:
- `STANDARD_RETURN`: transfer/approve returns bool
- `NO_RETURN_VALUE`: transfer/approve does not return a value (USDT-like)
- `APPROVE_RACE_CONDITION`: approve must be set to 0 before changing to non-zero
- `BYTES32_METADATA`: name/symbol return bytes32 instead of string
- `PAUSABLE`: contract has pause functionality
- `BLACKLISTABLE`: contract has address blacklist functionality
- `UPGRADEABLE`: contract uses proxy/upgradeable pattern
- `MINTABLE`: token supply can be increased
- `BURNABLE`: token supply can be decreased
- `FEE_ON_TRANSFER`: transfers deduct a fee (actual received < sent)
- `REBASING`: balances change automatically without transfers
- `MAX_TRANSFER_LIMIT`: contract enforces maximum transfer amount
- `COOLDOWN_REQUIRED`: contract enforces minimum time between transfers

#### Scenario: TokenCapability values exist

- **WHEN** accessing `TokenCapability.values()`
- **THEN** it SHALL contain at least 13 values as specified above

#### Scenario: TokenCapability can be used in EnumSet

- **WHEN** creating `EnumSet.of(TokenCapability.NO_RETURN_VALUE, TokenCapability.APPROVE_RACE_CONDITION)`
- **THEN** the set SHALL contain exactly those two values

### Requirement: TokenRiskProfile risk assessment model

`TokenRiskProfile` SHALL be an immutable value object combining:
- `contractAddress` (String): the token contract address (lowercase)
- `capabilities` (Set of TokenCapability): detected/configured capabilities
- `riskLevel` (RiskLevel enum: LOW / MEDIUM / HIGH / CRITICAL): overall risk assessment
- `admissionPassed` (boolean): whether the token passed admission testing
- `lastAuditTime` (LocalDateTime, nullable): when the token was last audited
- `autoProcessingAllowed` (boolean): whether automated processing is permitted

#### Scenario: requiresBalanceDiff for fee-on-transfer tokens

- **WHEN** a TokenRiskProfile has capabilities containing `FEE_ON_TRANSFER`
- **THEN** `requiresBalanceDiff()` SHALL return `true`

#### Scenario: requiresBalanceDiff for HIGH risk tokens

- **WHEN** a TokenRiskProfile has riskLevel=HIGH and does not contain FEE_ON_TRANSFER
- **THEN** `requiresBalanceDiff()` SHALL return `true`

#### Scenario: requiresBalanceDiff false for LOW risk standard token

- **WHEN** a TokenRiskProfile has riskLevel=LOW and capabilities only containing STANDARD_RETURN
- **THEN** `requiresBalanceDiff()` SHALL return `false`

#### Scenario: requiresApproveReset

- **WHEN** a TokenRiskProfile has capabilities containing `APPROVE_RACE_CONDITION`
- **THEN** `requiresApproveReset()` SHALL return `true`

#### Scenario: isStandardProcessing for normal tokens

- **WHEN** a TokenRiskProfile has riskLevel=LOW, no FEE_ON_TRANSFER, no REBASING
- **THEN** `isStandardProcessing()` SHALL return `true`

#### Scenario: isStandardProcessing false for fee-on-transfer

- **WHEN** a TokenRiskProfile has capabilities containing `FEE_ON_TRANSFER`
- **THEN** `isStandardProcessing()` SHALL return `false`

#### Scenario: autoProcessingAllowed controls automation

- **WHEN** a TokenRiskProfile has `autoProcessingAllowed=false`
- **THEN** the system SHALL NOT automatically execute withdrawals or collections for this token

### Requirement: RiskLevel enum

`RiskLevel` SHALL be an enum with four ordered values: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. The ordering SHALL allow comparison (LOW < MEDIUM < HIGH < CRITICAL).

#### Scenario: RiskLevel comparison

- **WHEN** comparing `RiskLevel.MEDIUM.compareTo(RiskLevel.HIGH)`
- **THEN** the result SHALL be negative (MEDIUM < HIGH)

#### Scenario: RiskLevel values count

- **WHEN** accessing `RiskLevel.values()`
- **THEN** there SHALL be exactly 4 values in order: LOW, MEDIUM, HIGH, CRITICAL
