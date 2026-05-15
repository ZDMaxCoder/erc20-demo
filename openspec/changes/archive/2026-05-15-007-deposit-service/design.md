## Context

Deposit flow: User sends ERC-20 tokens to their assigned address → block sync detects Transfer event → MQ message published → deposit service creates record → confirmation job waits for N blocks → once confirmed, credit user balance.

Critical safety requirements:
- Never credit a deposit twice (idempotency via txHash+logIndex)
- Never credit a deposit on a reorged block (reorg handling)
- Never credit below minimum amount (spam protection)

## Goals / Non-Goals

**Goals:**
- Allocate unique deposit addresses per user per token
- Detect deposits via MQ consumption (at-least-once, idempotent)
- Track confirmation count and credit when threshold reached
- Handle reorgs by reversing credited deposits

**Non-Goals:**
- Not implementing HD wallet key derivation crypto (use Web3j/bitcoinj)
- Not implementing the account balance logic here (separate change 009)

## Decisions

### Pre-generated address pool
Generate addresses in batches (e.g., 100 at a time), store as AVAILABLE. Allocation picks from pool.
**Why:** Avoids latency of key derivation during user request. Pool can be refilled by background job.

### BIP-44 derivation path: m/44'/60'/0'/0/{index}
Standard Ethereum derivation path. Index stored in t_user_address.address_index.
**Why:** Industry standard. Compatible with hardware wallets for key recovery.

### Confirmation counting via scheduled job (not MQ)
A periodic job (every 10s) checks all CONFIRMING deposits and updates confirm count based on current block height.
**Why:** Simpler than publishing a message for every new block. Confirmation is a function of (current_block - deposit_block), easily computed.

### Reorg handling: reverse credit with negative flow
If a deposit was already credited (credited=true) and its block is reorged, deduct the amount and record a reversal flow.
**Why:** Ensures balance consistency. Reversal flow creates an audit trail.

## Risks / Trade-offs

- [Risk] Address pool exhaustion during traffic spike → Mitigation: alert when pool < threshold, async refill
- [Risk] Reorg reversal makes user balance negative → Mitigation: alert operator, user likely withdrew already — this is a loss scenario requiring manual intervention

## Implementation Context

**Depends on:**
```java
// MQ message from block-sync-engine
TransferEventMessage: contractAddress, from, to, value(String), txHash, blockNumber, blockHash, logIndex, timestamp

// From 001 - utilities
AmountUtil.fromChainAmount(BigInteger chainAmount, int tokenDecimals, int amountExponent) -> long
IdempotentKeyGenerator.depositKey(txHash, logIndex) -> String

// From 001 - tables
t_user_address: user_id, address, address_index, token_id, status(AVAILABLE/BINDIED/DISABLED)
t_deposit_record: all fields (see schema)
t_token_config: contract_address, decimals, deposit_confirm_blocks, min_deposit_amount, enabled

// AccountService (from 009, but interface defined here)
void increaseAvailable(AccountOperateRequest request);
// If 009 not yet implemented, define the interface and use a stub
```

**Key outputs:**
```java
String allocateDepositAddress(long userId, long tokenId);
Optional<UserAddress> findByAddress(String address);
void onTransferEvent(TransferEventMessage message);  // MQ consumer
void creditDeposit(long depositId);  // @Transactional
```
