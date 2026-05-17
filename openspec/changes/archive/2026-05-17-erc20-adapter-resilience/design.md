## Context

ERC-20 适配层 Phase 1-3 已完成，提供了完整的准入网关、预检、执行、四层确认和 Paused/Upgraded 自动熔断能力。当前缺乏：连续链上失败的自动保护、确认数不足的安全门、metadata 重复 RPC 调用的性能浪费、对账绕过适配层的架构不一致。

现有关键组件：
- `TransferConfirmer.confirm(txHash, contract, expectedAmount, toAddress, balanceBefore)` — 四层确认
- `TransactionConfirmTracker.scanPendingTransactions()` — 5s 定时扫描 PENDING 交易
- `TokenRiskProfileRegistry` — ConcurrentHashMap 缓存 + invalidate
- `AdminEventMonitor` — Paused/Upgraded → `token.enabled=0` + `registry.invalidate()`
- `SafeERC20Caller` — 每次 RPC 读取 decimals/symbol/name
- `ChainReconcileJob` — 直接依赖 `SafeERC20Caller.safeBalanceOf`
- `TokenConfig.depositConfirmBlocks` — 已存在的确认数配置字段

## Goals / Non-Goals

**Goals:**
- 连续 N 次链上 transfer 失败时自动临时熔断单个 token，防止持续亏 gas
- TransferConfirmer 在确认数不足时返回 PENDING，防止浅 reorg 造成的误确认
- decimals/symbol/name 首次 RPC 后缓存，减少不必要 RPC 调用
- ChainReconcileJob 统一走 ERC20Adapter.balanceOf，保持架构一致性

**Non-Goals:**
- 不实现全局 RPC 限流（P2，后续 change）
- 不实现自动恢复探针的完整调度（本期仅预留 reset 接口，探针调度 P2）
- 不新增 safeTransferFrom（P2）
- 不修改 AdminEventMonitor 的永久禁用逻辑

## Implementation Context

### 关键接口签名

```java
// TransferConfirmer 当前签名
public TransferResult confirm(String txHash, String contract,
                              BigInteger expectedAmount, String toAddress)
public TransferResult confirm(String txHash, String contract,
                              BigInteger expectedAmount, String toAddress,
                              BigInteger balanceBefore)

// TransactionConfirmTracker 调用点
TransferResult result = transferConfirmer.confirm(
    tx.getTxHash(), contractAddress, expectedAmount, tx.getToAddress());

// DefaultERC20Adapter 读操作
public BigInteger balanceOf(String contract, String owner) {
    return safeERC20Caller.safeBalanceOf(contract, owner);
}
public int decimals(String contract) {
    return safeERC20Caller.safeDecimals(contract);
}

// ChainReconcileJob 当前依赖
private final SafeERC20Caller safeERC20Caller;
BigInteger chainBalance = safeERC20Caller.safeBalanceOf(contractAddress, walletAddress);

// TokenConfig 已有字段
private Integer depositConfirmBlocks;
```

### 数据库现有字段
- `t_token_config.enabled` (TINYINT): 0/1，AdminEventMonitor 设为 0 表示永久禁用
- `t_token_config.deposit_confirm_blocks` (INT): 确认数阈值

## Decisions

### D1: 连续失败熔断器的状态存储 — Redis AtomicLong

**选择**: Redis AtomicLong (`circuit_breaker:{contract}:failures`) + DB 字段 `circuit_breaker_status`

**替代方案**:
- 方案 A: 纯内存 ConcurrentHashMap — 重启丢失，多实例不共享 ❌
- 方案 B: 纯 DB 字段 — 每次确认都要更新 DB，高频写入不合适 ❌
- 方案 C (选择): Redis 计数 + DB 状态位 — 计数高频走 Redis，触发/恢复时才写 DB ✅

**理由**: 计数操作频率高（每 5s 一批），Redis AtomicLong 适合高频原子递增；熔断状态变更是低频操作，写 DB 保证持久化。

### D2: 临时熔断与永久禁用的区分

**选择**: 新增 `t_token_config.circuit_breaker_status` 字段 (VARCHAR 16)，值为 `CLOSED`/`OPEN`/`HALF_OPEN`

- `CLOSED`: 正常，计数归零
- `OPEN`: 临时熔断中，拒绝新操作（但 AdminEventMonitor 的 `enabled=0` 优先级更高）
- `HALF_OPEN`: 预留给探针恢复（本期不实现调度，仅 reset 方法）

**与 `enabled` 字段关系**: `enabled=0` 是永久禁用（需人工恢复），`circuit_breaker_status=OPEN` 是临时熔断（可自动恢复）。准入检查顺序：`enabled` → `circuit_breaker_status` → `admission`。

### D3: 确认数校验的参数来源

**选择**: `TransactionConfirmTracker` 从 `TokenConfig.depositConfirmBlocks` 读取确认数阈值，传递给 `TransferConfirmer`

**理由**: 该字段已存在且业务含义一致——达到多少确认数才认为交易安全。充值和提现共用同一阈值，合理。

### D4: Metadata 缓存层的位置

**选择**: 新增 `TokenMetadataCache` 组件，注入 `DefaultERC20Adapter`，替代直接调用 `SafeERC20Caller`

**替代方案**:
- 方案 A: 在 SafeERC20Caller 内部加缓存 — 职责混淆（Caller 是 RPC 包装，不应管缓存）❌
- 方案 B (选择): 独立 Cache 组件 — 职责清晰，可独立测试和 invalidate ✅

### D5: ChainReconcileJob 改造方式

**选择**: 将依赖从 `SafeERC20Caller` 改为 `ERC20Adapter`

**注意**: 对账不应触发准入拒绝（disabled token 也需要对账），所以使用 `ERC20Adapter.balanceOf` 而非 `safeTransfer`。读操作不经过 `TokenAdmissionGateway`（当前 `DefaultERC20Adapter.balanceOf` 就是直接委托 SafeERC20Caller，不走准入），架构上统一入口即可。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| 连续失败熔断误触发（网络抖动导致批量失败） | 阈值可配置（默认 5），OPEN 时可人工 reset |
| Redis 计数器与 DB 状态不一致（Redis 重启） | 启动时从 DB 读取 circuit_breaker_status 初始化；Redis 丢失仅意味着重新计数，不会遗漏已触发的熔断 |
| 确认数校验增加确认延迟 | 这是预期行为（更安全），depositConfirmBlocks 通常 12-20，5s 轮询 * 12 = 60s 额外延迟 |
| Metadata 缓存过期问题 | decimals/symbol/name 是合约不可变数据，无过期风险；proxy upgraded 时 registry.invalidate 同时清 metadata 缓存 |

## Migration Plan

1. 部署 V12 迁移脚本（增加 `circuit_breaker_status` 字段，默认 `CLOSED`）
2. 部署新代码（向后兼容：缺少字段时默认 CLOSED）
3. 无数据迁移需求
4. 回滚：删除字段 + 回滚代码即可，不影响业务数据

## Open Questions

- 连续失败阈值是否需要 per-token 配置？当前设计为全局默认值（application.yml），后续可按需改为 per-token。
