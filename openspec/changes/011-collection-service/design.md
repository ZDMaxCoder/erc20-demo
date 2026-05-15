## Context

User deposit addresses hold tokens but need ETH to pay gas for outgoing transfers. Collection flow:
1. Check if address balance exceeds collection threshold
2. Estimate gas needed for ERC-20 transfer
3. Send ETH from hot wallet to user address (gas supply)
4. Wait for gas supply tx confirmation
5. Send ERC-20 transfer from user address to hot wallet
6. Wait for collection tx confirmation

## Goals / Non-Goals

**Goals:**
- Threshold-based collection (only collect when worthwhile)
- Two-step execution with state tracking
- Concurrency control (don't overwhelm hot wallet nonce)
- Hot wallet balance monitoring with cold wallet alerts

**Non-Goals:**
- Not implementing cold→hot transfer automation (requires manual approval)
- Not implementing multi-signature for cold wallet operations

## Decisions

### Token bucket rate limiting for collection
Max N concurrent collection tasks (configurable, default 20). Prevents hot wallet nonce queue from growing too large.
**Why:** Each collection needs 1-2 nonces from hot wallet. 50 concurrent collections = 50-100 pending nonces, too risky.

### Two independent transactions per collection
Gas supply and token transfer are separate transactions. Each has its own state tracking.
**Why:** Different signers (hot wallet signs gas supply, user address signs token transfer). Failure of one doesn't necessarily mean failure of the other.

### Triggered by deposit confirmation + periodic scan
Primary trigger: MQ message when deposit confirmed. Backup: scheduled scan every 4 hours.
**Why:** Responsive to new deposits while still catching any missed collections.

## Risks / Trade-offs

- [Risk] Gas supplied but collection fails → ETH stranded on user address → Mitigation: retry collection, ETH not lost just temporarily parked
- [Risk] Hot wallet drained by too many gas supplies → Mitigation: balance check before each supply, alert when below threshold

## Implementation Context

**Depends on:**
```java
// From 006 - WalletService
TransactionRecord sendERC20Transfer(String from, String to, String contract, BigInteger amount, GasPriority priority);
TransactionRecord sendEthTransfer(String from, String to, BigInteger amountWei, GasPriority priority);

// From 002 - SafeERC20Caller
BigInteger safeBalanceOf(String contract, String owner);

// From 005 - GasEstimator
BigInteger estimateERC20Transfer(String contract, String from, String to, BigInteger amount);

// From 001 - tables
t_collection_task: from_address, to_address, token_id, amount, amount_exponent, tx_hash, status, trigger_type
t_wallet_config: wallet_type(HOT), address
t_user_address: all user deposit addresses

// MQ
Topic: DEPOSIT_CONFIRMED (trigger collection check)
```

**Collection task state machine:**
```
PENDING → GAS_SUPPLYING → GAS_CONFIRMED → COLLECTING → SUCCESS
Any step failure → FAILED (retryable)
```

**Configuration:**
```yaml
collection:
  enabled: true
  thresholds: {USDT: 100000000, USDC: 100000000}
  gas-buffer-multiplier: 1.5
  target-address: "0x..."
  batch-size: 20
  min-interval-hours: 4
```
