## Context

Risk control sits between withdrawal creation and execution. Each withdrawal request is evaluated against multiple rules. Rules can auto-pass, require manual review, or outright reject.

## Goals / Non-Goals

**Goals:**
- Extensible rule engine (add new rules without modifying existing code)
- Redis-based limit tracking for performance
- Configurable thresholds (can change without redeployment)
- Blacklist with both manual and automated management

**Non-Goals:**
- Not implementing ML-based fraud detection
- Not implementing full KYC/AML flow
- Not implementing behavioral analysis

## Decisions

### Chain-of-responsibility pattern for rules
Each rule implements RiskRule interface with check() and order(). Rules execute in order. First REJECT stops evaluation. Any NEED_MANUAL_REVIEW flags the request. All AUTO_PASS means auto-approve.
**Why:** Clean separation of concerns. New rules are just new classes implementing the interface.

### Redis for limit accumulation
Daily limits use Redis keys with TTL. Atomic INCRBY for thread safety. Rollback on withdrawal failure.
**Why:** Redis provides atomic counters with automatic expiration — perfect for sliding window limits.

### Blacklist: Redis Set + DB table
Redis for fast O(1) lookup. DB for persistence and audit trail. Sync on startup and on change.
**Why:** Every withdrawal checks blacklist — must be fast. DB ensures data isn't lost on Redis flush.

## Risks / Trade-offs

- [Risk] Redis limit desync after failure → Mitigation: rollback method called on withdrawal failure
- [Risk] False positive blocks legitimate withdrawal → Mitigation: manual review path, not outright rejection for most rules

## Implementation Context

**Depends on:**
```java
// Withdrawal context for rule evaluation
WithdrawRecord: userId, tokenId, toAddress, amount, amountExponent

// Redis (Redisson) already configured
// t_token_config for per-token configuration
```

**Key outputs:**
```java
RiskResult checkWithdraw(WithdrawRecord record);
// RiskResult: AUTO_PASS / NEED_MANUAL_REVIEW / REJECT (with reason)

boolean isBlacklisted(String address);
void addToBlacklist(String address, String reason, String operator);

boolean checkAndAccumulate(long userId, long tokenId, long amount);
void rollback(long userId, long tokenId, long amount);
```

**Configuration:**
```yaml
risk.withdraw:
  auto-pass-max-amount: 10000
  daily-limit: 100000
  hourly-max-count: 5
  new-address-review: true
  large-amount-threshold: 50000
```
