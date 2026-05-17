## ADDED Requirements

### Requirement: NONCE_TOO_LOW triggers automatic nonce reset and retry

When `WalletService.sendERC20Transfer()` receives a `BroadcastResult` with `errorType == NONCE_TOO_LOW`, the system SHALL:
1. Call `nonceManager.resetNonce(chainId, fromAddress)` to re-sync from chain
2. Re-allocate a fresh nonce
3. Rebuild, re-sign, and re-broadcast the transaction (one retry only)
4. If the retry also fails, throw `BizException(ErrorCode.BROADCAST_FAILED)` and release the new nonce

#### Scenario: First broadcast gets NONCE_TOO_LOW, retry succeeds

- **WHEN** the first broadcast returns `BroadcastErrorType.NONCE_TOO_LOW`
- **THEN** `nonceManager.resetNonce()` SHALL be called
- **THEN** `nonceManager.allocateNonce()` SHALL be called again to get a fresh nonce
- **THEN** the transaction SHALL be rebuilt with the new nonce, re-signed, and re-broadcast
- **THEN** if the retry succeeds, a `TransactionRecord` SHALL be persisted with the new txHash

#### Scenario: First broadcast gets NONCE_TOO_LOW, retry also fails

- **WHEN** the first broadcast returns `NONCE_TOO_LOW` and the retry broadcast also fails (any error type)
- **THEN** `nonceManager.releaseNonce()` SHALL be called for the retried nonce
- **THEN** `BizException(ErrorCode.BROADCAST_FAILED)` SHALL be thrown

#### Scenario: Non-NONCE_TOO_LOW errors do not trigger retry

- **WHEN** the broadcast returns `BroadcastErrorType.INSUFFICIENT_FUNDS`
- **THEN** no retry SHALL be attempted
- **THEN** the original nonce SHALL be released
- **THEN** `BizException(ErrorCode.BROADCAST_FAILED)` SHALL be thrown immediately

### Requirement: sendEthTransfer also handles NONCE_TOO_LOW

`WalletService.sendEthTransfer()` SHALL apply the same NONCE_TOO_LOW auto-recovery logic (reset + retry once) as `sendERC20Transfer()`.

#### Scenario: ETH transfer NONCE_TOO_LOW recovery

- **WHEN** `sendEthTransfer()` first broadcast returns `NONCE_TOO_LOW`
- **THEN** the same reset-and-retry flow SHALL execute
- **THEN** if retry succeeds, a `TransactionRecord` with the new txHash SHALL be persisted
