## Context

The platform handles ERC-20 deposits, withdrawals, and collections for a centralized exchange. A production security audit found 20+ vulnerabilities. The most critical: ReorgHandler marks deposits as REORGED but never calls `DepositService.handleReorg()` to reverse credited balances — meaning reorg'd deposits leave phantom money in user accounts. Additionally, TransactionConfirmTracker marks transactions as CONFIRMED based solely on receipt status `"0x1"` without verifying Transfer events actually fired.

**Current state:**
- DepositService.handleReorg() exists but is never called
- RiskControlService is fully implemented but never integrated into WithdrawService
- AmountUtil.fromChainAmount() calls longValueExact() without overflow guard
- MqCompensationJob.compensateStuckDeposits() directly credits deposits without verifying confirmation count
- SafeERC20Caller uses ZERO_ADDRESS for eth_call and defaults decimals to 18 on failure

## Goals / Non-Goals

**Goals:**
- Eliminate all fund-loss risks from reorg, silent transfer failure, and overflow scenarios
- Integrate existing RiskControlService into the withdrawal flow
- Add chain-based reconciliation as a defense-in-depth layer
- Harden SafeERC20Caller for edge-case ERC-20 tokens
- Reject unsupported token types (fee-on-transfer, rebasing) at intake

**Non-Goals:**
- Not implementing automatic transaction replacement (speed-up via higher gas nonce resubmit) — alert only
- Not implementing cross-chain support
- Not implementing fee-on-transfer token support (explicit rejection)
- Not adding ML-based fraud detection
- Not redesigning the amount model (long + exponent stays)

## Decisions

### 1. ReorgHandler delegates to DepositService and WithdrawService for balance reversal

ReorgHandler currently does SQL UPDATE directly on deposit status. Instead, it will collect affected block numbers and call `DepositService.handleReorg(affectedBlockNumbers)` (which already exists and correctly reverses balances). For withdrawals, it will query confirmed WithdrawRecords in the reorg range and call a new `WithdrawService.revertConfirmedWithdraw(withdrawId)` method.

**Why:** DepositService.handleReorg() already implements correct balance reversal with idempotency. Duplicating that logic in ReorgHandler is what caused the bug. For withdrawals, a similar pattern ensures accounting consistency via AccountService.

**Alternative considered:** Having ReorgHandler directly call AccountService — rejected because business logic (status transitions, event publishing) must stay in the service layer.

### 2. TransactionConfirmTracker parses Transfer events from receipt logs

When receipt status is `"0x1"`, the tracker will use `ERC20TransferEventParser` to scan receipt logs for a matching Transfer event. If no Transfer event found, the tx is marked FAILED. The parsed `actualAmount` is included in `TxStatusChangedMessage`.

**Why:** Some ERC-20 tokens return `true` from `transfer()` at the EVM level (no revert) but the Transfer event amount may differ or be absent (e.g., token is paused, transfer returns false via try/catch). Only the event is authoritative.

### 3. AmountUtil throws a checked BizException on overflow instead of ArithmeticException

`fromChainAmount()` and `toChainAmount()` will check `result.bitLength() > 63` before calling `longValueExact()`. This converts an unexpected runtime exception into a handled business error.

**Why:** ArithmeticException from `longValueExact()` is caught by generic `catch(Exception)` blocks throughout the codebase, silently swallowing the overflow. An explicit `AmountOverflowException` can be caught specifically and trigger alerts.

### 4. RiskControlService integrated at createWithdraw() — not at execute time

Risk check happens immediately after freezing balance in `createWithdraw()`. Result determines initial status: AUTO_PASS → APPROVED (+ publish execute message), REJECT → immediate unfreeze + REJECTED status, NEED_MANUAL_REVIEW → PENDING_REVIEW (current behavior).

**Why:** Checking at creation time gives fastest user feedback. Checking at execution time is too late (balance already frozen, user waiting). The existing RiskControlService.checkWithdraw() already evaluates all rules.

**Alternative considered:** Two-phase check (creation + execution) — rejected as over-complex; the risk rules use real-time data that doesn't change meaningfully in the seconds between creation and execution.

### 5. MqCompensationJob verifies on-chain confirmation count instead of force-crediting

`compensateStuckDeposits()` will query the chain for the deposit's tx receipt and current block number, compute actual confirmations, and only credit if confirmations >= `tokenConfig.depositConfirmBlocks`. Otherwise, it resets `updatedAt` to prevent re-triggering and logs a warning.

**Why:** Force-crediting after timeout assumes the tx is confirmed, which is dangerous if the block sync is lagging or the tx was actually dropped.

### 6. TokenConfig gains a `tokenType` field; non-STANDARD types rejected at intake

