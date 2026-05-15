## Context

Ethereum nonce rules:
- Each account's transactions must use consecutive nonces starting from 0
- A transaction with nonce N cannot be mined until nonce N-1 is mined
- A gap in nonces blocks all subsequent transactions
- Same nonce can only be used once (or used to replace a pending transaction)

In our platform, the hot wallet may broadcast 10+ withdrawals concurrently, each needing its own nonce.

## Goals / Non-Goals

**Goals:**
- Atomic nonce allocation under high concurrency (no duplicates)
- Fast nonce recovery on transaction failure (release back to pool)
- Auto-detect and fill nonce gaps
- Timeout reclamation for abandoned nonces

**Non-Goals:**
- Not handling multiple hot wallets in this change (but design supports it)
- Not implementing the actual gap-filling transactions (that's in wallet-service)

## Decisions

### Redis as primary nonce state, DB as backup
**Why:** Redis provides atomic operations and sub-millisecond latency needed for concurrent allocation. DB provides persistence for recovery after Redis flush.

### Distributed lock per wallet address
Lock key: `nonce:{chainId}:{address}`. LeaseTime=5s, WaitTime=10s.
**Why:** Prevents two threads from reading the same pending_nonce and allocating the same value.

### Gap tracking via Redis Set
Gaps are stored in `nonce:gaps:{chainId}:{address}`. When allocating, check gaps first — if non-empty, pop the smallest gap nonce instead of incrementing pending_nonce.
**Why:** Ensures gaps get filled before allocating new nonces, preventing unbounded gap growth.

### 5-minute timeout reclamation
Allocated nonces tracked in Sorted Set with timestamp as score. If not confirmed or explicitly released within 5 minutes, automatically moved to gaps set.
**Why:** Handles crash scenarios where the process dies after allocating a nonce but before broadcasting.

## Risks / Trade-offs

- [Risk] Redis crash loses nonce state → Mitigation: on Redis miss, reinitialize from chain (eth_getTransactionCount "pending")
- [Risk] Lock timeout during high contention → Mitigation: keep lock scope minimal (just read+increment), 10s wait is generous

## Implementation Context

**Depends on:**
```java
// From 001 - DistributedLock
@DistributedLock(key = "'nonce:' + #chainId + ':' + #walletAddress", waitTime = 10000, leaseTime = 5000)

// From 001 - Database table
t_nonce_record: chain_id, wallet_address, current_nonce, pending_nonce (UNIQUE: chain_id + wallet_address)

// Web3j calls needed
web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST)
```

**Redis key design:**
```
nonce:pending:{chainId}:{address}     → String (current pending nonce value)
nonce:gaps:{chainId}:{address}        → Set (known gap nonce values)
nonce:allocated:{chainId}:{address}   → Sorted Set (score=timestamp, value=nonce)
```

**Key outputs:**
```java
long allocateNonce(int chainId, String walletAddress);
void confirmNonce(int chainId, String walletAddress, long confirmedNonce);
void releaseNonce(int chainId, String walletAddress, long nonce);
void fixNonceGaps(int chainId, String walletAddress);
void resetNonce(int chainId, String walletAddress);
```
