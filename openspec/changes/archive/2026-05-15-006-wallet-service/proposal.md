## Why

The platform needs to construct, sign, and broadcast Ethereum transactions for withdrawals, fund collection, and gas supply. Transaction signing must be abstracted to support different key management solutions (local keys for dev, KMS/HSM for production).

## What Changes

- Implement transaction builder (ERC-20 transfer encoding, ETH transfer)
- Implement pluggable transaction signer (local dev, KMS prod)
- Implement transaction broadcaster with error classification
- Implement transaction confirmation tracker
- Implement WalletService as the unified entry point

## Capabilities

### New Capabilities

- `wallet-service`: Unified interface for sending ERC-20 and ETH transactions
- `transaction-builder`: Raw transaction construction with ABI encoding
- `transaction-signer`: Pluggable signing (local/KMS)
- `transaction-broadcaster`: Broadcast with multi-node support and error handling
- `transaction-confirm-tracker`: Periodic confirmation checking

## Impact

- erc20-platform-blockchain module
- Depends on: 004-nonce-management, 005-gas-strategy, 002-erc20-compatibility
