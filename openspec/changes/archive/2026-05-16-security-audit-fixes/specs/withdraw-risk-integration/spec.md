## ADDED Requirements

### Requirement: RiskControlService evaluated during withdrawal creation

After freezing the user's balance in `createWithdraw()`, the system SHALL call `RiskControlService.checkWithdraw()` and set the initial withdrawal status based on the result:
- AUTO_PASS → status APPROVED, publish withdraw execute message
- REJECT → unfreeze balance, status REJECTED
- NEED_MANUAL_REVIEW → status PENDING_REVIEW (existing behavior)

#### Scenario: Risk check auto-passes
- **WHEN** a withdrawal is created and RiskControlService returns AUTO_PASS
- **THEN** the withdrawal record is created with status APPROVED and a withdraw execute message is published

#### Scenario: Risk check rejects
- **WHEN** a withdrawal is created and RiskControlService returns REJECT with reason "blacklisted address"
- **THEN** the frozen balance is released, the withdrawal status is set to REJECTED, and the reject reason is stored

#### Scenario: Risk check requires manual review
- **WHEN** a withdrawal is created and RiskControlService returns NEED_MANUAL_REVIEW
- **THEN** the withdrawal status is set to PENDING_REVIEW (no automatic execution)

### Requirement: SIGNING state timeout recovery

Withdrawals stuck in SIGNING status for more than 2 minutes SHALL be automatically reverted to APPROVED for re-execution.

#### Scenario: SIGNING timeout recovery
- **WHEN** a withdrawal has been in SIGNING status for more than 2 minutes
- **THEN** WithdrawRetryJob resets it to APPROVED status (allowing re-execution)

### Requirement: Stuck broadcasting transaction alerts before retry

When a BROADCASTING withdrawal is stuck and the on-chain tx is still PENDING, the system SHALL raise a STUCK_WITHDRAW alert (with bizId = withdrawId) before resetting to APPROVED for retry.

#### Scenario: Broadcasting timeout with pending chain tx
- **WHEN** a withdrawal has been in BROADCASTING for 10+ minutes and the chain tx is still PENDING
- **THEN** a STUCK_WITHDRAW WARN alert is raised and the withdrawal is reset to APPROVED

### Requirement: ERC-20 transfer pre-check via estimateGas

Before sending an ERC-20 transfer (withdrawal or collection), the system SHALL call `estimateGas`. If estimateGas returns an error indicating a revert (not a network timeout), the transfer SHALL NOT be sent, and a descriptive error is raised.

#### Scenario: estimateGas succeeds
- **WHEN** estimateGas returns a valid gas estimate for the ERC-20 transfer
- **THEN** the transaction is built and sent normally

#### Scenario: estimateGas indicates revert
- **WHEN** estimateGas returns an error with "execution reverted" or a revert reason
- **THEN** the transaction is NOT sent, the withdrawal is marked FAILED with reason "Contract call would revert"

#### Scenario: estimateGas network error
- **WHEN** estimateGas fails due to network timeout or connectivity
- **THEN** the system falls back to DEFAULT_ERC20_GAS (80000) and proceeds (existing behavior)
