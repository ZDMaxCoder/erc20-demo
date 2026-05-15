## 1. State Machine — RED

- [x] 1.1 Write WithdrawStateMachineTest: PENDING_REVIEW→APPROVED allowed. PENDING_REVIEW→REJECTED allowed. APPROVED→SIGNING allowed. SIGNING→BROADCASTING allowed. BROADCASTING→SUCCESS NOT allowed (must go via PENDING_CONFIRM). FAILED→APPROVED allowed (retry).
- [x] 1.2 Write test: any illegal transition → throws BizException.

## 2. State Machine — GREEN

- [x] 2.1 Implement WithdrawStateMachine: define allowed transitions map. Method canTransition(from, to) → boolean. Method assertTransition(from, to) → throws if illegal.
- [x] 2.2 Run tests — all pass.

## 3. Withdrawal Creation — RED

- [x] 3.1 Write WithdrawServiceTest.createWithdraw: valid request → record created with PENDING_REVIEW, balance frozen (available decreased, frozen increased), FREEZE flow recorded.
- [x] 3.2 Write test: available_balance < amount + fee → BizException "insufficient balance".
- [x] 3.3 Write test: same request_id twice → returns existing record, no double freeze (idempotent).
- [x] 3.4 Write test: invalid address format → BizException.
- [x] 3.5 Write test: token not enabled → BizException.

## 4. Withdrawal Creation — GREEN

- [x] 4.1 Implement WithdrawService.createWithdraw @Transactional: validate → freeze → create record → record flow → send MQ to risk.
- [x] 4.2 Handle DuplicateKeyException on request_id → return existing record.
- [x] 4.3 Run tests — all pass.

## 5. Approval/Rejection — RED

- [x] 5.1 Write test: approve when status=PENDING_REVIEW → status=APPROVED, MQ message sent.
- [x] 5.2 Write test: approve when status != PENDING_REVIEW → BizException.
- [x] 5.3 Write test: reject → status=REJECTED, balance unfrozen, UNFREEZE flow recorded.

## 6. Approval/Rejection — GREEN

- [x] 6.1 Implement approve: assert state machine, update status, send WithdrawExecuteMessage to MQ.
- [x] 6.2 Implement reject: assert state machine, unfreeze, update status, record flow.
- [x] 6.3 Run tests — all pass.

## 7. Execution — RED

- [x] 7.1 Write test: executeWithdraw when APPROVED → calls WalletService.sendERC20Transfer, status→BROADCASTING, tx_hash/nonce stored.
- [x] 7.2 Write test: WalletService throws → status stays APPROVED (for retry), retry_count incremented.
- [x] 7.3 Write test: executeWithdraw acquires distributed lock (mock verifies lock called).
- [x] 7.4 Write test: executeWithdraw when status != APPROVED → no-op (idempotent guard).

## 8. Execution — GREEN

- [x] 8.1 Implement executeWithdraw: acquire lock → assert APPROVED → SIGNING → convert amount → send tx → BROADCASTING. On failure: increment retry_count, keep APPROVED if retryable, FAILED if not.
- [x] 8.2 Implement WithdrawExecuteConsumer: MQ listener calls executeWithdraw.
- [x] 8.3 Run tests — all pass.

## 9. Confirmation/Failure — RED

- [x] 9.1 Write test: confirmWithdraw → status=SUCCESS, frozen decreased, WITHDRAW+WITHDRAW_FEE flows recorded.
- [x] 9.2 Write test: failWithdraw → status=FAILED, balance unfrozen, UNFREEZE flow, nonce released.
- [x] 9.3 Write test: concurrent confirmWithdraw for same id → only first succeeds (lock + state check).

## 10. Confirmation/Failure — GREEN

- [x] 10.1 Implement confirmWithdraw @Transactional: assert state → SUCCESS → decreaseFrozen → record flows.
- [x] 10.2 Implement failWithdraw @Transactional: assert state → FAILED → unfreeze → release nonce.
- [x] 10.3 Implement TxStatusChangedConsumer handler for WITHDRAW bizType.
- [x] 10.4 Run tests — all pass.

## 11. Retry Job — RED

- [x] 11.1 Write WithdrawRetryJobTest: BROADCASTING tx older than 10min, chain shows confirmed → confirmWithdraw called.
- [x] 11.2 Write test: BROADCASTING older than 10min, chain shows dropped → reset to APPROVED for re-execution.
- [x] 11.3 Write test: retry_count >= 3 → set FAILED + alert.

## 12. Retry Job — GREEN

- [x] 12.1 Implement WithdrawRetryJob @Scheduled(fixedDelay=30000): scan stuck withdrawals, check chain, take appropriate action.
- [x] 12.2 Run tests — all pass.

## 13. REFACTOR & Verify

- [x] 13.1 Review: every state change has log + audit trail. All tests pass.
- [x] 13.2 mvn test passes.
