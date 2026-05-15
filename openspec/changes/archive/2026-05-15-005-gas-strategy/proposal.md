## Why

Ethereum gas pricing directly affects transaction cost and confirmation speed. Post EIP-1559, gas pricing has two modes (legacy and EIP-1559). The platform needs dynamic gas pricing based on network conditions, with safety caps to prevent excessive spending during gas spikes. Stuck transactions must be accelerated via replacement.

## What Changes

- Implement EIP-1559 and Legacy gas strategy with priority levels
- Implement gas limit estimation with buffer
- Implement gas price caching to reduce RPC calls
- Implement stuck transaction detection and replacement

## Capabilities

### New Capabilities

- `gas-strategy`: Dynamic gas pricing with EIP-1559 and legacy support
- `gas-estimator`: Gas limit estimation for ERC-20 transfers
- `gas-price-cache`: Periodic gas price refresh and caching
- `stuck-transaction-handler`: Detect and accelerate stuck transactions

## Impact

- erc20-platform-blockchain module
- Redis keys for gas price cache
- Depends on: 001-project-foundation, 004-nonce-management