New enum `TokenType`: STANDARD (default), FEE_ON_TRANSFER, REBASING, UNSUPPORTED. DepositService.onTransferEvent() and CollectionService skip processing for non-STANDARD tokens. The field is set during token onboarding (admin API).

**Why:** Rather than implementing complex balance-diff verification for fee-on-transfer tokens, we explicitly reject them. This is a business decision — the platform only supports standard ERC-20.

### 7. Chain reconciliation as a separate scheduled job

New `ChainReconcileJob` runs daily, queries on-chain balanceOf for all enabled tokens × all managed addresses (hot wallet, user deposit addresses sampled), compares against platform accounting.

**Why:** AccountReconcileJob only verifies internal consistency (flow replay vs balance table). Chain reconciliation catches divergence between platform state and actual on-chain state — the ultimate source of truth.

### 8. AlertService gains optional `bizId` for fine-grained dedup

Current dedup key: `alert:dedup:{type}:{level}`. New key: `alert:dedup:{type}:{level}:{bizId}`. The `bizId` parameter is optional (defaults to empty string for backward compatibility).

**Why:** Current dedup suppresses all alerts of the same type+level, even for different business entities. A stuck transaction alert for tx A should not suppress the alert for tx B.

## Risks / Trade-offs

- [Risk] ReorgHandler calling DepositService creates circular dependency between blockchain and service modules → Mitigation: DepositService interface in service module, implementation injected via Spring. ReorgHandler depends on service module (already in dependency chain: blockchain depends on service via Maven).
- [Risk] TransactionConfirmTracker adding Transfer event parsing increases confirmation latency → Mitigation: Event parsing is in-memory log scanning, negligible cost vs. the 5s polling interval.
- [Risk] Chain reconciliation queries N balanceOf calls per run → Mitigation: Rate-limit to 10 RPC calls/sec, sample user addresses (not all), run at low-traffic time (3 AM).
- [Risk] Rejecting non-STANDARD tokens requires manual classification → Mitigation: Default to STANDARD; admin explicitly marks tokens during onboarding.
- [Risk] Adding `actualAmount` to TxStatusChangedMessage changes MQ message schema → Mitigation: Field is nullable, old consumers that don't read it are unaffected.

## Implementation Context

**Key interfaces from dependencies:**

```java
// DepositService (already exists)
public void handleReorg(List<Long> affectedBlockNumbers);
public void creditDeposit(long depositId);

// WithdrawService (confirmWithdraw to be extended, revertConfirmedWithdraw new)
public void confirmWithdraw(long withdrawId, String txHash, long blockNumber);
// NEW: public void revertConfirmedWithdraw(long withdrawId);

// AccountService operations
public void increaseAvailable(AccountOperateRequest request);
public void decreaseAvailable(AccountOperateRequest request);
public void freeze(AccountOperateRequest request);
public void unfreeze(AccountOperateRequest request);
public void decreaseFrozen(AccountOperateRequest request);

// RiskControlService (already exists, never called)
public RiskResult checkWithdraw(WithdrawRecord record);

// AmountUtil (to be modified)
public static long fromChainAmount(BigInteger chainAmount, int tokenDecimals, int amountExponent);
public static BigInteger toChainAmount(long minUnit, int amountExponent, int tokenDecimals);

// ERC20TransferEventParser (already exists)
public List<TransferEventDTO> parseTransferEvents(List<Log> logs, String contractAddress);

// TxStatusChangedMessage (to be extended)
// Fields: txHash, fromStatus, toStatus, blockNumber, blockHash
// NEW: BigInteger actualAmount (nullable)

// SafeERC20Caller (to be hardened)
public BigInteger safeBalanceOf(String contract, String owner);
public int safeDecimals(String contract);

// GasEstimator (to distinguish revert from network error)
public BigInteger estimateERC20Transfer(String contract, String from, String to, BigInteger amount);

// AlertService (to be extended)
public void alert(String alertType, AlertLevel level, String content);
// NEW overload: public void alert(String alertType, AlertLevel level, String content, String bizId);
```

**Database migration (V9):**
```sql
ALTER TABLE t_token_config ADD COLUMN token_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD';
```

**New ErrorCodes:**
```java
AMOUNT_OVERFLOW(20007, "Amount exceeds system capacity")
TRANSFER_NOT_CONFIRMED(40005, "ERC-20 transfer event not found in receipt")
TOKEN_TYPE_UNSUPPORTED(20008, "Token type not supported")
```

**Module dependency (existing):**
```
common → domain → dal → service → blockchain → mq → api/admin
```
ReorgHandler (blockchain module) can already call DepositService (service module) since blockchain depends on service.
