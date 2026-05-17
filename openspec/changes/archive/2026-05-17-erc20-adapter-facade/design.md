## Context

ERC-20 适配层已完成两轮重构（erc20-adapter-foundation + erc20-adapter-integration），底层基础设施就绪：

- **已有组件**: ReturnValueDecoder, ERC20RpcClient(preCheck), TransferConfirmer(3层), AdminEventMonitor(自动熔断), SafeERC20Caller(读接口+bytes32兼容), WalletService(nonce+sign+broadcast+NONCE_TOO_LOW恢复)
- **已有模型**: CallResult/CallOutcome(五态), TransferResult/TransferOutcome(四态), TokenRiskProfile, TokenCapability(13枚举), RiskLevel(4级)
- **已有异常**: ERC20AdapterException + 5个语义子类（已定义未运行时使用）
- **现状问题**: 业务层(WithdrawTransactionSenderImpl, CollectionTransactionSenderImpl)直接调用 WalletService，无统一入口、无准入检查、无策略路由

## Goals / Non-Goals

**Goals:**
- 建立 ERC20Adapter 统一门面，业务层通过单一接口完成所有 ERC-20 交互
- 建立 Token 准入网关，高风险 token(REBASING/ERC-777)在写操作前被显式拒绝
- 将 TokenRiskProfile 接入运行时，驱动策略选择（approve置零、balance-diff确认）
- 从 WalletService 提取 SafeTransferExecutor，职责单一化
- 补齐 balance-diff 第四层确认能力（fee-on-transfer 场景）
- 所有现有测试继续通过，零破坏性变更

**Non-Goals:**
- 不改变底层 nonce 管理、签名、广播机制（WalletService 保留这些职责）
- 不重构 TransferConfirmer 的前三层逻辑（仅新增第四层）
- 不实现 Token 能力自动探测（本轮靠 DB 配置）
- 不实现 ERC-777 的 ERC-165 自动检测（本轮靠 DB 标签）
- 不引入新的外部依赖
- 不改变 DepositService 的充值链路（充值走事件监听，不经适配层写操作）

## Decisions

### D1: ERC20Adapter 作为接口而非抽象类

**选择**: 定义 `ERC20Adapter` 接口 + `DefaultERC20Adapter` 实现类

**理由**: 接口允许测试时 mock 替换；未来多链扩展可有不同实现；符合项目已有模式（WithdrawTransactionSender 接口 + Impl）

**替代方案**: 抽象类模板方法 — 拒绝，因为不需要共享状态，组合优于继承

### D2: SafeTransferExecutor 提取而非 WalletService 内部重构

**选择**: 新建 `SafeTransferExecutor` 类，WalletService 暴露 `sendERC20TransferInternal`/`sendApproveInternal` 包级方法

**理由**: WalletService 已有 ETH 转账、replace transaction、query status 等职责；ERC-20 特有逻辑（预检+准入）属于适配层，不应留在 WalletService

**替代方案**: 在 WalletService 内部增加准入检查 — 拒绝，违反单一职责且增加 WalletService 对 TokenRiskProfile 的依赖

### D3: 准入网关在 Adapter 层而非 Service 层

**选择**: `TokenAdmissionGateway` 放在 blockchain 模块的 adapter 包内，由 `DefaultERC20Adapter` 在写操作前调用

**理由**: 准入是 ERC-20 交互的前置条件，与链上行为紧耦合；放在 service 层会导致每个 service 都要记得调用

**替代方案**: 在 WithdrawService/CollectionService 各自检查 — 拒绝，散落重复且易遗漏

### D4: TokenRiskProfileRegistry 用 ConcurrentHashMap 缓存 + 手动失效

**选择**: 本地 ConcurrentHashMap 缓存，AdminEventMonitor 熔断时调用 `invalidate(contract)`

**理由**: token profile 变更极少，不需要 TTL 过期；主动失效比定时刷新更精确；避免引入新缓存依赖

**替代方案**: Caffeine/Guava Cache with TTL — 过度设计，token 数量有限（通常 <100）

### D5: BalanceDiffChecker 作为可选第四层而非强制

**选择**: 仅当 `TokenRiskProfile.requiresBalanceDiff()` 返回 true 时启用（FEE_ON_TRANSFER 或 riskLevel >= HIGH）

**理由**: 标准 token 无需额外 RPC 调用（balanceOf before/after）；减少确认延迟；fee-on-transfer token 在当前准入策略下默认被拒绝（除非 autoProcessingAllowed=true）

**替代方案**: 所有 token 都做 balance-diff — 拒绝，增加 2次 RPC/每笔确认，对标准 token 无价值

### D6: approve 先置零策略内置于 DefaultERC20Adapter.safeApprove()

**选择**: 当 `TokenRiskProfile.requiresApproveReset()` 为 true 且新 amount > 0 时，先发一笔 approve(spender, 0) 再发 approve(spender, amount)

**理由**: approve race condition 是已知 ERC-20 缺陷（OpenZeppelin 文档建议先置零）；归集场景需要 approve

