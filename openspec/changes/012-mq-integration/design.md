## Context

The platform uses RocketMQ for inter-module communication. Messages must be delivered at-least-once with idempotent consumption. Message loss (rare but possible) is handled by periodic compensation scans.

## Goals / Non-Goals

**Goals:**
- Single source of truth for all topic/tag names
- Type-safe message DTOs for all message types
- Unified producer with sync send + retry
- BaseConsumer template with idempotency (Redis + DB)
- Compensation job catches messages lost by MQ

**Non-Goals:**
- Not implementing transaction messages (complexity not justified by current needs)
- Not implementing message tracing/tracking UI

## Decisions

### Message ordering
Use orderly messages (by hashKey) only for nonce-sensitive operations. Other messages use concurrent consumption for throughput.
**Why:** Most messages don't need ordering. Over-ordering kills throughput.

### Dual idempotency: Redis + DB
Redis Set (fast check, TTL 24h) + DB unique index (permanent, ultimate guarantee).
**Why:** Redis handles the common case fast. DB catches Redis failures and provides permanent deduplication.

### Compensation interval: 5 minutes
Scan for stuck records (approved but not processing, confirmed but not credited, etc.) every 5 minutes.
**Why:** Balances responsiveness with DB scan cost. 5 minutes is acceptable delay for edge cases.

### Amount fields in messages: String for BigInteger
All BigInteger values serialized as String in JSON messages to avoid precision loss.
**Why:** JSON number type has limited precision. String is safe for arbitrary-precision integers.

## Risks / Trade-offs

- [Risk] Compensation job creates duplicate messages → Mitigation: all consumers are idempotent, duplicates are harmless
- [Risk] Redis idempotency key expires, message replayed → Mitigation: DB unique index catches it

## Implementation Context

**All message types:**
```java
TransferEventMessage: contractAddress, from, to, value(String), txHash, blockNumber, blockHash, logIndex, timestamp
WithdrawExecuteMessage: withdrawId, userId, tokenId, toAddress, amount, amountExponent, requestId
DepositConfirmedMessage: depositId, userId, tokenId, address, amount, amountExponent
TxStatusChangedMessage: txHash, status(CONFIRMED/FAILED), blockNumber, blockHash, bizType, bizId
AlertMessage: alertType, alertLevel, content, source, timestamp
```

**Topics and Tags:**
```
BLOCK_TRANSFER_EVENT: DEPOSIT
WITHDRAW_EXECUTE: APPROVED
DEPOSIT_CONFIRMED: (no tag)
TX_STATUS_CHANGED: CONFIRMED, FAILED
PLATFORM_ALERT: (no tag)
COLLECTION_TASK: (no tag)
```

**Consumer groups:** {module}-{function}-group naming convention.
