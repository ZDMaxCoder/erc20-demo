## 1. RED — Transaction Builder Tests

- [x] 1.1 Write TransactionBuilderTest: buildERC20Transfer encodes correct function selector (0xa9059cbb) + ABI-encoded address + amount. Verify resulting RawTransaction has correct to=contract, value=0, data=encoded.
- [x] 1.2 Write test: buildEthTransfer creates RawTransaction with to=recipient, value=amount, data=empty.
- [x] 1.3 Write test: EIP-1559 transaction type selected when GasPrice.isEip1559()==true.

## 2. GREEN — Transaction Builder

- [x] 2.1 Implement TransactionBuilder.buildERC20Transfer: encode transfer(address,uint256) function call. Support both legacy and EIP-1559 types.
- [x] 2.2 Implement buildEthTransfer: simple value transfer.
- [x] 2.3 Run tests — all pass.

## 3. RED — Transaction Broadcaster Tests

- [x] 3.1 Write TransactionBroadcasterTest (MockWebServer): successful broadcast returns txHash.
- [x] 3.2 Write test: RPC returns "nonce too low" → BroadcastResult with error type NONCE_TOO_LOW.
- [x] 3.3 Write test: "already known" → treated as success (ALREADY_KNOWN).
- [x] 3.4 Write test: "replacement transaction underpriced" → UNDERPRICED error type.
- [x] 3.5 Write test: "insufficient funds" → INSUFFICIENT_FUNDS error type.

## 4. GREEN — Transaction Broadcaster

- [x] 4.1 Implement TransactionBroadcaster.broadcast: call eth_sendRawTransaction, parse response. Return BroadcastResult with success/error classification.
- [x] 4.2 Support multi-node broadcast: if backup RPC configured, send to both. ALREADY_KNOWN from second = success.
- [x] 4.3 Run tests — all pass.

## 5. Transaction Signer (Interface + Dev impl)

- [x] 5.1 Define TransactionSigner interface: String sign(RawTransaction rawTx, int chainId).
- [x] 5.2 Implement LocalKeySigner (@Profile("dev")): load private key from config, use Web3j Credentials to sign.
- [x] 5.3 Implement KmsTransactionSigner stub (@Profile("prod")): throws UnsupportedOperationException.

## 6. RED — Wallet Service Integration Tests

- [x] 6.1 Write WalletServiceTest: sendERC20Transfer orchestrates nonce allocation → gas estimation → build → sign → broadcast → save to DB. Mock all dependencies. Verify TransactionRecord saved with correct fields.
- [x] 6.2 Write test: broadcast fails → nonce released, exception thrown, no record saved with CONFIRMED status.
- [x] 6.3 Write test: replaceTransaction → new tx has same nonce, higher gas, original marked REPLACED.

## 7. GREEN — Wallet Service

- [x] 7.1 Implement WalletService.sendERC20Transfer: full pipeline allocateNonce → estimateGas → getGasPrice → build → sign → broadcast → save TransactionRecord(status=PENDING). On failure: releaseNonce + throw.
- [x] 7.2 Implement sendEthTransfer: same pipeline, gasLimit=21000.
- [x] 7.3 Implement replaceTransaction: load original, get replacement gas, rebuild with same nonce, sign, broadcast, update original to REPLACED.
- [x] 7.4 Implement queryTransactionStatus: eth_getTransactionReceipt → map to TxStatus.
- [x] 7.5 Run tests — all pass.

## 8. RED — Confirm Tracker Tests

- [x] 8.1 Write TransactionConfirmTrackerTest: PENDING tx with receipt status=1 on chain → updated to CONFIRMED, NonceManager.confirmNonce called.
- [x] 8.2 Write test: receipt status=0 → updated to FAILED.
- [x] 8.3 Write test: no receipt → stays PENDING.

## 9. GREEN — Confirm Tracker

- [x] 9.1 Implement TransactionConfirmTracker @Scheduled(fixedDelay=5000): scan PENDING records, check receipt, update status, publish TxStatusChangedMessage to MQ.
- [x] 9.2 Run tests — all pass.

## 10. REFACTOR & Verify

- [x] 10.1 Review error handling paths, ensure nonce is always released on failure. All tests pass.
- [x] 10.2 mvn test passes.
