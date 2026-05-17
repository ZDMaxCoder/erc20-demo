## Why

当前 ERC-20 交互能力散落在 TransactionBuilder（手动编码 ABI）、SafeERC20Caller（仅读接口，写接口为 stub）、TransactionConfirmTracker（结果确认）等多个类中，缺乏统一的调用抽象、返回值语义、异常分类和风险模型。需要构建一个底层基础设施层，为后续统一的 ERC-20 适配层提供类型安全的模型定义和核心解码能力。

## What Changes

- 新增统一异常体系：`ERC20AdapterException` 基类及 5 个语义子类（Reverted/Paused/Blacklisted/AmountMismatch/EventMissing）
- 新增统一返回模型：`CallResult`（eth_call 结果，含 optional return 语义）、`TransferResult`（转账确认结果，四态模型）
- 新增 `ReturnValueDecoder`：解码 ERC-20 函数返回值，兼容 void / bool / false-no-revert 三种模式
- 新增 `TokenCapability` 枚举：描述 token 的能力标签（NO_RETURN_VALUE / APPROVE_RACE_CONDITION / PAUSABLE / BLACKLISTABLE / UPGRADEABLE 等）
- 新增 `TokenRiskProfile` 模型：组合 capabilities + risk level，提供风险决策辅助方法

## Capabilities

### New Capabilities

- `erc20-return-value-decoding`: ERC-20 函数返回值解码，兼容 void return / bool return / false-no-revert 三种非标准模式
- `erc20-adapter-models`: 适配层统一模型定义，包括异常体系、调用结果、转账结果、Token 能力标签和风险画像

### Modified Capabilities

（无，本 change 纯新增基础设施，不修改现有 spec 级行为）

## Impact

- 新增包：`erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/`
- 子包：`model/`、`exception/`、`rpc/`
- 新增枚举可能位于 `erc20-platform-common` 模块（TokenCapability）
- 不修改现有业务代码，后续 change 将基于这些模型重构上层逻辑
- 无数据库变更、无配置变更、无 API 变更
