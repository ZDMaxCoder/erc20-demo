## Why

Tokens deposited to individual user addresses must be collected (归集) into the platform hot wallet for centralized management and withdrawal execution. Collection requires two-step transactions: first supply ETH for gas, then transfer ERC-20 tokens.

## What Changes

- Implement collection scanning and task creation
- Implement gas supply service (ETH transfer to user address)
- Implement ERC-20 collection execution
- Implement cold/hot wallet balance management

## Capabilities

### New Capabilities

- `collection-service`: Scan, schedule, and execute fund collection
- `gas-supply-service`: Supply ETH for gas to user addresses
- `wallet-transfer-service`: Hot/cold wallet balance monitoring

## Impact

- erc20-platform-service module
- Uses WalletService for transactions
- Depends on: 006-wallet-service, 007-deposit-service
