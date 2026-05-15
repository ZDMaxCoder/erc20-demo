## Why

Many mainstream ERC-20 tokens deviate from the standard. USDT doesn't return a bool on transfer, BNB requires approve(0) before setting a new allowance, some tokens return bytes32 for decimals/name/symbol. Without a compatibility layer, the platform will fail to handle these tokens.

## What Changes

- Implement SafeERC20Caller that handles non-standard return values
- Implement ERC20TransferEventParser that parses both standard and non-standard Transfer events
- Implement TokenMetadataReader for safe metadata reading
- Configure Web3j with connection pooling and failover

## Capabilities

### New Capabilities

- `safe-erc20-caller`: Safe contract call wrapper handling non-standard implementations
- `transfer-event-parser`: Robust Transfer event log parser
- `token-metadata-reader`: Safe token metadata reading with non-standard compatibility
- `web3j-configuration`: Web3j client with connection pool, timeout, and failover

## Impact

- erc20-platform-blockchain module
- All downstream modules that interact with ERC-20 contracts depend on this
- Depends on: 001-project-foundation
