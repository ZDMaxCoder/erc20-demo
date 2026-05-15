## 1. Setup

- [x] 1.1 Create CollectionProperties configuration class mapping collection.* properties.

## 2. RED — Gas Supply Tests

- [x] 2.1 Write GasSupplyServiceTest: estimateRequiredGas returns gas_limit * gas_price * buffer_multiplier.
- [x] 2.2 Write test: supplyGas calls WalletService.sendEthTransfer with correct amount, returns TransactionRecord.
- [x] 2.3 Write test: hot wallet balance insufficient → raises WARN alert, returns null (skip collection).

## 3. GREEN — Gas Supply

- [x] 3.1 Implement GasSupplyService.estimateRequiredGas: estimate ~65000 gas * current gas price * 1.5 buffer.
- [x] 3.2 Implement supplyGas: check hot wallet ETH balance first, if insufficient alert and return null. Otherwise call sendEthTransfer.
- [x] 3.3 Run tests — all pass.

## 4. RED — Collection Service Tests

- [x] 4.1 Write CollectionServiceTest.scanForCollection: address with balance >= threshold and no active task → creates PENDING task.
- [x] 4.2 Write test: address with balance < threshold → no task created.
- [x] 4.3 Write test: address with existing active (non-FAILED) task → no duplicate task.
- [x] 4.4 Write test: address collected within minIntervalHours → skipped.
- [x] 4.5 Write test: executeCollection with sufficient ETH → skips gas supply, directly sends ERC-20 transfer, task→SUCCESS.
- [x] 4.6 Write test: executeCollection with insufficient ETH → GAS_SUPPLYING state, gas supply tx sent.

## 5. GREEN — Collection Service

- [x] 5.1 Implement scanForCollection: query BOUND addresses for token, check balances, apply threshold and interval filters, create tasks.
- [x] 5.2 Implement executeCollection: check ETH balance → if low: GAS_SUPPLYING + supply gas → if ok: COLLECTING + send ERC-20 transfer. Update task state at each step.
- [x] 5.3 Implement batchCollection: iterate tasks with semaphore rate limiting (max concurrent = config.batchSize).
- [x] 5.4 Run tests — all pass.

## 6. RED — Collection Triggers

- [x] 6.1 Write CollectionTriggerTest: on DepositConfirmedMessage for address with balance > threshold → collection task created.
- [x] 6.2 Write test: deposit confirmed but balance still below threshold → no task.

## 7. GREEN — Collection Triggers

- [x] 7.1 Implement CollectionTriggerConsumer: on deposit confirmed, check address balance, create task if threshold met.
- [x] 7.2 Implement scheduled scan @Scheduled(cron="0 0 */4 * * ?"): iterate tokens, call scanForCollection.
- [x] 7.3 Handle TxStatusChanged for COLLECTION/GAS_SUPPLY bizType: advance task state machine.
- [x] 7.4 Run tests — all pass.

## 8. Hot Wallet Monitor

- [x] 8.1 Implement WalletTransferService.checkHotWalletBalance @Scheduled(fixedDelay=300000): query balances, alert if below thresholds.

## 9. REFACTOR & Verify

- [x] 9.1 Verify state machine: PENDING→GAS_SUPPLYING→GAS_CONFIRMED→COLLECTING→SUCCESS. All tests pass.
- [x] 9.2 mvn test passes.
