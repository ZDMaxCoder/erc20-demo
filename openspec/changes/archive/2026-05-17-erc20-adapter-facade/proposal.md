## Why

当前 ERC-20 适配层已完成底层基础设施（ReturnValueDecoder、ERC20RpcClient、TransferConfirmer、异常体系、CallResult/TransferResult 模型），但业务层仍需直接组合 WalletService + SafeERC20Caller + TransferConfirmer 三个类完成操作，缺少统一门面；TokenRiskProfile 模型已定义但未接入运行时；任何 token 都能无条件走完整链路，无准入拒绝机制。需要补齐最后 30% 的适配层能力，让业务层通过单一接口完成所有 ERC-20 交互，同时建立 token 准入网关阻断高风险 token。

## What Changes

- 新增 `ERC20Adapter` 接口作为业务层唯一入口（读 + 写 + 确认）
- 新增 `DefaultERC20Adapter` 实现，委托已有组件
- 从 `WalletService` 提取 `SafeTransferExecutor`（预检 + nonce + 签名 + 广播）
- 新增 `TokenAdmissionGateway`，每次写操作前校验 token 准入，拒绝 REBASING/ERC-777 等高风险类型
- 新增 `TokenRiskProfileRegistry`，从 DB 加载 token 能力标签和风险等级
- 实现 approve 先置零策略（`APPROVE_RACE_CONDITION` 能力标签触发）
- 新增 `BalanceDiffChecker`，作为 `TransferConfirmer` 可选第四层确认
- 新增 `TokenAdmissionRejectedException` 和 `TransferPreCheckFailedException` 异常
- **BREAKING**: `WithdrawTransactionSenderImpl` 和 `CollectionTransactionSenderImpl` 改为调用 `ERC20Adapter` 而非直接调用 `WalletService`
- `t_token_config` 表增加 `capabilities` 和 `risk_level` 字段（Flyway V11）

## Capabilities

### New Capabilities

- `adapter-facade`: 统一 ERC20Adapter 门面接口，封装读/写/确认操作，隐藏内部组件协作细节
- `token-admission`: Token 准入网关，基于 TokenRiskProfile 拒绝高风险 token 的写操作
- `transfer-executor`: SafeTransferExecutor 提取，统一预检→nonce→签名→广播流程，含 approve 先置零策略
- `balance-diff-confirm`: BalanceDiffChecker 第四层确认，fee-on-transfer 场景检测实际到账金额

### Modified Capabilities

（无已有 spec 需修改，所有变更通过新能力引入）

## Impact

- **blockchain 模块**: 新增 adapter 门面层、准入网关、执行器；WalletService 暴露 internal 方法
- **dal 模块**: Flyway V11 迁移，TokenConfig 实体增加字段
- **service 模块**: WithdrawTransactionSenderImpl/CollectionTransactionSenderImpl 改调用链
- **测试**: 所有现有测试必须继续通过；新增适配层集成测试
- **数据库**: t_token_config 表 DDL 变更（两列 nullable，向后兼容）
