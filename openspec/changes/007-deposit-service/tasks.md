## 1. Address Service — RED

- [x] 1.1 Write AddressServiceTest: allocateDepositAddress for new user → returns an address, address status changed to BOUND, user_id set.
- [x] 1.2 Write test: allocate for user who already has address for same token → returns same address (idempotent).
- [x] 1.3 Write test: findByAddress with known address → returns UserAddress. Unknown address → Optional.empty().
- [x] 1.4 Write test: pool empty → triggers preGenerateAddresses, then allocation succeeds.

## 2. Address Service — GREEN

- [x] 2.1 Implement AddressService.allocateDepositAddress: check existing → pick AVAILABLE from pool → update to BOUND. If pool empty, call preGenerate first.
- [x] 2.2 Implement preGenerateAddresses(count): HD derivation BIP-44 m/44'/60'/0'/0/{index}, save with status AVAILABLE.
- [x] 2.3 Implement findByAddress: query by normalized lowercase address.
- [x] 2.4 Run tests — all pass.

## 3. Deposit Service — RED

- [x] 3.1 Write DepositServiceTest: TransferEvent to platform address for registered token → DepositRecord created with status CONFIRMING, correct amount conversion.
- [x] 3.2 Write test: to-address is NOT platform address → no record created (skip).
- [x] 3.3 Write test: contract not in token_config → no record created (skip).
- [x] 3.4 Write test: same TransferEvent processed twice (same txHash+logIndex) → only one record (idempotent).
- [x] 3.5 Write test: amount < min_deposit_amount → record created with status BELOW_MINIMUM, no credit.
- [x] 3.6 Write test: creditDeposit → account balance increased, deposit status=SUCCESS, credited=true, flow recorded.
- [x] 3.7 Write test: creditDeposit called twice for same deposit → second call is no-op (idempotent via flow key).

## 4. Deposit Service — GREEN

- [x] 4.1 Implement DepositService.onTransferEvent: normalize address → lookup → check token → idempotent check → convert amount → create record.
- [x] 4.2 Implement creditDeposit @Transactional: verify CONFIRMING status → AccountService.increaseAvailable → update status SUCCESS + credited=true.
- [x] 4.3 Run tests — all pass.

## 5. Deposit Confirmation — RED

- [x] 5.1 Write DepositConfirmJobTest: CONFIRMING deposit at block 100, current block 112, required confirms=12 → creditDeposit called.
- [x] 5.2 Write test: current block 110, required 12 → NOT yet credited (only 10 confirmations).
- [x] 5.3 Write test: multiple CONFIRMING deposits, only those meeting threshold get credited.

## 6. Deposit Confirmation — GREEN

- [x] 6.1 Implement DepositConfirmJob @Scheduled(fixedDelay=10000): query CONFIRMING deposits, get current block from sync progress, calculate confirmations, credit those meeting threshold.
- [x] 6.2 Run tests — all pass.

## 7. Reorg Handling — RED

- [x] 7.1 Write DepositReorgTest: deposit credited (status=SUCCESS, credited=true) then reorg → status=REORGED, balance deducted, reversal flow recorded.
- [x] 7.2 Write test: deposit not yet credited (CONFIRMING) then reorg → status=REORGED, no balance change needed.

## 8. Reorg Handling — GREEN

- [x] 8.1 Implement DepositService.handleReorg(affectedBlockNumbers): query affected deposits, set REORGED, if credited=true reverse the credit.
- [x] 8.2 Run tests — all pass.

## 9. REFACTOR & Verify

- [x] 9.1 Review amount conversion paths — ensure no precision loss. All tests pass.
- [x] 9.2 mvn test passes for service module.
