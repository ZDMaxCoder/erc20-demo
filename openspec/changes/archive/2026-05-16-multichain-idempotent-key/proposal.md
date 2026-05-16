## Why

IdempotentKeyGenerator 当前生成的幂等键不包含 chainId，在多链部署场景下存在键冲突风险。尤其是 `collectionKey`（基于地址+tokenId+区块号），同一地址在不同链的相同区块高度完全可能产生归集操作，导致幂等判断失效。从可扩展性角度，所有幂等键应包含链标识以确保跨链唯一性。

## What Changes

- **BREAKING**: `IdempotentKeyGenerator` 所有方法签名新增 `int chainId` 参数，生成的 key 前缀包含 chainId
- `DepositRecord` 和 `WithdrawRecord` 实体新增 `chainId` 字段
- 新增 Flyway V10 迁移脚本，为 `t_deposit_record` 和 `t_withdraw_record` 表添加 `chain_id` 列
- `DepositService` 和 `WithdrawService` 调用处适配，从 `TokenConfig.chainId` 获取链 ID 传入
- 所有相关单元测试更新

## Capabilities

### New Capabilities

- `multichain-idempotent-key`: 幂等键生成支持多链隔离，确保不同链上的充值、提现、归集操作产生不同的幂等键

### Modified Capabilities

## Impact

- `erc20-platform-common`: IdempotentKeyGenerator 接口变更（破坏性）
- `erc20-platform-domain`: DepositRecord、WithdrawRecord 实体新增字段
- `erc20-platform-dal`: Flyway 迁移脚本新增 V10
- `erc20-platform-service`: DepositService、WithdrawService 调用适配
- 无存量数据，无需数据迁移
