## 1. Foundation — ErrorCodes, Exceptions, and DB Migration

- [x] 1.1 Add AMOUNT_OVERFLOW, TRANSFER_NOT_CONFIRMED, TOKEN_TYPE_UNSUPPORTED to ErrorCode enum. Add AmountOverflowException and ContractRevertException classes.
- [x] 1.2 Add AMOUNT_OVERFLOW value to DepositStatus enum. Add REJECTED to WithdrawStatus and update stateMachine transitions.
- [x] 1.3 Create V9 migration: ALTER TABLE t_token_config ADD COLUMN token_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD'. Add tokenType field to TokenConfig entity.
- [x] 1.4 Write AmountUtilTest: test overflow detection in fromChainAmount and toChainAmount throws AmountOverflowException. Run tests — RED.
- [x] 1.5 Implement overflow protection in AmountUtil.fromChainAmount() and toChainAmount(). Run tests — GREEN.

## 2. ReorgHandler — Fund Reversal

- [x] 2.1 Write ReorgHandlerTest: verify handleReorg calls DepositService.handleReorg() with affected block numbers.
- [x] 2.2 Write ReorgHandlerTest: verify handleReorg queries confirmed withdrawals in reorg range and calls WithdrawService.revertConfirmedWithdraw().
- [x] 2.3 Write ReorgHandlerTest: verify TransactionRecords with CONFIRMED status above forkPoint are reset to PENDING.
- [x] 2.4 Add WithdrawService.revertConfirmedWithdraw() method: restore frozen balance (add back decreaseFrozen amounts via increaseAvailable? No — set status to BROADCASTING which preserves the freeze). Write test.
- [x] 2.5 Implement ReorgHandler changes: inject DepositService, WithdrawService, WithdrawRecordMapper, TransactionRecordMapper. Remove direct deposit SQL update. Call DepositService.handleReorg(), query+revert withdrawals, reset transaction records. Run tests — GREEN.

## 3. TransactionConfirmTracker — Transfer Event Verification

- [x] 3.1 Add actualAmount field (BigInteger, nullable) to TxStatusChangedMessage.
- [x] 3.2 Write TransactionConfirmTrackerTest: receipt status 0x1 with Transfer event → CONFIRMED with actualAmount populated.
- [x] 3.3 Write TransactionConfirmTrackerTest: receipt status 0x1 without Transfer event → FAILED with reason "Transfer event not found".
- [x] 3.4 Implement: inject ERC20TransferEventParser and TokenConfigMapper. In checkConfirmation(), when receipt success, look up TransactionRecord's contract (via a new contractAddress field or join), parse logs. If Transfer found → CONFIRMED + set actualAmount. If not → FAILED. Run tests — GREEN.

## 4. Withdrawal Risk Integration

- [x] 4.1 Write WithdrawServiceTest: createWithdraw with risk AUTO_PASS → status APPROVED + execute message published.
- [x] 4.2 Write WithdrawServiceTest: createWithdraw with risk REJECT → balance unfrozen + status REJECTED.
- [x] 4.3 Write WithdrawServiceTest: createWithdraw with risk NEED_MANUAL_REVIEW → status PENDING_REVIEW (current).
- [x] 4.4 Implement: inject RiskControlService into WithdrawService. After freeze in createWithdraw(), call checkWithdraw(). Handle three outcomes. Run tests — GREEN.

## 5. Withdrawal Confirmation Amount Verification

- [x] 5.1 Extend WithdrawService.confirmWithdraw() signature to accept optional actualAmount (BigInteger). Write test: matching amount → no alert.
- [x] 5.2 Write test: mismatched actualAmount → WITHDRAW_AMOUNT_MISMATCH alert raised but withdrawal still confirmed.
- [x] 5.3 Write test: null actualAmount → no comparison, confirm normally.
- [x] 5.4 Implement in WithdrawService and update TxStatusConsumer to pass actualAmount from message. Run tests — GREEN.

## 6. WithdrawRetryJob Enhancements

- [x] 6.1 Write WithdrawRetryJobTest: SIGNING status stuck > 2 min → reset to APPROVED.
- [x] 6.2 Write WithdrawRetryJobTest: BROADCASTING stuck with PENDING chain tx → alert raised + reset to APPROVED.
- [x] 6.3 Implement scanStuckSigning() in WithdrawRetryJob. Implement alert in handleStuckBroadcasting(). Run tests — GREEN.

## 7. GasEstimator — Revert vs Network Error Distinction

- [x] 7.1 Write GasEstimatorTest: response hasError with "execution reverted" → throws ContractRevertException.
- [x] 7.2 Write GasEstimatorTest: IOException → returns DEFAULT_ERC20_GAS fallback.
- [x] 7.3 Implement: in estimateERC20Transfer(), check response.hasError() before accessing result. If revert → throw ContractRevertException. If IOException → fallback. Run tests — GREEN.
- [x] 7.4 Update WalletService.sendERC20Transfer(): catch ContractRevertException and throw BizException with descriptive message (do not send tx).

