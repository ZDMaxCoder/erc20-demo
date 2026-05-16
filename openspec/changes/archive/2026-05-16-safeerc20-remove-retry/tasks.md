## 1. ChainCallException and SafeERC20Caller refactor

- [x] 1.1 Create ChainCallException class in erc20-platform-blockchain/.../erc20/ package with contract field and constructor.
- [x] 1.2 Write SafeERC20CallerTest: ethCall with IOException → throws ChainCallException immediately (verify no retry, no sleep).
- [x] 1.3 Write SafeERC20CallerTest: ethCall with response error → throws ChainCallException with error message.
- [x] 1.4 Refactor SafeERC20Caller.ethCall(): remove for-loop, MAX_RETRY_ATTEMPTS, RETRY_DELAY_MS, Thread.sleep. Single attempt: IOException → throw new ChainCallException(contract, e). response.hasError() → throw new ChainCallException(contract, error message).
- [x] 1.5 Run tests — GREEN. Verify existing SafeERC20CallerTest still passes.

## 2. Upstream caller error handling

- [x] 2.1 Write ChainReconcileJobTest: safeBalanceOf throws ChainCallException → WARN alert raised, continues with next token.
- [x] 2.2 Implement ChainReconcileJob: wrap safeBalanceOf call in try-catch(ChainCallException), raise CHAIN_CALL_FAILED WARN alert, continue loop.
- [x] 2.3 Verify CollectionService.checkAndCreateTask() already has try-catch(Exception) that covers ChainCallException — add explicit log if needed.
- [x] 2.4 Verify MqCompensationJob handles ChainCallException in compensateStuckDeposits() — the Web3j query failures should not credit deposits.
- [x] 2.5 Run mvn test — all tests pass.
