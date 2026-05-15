## Context

Every outgoing transaction follows the same pipeline: allocate nonce → determine gas → build raw tx → sign → broadcast → track confirmation. This change implements the complete pipeline as a reusable service consumed by withdrawal, collection, and gas supply flows.

## Goals / Non-Goals

**Goals:**
- Unified sendERC20Transfer / sendEthTransfer / replaceTransaction API
- Pluggable signing for different environments
- Robust broadcast with error classification and recovery
- Automatic confirmation tracking

**Non-Goals:**
- Not implementing batch transaction optimization
- Not implementing the actual withdrawal/collection business logic (those are separate changes)

## Decisions

### TransactionSigner as interface with profile-based implementations
- `@Profile("dev")`: LocalKeySigner loads private key from config
- `@Profile("prod")`: KmsTransactionSigner calls external KMS API
**Why:** Clean separation. Dev doesn't need KMS setup. Prod never exposes raw private keys.

### Error classification on broadcast
Map eth_sendRawTransaction errors to actions:
- "nonce too low" → re-allocate nonce, retry
- "already known" → ignore (already in mempool)
- "replacement transaction underpriced" → increase gas ≥10%, retry
- "insufficient funds" → halt + CRITICAL alert
- Other → log + alert
**Why:** Different errors require different recovery strategies. Blanket retry would make things worse.

### Immediate DB persistence after broadcast
TransactionRecord is saved immediately after successful eth_sendRawTransaction, before confirmation.
**Why:** If process crashes after broadcast but before save, the transaction exists on-chain with no local record. Persisting immediately enables recovery.

## Risks / Trade-offs

- [Risk] KMS latency adds signing delay → Mitigation: batch signing where possible, timeout + fallback
- [Risk] Multi-node broadcast may cause "already known" → Mitigation: classify as success, not error

## Implementation Context

**Depends on:**
```java
// From 004 - NonceManager
long allocateNonce(int chainId, String walletAddress);
void confirmNonce(int chainId, String walletAddress, long confirmedNonce);
void releaseNonce(int chainId, String walletAddress, long nonce);

// From 005 - GasStrategy
GasPrice getGasPrice(GasPriority priority);
GasPrice getReplacementGasPrice(GasPrice original);
BigInteger estimateERC20Transfer(String contract, String from, String to, BigInteger amount);

// From 001 - Database
t_transaction_record: tx_hash, chain_id, from_address, to_address, tx_type, nonce,
  gas_price, gas_used, gas_limit, status, block_number, block_hash, raw_tx, replaced_by_tx_hash
t_wallet_config: wallet_type, address, enabled

// Web3j
web3j.ethSendRawTransaction(signedTxHex)
web3j.ethGetTransactionReceipt(txHash)
web3j.ethGetBlockByNumber("latest")
```

**Key outputs:**
```java
TransactionRecord sendERC20Transfer(String from, String to, String contract, BigInteger amount, GasPriority priority);
TransactionRecord sendEthTransfer(String from, String to, BigInteger amountWei, GasPriority priority);
TransactionRecord replaceTransaction(String txHash, boolean cancel);
TxStatus queryTransactionStatus(String txHash);
```