## 8. Token Type Safety and Mint Filtering

- [x] 8.1 Write DepositServiceTest: Transfer event with from=zero address (mint) → skipped, no deposit created.
- [x] 8.2 Write DepositServiceTest: Transfer event for token with tokenType=FEE_ON_TRANSFER → skipped.
- [x] 8.3 Write DepositServiceTest: AmountOverflowException during amount conversion → deposit created with AMOUNT_OVERFLOW status + alert.
- [x] 8.4 Implement mint filtering, token type check, and overflow handling in DepositService.onTransferEvent(). Run tests — GREEN.
- [x] 8.5 Write CollectionServiceTest: scanForCollection with non-STANDARD token → returns without scanning.
- [x] 8.6 Write CollectionServiceTest: balance overflow → alert raised, no task created.
- [x] 8.7 Implement token type guard and overflow guard in CollectionService. Run tests — GREEN.

## 9. MqCompensationJob — Verify Before Credit

- [x] 9.1 Write MqCompensationJobTest: stuck deposit with sufficient confirmations → credit.
- [x] 9.2 Write MqCompensationJobTest: stuck deposit with insufficient confirmations → not credited, updatedAt refreshed, alert raised.
- [x] 9.3 Write MqCompensationJobTest: stuck deposit with no receipt on chain → not credited, CRITICAL alert.
- [x] 9.4 Implement: inject Web3j (or a chain query helper). In compensateStuckDeposits(), query receipt + current block, compute confirmations. Conditional credit. Run tests — GREEN.

## 10. SafeERC20Caller Hardening

- [x] 10.1 Write SafeERC20CallerTest: safeBalanceOf with empty response → throws RuntimeException.
- [x] 10.2 Write SafeERC20CallerTest: safeDecimals failure → throws RuntimeException (no default 18).
- [x] 10.3 Write SafeERC20CallerTest: ethCall with IOException retries 3 times.
- [x] 10.4 Write SafeERC20CallerTest: decodeBytes32AsString with ABI dynamic offset → correctly decoded.
- [x] 10.5 Implement: add empty-response guard, remove decimals default, add retry logic (simple loop, 500ms sleep, 3 attempts), fix decodeBytes32AsString, add configurable from-address via @Value. Run tests — GREEN.

## 11. AlertService Fine-Grained Dedup

- [x] 11.1 Write AlertServiceTest: two alerts same type/level but different bizId → both created.
- [x] 11.2 Write AlertServiceTest: same type/level/bizId within dedup window → second deduplicated.
- [x] 11.3 Write AlertServiceTest: alert without bizId → backward compatible dedup by type+level.
- [x] 11.4 Implement: add overloaded alert() method with bizId param. Update dedup key. Run tests — GREEN.

## 12. Chain Reconciliation Job

- [x] 12.1 Write ChainReconcileJobTest: hot wallet balance matches → no alert.
- [x] 12.2 Write ChainReconcileJobTest: hot wallet balance diverges → CHAIN_BALANCE_MISMATCH alert.
- [x] 12.3 Write ChainReconcileJobTest: balanceOf RPC fails → WARN alert, continues with other tokens.
- [x] 12.4 Implement ChainReconcileJob: inject SafeERC20Caller, TokenConfigMapper, WalletConfigMapper, AlertService. Scheduled at 3 AM. Query each enabled token's hot wallet balance on-chain, compare with expected. Run tests — GREEN.

## 13. Admin Event Monitoring

- [x] 13.1 Write AdminEventMonitorTest: Paused() event detected → CRITICAL alert.
- [x] 13.2 Write AdminEventMonitorTest: Upgraded() event detected → CRITICAL alert with new impl address.
- [x] 13.3 Implement AdminEventMonitor: subscribe to token contract logs for Paused/Upgraded topics. On detection, call alertService.alert(). Run tests — GREEN.

## 14. TransactionBuilder and ERC20TransferEventParser Fixes

- [x] 14.1 Write TransactionBuilderTest: EIP-1559 tx uses provided chainId (not hardcoded 1).
- [x] 14.2 Implement: change buildERC20Transfer/buildEthTransfer to accept chainId parameter. Update callers. Run tests — GREEN.
- [x] 14.3 Write ERC20TransferEventParserTest: data with 0x prefix + 64 hex chars → correct amount parsed.
- [x] 14.4 Implement: fix data parsing to use 32-byte alignment (skip "0x", take first 64 hex chars). Run tests — GREEN.

## 15. DepositConfirmJob Pagination

- [x] 15.1 Write DepositConfirmJobTest: query limited to 500 records.
- [x] 15.2 Implement: add LIMIT 500 to the confirming deposits query. Run tests — GREEN.

## 16. Integration Verification

- [x] 16.1 Run mvn clean compile -DskipTests to verify all new code compiles.
- [x] 16.2 Run mvn test to verify all tests pass (existing + new).
