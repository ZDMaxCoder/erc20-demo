## ADDED Requirements

### Requirement: ERC20RpcClient pre-check transfer

`ERC20RpcClient` SHALL provide a `preCheckTransfer(String contract, String from, String to, BigInteger amount)` method that performs an `eth_call` to simulate the ERC-20 `transfer(to, amount)` function and returns a `CallResult` decoded via `ReturnValueDecoder`.

#### Scenario: Standard token returns true

- **WHEN** `preCheckTransfer` is called and the eth_call returns ABI-encoded `true` (0x...0001)
- **THEN** the returned `CallResult.isSuccess()` SHALL be `true`
- **THEN** the returned `CallResult.getOutcome()` SHALL be `SUCCESS`

#### Scenario: No-return-value token (USDT-like)

- **WHEN** `preCheckTransfer` is called and the eth_call returns empty/null/0x
- **THEN** the returned `CallResult.isSuccess()` SHALL be `true`
- **THEN** the returned `CallResult.getOutcome()` SHALL be `SUCCESS_NO_RETURN`

#### Scenario: Token returns false without reverting

- **WHEN** `preCheckTransfer` is called and the eth_call returns ABI-encoded `false` (0x...0000)
- **THEN** the returned `CallResult.isDangerousFalse()` SHALL be `true`
- **THEN** `CallResult.isSuccess()` SHALL be `false`

#### Scenario: Contract reverts (execution reverted)

- **WHEN** `preCheckTransfer` is called and the RPC response contains an error with "execution reverted"
- **THEN** the returned `CallResult.getOutcome()` SHALL be `REVERTED`

### Requirement: ERC20RpcClient pre-check approve

`ERC20RpcClient` SHALL provide a `preCheckApprove(String contract, String owner, String spender, BigInteger amount)` method that performs an `eth_call` to simulate the ERC-20 `approve(spender, amount)` function and returns a `CallResult`.

#### Scenario: Approve returns true

- **WHEN** `preCheckApprove` is called and the eth_call returns ABI-encoded `true`
- **THEN** `CallResult.isSuccess()` SHALL be `true`

#### Scenario: Approve reverts due to non-zero allowance

- **WHEN** `preCheckApprove` is called on a contract that requires approve-to-zero and current allowance is non-zero
- **THEN** `CallResult.getOutcome()` SHALL be `REVERTED`

### Requirement: ERC20RpcClient RPC error handling

When the underlying `eth_call` throws IOException or receives an RPC error response, `ERC20RpcClient` SHALL throw `ChainCallException` with the contract address.

#### Scenario: Network IOException

- **WHEN** `preCheckTransfer` is called and web3j throws IOException
- **THEN** a `ChainCallException` SHALL be thrown with the contract address

#### Scenario: RPC error response

- **WHEN** `preCheckTransfer` is called and the RPC returns an error object
- **THEN** for "execution reverted" errors, `CallResult.reverted()` SHALL be returned
- **THEN** for other errors, a `ChainCallException` SHALL be thrown
