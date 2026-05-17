## Why

ERC-20 适配层 Phase 1-3 已完成核心能力（准入网关、预检、四层确认、自动熔断），但在弹性和运维友好性方面存在短板：TransferConfirmer 不验证确认数（浅 reorg 风险）、metadata 每次 RPC 调用（性能浪费）、单 token 连续失败无自动保护（持续亏 gas）、对账 Job 绕过适配层直接调底层（架构一致性缺失）。本次增强补齐这些 P1 能力，使适配层达到完整生产级弹性标准。

## What Changes

- 新增 **连续失败熔断器** (ConsecutiveFailureBreaker)：某 token 连续 N 次链上 transfer 失败时自动临时熔断，与 AdminEventMonitor 的永久禁用区分，支持探针自动恢复
- 增强 **TransferConfirmer 确认数校验**：confirm 方法新增 minConfirmations 参数，确认数不足时返回 PENDING，复用 TokenConfig.depositConfirmBlocks 字段
- 新增 **TokenMetadataCache**：decimals/symbol/name 不可变数据首次 RPC 后缓存，DefaultERC20Adapter 读操作走缓存
- 重构 **ChainReconcileJob** 统一走 ERC20Adapter.balanceOf，保证对账逻辑与业务操作走相同入口

## Capabilities

### New Capabilities
- `consecutive-failure-breaker`: 连续失败自动熔断，含计数、触发、恢复、与永久禁用的区分
- `token-metadata-cache`: ERC-20 不可变 metadata 缓存，含加载、命中、失效策略

### Modified Capabilities
- `transfer-event-verification`: 确认流程增加 minConfirmations 参数，确认数不足返回 PENDING
- `chain-reconciliation`: 对账余额查询改为通过 ERC20Adapter 统一入口

## Impact

- **blockchain 模块**：新增 ConsecutiveFailureBreaker、TokenMetadataCache 组件；TransferConfirmer 增加确认数逻辑；TransactionConfirmTracker 调用熔断器记录成功/失败
- **blockchain/reconcile**：ChainReconcileJob 依赖从 SafeERC20Caller 改为 ERC20Adapter
- **dal 模块**：V12 迁移增加 t_token_config.circuit_breaker_status 字段
- **依赖变更**：无新外部依赖
- **前置依赖**：erc20-adapter-foundation、erc20-adapter-integration、erc20-adapter-facade（均已完成）
