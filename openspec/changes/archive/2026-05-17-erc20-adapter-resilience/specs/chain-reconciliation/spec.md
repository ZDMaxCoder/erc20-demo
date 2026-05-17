## MODIFIED Requirements

### Requirement: Daily on-chain balance reconciliation

The system SHALL run a daily scheduled job (3 AM) that queries on-chain ERC-20 balances for all enabled tokens and managed wallets, comparing them against the platform's expected balances. The on-chain balance query SHALL be performed through the `ERC20Adapter.balanceOf` interface, ensuring consistency with the adapter layer's address normalization and error handling.

#### Scenario: Chain balance matches platform accounting
- **WHEN** the hot wallet's on-chain balance of token X equals the platform's expected balance for that wallet
- **THEN** no alert is raised and the check passes

#### Scenario: Chain balance diverges from platform accounting
- **WHEN** the hot wallet's on-chain balance of token X differs from the platform's expected balance by more than a configurable threshold
- **THEN** a CHAIN_BALANCE_MISMATCH CRITICAL alert is raised with details (wallet, token, expected, actual, diff)

#### Scenario: balanceOf RPC call fails
- **WHEN** the on-chain balanceOf call fails for a specific token/address combination
- **THEN** that pair is skipped, a WARN alert is raised, and reconciliation continues with remaining pairs

#### Scenario: Disabled token is still reconciled
- **WHEN** a token has `enabled=0` (disabled by AdminEventMonitor or manual action)
- **THEN** the reconciliation job still queries its on-chain balance (ERC20Adapter.balanceOf does not check admission for read operations)

### Requirement: MqCompensationJob verifies confirmation count before crediting

`compensateStuckDeposits()` SHALL NOT directly call `creditDeposit()`. Instead, it SHALL query the chain for the transaction's current confirmation count and only credit if confirmations >= the token's `depositConfirmBlocks`. If insufficient confirmations, it resets `updatedAt` to prevent immediate re-trigger.

#### Scenario: Stuck deposit with sufficient confirmations
- **WHEN** a deposit has been CONFIRMING for 30+ minutes and the chain shows confirmations >= depositConfirmBlocks
- **THEN** the deposit is credited normally

#### Scenario: Stuck deposit with insufficient confirmations
- **WHEN** a deposit has been CONFIRMING for 30+ minutes but the chain shows fewer confirmations than required
- **THEN** the deposit is NOT credited, updatedAt is refreshed, and a DEPOSIT_STUCK WARN alert is raised

#### Scenario: Stuck deposit with no receipt on chain
- **WHEN** a deposit has been CONFIRMING for 30+ minutes and no receipt is found on chain for its txHash
- **THEN** the deposit is NOT credited, a DEPOSIT_TX_MISSING CRITICAL alert is raised

### Requirement: DepositConfirmJob uses pagination

The DepositConfirmJob SHALL limit its query to 500 records per batch to prevent memory exhaustion.

#### Scenario: Large number of confirming deposits
- **WHEN** there are 1000+ deposits in CONFIRMING status
- **THEN** the job processes them in batches of 500

### Requirement: ChainReconcileJob uses ERC20Adapter as single entry point

`ChainReconcileJob` SHALL depend on `ERC20Adapter` (not `SafeERC20Caller` directly) for all on-chain balance queries. This ensures address normalization, error handling, and future metadata caching are applied consistently.

#### Scenario: Reconcile job calls adapter for balance
- **WHEN** ChainReconcileJob queries balance for token 0xABC on wallet 0xDEF
- **THEN** the call goes through `ERC20Adapter.balanceOf("0xABC", "0xDEF")`
- **AND** the address is normalized to lowercase internally

#### Scenario: Adapter exception during reconcile is handled gracefully
- **WHEN** `ERC20Adapter.balanceOf` throws `ChainCallException` for a specific token
- **THEN** that token is skipped with a WARN alert and remaining tokens continue
