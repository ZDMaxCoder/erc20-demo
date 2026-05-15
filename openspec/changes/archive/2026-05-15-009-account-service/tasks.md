## 1. RED — Core Balance Operations

- [x] 1.1 Write AccountServiceTest.increaseAvailable: initial balance 0, increase by 1000 → available=1000. Flow recorded with direction=IN, before=0, after=1000.
- [x] 1.2 Write test: increaseAvailable with same idempotentKey twice → balance only increased once (idempotent).
- [x] 1.3 Write test: freeze 500 from available 1000 → available=500, frozen=500. Flow recorded type=FREEZE.
- [x] 1.4 Write test: freeze 1500 from available 1000 → BizException "insufficient balance".
- [x] 1.5 Write test: unfreeze 500 from frozen 500 → available=1000, frozen=0. Flow type=UNFREEZE.
- [x] 1.6 Write test: decreaseFrozen 500 from frozen 500 → frozen=0, available unchanged. Flow type=WITHDRAW.
- [x] 1.7 Write test: getBalance for non-existent account → creates with zeros (lazy init).
- [x] 1.8 Write concurrent test: two threads both freeze 600 from available 1000 → exactly one succeeds (optimistic lock prevents oversell).

## 2. GREEN — Core Balance Operations

- [x] 2.1 Define AccountOperateRequest DTO: userId, tokenId, amount, amountExponent, flowType, bizId, bizType, idempotentKey.
- [x] 2.2 Implement increaseAvailable @Transactional: idempotent check → read balance → optimistic lock update → record flow. Retry up to 3 times on version conflict.
- [x] 2.3 Implement freeze @Transactional: verify available >= amount → atomic update (available-=amount, frozen+=amount, version++) with WHERE available-amount>=0 → record flow.
- [x] 2.4 Implement unfreeze @Transactional: frozen-=amount, available+=amount, WHERE frozen-amount>=0 → record flow.
- [x] 2.5 Implement decreaseFrozen @Transactional: frozen-=amount, WHERE frozen-amount>=0 → record flow.
- [x] 2.6 Implement getBalance: query or create with zeros.
- [x] 2.7 Run all tests — all pass.

## 3. RED — Flow Service & Reconciliation

- [x] 3.1 Write AccountFlowServiceTest.verifyBalance: create account, perform increase(1000), freeze(300), unfreeze(100), decrease(200). Verify flow replay = available + frozen = 1000 - 300 + 100 + (300 - 100 - 200) = 600 + 0 = ... verify mathematical consistency.
- [x] 3.2 Write test: tamper with balance directly (simulate bug) → verifyBalance returns false.
- [x] 3.3 Write test: queryFlows returns paginated results ordered by created_at DESC.

## 4. GREEN — Flow Service & Reconciliation

- [x] 4.1 Implement AccountFlowService.recordFlow: insert flow, catch DuplicateKeyException → idempotent.
- [x] 4.2 Implement queryFlows: paginated query.
- [x] 4.3 Implement verifyBalance: sum all IN flows minus all OUT flows for (userId, tokenId). Compare with current (available + frozen). Return boolean.
- [x] 4.4 Run tests — all pass.

## 5. Reconciliation Job

- [x] 5.1 Implement AccountReconcileJob: @Scheduled(cron="0 0 2 * * ?") full scan, @Scheduled(cron="0 0 * * * ?") sampling 100 accounts. On mismatch → CRITICAL alert, log ERROR with details.

## 6. REFACTOR & Verify

- [x] 6.1 Ensure all balance updates go through AccountService (no direct SQL). All tests pass.
- [x] 6.2 mvn test passes.
