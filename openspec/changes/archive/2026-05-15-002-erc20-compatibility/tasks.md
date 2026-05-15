## 1. Web3j Configuration (Setup)

- [x] 1.1 Create Web3jConfig class: configure HttpService with OkHttp connection pool (maxIdleConnections=10, keepAlive=30s), connect timeout 10s, read timeout 30s. Support primary and backup RPC URLs from application.yml.
- [x] 1.2 Implement Web3jProvider with automatic failover: on RpcException or timeout, switch to backup URL. Log WARN on failover.

## 2. Transfer Event Parser — RED

- [x] 2.1 Write ERC20TransferEventParserTest: standard Transfer event (3 topics, from=topics[1], to=topics[2], value=data) → correct TransferEvent with normalized lowercase addresses.
- [x] 2.2 Write test: topics[0] != TRANSFER_SIGNATURE → Optional.empty().
- [x] 2.3 Write test: topics.length == 2 (non-standard) → Optional.empty() (defensive).
- [x] 2.4 Write test: data is empty or < 32 bytes → Optional.empty().
- [x] 2.5 Write test: contractAddress normalization — mixed case input → lowercase in result.
- [x] 2.6 Write test: parseFromReceipt with multiple logs, only matching contract parsed.

## 3. Transfer Event Parser — GREEN

- [x] 3.1 Define TransferEvent value object: contractAddress, from, to, value (BigInteger), txHash, blockNumber, logIndex. @Data @Builder.
- [x] 3.2 Implement ERC20TransferEventParser.parse(Log, contractAddress): check topics[0]==TRANSFER_SIGNATURE (0xddf252ad...), standard path topics.length>=3, decode from/to/value. Return Optional.empty() on any failure.
- [x] 3.3 Implement parseFromReceipt: filter logs by contractAddress, collect successful parse results.
- [x] 3.4 Run tests — all pass.

## 4. Safe ERC-20 Caller — RED

- [x] 4.1 Write SafeERC20CallerTest (using MockWebServer for RPC): safeBalanceOf returns correct BigInteger from standard response.
- [x] 4.2 Write test: safeDecimals standard response (uint8) returns correct int.
- [x] 4.3 Write test: safeDecimals non-standard (bytes32) returns correct int.
- [x] 4.4 Write test: safeDecimals failure returns default 18 with WARN log.
- [x] 4.5 Write test: safeSymbol standard (string) returns correct value.
- [x] 4.6 Write test: safeSymbol non-standard (bytes32 with null padding) returns correct string.

## 5. Safe ERC-20 Caller — GREEN

- [x] 5.1 Implement SafeERC20Caller.safeBalanceOf: encode balanceOf(address), eth_call, decode uint256.
- [x] 5.2 Implement safeTransfer: encode transfer(address,uint256), handle no-return-value (check receipt status only) and bool-return-value cases.
- [x] 5.3 Implement safeApprove: try approve(spender, amount), on failure with non-zero allowance do approve(spender, 0) then approve(spender, amount).
- [x] 5.4 Implement safeDecimals: try uint8 decode, fallback to bytes32 decode, fallback to 18.
- [x] 5.5 Implement safeSymbol: try string decode, fallback to bytes32 → strip nulls → string.
- [x] 5.6 Run tests — all pass.

## 6. Token Metadata Reader — RED

- [x] 6.1 Write TokenMetadataReaderTest: successful read returns TokenMetadata with name, symbol, decimals.
- [x] 6.2 Write test: partial failure (decimals fails) → returns TokenMetadata with decimals=null, other fields populated.

## 7. Token Metadata Reader — GREEN

- [x] 7.1 Implement TokenMetadataReader: read name(), symbol(), decimals() using SafeERC20Caller. Return null for failed fields. Create TokenMetadata DTO.
- [x] 7.2 Run tests — all pass.

## 8. Refactor

- [x] 8.1 Extract ABI encoding/decoding utilities if duplicated. Verify all tests still pass.
- [x] 8.2 Verify mvn test passes for erc20-platform-blockchain module.
