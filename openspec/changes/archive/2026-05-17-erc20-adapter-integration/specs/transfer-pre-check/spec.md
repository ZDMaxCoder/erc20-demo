## ADDED Requirements

### Requirement: Pre-check transfer before nonce allocation

`WalletService.sendERC20Transfer()` SHALL call `ERC20RpcClient.preCheckTransfer()` BEFORE allocating a nonce. If the pre-check result is not successful, the method SHALL throw `BizException` without allocating or consuming a nonce.

#### Scenario: Pre-check succeeds, proceed to send

- **WHEN** `preCheckTransfer` returns `CallResult` with `isSuccess() == true`
- **THEN** the flow SHALL proceed to allocate nonce and send the transaction

#### Scenario: Pre-check returns RETURNED_FALSE

- **WHEN** `preCheckTransfer` returns `CallResult` with `isDangerousFalse() == true`
- **THEN** `BizException(ErrorCode.CHAIN_ERROR)` SHALL be thrown with message indicating "Transfer would return false"
- **THEN** no nonce SHALL be allocated

#### Scenario: Pre-check returns REVERTED

- **WHEN** `preCheckTransfer` returns `CallResult` with outcome `REVERTED`
- **THEN** `BizException(ErrorCode.CHAIN_ERROR)` SHALL be thrown
- **THEN** no nonce SHALL be allocated

#### Scenario: Pre-check RPC failure (ChainCallException)

- **WHEN** `preCheckTransfer` throws `ChainCallException` (network error)
- **THEN** the exception SHALL propagate as `BizException(ErrorCode.CHAIN_ERROR)`
- **THEN** no nonce SHALL be allocated

### Requirement: Pre-check does not apply to ETH transfers

`WalletService.sendEthTransfer()` SHALL NOT perform ERC-20 pre-check since native ETH transfers do not have a return-value semantic.

#### Scenario: ETH transfer skips pre-check

- **WHEN** `sendEthTransfer()` is called
- **THEN** `ERC20RpcClient.preCheckTransfer()` SHALL NOT be invoked
- **THEN** the flow SHALL proceed directly to nonce allocation
