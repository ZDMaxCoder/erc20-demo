## Context

Phase 1（erc20-adapter-foundation）建立了适配层基础设施：
- `CallResult`（五态）+ `ReturnValueDecoder`：解码 ERC-20 函数返回值
- `TransferResult`（四态）：转账确认结果的语义封装
- `TokenRiskProfile`：Token 风险画像和能力标签
- 异常体系：ERC20AdapterException + 5 个语义子类

这些模型目前**零集成**到业务逻辑。现有处理分散在：
- `WalletService`：手工构建 rawTx，广播失败统一处理
- `TransactionConfirmTracker`：二态确认（CONFIRMED/FAILED），不做金额对比
- `WithdrawService`：金额不一致仅告警但标记 SUCCESS
- `AdminEventMonitor`：Paused/Upgraded 仅告警，不自动禁用

## Goals / Non-Goals

**Goals:**
- 将 Phase 1 基础设施集成到运行时流程，消除 P0/P1 安全风险
- 金额不一致时阻断提现（ANOMALY 状态），保护平台资金
- 自动熔断异常 Token，减少人工干预窗口
- NONCE_TOO_LOW 自动恢复，提高系统自愈能力
- 三层转账验证，覆盖非标代币的静默失败场景
- 提现前 eth_call 预检，避免浪费 Gas

**Non-Goals:**
- 不重构充值流程（TokenType 门控策略保持不变）
- 不实现 CircuitBreaker 自动熔断计数（仅做事件驱动的 disable）
- 不修改归集流程（归集复用 ERC20RpcClient 但不改变调度逻辑）
- 不引入 balance diff 到充值流程（留给后续 Phase）
- 不删除 SafeERC20Caller（保留其读操作 balanceOf/decimals/symbol/name）

## Decisions

### Decision 1: ERC20RpcClient 作为写操作统一入口

**选择**：新建 `ERC20RpcClient` 类，封装 transfer/approve 的 eth_call 预检和返回值解码。

**不是**替代 `WalletService`，而是被 `WalletService` 调用：
- `ERC20RpcClient.preCheckTransfer(contract, from, to, amount)` → 返回 `CallResult`
- `ERC20RpcClient.preCheckApprove(contract, owner, spender, amount)` → 返回 `CallResult`
- `WalletService.sendERC20Transfer()` 在构建交易前先调用 preCheck

**替代方案**：将预检逻辑直接放入 GasEstimator（已有 eth_call 逻辑）
**否决原因**：职责混淆，GasEstimator 关注 gas 估算，不应承担返回值语义解析

```java
package com.erc20.platform.blockchain.adapter.rpc;

@Component
public class ERC20RpcClient {
    private final Web3j web3j;
    private final ReturnValueDecoder decoder;
    
    // eth_call 模拟 transfer，解码返回值
    public CallResult preCheckTransfer(String contract, String from, String to, BigInteger amount);
    
    // eth_call 模拟 approve，解码返回值
    public CallResult preCheckApprove(String contract, String owner, String spender, BigInteger amount);
}
```

### Decision 2: TransferConfirmer 替换 TransactionConfirmTracker 内部逻辑

**选择**：新建 `TransferConfirmer` 类，实现三层验证，`TransactionConfirmTracker` 委托给它。

**不是**删除 `TransactionConfirmTracker`，而是将其确认逻辑提取为 `TransferConfirmer`，保留 `TransactionConfirmTracker` 作为定时任务调度器。

三层验证流程：
1. **Receipt 层**：receipt.status == "0x1"？否 → FAILED
2. **Event 层**：存在 Transfer event？否 → FAILED（TransferEventMissingException）
3. **Amount 层**：actualAmount == expectedAmount？否 → ANOMALY（AmountMismatchException）

可选的第四层（仅当 TokenRiskProfile.requiresBalanceDiff() 为 true）：
4. **Balance Diff 层**：balanceDiff == actualAmount？否 → ANOMALY

