## 1. Database Migration & Model Enhancement

- [x] 1.1 Create V12 Flyway migration: ALTER TABLE t_token_config ADD COLUMN circuit_breaker_status VARCHAR(16) DEFAULT 'CLOSED'
- [x] 1.2 Add `circuitBreakerStatus` field to `TokenConfig` entity; add `circuitBreakerOpen` boolean field to `TokenRiskProfile` model with builder support
- [x] 1.3 Update `TokenRiskProfileRegistry.buildProfile()` to read `circuitBreakerStatus` from DB and set `circuitBreakerOpen = "OPEN".equals(status)`

## 2. TokenMetadataCache

- [x] 2.1 TDD Red: Write `TokenMetadataCacheTest` — first call triggers RPC, second call returns cached, invalidate clears cache, address normalization to lowercase
- [x] 2.2 TDD Green: Implement `TokenMetadataCache` component with ConcurrentHashMap, `getDecimals`/`getSymbol`/`getName`/`invalidate` methods, delegating to SafeERC20Caller on miss
- [x] 2.3 Refactor `DefaultERC20Adapter`: inject `TokenMetadataCache`, change `decimals()`/`symbol()`/`name()` to delegate to cache instead of direct SafeERC20Caller calls
- [x] 2.4 Update `DefaultERC20AdapterReadTest` to verify cache delegation and that balanceOf/allowance still go direct to SafeERC20Caller

## 3. TransferConfirmer Confirmation Count

- [x] 3.1 TDD Red: Write `TransferConfirmerTest` new scenarios — receipt present but confirmations < minConfirmations → PENDING; confirmations >= threshold → normal flow; minConfirmations=0 skips check
- [x] 3.2 TDD Green: Add `confirm()` overload accepting `int minConfirmations` parameter; fetch current block via `web3j.ethBlockNumber()`, compare with receipt block; if insufficient return `TransferResult.pending(txHash)`
- [x] 3.3 Update `TransactionConfirmTracker.checkConfirmation()`: resolve `depositConfirmBlocks` from TokenConfig, pass to `transferConfirmer.confirm()` as minConfirmations

## 4. ConsecutiveFailureBreaker

- [x] 4.1 TDD Red: Write `ConsecutiveFailureBreakerTest` — recordSuccess resets counter, recordFailure increments, threshold reached trips breaker (DB update + registry invalidate + alert), below threshold no action, resetBreaker restores CLOSED
- [x] 4.2 TDD Green: Implement `ConsecutiveFailureBreaker` component — Redis AtomicLong for counter (`circuit_breaker:{contract}:failures`), configurable threshold from `@Value`, tripBreaker updates DB + invalidates registry + alerts, resetBreaker clears counter + DB + invalidates
- [x] 4.3 Update `TokenAdmissionGateway.checkAdmission()`: after existing checks, add check for `profile.isCircuitBreakerOpen()` → throw TokenAdmissionRejectedException with "circuit breaker is OPEN"
- [x] 4.4 Write `TokenAdmissionGatewayTest` new scenario: circuit breaker OPEN → rejection
- [x] 4.5 Update `TransactionConfirmTracker.checkConfirmation()`: on SUCCESS call `breaker.recordSuccess(contract)`, on FAILED call `breaker.recordFailure(contract)`

## 5. ChainReconcileJob Adapter Integration

- [x] 5.1 TDD Red: Update `ChainReconcileJobTest` — verify `ERC20Adapter.balanceOf` is called instead of `SafeERC20Caller.safeBalanceOf`; verify disabled token still gets reconciled; verify ChainCallException is caught gracefully
- [x] 5.2 TDD Green: Refactor `ChainReconcileJob` — replace `SafeERC20Caller` dependency with `ERC20Adapter`; call `adapter.balanceOf(contract, address)` for on-chain balance queries

## 6. AdminEventMonitor Metadata Cache Integration

- [x] 6.1 Update `AdminEventMonitor.processLog()`: on Upgraded event, also call `tokenMetadataCache.invalidate(contractAddr)` in addition to registry invalidation
- [x] 6.2 Update `AdminEventMonitorTest`: verify `tokenMetadataCache.invalidate()` is called on Upgraded event

## 7. Compilation & Integration Verification

- [x] 7.1 Run `mvn clean compile -DskipTests` to verify all modules compile without errors
- [x] 7.2 Run `mvn test -pl erc20-platform-blockchain` to verify all blockchain module tests pass
- [x] 7.3 Run `mvn test -pl erc20-platform-service` to verify all service module tests pass
- [x] 7.4 Run `mvn test` to verify full project test suite passes
