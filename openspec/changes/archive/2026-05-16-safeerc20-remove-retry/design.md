## Context

SafeERC20Caller 是平台所有链上只读调用（balanceOf、decimals、symbol、name）的统一入口。当前实现在 `ethCall()` 中对 IOException 自动重试 3 次（间隔 500ms）。虽然只读操作重试不会造成资损，但底层自动重试存在以下问题：

1. 调用方无法区分"瞬时网络抖动"和"节点持续不可用"
2. 重试策略硬编码，无法适应不同场景（对账可容忍延迟，归集需要快速失败）
3. 隐藏了错误信息，上游无法感知底层健康状态

## Goals / Non-Goals

**Goals:**
- SafeERC20Caller 单次调用、快速失败、抛出明确异常
- 上游调用方按业务场景处理 ChainCallException
- 保持 response.hasError()（合约级错误）的现有处理不变

**Non-Goals:**
- 不在本次变更中引入通用的 RPC 重试中间件
- 不修改写路径（WalletService/NonceManager）的重试策略

## Decisions

### 1. 新增 ChainCallException 替代通用 RuntimeException

IOException 包装为 `ChainCallException`（unchecked），携带 contract 地址。上游通过 catch 这个具体类型做细粒度处理，不影响其他 RuntimeException 的 catch 逻辑。

**Why:** 通用 RuntimeException 无法被上游精确 catch，且不携带业务上下文（哪个合约调用失败了）。

### 2. 各调用方处理策略

| 调用方 | 处理 |
|--------|------|
| ChainReconcileJob | catch → WARN 告警 + 跳过该 token/address，继续剩余 |
| CollectionService.checkAndCreateTask() | catch → log.error + 跳过该地址 |
| MqCompensationJob.compensateStuckDeposits() | catch → 不入账，刷新 updatedAt |
| SafeERC20CallerTest | 验证 IOException 直接抛出 ChainCallException（无重试） |

### 3. 移除而非配置化

不将重试次数改为可配置（0=不重试），而是直接移除重试代码。配置化增加了复杂度且违反"底层不决策"原则。如果未来某个调用方确实需要重试，应在该调用方层面包装 RetryTemplate。

## Risks / Trade-offs

- [Risk] 移除重试后，瞬时网络抖动导致更多单次失败 → Mitigation: 各调用方已有定时重试机制（ChainReconcileJob 每天跑、MqCompensationJob 每 5 分钟跑），单次失败下次会重新执行
- [Risk] 大量 ChainCallException 日志噪音 → Mitigation: 调用方 catch 后按 WARN 级别记录，不是 ERROR

## Implementation Context

**当前 ethCall() 实现（需修改）：**
```java
private String ethCall(String contract, Function function) {
    // 移除: for loop, Thread.sleep, MAX_RETRY_ATTEMPTS
    // 保留: 单次调用 + response.hasError() 检查
    // IOException → throw new ChainCallException(contract, e)
}
```

**ChainCallException（新建）：**
```java
package com.erc20.platform.blockchain.erc20;

public class ChainCallException extends RuntimeException {
    private final String contract;
    
    public ChainCallException(String contract, Throwable cause) {
        super("Chain call failed for contract " + contract, cause);
        this.contract = contract;
    }
    
    public String getContract() { return contract; }
}
```

**受影响的上游方法签名（不变，只增加 catch）：**
```java
// ChainReconcileJob
private void checkBalance(TokenConfig token, String address); // 内部 catch

// CollectionService  
private void checkAndCreateTask(UserAddress addr, TokenConfig token); // 已有 try-catch

// MqCompensationJob
private void compensateStuckDeposits(); // 内部对单条 catch
```
