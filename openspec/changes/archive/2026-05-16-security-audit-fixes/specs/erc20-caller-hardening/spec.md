## ADDED Requirements

### Requirement: safeBalanceOf guards against empty return

`SafeERC20Caller.safeBalanceOf()` SHALL throw a RuntimeException if the eth_call returns an empty or null result, rather than silently returning zero.

#### Scenario: Empty balanceOf response
- **WHEN** eth_call for balanceOf returns empty data (contract does not exist or is self-destructed)
- **THEN** a RuntimeException is thrown with message "balanceOf returned empty for {contract}"

#### Scenario: Valid balanceOf response
- **WHEN** eth_call for balanceOf returns valid encoded data
- **THEN** the decoded BigInteger balance is returned

### Requirement: safeDecimals does not default to 18

`SafeERC20Caller.safeDecimals()` SHALL throw a RuntimeException if decimals cannot be read from the contract, rather than defaulting to 18. Callers (token onboarding) SHALL handle this by rejecting the token.

#### Scenario: decimals call fails
- **WHEN** eth_call for decimals fails or returns undecodable data
- **THEN** a RuntimeException is thrown (no silent default to 18)

#### Scenario: decimals call succeeds
- **WHEN** eth_call for decimals returns valid encoded data (e.g., 6)
- **THEN** the correct integer is returned

### Requirement: SafeERC20Caller uses configurable from-address

`SafeERC20Caller` SHALL use a configurable caller address (from application properties) for eth_call requests instead of ZERO_ADDRESS. Default is ZERO_ADDRESS if not configured.

#### Scenario: Configured caller address
- **WHEN** `blockchain.caller-address` is set to "0xabc..."
- **THEN** all eth_call requests use "0xabc..." as the from address

#### Scenario: No configured caller address
- **WHEN** `blockchain.caller-address` is not set
- **THEN** eth_call requests use ZERO_ADDRESS as from (backward compatible)

### Requirement: SafeERC20Caller retries on transient failures

`SafeERC20Caller.ethCall()` SHALL retry up to 3 times with 500ms interval on IOException/network errors.

#### Scenario: First call fails, retry succeeds
- **WHEN** the first eth_call throws IOException but the second succeeds
- **THEN** the valid result is returned without error

#### Scenario: All retries exhausted
- **WHEN** all 3 eth_call attempts throw IOException
- **THEN** the IOException is propagated as a RuntimeException

### Requirement: decodeBytes32AsString handles ABI dynamic-type offset

`decodeBytes32AsString()` SHALL detect if the hex data begins with an ABI dynamic-type offset (`0000...0020` at position 0-64) and, if so, skip the offset and length fields to read the actual string data.

#### Scenario: Standard bytes32 encoding (no offset)
- **WHEN** hex data is 64 chars of right-padded UTF-8 bytes (e.g., "555344540000...")
- **THEN** the decoded string is "USDT"

#### Scenario: Dynamic-type encoding with offset
- **WHEN** hex data starts with offset "0000...0020" followed by length and actual bytes
- **THEN** the system reads past the offset and length to decode the actual string

### Requirement: GasEstimator distinguishes revert from network error

`GasEstimator.estimateERC20Transfer()` SHALL distinguish between an estimateGas response error (indicating contract would revert) and a network/connectivity exception. On revert, it SHALL throw a `ContractRevertException`. On network error, it SHALL fall back to DEFAULT_ERC20_GAS.

#### Scenario: estimateGas returns error response (revert)
- **WHEN** ethEstimateGas response `hasError()` is true and message contains "execution reverted"
- **THEN** a ContractRevertException is thrown with the revert reason

#### Scenario: estimateGas throws IOException (network)
- **WHEN** ethEstimateGas throws IOException
- **THEN** DEFAULT_ERC20_GAS (80000) is returned as fallback

### Requirement: TransactionBuilder uses configurable chainId

`TransactionBuilder.buildERC20Transfer()` and `buildEthTransfer()` SHALL accept chainId as a parameter instead of hardcoding `1L`.

#### Scenario: EIP-1559 transaction with chain 137
- **WHEN** building an EIP-1559 transaction for Polygon (chainId=137)
- **THEN** the RawTransaction uses chainId 137

### Requirement: ERC20TransferEventParser uses 32-byte aligned data extraction

`ERC20TransferEventParser` SHALL extract the Transfer event value from `data` using 32-byte (64 hex char) alignment, properly handling the `0x` prefix.

#### Scenario: Standard Transfer event data
- **WHEN** log data is "0x" followed by 64 hex chars representing the transfer amount
- **THEN** the amount is correctly parsed as a BigInteger

#### Scenario: Data with additional padding
- **WHEN** log data contains extra trailing bytes beyond the 32-byte value
- **THEN** only the first 32 bytes (64 hex chars after 0x) are used for amount parsing