**替代方案**: 改用 increaseAllowance — 很多 token 不支持此方法，兼容性差

### D7: 新增两个异常类扩展已有体系

**选择**: `TokenAdmissionRejectedException` 和 `TransferPreCheckFailedException` 继承 `ERC20AdapterException`

**理由**: 复用已有异常层次；业务层可统一 catch `ERC20AdapterException` 处理所有适配层错误

**替代方案**: 抛 BizException — 拒绝，混淆业务异常和链上异常的语义边界

## Implementation Context

### 已有关键接口/类签名（供 session 隔离使用）

```java
// adapter/model/TokenRiskProfile — 已存在
public class TokenRiskProfile {
    String contractAddress;
    Set<TokenCapability> capabilities;
    RiskLevel riskLevel;
    boolean admissionPassed;
    long lastAuditTime;
    boolean autoProcessingAllowed;

    public boolean requiresBalanceDiff();      // FEE_ON_TRANSFER || riskLevel >= HIGH
    public boolean requiresApproveReset();     // APPROVE_RACE_CONDITION
    public boolean isStandardProcessing();     // LOW risk && !FEE_ON_TRANSFER && !REBASING
}

// adapter/model/CallResult — 已存在
public class CallResult {
    CallOutcome outcome; // SUCCESS, SUCCESS_NO_RETURN, RETURNED_FALSE, REVERTED, UNKNOWN
    String rawValue;
    public boolean isSuccess();               // SUCCESS || SUCCESS_NO_RETURN
    public boolean isDangerousFalse();        // RETURNED_FALSE
}

// adapter/model/TransferResult — 已存在
public class TransferResult {
    TransferOutcome outcome; // SUCCESS, FAILED, PENDING, ANOMALY
    String txHash; BigInteger actualAmount, expectedAmount, balanceDiff;
    String anomalyReason; List<TransferEvent> events;
}

// adapter/rpc/ERC20RpcClient — 已存在
@Component
public class ERC20RpcClient {
    CallResult preCheckTransfer(String contract, String from, String to, BigInteger amount);
    CallResult preCheckApprove(String contract, String owner, String spender, BigInteger amount);
}

// adapter/TransferConfirmer — 已存在
@Component
public class TransferConfirmer {
    TransferResult confirm(String txHash, String contract, BigInteger expectedAmount, String toAddress);
}

// blockchain/wallet/WalletService — 已存在，需暴露 internal 方法
@Service
public class WalletService {
    String sendERC20Transfer(String from, String to, String contract, BigInteger amount, GasPriority priority);
    // 新增: String sendERC20TransferInternal(String from, String to, String contract, BigInteger amount);
    // 新增: String sendApproveInternal(String owner, String contract, String spender, BigInteger amount);
}

// blockchain/erc20/SafeERC20Caller — 已存在
@Component
public class SafeERC20Caller {
    BigInteger safeBalanceOf(String contract, String owner);
    int safeDecimals(String contract);
    String safeSymbol(String contract);
    String safeName(String contract);
}

// service/WithdrawTransactionSender — 已存在接口
public interface WithdrawTransactionSender {
    String sendERC20Transfer(String from, String to, String contract, long amount, int exponent);
}
```

### DB 变更（Flyway V11）

```sql
ALTER TABLE t_token_config ADD COLUMN capabilities VARCHAR(512) DEFAULT NULL COMMENT 'token能力标签,逗号分隔';
ALTER TABLE t_token_config ADD COLUMN risk_level VARCHAR(16) DEFAULT 'LOW' COMMENT '风险等级:LOW/MEDIUM/HIGH/CRITICAL';
```

## Risks / Trade-offs

- **[WalletService internal 方法暴露]** → 使用包级可见性（无 public 修饰），SafeTransferExecutor 与 WalletService 同包
- **[approve 先置零增加一笔交易]** → 仅对 APPROVE_RACE_CONDITION 标记的 token 生效，标准 token 无额外开销
- **[balance-diff 依赖 balanceOf 时间点]** → 在 TransferConfirmer 确认时查询 after balance，before balance 由调用方在 transfer 发起前记录并传入 ConfirmRequest
- **[ConcurrentHashMap 缓存无容量限制]** → token 数量有限（<100），不会 OOM；AdminEventMonitor 熔断时主动 invalidate
- **[DB 字段 nullable]** → 未配置 capabilities 的 token 默认视为 STANDARD_RETURN + LOW risk + admissionPassed=true（向后兼容已有 token）

## Migration Plan

1. **Phase 1**: 新增类 + 新增异常 + DB 迁移（非破坏性，所有新增）
2. **Phase 2**: WalletService 暴露 internal 方法 + SafeTransferExecutor 实现
3. **Phase 3**: DefaultERC20Adapter 组装 + WithdrawTransactionSenderImpl/CollectionTransactionSenderImpl 切换调用
4. **回滚策略**: Phase 3 如出问题，Impl 类切回直接调 WalletService（一行代码回滚）

## Open Questions

（无 — 所有设计决策已在前两轮重构中验证，本轮是补齐最后一环）
