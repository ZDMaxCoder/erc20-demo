## ADDED Requirements

### Requirement: ReorgHandler reverses credited deposit balances

When a chain reorganization is detected, the system SHALL call `DepositService.handleReorg()` with the list of affected block numbers to reverse any already-credited deposit balances. The direct SQL status update of deposit records SHALL be removed from ReorgHandler.

#### Scenario: Reorg reverts a credited deposit
- **WHEN** a reorg is detected with fork point at block 100 and a deposit at block 101 was already credited (status=SUCCESS, credited=1)
- **THEN** the system calls `DepositService.handleReorg([101])` which decreases the user's available balance by the deposit amount and marks the deposit as REORGED

#### Scenario: Reorg with no credited deposits
- **WHEN** a reorg is detected and all affected deposits are still in CONFIRMING status (not yet credited)
- **THEN** the deposits are marked REORGED but no balance adjustment occurs

### Requirement: ReorgHandler reverts confirmed withdrawals

When a chain reorganization is detected, the system SHALL query WithdrawRecords that were confirmed (status=SUCCESS) at block numbers above the fork point and revert them by calling `WithdrawService.revertConfirmedWithdraw(withdrawId)`.

#### Scenario: Reorg reverts a confirmed withdrawal
- **WHEN** a reorg is detected with fork point at block 100 and a withdrawal at block 102 has status SUCCESS
- **THEN** the system calls `WithdrawService.revertConfirmedWithdraw()` which transitions the withdrawal back to BROADCASTING status and restores the frozen balance (does not unfreeze — the withdrawal will be re-confirmed when the replacement block arrives)

#### Scenario: Reorg with no confirmed withdrawals in range
- **WHEN** a reorg is detected and no withdrawals have status SUCCESS with blockNumber > forkPoint
- **THEN** no withdrawal reversal occurs

### Requirement: ReorgHandler resets confirmed transaction records

When a chain reorganization is detected, the system SHALL reset TransactionRecord entries with status CONFIRMED and blockNumber > forkPoint back to PENDING status.

#### Scenario: Confirmed transaction reset during reorg
- **WHEN** a reorg is detected at fork point 100 and a TransactionRecord at block 101 has status CONFIRMED
- **THEN** the TransactionRecord status is reset to PENDING, blockNumber and blockHash are cleared
