## ADDED Requirements

### Requirement: TokenMetadataCache caches immutable ERC-20 metadata

The system SHALL provide a `TokenMetadataCache` component that caches `decimals`, `symbol`, and `name` values per contract address. On first access, the values SHALL be fetched via `SafeERC20Caller` and stored in a ConcurrentHashMap. Subsequent accesses SHALL return cached values without RPC calls.

#### Scenario: First access triggers RPC
- **WHEN** `getDecimals("0xabc")` is called for the first time
- **THEN** `SafeERC20Caller.safeDecimals("0xabc")` is invoked exactly once
- **AND** the result is cached

#### Scenario: Second access returns cached value
- **WHEN** `getDecimals("0xabc")` is called after a previous successful call
- **THEN** the cached value is returned without any RPC call

#### Scenario: Symbol and name are cached independently
- **WHEN** `getSymbol("0xabc")` is called after `getDecimals("0xabc")`
- **THEN** `SafeERC20Caller.safeSymbol("0xabc")` is invoked (symbol not yet cached)
- **AND** subsequent `getSymbol("0xabc")` calls return cached value

### Requirement: TokenMetadataCache invalidation

The system SHALL provide an `invalidate(String contract)` method that removes all cached metadata for the given contract address. This SHALL be called by `AdminEventMonitor` when an `Upgraded` event is detected (proxy implementation change may alter metadata).

#### Scenario: Invalidation clears cache
- **WHEN** `invalidate("0xabc")` is called
- **THEN** the next `getDecimals("0xabc")` call triggers a fresh RPC

#### Scenario: Upgraded event triggers metadata invalidation
- **WHEN** AdminEventMonitor detects an `Upgraded` event for token 0xabc
- **THEN** `TokenMetadataCache.invalidate("0xabc")` is called

### Requirement: DefaultERC20Adapter uses TokenMetadataCache for read operations

`DefaultERC20Adapter` SHALL delegate `decimals()`, `symbol()`, and `name()` calls to `TokenMetadataCache` instead of directly calling `SafeERC20Caller`. The `balanceOf` and `allowance` methods SHALL continue to call `SafeERC20Caller` directly (these values change over time).

#### Scenario: decimals() uses cache
- **WHEN** `adapter.decimals("0xabc")` is called twice
- **THEN** only one RPC call is made to the node

#### Scenario: balanceOf() bypasses cache
- **WHEN** `adapter.balanceOf("0xabc", owner)` is called twice
- **THEN** two RPC calls are made (balance is mutable, not cached)

### Requirement: Address normalization in cache

`TokenMetadataCache` SHALL normalize contract addresses to lowercase before cache lookup and storage.

#### Scenario: Mixed-case address treated as lowercase
- **WHEN** `getDecimals("0xABC123")` is called after `getDecimals("0xabc123")`
- **THEN** the cached value from the first call is returned (cache hit)
