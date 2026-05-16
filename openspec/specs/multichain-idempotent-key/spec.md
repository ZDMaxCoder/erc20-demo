## Requirements

### Requirement: Deposit idempotent key includes chainId

IdempotentKeyGenerator.depositKey SHALL accept chainId as first parameter and generate keys in format `{chainId}_{txHash}_{logIndex}`.

#### Scenario: Same txHash on different chains produces different keys

- **WHEN** depositKey is called with chainId=1, txHash="0xabc", logIndex=3
- **THEN** the result SHALL be "1_0xabc_3"

#### Scenario: Same txHash and logIndex on chain 56 produces distinct key

- **WHEN** depositKey is called with chainId=56, txHash="0xabc", logIndex=3
- **THEN** the result SHALL be "56_0xabc_3"

### Requirement: Withdraw idempotent key includes chainId

IdempotentKeyGenerator.withdrawKey SHALL accept chainId as first parameter and generate keys in format `WD_{chainId}_{requestId}`.

#### Scenario: Withdraw key with chainId

- **WHEN** withdrawKey is called with chainId=1, requestId="req123"
- **THEN** the result SHALL be "WD_1_req123"

#### Scenario: Same requestId on different chains produces different keys

- **WHEN** withdrawKey is called with chainId=137, requestId="req123"
- **THEN** the result SHALL be "WD_137_req123"

### Requirement: Collection idempotent key includes chainId

IdempotentKeyGenerator.collectionKey SHALL accept chainId as first parameter and generate keys in format `COL_{chainId}_{fromAddress}_{tokenId}_{blockNumber}`.

#### Scenario: Collection key with chainId

- **WHEN** collectionKey is called with chainId=1, fromAddress="0xaddr", tokenId=1, blockNumber=100
- **THEN** the result SHALL be "COL_1_0xaddr_1_100"

#### Scenario: Same address on different chains produces different keys

- **WHEN** collectionKey is called with chainId=56, fromAddress="0xaddr", tokenId=1, blockNumber=100
- **THEN** the result SHALL be "COL_56_0xaddr_1_100"

### Requirement: DepositRecord contains chainId field

DepositRecord entity SHALL have an Integer chainId field mapped to `chain_id` column.

#### Scenario: Deposit record persisted with chainId

- **WHEN** a deposit is processed for a token with chainId=1
- **THEN** the DepositRecord SHALL be saved with chainId=1

### Requirement: WithdrawRecord contains chainId field

WithdrawRecord entity SHALL have an Integer chainId field mapped to `chain_id` column.

#### Scenario: Withdraw record persisted with chainId

- **WHEN** a withdrawal is created for a token with chainId=137
- **THEN** the WithdrawRecord SHALL be saved with chainId=137

### Requirement: Database schema supports chainId on deposit and withdraw tables

Flyway migration SHALL add `chain_id INT NOT NULL` column to both `t_deposit_record` and `t_withdraw_record`.

#### Scenario: Migration adds chain_id to t_deposit_record

- **WHEN** Flyway V10 migration runs
- **THEN** t_deposit_record SHALL have a `chain_id` column of type INT NOT NULL

#### Scenario: Migration adds chain_id to t_withdraw_record

- **WHEN** Flyway V10 migration runs
- **THEN** t_withdraw_record SHALL have a `chain_id` column of type INT NOT NULL
