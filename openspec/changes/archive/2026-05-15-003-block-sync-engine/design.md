## Context

Ethereum blocks are produced ~12 seconds apart. The platform must sync each block sequentially, parse Transfer events for registered tokens, and detect chain reorganizations. A reorg occurs when the canonical chain switches to a different fork, invalidating previously seen blocks.

## Goals / Non-Goals

**Goals:**
- Sync blocks sequentially with no gaps
- Detect reorgs by verifying parentHash continuity
- Roll back affected data on reorg (max depth 50 blocks)
- Publish parsed Transfer events to MQ for downstream processing

**Non-Goals:**
- Not implementing parallel block processing (must be sequential for reorg safety)
- Not implementing websocket subscriptions (polling is more reliable)

## Decisions

### Reorg detection: parentHash chain verification
Every synced block's parentHash is checked against the stored hash of the previous block. Mismatch = reorg detected.
**Why:** Simple, reliable, no additional infrastructure needed. Works with any Ethereum node.

### Max reorg depth: 50 blocks
If reorg depth exceeds 50, halt sync and raise CRITICAL alert for manual intervention.
**Why:** Ethereum reorgs beyond a few blocks are extremely rare. 50 blocks provides large safety margin while bounding the rollback cost.

### Single-writer pattern with distributed lock
Only one instance syncs at a time, enforced by distributed lock.
**Why:** Prevents duplicate processing and race conditions on block progress updates in multi-instance deployments.

### Event publishing: at-least-once with idempotency downstream
Save block data to DB first, then publish MQ messages. If MQ publish fails, compensation job re-publishes.
**Why:** Ensures no events are lost. Downstream consumers handle duplicates via idempotent keys.

## Risks / Trade-offs

- [Risk] RPC node latency causes sync lag → Mitigation: batch-size config, backup node failover
- [Risk] Transaction receipt fetching is slow for large blocks → Mitigation: filter logs by registered contract addresses using eth_getLogs instead of fetching all receipts

## Implementation Context

**Depends on from previous changes:**
```java
// From 002 - ERC20TransferEventParser
Optional<TransferEvent> parse(Log log, String contractAddress);

// TransferEvent value object
contractAddress, from, to, value(BigInteger), txHash, blockNumber, logIndex

// From 001 - Database tables
t_block_sync_progress: chain_id, last_synced_block, last_synced_block_hash, status
t_block_record: block_number, block_hash, parent_hash, chain_id, tx_count, synced_at
t_token_config: contract_address, decimals, deposit_confirm_blocks, enabled
t_deposit_record: status column (can be set to REORGED)

// From 001 - @DistributedLock annotation
@DistributedLock(key = "'block_sync:' + #chainId", leaseTime = 30000)
```

**Configuration:**
```yaml
blockchain:
  sync:
    chain-id: 1
    start-block: latest
    batch-size: 5
    poll-interval: 3000
    max-reorg-depth: 50
    rpc-url: http://localhost:8545
    backup-rpc-url: http://localhost:8546
```