```java
package com.erc20.platform.blockchain.adapter;

@Component
public class TransferConfirmer {
    private final Web3j web3j;
    private final ERC20TransferEventParser eventParser;
    private final SafeERC20Caller safeERC20Caller; // 用于 balanceOf

    // 核心方法：给定 txHash + 期望参数，返回 TransferResult
    public TransferResult confirm(String txHash, String contract, 
                                  BigInteger expectedAmount, String toAddress);
}
```

**替代方案**：直接修改 TransactionConfirmTracker
**否决原因**：TransferConfirmer 是纯逻辑、易测试的组件；TransactionConfirmTracker 混杂了定时任务、DB 操作、MQ 发送，不利于单元测试

### Decision 3: WithdrawStatus 新增 ANOMALY 状态

**选择**：在 `WithdrawStatus` 枚举中新增 `ANOMALY("ANOMALY", "Amount mismatch detected")`。

状态流转：`PENDING_CONFIRM → ANOMALY`（金额不一致时）

DB 迁移 V11：无 schema 变更（status 字段为 VARCHAR，直接存储新枚举值）。

WithdrawService.doConfirmWithdraw() 改造：
- TransferResult.outcome == SUCCESS → 标记 WithdrawStatus.SUCCESS，正常出金
- TransferResult.outcome == ANOMALY → 标记 WithdrawStatus.ANOMALY，不调用 decreaseFrozen，发 CRITICAL 告警
- TransferResult.outcome == FAILED → 调用 failWithdraw()

### Decision 4: AdminEventMonitor 自动 disable

**选择**：检测到 Paused/Upgraded 事件后，直接更新 `TokenConfig.enabled = false`。

```java
// AdminEventMonitor.processLog() 中新增
token.setEnabled(0);
tokenConfigMapper.updateById(token);
```

同时发送 CRITICAL 告警（保留现有行为），告警内容追加"token已自动禁用"。

**替代方案**：通过事件 + 异步消费来禁用
**否决原因**：这是安全关键路径，需要同步生效，不应有消息延迟

### Decision 5: WalletService NONCE_TOO_LOW 自动恢复

**选择**：在 `WalletService.sendERC20Transfer()` 中识别 `BroadcastErrorType.NONCE_TOO_LOW`，执行：
1. 调用 `nonceManager.resetNonce(chainId, from)` 重新从链上同步
2. 重新分配 nonce 并重试一次（仅一次，避免无限循环）
3. 第二次仍失败则正常抛异常

```java
if (broadcastResult.getErrorType() == BroadcastErrorType.NONCE_TOO_LOW) {
    nonceManager.resetNonce(chainId, from);
    nonce = nonceManager.allocateNonce(chainId, from);
    // rebuild + re-sign + re-broadcast
    ...
}
```

### Decision 6: WalletService 集成 ERC20RpcClient 预检

**选择**：`sendERC20Transfer()` 在分配 nonce 之前调用 `erc20RpcClient.preCheckTransfer()`。

```java
CallResult preCheck = erc20RpcClient.preCheckTransfer(contract, from, to, amount);
if (!preCheck.isSuccess()) {
    if (preCheck.isDangerousFalse()) {
        throw new BizException(ErrorCode.CHAIN_ERROR, "Transfer would return false");
    }
    throw new BizException(ErrorCode.CHAIN_ERROR, "Transfer pre-check failed: " + preCheck.getOutcome());
}
long nonce = nonceManager.allocateNonce(chainId, from);
...
```

预检在 nonce 分配之前，避免失败后需要 release nonce。

## Implementation Context

### 现有关键接口签名

