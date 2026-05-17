## Context

当前 ERC-20 交互实现散落在多个类中：
- `SafeERC20Caller`: 仅实现读接口 (balanceOf/decimals/symbol/name)，`safeTransfer()`/`safeApprove()` 是 stub
- `TransactionBuilder`: 手动编码 `transfer(address,uint256)` ABI
- `TransactionConfirmTracker`: receipt status + Transfer event 验证
- `ERC20TransferEventParser`: Transfer event 日志解析

缺少统一的返回值语义、异常分类和 token 风险模型。本 change 构建适配层最底层基础设施，为后续统一 ERC20Facade 提供类型安全的模型和核心解码能力。

系统未上线，无需兼容旧代码。

## Goals / Non-Goals

**Goals:**
- 定义适配层统一异常体系，支持精确的错误分类和上游决策
- 定义 `CallResult` 模型，统一 eth_call 返回值语义（void/bool/false-no-revert）
- 定义 `TransferResult` 模型，统一转账确认结果的四态表达
- 实现 `ReturnValueDecoder`，解码 ERC-20 函数的 optional return 值
- 定义 `TokenCapability` 枚举和 `TokenRiskProfile` 模型，为 token 风险治理提供数据基础

**Non-Goals:**
- 不实现 ERC20RpcClient（底层 RPC 调用，下一个 change）
- 不实现 TransferConfirmer（三层联合确认，后续 change）
- 不实现 CircuitBreaker（自动熔断，后续 change）
- 不修改现有业务层代码
- 不涉及数据库或配置变更

## Decisions

### 1. 异常体系设计：单根继承 + 语义子类

**选择**: `ERC20AdapterException` (RuntimeException) 为根，5 个语义子类。

**备选方案**: 使用错误码 enum + 单一异常类。

**理由**: 语义子类允许 catch 精确分支，调用方可按异常类型决定处理策略（重试/熔断/告警）。Java 8 multi-catch 语法也更清晰。异常子类数量有限（5 个），不会膨胀。

### 2. CallResult 设计：值对象 + 枚举状态

**选择**: 不可变值对象，内含 `CallOutcome` 枚举（SUCCESS / SUCCESS_NO_RETURN / RETURNED_FALSE / REVERTED / UNKNOWN）。

**备选方案**: 直接抛异常区分成功/失败。

**理由**: eth_call 的"返回 false 但未 revert"不是典型异常场景（合约层面没有 revert），用值对象表达更准确。调用方可基于 outcome 做分支而非 try-catch。

### 3. TransferResult 设计：四态模型

**选择**: `TransferOutcome` 四态 — SUCCESS / FAILED / PENDING / ANOMALY。

**备选方案**: 三态（SUCCESS/FAILED/PENDING）+ 额外 anomaly flag。

**理由**: ANOMALY 是一个独立语义，表示"链上成功但业务层检测到异常"（如 amount mismatch、balance diff 不符）。将其作为独立状态使调用方必须显式处理，而非忽略一个 flag。

### 4. TokenCapability：枚举 + EnumSet 组合

**选择**: 使用 Java 枚举，在 `TokenRiskProfile` 中以 `Set<TokenCapability>` 方式组合。

**备选方案**: 位掩码 long；或 JSON 字符串字段。

**理由**: 枚举类型安全 + EnumSet 性能最优（内部即位操作）+ IDE 补全友好。后续持久化时可序列化为逗号分隔字符串存入 DB。

### 5. 模块位置

**选择**:
- `TokenCapability` 枚举 → `erc20-platform-common` 模块（因为 service 层也需要引用）
- 其余所有类 → `erc20-platform-blockchain` 模块 `adapter/` 包

**理由**: `common` 模块是所有模块的底层依赖，枚举放在此处避免循环依赖。异常、CallResult、TransferResult、ReturnValueDecoder 都是 blockchain 模块内部能力。

## Implementation Context

### 现有关键接口/类（供实现时参考）

```java
// 现有异常 (erc20-platform-blockchain)
public class ChainCallException extends RuntimeException { ... }

// 现有 TokenType 枚举 (erc20-platform-common)
public enum TokenType {
    STANDARD("STANDARD", "Standard ERC-20 token"),
    FEE_ON_TRANSFER("FEE_ON_TRANSFER", "Fee-on-transfer token"),
    REBASING("REBASING", "Rebasing token"),
    UNSUPPORTED("UNSUPPORTED", "Unsupported token type");
}

// 现有 TransferEvent (erc20-platform-blockchain)
public class TransferEvent {
    private String contractAddress;
    private String from;
    private String to;
    private BigInteger value;
    private String txHash;
    private long blockNumber;
    private int logIndex;
}

// 现有 AlertLevel 枚举 (erc20-platform-common)
public enum AlertLevel { INFO, WARN, CRITICAL; }
```

### 新增包结构

```
erc20-platform-common/
└── src/main/java/com/erc20/platform/common/enums/
    └── TokenCapability.java

erc20-platform-blockchain/
└── src/main/java/com/erc20/platform/blockchain/adapter/
    ├── exception/
    │   ├── ERC20AdapterException.java
    │   ├── TransferRevertedException.java
    │   ├── TokenPausedException.java
    │   ├── TokenBlacklistedException.java
    │   ├── AmountMismatchException.java
    │   └── TransferEventMissingException.java
    ├── model/
    │   ├── CallResult.java
    │   ├── CallOutcome.java
    │   ├── TransferResult.java
    │   ├── TransferOutcome.java
    │   └── TokenRiskProfile.java
    └── rpc/
        └── ReturnValueDecoder.java
```

## Risks / Trade-offs

- [TokenCapability 可能不完整] → 枚举设计允许后续添加值，不影响已有代码
- [TransferResult.ANOMALY 语义边界模糊] → 通过 anomalyReason 字段携带详情，由上游按 reason 分类处理
- [ReturnValueDecoder 仅处理 bool 类型返回值] → ERC-20 标准函数只有 bool 返回值需要兼容解码，其他类型 (uint256/string) 由 MetadataResolver 处理（后续 change）
