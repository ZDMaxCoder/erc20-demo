## 1. RED — Rule Engine Tests

- [x] 1.1 Write RiskControlServiceTest: withdrawal to blacklisted address → REJECT with reason.
- [x] 1.2 Write test: amount below auto-pass threshold, address not blacklisted, not new → AUTO_PASS.
- [x] 1.3 Write test: amount above auto-pass but below daily limit → NEED_MANUAL_REVIEW.
- [x] 1.4 Write test: amount exceeds daily limit → REJECT.
- [x] 1.5 Write test: hourly frequency exceeded → NEED_MANUAL_REVIEW.
- [x] 1.6 Write test: first-time withdrawal address with new-address-review=true → NEED_MANUAL_REVIEW.
- [x] 1.7 Write test: amount >= large-amount-threshold → NEED_MANUAL_REVIEW regardless of other rules.
- [x] 1.8 Write test: multiple rules triggered — first REJECT wins, NEED_MANUAL_REVIEW accumulated.

## 2. GREEN — Rule Engine Framework

- [x] 2.1 Define RiskRule interface: RiskResult check(WithdrawRecord), int order(). Define RiskResult: status(AUTO_PASS/NEED_MANUAL_REVIEW/REJECT), reason String.
- [x] 2.2 Implement RiskControlService: inject List<RiskRule>, sort by order, evaluate sequentially. First REJECT stops. Any MANUAL_REVIEW → final result MANUAL_REVIEW. All PASS → AUTO_PASS.
- [x] 2.3 Create RiskProperties config class.
- [x] 2.4 Run tests (framework only, rules next) — tests relying on individual rules still RED.

## 3. GREEN — Individual Rules

- [x] 3.1 Implement AddressBlacklistRule (order=1): delegate to AddressBlacklistService.isBlacklisted.
- [x] 3.2 Implement AmountLimitRule (order=2): check vs auto-pass-max-amount and daily-limit.
- [x] 3.3 Implement FrequencyRule (order=3): check Redis counter for hourly count.
- [x] 3.4 Implement NewAddressRule (order=4): query t_withdraw_record for prior SUCCESS to same address. First time + config enabled → MANUAL_REVIEW.
- [x] 3.5 Implement LargeAmountRule (order=5): amount >= threshold → MANUAL_REVIEW.
- [x] 3.6 Run all tests — all pass.

## 4. RED — Limit Service Tests

- [x] 4.1 Write WithdrawLimitServiceTest: checkAndAccumulate first call → true, accumulates in Redis.
- [x] 4.2 Write test: accumulate past daily limit → returns false.
- [x] 4.3 Write test: rollback reduces accumulated amount.
- [x] 4.4 Write test: Redis key has correct TTL (daily=48h, hourly=2h).

## 5. GREEN — Limit Service

- [x] 5.1 Implement WithdrawLimitService: Redis INCRBY with TTL for daily/hourly tracking. Check against limits. Rollback via DECRBY.
- [x] 5.2 Run tests — all pass.

## 6. RED — Blacklist Tests

- [x] 6.1 Write AddressBlacklistServiceTest: add address → isBlacklisted returns true. Remove → returns false.
- [x] 6.2 Write test: address normalization (mixed case treated same as lowercase).

## 7. GREEN — Blacklist

- [x] 7.1 Implement AddressBlacklistService: Redis Set + DB persistence. Load from DB on startup. Normalize addresses.
- [x] 7.2 Run tests — all pass.

## 8. REFACTOR & Verify

- [x] 8.1 Ensure new rules can be added by just implementing interface + @Component. All tests pass.
- [x] 8.2 mvn test passes.
