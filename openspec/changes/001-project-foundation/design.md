## Context

Building a centralized ERC-20 deposit/withdrawal platform from scratch. Need a solid foundation that supports:
- High concurrency (multiple withdrawals broadcasting simultaneously)
- Data consistency (amount precision, idempotency)
- Fault tolerance (retries, compensation, distributed locks)

## Goals / Non-Goals

**Goals:**
- Establish clean multi-module Maven structure
- Design database schema supporting all business flows
- Provide reusable utilities (amount math, locks, retry)

**Non-Goals:**
- No business logic in this change
- No blockchain interaction code
- No API endpoints

## Decisions

### Multi-module structure
```
erc20-platform-common     → utilities, exceptions, constants
erc20-platform-domain     → entities, enums, value objects
erc20-platform-dal        → MyBatis-Plus mappers, Flyway migrations
erc20-platform-service    → business logic
erc20-platform-blockchain → Web3j, chain interaction
erc20-platform-mq         → RocketMQ producers/consumers
erc20-platform-api        → REST API, Spring Boot entry point
erc20-platform-admin      → Admin API
```
**Why:** Clear separation of concerns. Each module has a single responsibility. Dependency direction is strictly upward (api → service → dal → domain → common).

### Amount storage: long + exponent
**Why:** Avoids floating-point precision issues. Enables lossless conversion to/from chain amounts (BigInteger). Example: 12.34 CNY → minUnit=1234, exponent=2.

### Database: Flyway migrations
**Why:** Version-controlled schema changes. Team members can replay migrations to get consistent schema.

### Distributed lock: Redisson
**Why:** Battle-tested Redis-based distributed lock. Supports reentrant locks, fair locks, and configurable lease time.

## Risks / Trade-offs

- [Risk] Long + exponent may overflow for very large amounts → Use BIGINT UNSIGNED (max 18446744073709551615), sufficient for all ERC-20 tokens
- [Risk] Flyway migrations are append-only → Mitigation: careful review before merging, never modify existing migrations

## Implementation Context

**Dependencies (versions managed in parent POM):**
- Spring Boot 2.7.18
- Web3j 4.9.8
- MyBatis-Plus 3.5.3
- RocketMQ Spring Boot Starter 2.2.3
- Redisson 3.23.5
- Guava 32.1.3-jre
- MapStruct 1.5.5.Final
- Lombok

**Key utility interfaces produced by this change (consumed by later changes):**
```java
// AmountUtil
long toMinUnit(BigDecimal humanReadable, int exponent);
BigDecimal toHumanReadable(long minUnit, int exponent);
BigInteger toChainAmount(long minUnit, int amountExponent, int tokenDecimals);
long fromChainAmount(BigInteger chainAmount, int tokenDecimals, int amountExponent);

// IdempotentKeyGenerator
String depositKey(String txHash, int logIndex);    // "txHash_logIndex"
String withdrawKey(String requestId);               // "WD_requestId"
String collectionKey(String fromAddress, long tokenId, long blockNumber);

// @DistributedLock(key, waitTime, leaseTime) — AOP annotation

// Enums: DepositStatus, WithdrawStatus, TxStatus, WalletType, FlowType, FlowDirection, AlertLevel
```
