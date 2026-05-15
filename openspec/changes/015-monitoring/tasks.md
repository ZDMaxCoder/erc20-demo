## 1. Health Checks — RED

- [x] 1.1 Write EthereumHealthIndicatorTest: RPC responds within 3s → UP with block number in details.
- [x] 1.2 Write test: RPC timeout → DOWN.
- [x] 1.3 Write BlockSyncHealthIndicatorTest: sync delay < 30 → UP. Delay > 30 → DOWN.

## 2. Health Checks — GREEN

- [x] 2.1 Implement EthereumHealthIndicator (extends AbstractHealthIndicator): call eth_blockNumber, report block number and sync lag. DOWN if unreachable or lag > 120s.
- [x] 2.2 Implement BlockSyncHealthIndicator: check t_block_sync_progress, calculate delay vs chain head.
- [x] 2.3 Configure actuator to expose health, info, metrics, prometheus endpoints.
- [x] 2.4 Run tests — all pass.

## 3. Business Metrics (Infrastructure — limited TDD value)

- [x] 3.1 Implement BusinessMetrics @Component: register counters (deposit.count, withdraw.count, collection.count, block.synced.count, reorg.count with appropriate tags).
- [x] 3.2 Register gauges: hot.wallet.balance (refreshed every 60s), block.sync.delay, pending.withdraw.count, pending.nonce.count.
- [x] 3.3 Register timers: deposit.confirm.duration, withdraw.process.duration, gas.price.gwei.
- [x] 3.4 Add metric instrumentation calls to key service methods (deposit credited, withdraw completed, etc.).

## 4. Alert Service — RED

- [x] 4.1 Write AlertServiceTest: alert(type, CRITICAL, content) → record saved to t_alert_record, message published to MQ.
- [x] 4.2 Write test: same type+level alert within 10 minutes → deduplicated (not saved again).
- [x] 4.3 Write test: same type+level after 10 minutes → new alert created.
- [x] 4.4 Write test: INFO level → saved but NOT published to MQ.

## 5. Alert Service — GREEN

- [x] 5.1 Implement AlertService.alert: dedup check (Redis key "alert:dedup:{type}:{level}" TTL=10min) → save to DB → if WARN or CRITICAL, publish AlertMessage to MQ.
- [x] 5.2 Create AlertProperties: dedupIntervalMinutes, criticalNotify.
- [x] 5.3 Run tests — all pass.

## 6. Alert Integration Points

- [x] 6.1 Add alertService.alert calls to: ReorgHandler (CRITICAL), BlockSyncEngine sync delay (CRITICAL), StuckTransactionHandler exhausted retries (CRITICAL), GasPriceCache exceeds cap (WARN), WalletTransferService hot wallet low (WARN/CRITICAL), AccountReconcileJob mismatch (CRITICAL), NonceGapDetector (WARN), Web3jProvider failover (WARN).
- [x] 6.2 Implement admin alert handling endpoint (already defined in 013, wire up here).

## 7. REFACTOR & Verify

- [x] 7.1 Verify alert dedup prevents storm. All tests pass.
- [x] 7.2 Start application, verify /actuator/health and /actuator/prometheus return expected data.
- [x] 7.3 mvn test passes for all modules.
