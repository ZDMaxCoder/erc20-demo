## Context

The account service is the single source of truth for user balances. It must be correct under all circumstances — no amount of business logic errors elsewhere should be able to corrupt the ledger.

## Goals / Non-Goals

**Goals:**
- Atomic balance operations in single transactions
- Every balance change produces a flow record (audit trail)
- Idempotent operations (same idempotent_key = no-op)
- Optimistic locking prevents lost updates
- Non-negative balance constraint (enforced in SQL)
- Reconciliation: flow replay must equal current balance

**Non-Goals:**
- Not implementing user-facing balance inquiries (that's REST API layer)
- Not implementing cross-token operations

## Decisions

### Optimistic locking via version field
```sql
UPDATE t_account_balance SET available_balance = available_balance + ?, version = version + 1
WHERE user_id = ? AND token_id = ? AND version = ? AND available_balance + ? >= 0
```
**Why:** No row locks held during transaction preparation. Retry on version conflict (max 3 times). The `>= 0` check in SQL is the ultimate safety net.

### Idempotency via unique index on idempotent_key in t_account_flow
On DuplicateKeyException, treat as success.
**Why:** Double-crediting is worse than any other failure mode. DB unique constraint is the final defense.

### Flow records are append-only
Never update or delete flow records. They are the immutable audit log.
**Why:** Enables reconciliation and forensic investigation.

### Reconciliation = replay all flows from zero
Sum all IN flows minus all OUT flows for a (user_id, token_id) pair. Result must equal current available_balance + frozen_balance.
**Why:** Mathematical proof of correctness. If mismatch, something is deeply wrong — alert immediately.

## Risks / Trade-offs

- [Risk] Optimistic lock contention under high concurrency → Mitigation: 3 retries with small random backoff, sufficient for typical load
- [Risk] Flow table grows unbounded → Mitigation: partition by month, archive old data (future optimization)

## Implementation Context

**Depends on:**
```java
// From 001 - tables
t_account_balance: user_id, token_id, available_balance(long), available_exponent,
  frozen_balance(long), frozen_exponent, version
t_account_flow: user_id, token_id, flow_type, amount(long), amount_exponent,
  direction(IN/OUT), before_balance, after_balance, biz_id, biz_type, idempotent_key(UNIQUE)

// From 001 - enums
FlowType: DEPOSIT, WITHDRAW, WITHDRAW_FEE, FREEZE, UNFREEZE, COLLECTION_FEE, ADJUSTMENT
FlowDirection: IN, OUT
```

**Key outputs (consumed by deposit/withdraw/collection services):**
```java
void increaseAvailable(AccountOperateRequest request);  // deposit crediting
void freeze(AccountOperateRequest request);             // withdraw creation
void unfreeze(AccountOperateRequest request);           // withdraw rejection/failure
void decreaseFrozen(AccountOperateRequest request);     // withdraw success
AccountBalance getBalance(long userId, long tokenId);

@Data @Builder
class AccountOperateRequest {
    long userId, tokenId, amount;
    int amountExponent;
    FlowType flowType;
    String bizId, bizType, idempotentKey;
}
```
