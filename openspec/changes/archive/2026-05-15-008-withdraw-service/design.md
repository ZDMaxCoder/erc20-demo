## Context

Withdrawal is the most sensitive operation. A bug here can result in:
- Double spending (sending the same withdrawal twice)
- Fund loss (sending to wrong address or with wrong amount)
- Stuck funds (nonce gap blocks all subsequent withdrawals)

Every state transition must be logged and auditable.

## Goals / Non-Goals

**Goals:**
- Idempotent withdrawal creation (request_id uniqueness)
- Atomic balance freeze on creation
- Strict state machine preventing illegal transitions
- Distributed lock per withdrawal for concurrent safety
- Automatic retry with configurable limits
- Clean failure recovery (unfreeze balance, release nonce)

**Non-Goals:**
- Not implementing the risk control logic here (separate change 010)
- Not implementing withdrawal batching (future optimization)

## Decisions

### State machine with explicit transitions
```
PENDING_REVIEW → APPROVED | REJECTED
APPROVED → SIGNING
SIGNING → BROADCASTING
BROADCASTING → PENDING_CONFIRM
PENDING_CONFIRM → SUCCESS
BROADCASTING | PENDING_CONFIRM → FAILED
FAILED → APPROVED (manual retry)
```
Any operation that doesn't match a valid transition is rejected immediately.
**Why:** Eliminates entire classes of concurrency bugs. Makes behavior predictable and auditable.

### Balance freeze on creation, deduct on success
Create: freeze(amount + fee). Success: decreaseFrozen(amount + fee). Failure/Rejection: unfreeze(amount + fee).
**Why:** Ensures user can't spend the same balance twice. Frozen amount is reserved until final outcome.

### Distributed lock per withdrawal ID
Every state-changing operation acquires lock `withdraw:{id}` first.
**Why:** MQ retry and scheduled retry job may both try to process same withdrawal concurrently.

### MQ-driven execution with scheduled compensation
Approval sends MQ message to execute queue. If message lost, compensation job (every 30s) rescans APPROVED records older than 5 min.
**Why:** At-least-once delivery with idempotent execution. Compensation catches edge cases.

## Risks / Trade-offs

- [Risk] Nonce allocated but broadcast fails → Mitigation: releaseNonce in catch block, compensation
- [Risk] Process crashes between broadcast and DB update → Mitigation: TransactionConfirmTracker will discover the on-chain tx, compensation job reconciles
- [Risk] Unfreeze amount mismatch → Mitigation: store exact frozen amount on withdraw record, always unfreeze from stored value

## Implementation Context

**Depends on:**
```java
// From 006 - WalletService
TransactionRecord sendERC20Transfer(String from, String to, String contract, BigInteger amount, GasPriority priority);
TransactionRecord replaceTransaction(String txHash, boolean cancel);

// From 009 - AccountService
void freeze(AccountOperateRequest request);
void unfreeze(AccountOperateRequest request);
void decreaseFrozen(AccountOperateRequest request);

// From 001 - utilities
AmountUtil.toChainAmount(long minUnit, int amountExponent, int tokenDecimals) -> BigInteger
@DistributedLock annotation

// From 001 - tables
t_withdraw_record: all fields (see schema)
t_wallet_config: HOT wallet address

// MQ
Topic: WITHDRAW_EXECUTE, Tag: APPROVED
Topic: TX_STATUS_CHANGED, for confirmation callbacks
```

**Key outputs:**
```java
WithdrawRecord createWithdraw(WithdrawRequest request); // @Transactional
void approve(long withdrawId, String operator);
void reject(long withdrawId, String operator, String reason);
void executeWithdraw(long withdrawId); // from MQ consumer
void confirmWithdraw(long withdrawId, String txHash, long blockNumber); // @Transactional
void failWithdraw(long withdrawId, String reason); // @Transactional
```
