## Context

`IdempotentKeyGenerator` 是幂等保证的核心工具类，被 `DepositService`、`WithdrawService` 及归集流程调用。当前生成的 key 格式不含 chainId，隐含假设平台仅服务单链。

现有 chainId 分布：
- `TokenConfig.chainId` — 每个代币配置已绑定链
- `WalletConfig.chainId`、`NonceRecord.chainId`、`TransactionRecord.chainId` — 区块链层已有链意识
- `DepositRecord`、`WithdrawRecord` — **缺失 chainId**

## Goals / Non-Goals

**Goals:**
- 所有幂等键包含 chainId，确保多链场景下唯一性
- DepositRecord/WithdrawRecord 具备 chainId 字段，支撑后续多链查询和统计
- 改动最小化，不影响现有业务流程

**Non-Goals:**
- 不涉及多链并行扫块逻辑（DepositConfirmJob 的多链遍历属于后续工作）
- 不涉及 CollectionService 实现（collectionKey 目前仅在 Generator 中定义，尚未被调用）
- 不涉及 Redis 消费去重 key 改造（BaseConsumer 使用 msgId，非业务幂等键）

## Implementation Context

```java
// 当前接口 — erc20-platform-common
public final class IdempotentKeyGenerator {
    public static String depositKey(String txHash, int logIndex);
    public static String withdrawKey(String requestId);
    public static String collectionKey(String fromAddress, long tokenId, long blockNumber);
}

// 调用方 — DepositService
TokenConfig tokenConfig = ...; // 已查询，含 chainId
String idempotentKey = IdempotentKeyGenerator.depositKey(event.getTxHash(), event.getLogIndex());

// 调用方 — WithdrawService
TokenConfig tokenConfig = tokenConfigMapper.selectById(request.getTokenId()); // 含 chainId
String idempotentKey = IdempotentKeyGenerator.withdrawKey(request.getRequestId());
```

## Decisions

### 1. chainId 位于 key 最前缀

格式：`{chainId}_{txHash}_{logIndex}`、`WD_{chainId}_{requestId}`、`COL_{chainId}_{addr}_{tokenId}_{block}`

**理由**：chainId 在前便于人工排查和按链分片查询；业务前缀（WD_/COL_）保留用于区分类型。

**备选**：chainId 放末尾 — 不利于可读性和索引前缀匹配。

### 2. chainId 类型为 int

与现有实体（TokenConfig、NonceRecord 等）保持一致，均为 `Integer chainId`。

### 3. DB 列定义 `chain_id INT NOT NULL`

无 DEFAULT 值，强制要求写入时指定。无存量数据无需默认值兼容。

### 4. 不保留旧方法签名

无存量数据、无外部调用方，直接破坏性修改。不提供重载方法避免误用。

## Risks / Trade-offs

- [编译错误扩散] 所有调用 IdempotentKeyGenerator 的代码必须同步修改 → 改动集中在 service 层两处，可控
- [collectionKey 无调用方] 当前 collectionKey 未被使用，但仍一并改造保证一致性 → 无风险
- [DepositConfirmJob 硬编码 DEFAULT_CHAIN_ID=1] 本次不改，属于多链扫块范畴 → 记录为后续工作
