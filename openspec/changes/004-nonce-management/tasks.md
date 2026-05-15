## 1. RED — Write Tests First

- [x] 1.1 Write NonceManagerTest: first allocate returns nonce 0 (initialized from chain mock returning 0). Second allocate returns 1. Third returns 2.
- [x] 1.2 Write test: confirmNonce(5) → current_nonce updated to 5.
- [x] 1.3 Write test: allocate returns 0,1,2. Release(1). Next allocate returns 1 (gap reuse). Then allocate returns 3.
- [x] 1.4 Write test: resetNonce → reinitializes from chain value, clears gaps.
- [x] 1.5 Write concurrent test: 10 threads call allocateNonce simultaneously → all get unique nonces (0-9), no duplicates, no gaps.
- [x] 1.6 Write NonceGapDetectorTest: allocated nonce older than 5 minutes → detected and moved to gaps set.

## 2. GREEN — Implement to Pass

- [x] 2.1 Implement NonceRedisOperations helper: getPendingNonce, setPendingNonce, addToGaps, popSmallestGap, addToAllocated, removeFromAllocated, getTimedOutAllocations. Use Redisson RBucket, RSortedSet, RScoredSortedSet.
- [x] 2.2 Implement NonceManager.allocateNonce: acquire distributed lock (key="nonce:{chainId}:{address}", lease=5s, wait=10s). Check gaps first (pop smallest). Otherwise increment pending_nonce. Add to allocated set. Update DB.
- [x] 2.3 Implement confirmNonce: update current_nonce = max(current, confirmed) in Redis + DB. Remove from allocated set.
- [x] 2.4 Implement releaseNonce: if nonce == pendingNonce - 1, decrement. Otherwise add to gaps. Remove from allocated set.
- [x] 2.5 Implement resetNonce: fetch chain counts, reset Redis and DB state.
- [x] 2.6 Implement NonceGapDetector @Scheduled(fixedDelay=30000): scan allocated set for entries > 5 min. Move to gaps. Log WARN for each reclaimed nonce.
- [x] 2.7 Run all tests — all pass.

## 3. REFACTOR

- [x] 3.1 Review lock scope — ensure minimal code inside lock. Extract pure logic from locked section if possible.
- [x] 3.2 All tests still pass. mvn test passes.