```java
// NonceManager
public long allocateNonce(int chainId, String walletAddress);
public void resetNonce(int chainId, String walletAddress);
public void releaseNonce(int chainId, String walletAddress, long nonce);

// BroadcastResult
public boolean isSuccess();
public BroadcastErrorType getErrorType();
public String getErrorMessage();

// BroadcastErrorType
NONE, NONCE_TOO_LOW, ALREADY_KNOWN, UNDERPRICED, INSUFFICIENT_FUNDS, UNKNOWN

// ReturnValueDecoder (adapter/rpc/)
public CallResult decodeBoolReturn(String hexResult);

// CallResult (adapter/model/)
public boolean isSuccess();
public boolean isDangerousFalse();
public CallOutcome getOutcome();

// TransferResult (adapter/model/)
public TransferOutcome getOutcome();
public boolean isAmountConsistent();
public boolean hasBalanceDiffAnomaly();
public static TransferResult failed(String txHash, String reason);
public static TransferResult pending(String txHash);

// ERC20TransferEventParser
public List<TransferEvent> parseFromReceipt(TransactionReceipt receipt, String contractAddress);

// TransferEvent
public String getFrom();
public String getTo();
public BigInteger getValue();

// WithdrawStatus (要修改)
PENDING_REVIEW, APPROVED, SIGNING, BROADCASTING, PENDING_CONFIRM, SUCCESS, FAILED, REJECTED

// SafeERC20Caller (保留读操作)
public BigInteger safeBalanceOf(String contract, String owner);

// TokenConfigMapper
int updateById(TokenConfig entity);

// AlertService
void alert(String alertType, AlertLevel level, String content);
```

### 包结构

```
blockchain/adapter/
├── exception/       — (Phase 1, 已有)
├── model/           — (Phase 1, 已有: CallResult, TransferResult, etc.)
├── rpc/             — ReturnValueDecoder (已有) + ERC20RpcClient (新增)
└── TransferConfirmer.java  — (新增)
```

### 模块依赖

- `ERC20RpcClient` 在 erc20-platform-blockchain，依赖 Web3j + ReturnValueDecoder
- `TransferConfirmer` 在 erc20-platform-blockchain，依赖 Web3j + ERC20TransferEventParser + SafeERC20Caller
- `WalletService` 改造在 erc20-platform-blockchain，新增依赖 ERC20RpcClient
- `TransactionConfirmTracker` 改造在 erc20-platform-blockchain，新增依赖 TransferConfirmer
- `WithdrawService` 改造在 erc20-platform-service，依赖新的 TransferResult 消息结构
- `AdminEventMonitor` 改造在 erc20-platform-blockchain，无新增依赖
- `WithdrawStatus` 改造在 erc20-platform-common

## Risks / Trade-offs

**[Risk] preCheck 与实际执行状态不一致** → eth_call 基于当前 state，到实际上链期间合约状态可能变化（如刚好被 pause）。Mitigation: preCheck 是防御层不是保证层，TransferConfirmer 在确认时仍做完整验证。

**[Risk] NONCE_TOO_LOW 重试导致重复交易** → 如果原始交易实际已被广播成功（节点延迟响应），重试可能导致同地址同 nonce 双花。Mitigation: 使用相同 nonce 重试，EVM 保证同 nonce 只会上链一个；resetNonce 从 PENDING 计数获取，包含已广播的。

**[Risk] AdminEventMonitor 自动 disable 导致误禁** → 某些合约可能 emit 类似 topic 但不是真正暂停。Mitigation: 仅监听严格匹配的 keccak256 topic 签名，且发 CRITICAL 告警让运维知晓。

**[Risk] ANOMALY 状态下资金处理** → 用户资金仍处于冻结状态，需要人工介入决策。Mitigation: ANOMALY 告警包含完整信息（expected/actual/txHash），运维可手动标记 SUCCESS 或 FAILED。

**[Trade-off] TransferConfirmer balance diff 需要额外 RPC 调用** → 对 STANDARD token（大多数情况）不做 balance diff（仅做 receipt + event + amount 对比），只有 TokenRiskProfile.requiresBalanceDiff() 返回 true 时才做。当前 Phase 不集成 TokenRiskProfile 到确认流程（简化），后续 Phase 再加。
