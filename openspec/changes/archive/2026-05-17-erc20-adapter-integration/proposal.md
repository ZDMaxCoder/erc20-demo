## Why

Phase 1（erc20-adapter-foundation）已完成适配层基础设施（CallResult/TransferResult/ReturnValueDecoder/TokenRiskProfile/异常体系），但这些模型完全孤立——零处被业务逻辑引用。审计发现多个 P0/P1 安全风险：提现金额不一致仍标记 SUCCESS（资金损失风险）、无自动熔断机制、nonce 冲突无自动修复、转账确认缺乏三层验证。需要将基础设施集成到运行时流程，消除这些风险。

## What Changes

- **新增 ERC20RpcClient**：封装 transfer/approve/balanceOf 的 RPC 调用，接入 ReturnValueDecoder 解码返回值，替代现有 WalletService 手工构建 rawTx 逻辑
- **新增 TransferConfirmer**：三层验证（receipt status → Transfer event → balance diff），输出 TransferResult 四态结果，替代 TransactionConfirmTracker 的简单 CONFIRMED/FAILED 逻辑
- **新增 eth_call 预检**：提现发送前做 eth_call 模拟，RETURNED_FALSE/REVERTED 直接拒绝，避免浪费 Gas
- **重构 WithdrawService 确认逻辑**：金额不一致时标记新状态 ANOMALY，不再自动标 SUCCESS
- **重构 AdminEventMonitor**：Paused/Upgraded 事件自动 disable token（调用 tokenConfigMapper 更新）
- **重构 WalletService 广播失败处理**：NONCE_TOO_LOW 时自动调用 resetNonce 并重试一次
- **DB 迁移**：WithdrawStatus 新增 ANOMALY 状态

## Capabilities

### New Capabilities
- `erc20-rpc-client`: ERC20RpcClient 封装链上 RPC 调用，统一返回 CallResult，处理非标返回值
- `transfer-confirmer`: TransferConfirmer 三层验证引擎，输出 TransferResult 四态结果
- `withdraw-amount-guard`: 提现金额不一致阻断，ANOMALY 状态流转
- `token-auto-fuse`: Paused/Upgraded 事件自动禁用 token
- `nonce-auto-recovery`: NONCE_TOO_LOW 自动 reset + 重试
- `transfer-pre-check`: 提现前 eth_call 预检，拦截注定失败的交易

### Modified Capabilities
- `transfer-event-verification`: TransactionConfirmTracker 被 TransferConfirmer 替代，确认逻辑从二态变四态
- `admin-event-monitoring`: 从"仅告警"升级为"告警+自动熔断"

## Impact

- **erc20-platform-blockchain**: 新增 ERC20RpcClient、TransferConfirmer；重构 WalletService（广播重试逻辑）、TransactionConfirmTracker（替换为委托 TransferConfirmer）、AdminEventMonitor（新增 auto-disable）
- **erc20-platform-service**: 重构 WithdrawService.doConfirmWithdraw()（ANOMALY 阻断）
- **erc20-platform-common**: WithdrawStatus 枚举新增 ANOMALY
- **erc20-platform-dal**: DB 迁移 V11（withdraw_record 状态字段扩展）
- **依赖**: 无新增外部依赖，使用现有 Web3j + Redisson
