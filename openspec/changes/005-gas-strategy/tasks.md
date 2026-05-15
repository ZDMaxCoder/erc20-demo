## 1. Setup

- [x] 1.1 Define GasStrategy interface, GasPrice model, GasPriority enum, GasProperties config class (per design.md).

## 2. RED — EIP-1559 Strategy Tests

- [x] 2.1 Write EIP1559GasStrategyTest (MockWebServer for eth_feeHistory): LOW price < MEDIUM < HIGH < URGENT.
- [x] 2.2 Write test: replacement price is >= original * 1.1 (at least 10% increase).
- [x] 2.3 Write test: calculated price exceeding max-gas-price config → capped to max with WARN.
- [x] 2.4 Write test: eth_feeHistory returns empty/error → graceful fallback (use last known value or default).

## 3. GREEN — EIP-1559 Strategy

- [x] 3.1 Implement EIP1559GasStrategy: call eth_feeHistory for 10 blocks with percentiles [10,50,75,95]. Calculate baseFee from latest block. Apply multipliers: LOW=1.1, MEDIUM=1.25, HIGH=1.5, URGENT=2.0. Add percentile priority fee.
- [x] 3.2 Implement getReplacementGasPrice: max(original*1.15, original+1Gwei) for both maxFeePerGas and maxPriorityFeePerGas.
- [x] 3.3 Apply cap: if result > maxGasPrice config, clamp and log WARN.
- [x] 3.4 Run tests — all pass.

## 4. RED — Legacy Strategy Tests

- [x] 4.1 Write LegacyGasStrategyTest: LOW=suggested*0.9, MEDIUM=suggested*1.0, HIGH=suggested*1.3, URGENT=suggested*1.8.
- [x] 4.2 Write test: replacement is >= original * 1.1.
- [x] 4.3 Write test: cap enforcement same as EIP-1559.

## 5. GREEN — Legacy Strategy

- [x] 5.1 Implement LegacyGasStrategy: call eth_gasPrice, apply multipliers, apply cap.
- [x] 5.2 Run tests — all pass.

## 6. RED — Gas Estimator Tests

- [x] 6.1 Write GasEstimatorTest: eth_estimateGas returns 60000 → result is 60000 * 1.2 = 72000 (20% buffer).
- [x] 6.2 Write test: eth_estimateGas fails (revert) → returns default 80000.
- [x] 6.3 Write test: estimateEthTransfer always returns 21000.

## 7. GREEN — Gas Estimator

- [x] 7.1 Implement GasEstimator: estimateERC20Transfer (call eth_estimateGas + buffer), estimateEthTransfer (fixed 21000).
- [x] 7.2 Run tests — all pass.

## 8. Gas Price Cache & Stuck Handler (Less TDD-critical — infrastructure)

- [x] 8.1 Implement GasPriceCache @Scheduled(fixedDelay=15000): refresh gas prices to Redis. Expose cached getGasPrice.
- [x] 8.2 Implement StuckTransactionHandler @Scheduled(fixedDelay=60000): scan PENDING txs older than stuckTimeout, check chain, trigger replacement or re-broadcast. Respect maxReplacementCount.

## 9. REFACTOR & Verify

- [x] 9.1 Extract BigInteger calculation helpers if duplicated. All tests pass.
- [x] 9.2 mvn test passes for blockchain module.
