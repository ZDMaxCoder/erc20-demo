## 1. Configuration (Setup)

- [x] 1.1 Create BlockSyncProperties configuration class mapping blockchain.sync.* properties (chainId, startBlock, batchSize, pollInterval, maxReorgDepth, rpcUrl, backupRpcUrl).

## 2. Reorg Detection — RED

- [x] 2.1 Write ReorgHandlerTest: given stored blocks [100(hashA), 101(hashB, parent=hashA)], new block 102 with parentHash != hashB → reorg detected, handler finds fork point at 100.
- [x] 2.2 Write test: reorg depth exceeds maxReorgDepth (50) → throws exception, does not auto-recover.
- [x] 2.3 Write test: after reorg, sync progress reset to fork point, affected block_records marked reorged.

## 3. Reorg Detection — GREEN

- [x] 3.1 Implement ReorgHandler.handleReorg(currentBlockNumber, expectedParentHash, actualParentHash): backtrack comparing stored hashes with chain until fork point found. Mark affected blocks. Reset progress. Send alert.
- [x] 3.2 If backtrack > maxReorgDepth: set progress status=ERROR, send CRITICAL alert, throw exception to halt sync.
- [x] 3.3 Run tests — all pass.

## 4. Block Sync Engine — RED

- [x] 4.1 Write BlockSyncEngineTest (MockWebServer): mock eth_getBlockByNumber returning block with correct parentHash → block saved, progress updated.
- [x] 4.2 Write test: parentHash mismatch → ReorgHandler invoked.
- [x] 4.3 Write test: RPC returns null (block not yet available) → no action, wait for next poll.

## 5. Block Sync Engine — GREEN

- [x] 5.1 Implement BlockSyncEngine with @Scheduled(fixedDelay from config): acquire distributed lock, fetch lastSyncedBlock+1, verify parentHash, process or trigger reorg.
- [x] 5.2 Implement processBlock: save block to t_block_record, update t_block_sync_progress, all in one transaction.
- [x] 5.3 Run tests — all pass.

## 6. Transfer Event Extractor — RED

- [x] 6.1 Write TransferEventExtractorTest: given a block with logs matching registered token contracts → returns correct list of TransferEvents.
- [x] 6.2 Write test: logs from unregistered contract → filtered out.

## 7. Transfer Event Extractor — GREEN

- [x] 7.1 Implement TransferEventExtractor.extractFromBlock: use eth_getLogs filtered by registered contract addresses (cached from t_token_config). Parse each log using ERC20TransferEventParser.
- [x] 7.2 Run tests — all pass.

## 8. MQ Event Publishing — RED

- [x] 8.1 Write BlockEventPublisherTest: after block processed, TransferEventMessage published to correct topic/tag with correct fields.

## 9. MQ Event Publishing — GREEN

- [x] 9.1 Implement BlockEventPublisher: convert TransferEvent to TransferEventMessage, publish to BLOCK_TRANSFER_EVENT:DEPOSIT topic. Use tx_hash as message key.
- [x] 9.2 Run tests — all pass.

## 10. Refactor & Verify

- [x] 10.1 Review for duplication, extract shared helpers if needed. All tests still pass.
- [x] 10.2 mvn test passes for blockchain module.
