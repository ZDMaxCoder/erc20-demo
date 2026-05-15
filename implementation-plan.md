# ERC-20 充值/提现中心化平台 — Claude Code 落地实施方案

## 设计原则

1. **会话隔离**：每个 Claude Code 会话专注一个模块，避免上下文溢出
2. **渐进构建**：先基础设施，再核心模块，最后集成联调
3. **每次会话开头提供必要上下文**：通过精简的 prompt 把前序模块的接口/模型告知当前会话

---

## 整体架构概览

```
┌────────────────────────────────────────────────────────────┐
│                      API Gateway                            │
├──────────┬──────────┬──────────┬──────────┬────────────────┤
│ 地址管理  │ 充值服务  │ 提现服务  │ 账务服务  │   风控服务     │
├──────────┴──────────┴──────────┴──────────┴────────────────┤
│                    钱包服务 (签名/广播)                       │
├────────────────────────────────────────────────────────────┤
│              区块同步 + 交易解析引擎                          │
├──────────┬─────────────────────────────────────────────────┤
│  MySQL   │  Redis  │  RocketMQ  │  Ethereum Node (RPC)     │
└──────────┴─────────┴────────────┴──────────────────────────┘
```

---

## 会话执行计划

### 会话 1：项目骨架初始化

```
Claude Code Prompt:
```

```text
创建一个 Java 8 / Maven / Spring Boot 2.7.x 多模块项目，项目名为 erc20-platform。

模块划分：
- erc20-platform-common: 公共工具、常量、异常、通用模型
- erc20-platform-domain: 领域模型、枚举、值对象
- erc20-platform-dal: 数据访问层（MyBatis-Plus + MySQL）
- erc20-platform-service: 核心业务逻辑
- erc20-platform-blockchain: 区块链交互（Web3j）
- erc20-platform-mq: RocketMQ 生产者/消费者
- erc20-platform-api: REST API + Spring Boot 启动入口
- erc20-platform-admin: 管理后台 API

父 POM 统一管理依赖版本：
- Spring Boot 2.7.18
- Web3j 4.9.8
- MyBatis-Plus 3.5.3
- RocketMQ Spring Boot Starter 2.2.3
- Redisson 3.23.5
- Guava 32.1.3-jre
- HikariCP (Spring Boot 内置)
- MapStruct 1.5.5.Final
- Lombok

要求：
1. 父 POM 中用 dependencyManagement 统一管理
2. 每个子模块只声明自己需要的依赖，不声明版本
3. 配置 maven-compiler-plugin source/target 为 1.8
4. common 模块提供：统一响应体 Result<T>、业务异常 BizException、错误码枚举 ErrorCode
5. 生成标准 .gitignore
6. application.yml 放在 api 模块，分 dev/test/prod profile
```

---

### 会话 2：数据库 Schema 设计

```
Claude Code Prompt:
```

```text
为 ERC-20 充值提现平台设计 MySQL 数据库 Schema。请生成完整的建表 SQL 文件（放在 erc20-platform-dal/src/main/resources/db/migration/ 目录下，用 Flyway 命名规范）。

技术约束：
- 金额用两个字段表示：amount BIGINT (最小单位整数值) + amount_exponent TINYINT (小数位数)
- 所有表包含 id(BIGINT AUTO_INCREMENT), created_at, updated_at, version(乐观锁)
- 所有金额相关字段用 BIGINT UNSIGNED
- 使用 UTF8MB4 字符集

需要的表：

1. t_token_config - ERC-20 代币配置
   - token_symbol, token_name, contract_address(唯一索引), decimals
   - min_deposit_amount, min_withdraw_amount
   - deposit_confirm_blocks (充值确认数)
   - enabled (是否启用)

2. t_user_address - 用户充值地址
   - user_id, address(唯一索引), address_index(HD派生索引)
   - token_id (外键关联 token_config)
   - status (AVAILABLE/BINDIED/DISABLED)
   - 联合索引 (user_id, token_id)

3. t_deposit_record - 充值记录
   - user_id, token_id, address
   - tx_hash(唯一索引), block_number, block_hash, log_index
   - amount, amount_exponent
   - confirm_blocks (当前确认数), required_confirm_blocks
   - status (PENDING/CONFIRMING/SUCCESS/REORGED/FAILED)
   - credited (是否已入账 BOOLEAN)
   - idempotent_key (tx_hash + log_index 组合唯一索引)

4. t_withdraw_record - 提现记录
   - request_id(业务幂等键，唯一索引)
   - user_id, token_id, to_address
   - amount, amount_exponent, fee_amount, fee_exponent
   - status (PENDING_REVIEW/APPROVED/SIGNING/BROADCASTING/PENDING_CONFIRM/SUCCESS/FAILED/REJECTED)
   - tx_hash, nonce, gas_price, gas_limit, block_number
   - retry_count, last_error
   - reviewed_by, reviewed_at

5. t_block_sync_progress - 区块同步进度
   - chain_id, last_synced_block, last_synced_block_hash
   - status (SYNCING/PAUSED/ERROR)
   - 唯一索引 (chain_id)

6. t_block_record - 已同步区块记录（用于重组检测）
   - block_number, block_hash, parent_hash, chain_id
   - tx_count, synced_at
   - 唯一索引 (chain_id, block_number)

7. t_wallet_config - 钱包配置
   - wallet_type (HOT/COLD/COLLECTION)
   - address, name, description
   - balance_alert_threshold (余额告警阈值)
   - enabled

8. t_nonce_record - Nonce 管理
   - chain_id, wallet_address
   - current_nonce, pending_nonce
   - 唯一索引 (chain_id, wallet_address)

9. t_transaction_record - 链上交易统一记录
   - tx_hash, chain_id, from_address, to_address
   - tx_type (DEPOSIT_COLLECTION/WITHDRAW/GAS_SUPPLY)
   - nonce, gas_price, gas_used, gas_limit
   - status (PENDING/CONFIRMED/FAILED/REPLACED)
   - block_number, block_hash
   - raw_tx (原始签名交易 hex)
   - replaced_by_tx_hash (加速/替换后新 tx_hash)

10. t_account_balance - 用户账户余额
    - user_id, token_id
    - available_balance, available_exponent
    - frozen_balance, frozen_exponent
    - 唯一索引 (user_id, token_id)

11. t_account_flow - 账务流水
    - user_id, token_id
    - flow_type (DEPOSIT/WITHDRAW/WITHDRAW_FEE/FREEZE/UNFREEZE)
    - amount, amount_exponent
    - direction (IN/OUT)
    - before_balance, after_balance
    - biz_id, biz_type (关联业务单据)
    - idempotent_key (唯一索引，保证幂等)

12. t_collection_task - 归集任务
    - from_address, to_address, token_id
    - amount, amount_exponent
    - tx_hash, status (PENDING/BROADCASTING/SUCCESS/FAILED)
    - trigger_type (AUTO/MANUAL)

13. t_alert_record - 告警记录
    - alert_type, alert_level (INFO/WARN/CRITICAL)
    - content, handled (是否已处理)
    - handler, handled_at

请在每张表上添加适当的索引，并在 SQL 注释中说明索引用途。
```

---

### 会话 3：领域模型与公共组件

```
Claude Code Prompt:
```

```text
在 erc20-platform 项目中实现领域模型和公共组件。

## 1. erc20-platform-common 模块

需要实现：

### 1.1 金额工具类 AmountUtil
- 提供 toMinUnit(BigDecimal humanReadable, int exponent) -> long 
- 提供 toHumanReadable(long minUnit, int exponent) -> BigDecimal
- 提供 toChainAmount(long minUnit, int amountExponent, int tokenDecimals) -> BigInteger（转为链上 wei/最小单位）
- 提供 fromChainAmount(BigInteger chainAmount, int tokenDecimals, int amountExponent) -> long
- 所有计算禁止浮点，全部用 BigDecimal / BigInteger

### 1.2 以太坊地址工具类 AddressUtil
- 校验地址格式（EIP-55 checksum 和 lowercase 都支持）
- 标准化地址（统一小写存储，展示时用 checksum 格式）

### 1.3 幂等键生成器 IdempotentKeyGenerator
- deposit: txHash + "_" + logIndex
- withdraw: "WD_" + requestId
- collection: "COL_" + fromAddress + "_" + tokenId + "_" + blockNumber

### 1.4 分布式锁工具 DistributedLock（基于 Redisson）
- 注解 @DistributedLock(key, waitTime, leaseTime)
- AOP 切面实现

### 1.5 重试工具 RetryTemplate
- 支持配置最大重试次数、退避策略（指数退避）
- 支持指定可重试异常类型

### 1.6 通用枚举
- DepositStatus, WithdrawStatus, TxStatus, WalletType, FlowType, FlowDirection, AlertLevel

## 2. erc20-platform-domain 模块

为上一会话设计的每张表生成对应的：
- Entity 类（MyBatis-Plus @TableName 注解）
- 所有金额字段用 long 类型 + 对应 exponent 字段

注意 Java 8 兼容，不要使用 Java 8 以上的特性。使用 Lombok @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor。
```

---

### 会话 4：ERC-20 非标准实现兼容层

```
Claude Code Prompt:
```

```text
在 erc20-platform-blockchain 模块中，实现对 ERC-20 标准和非标准实现的兼容层。

## 背景
许多主流 ERC-20 代币的实现与标准有偏差：
1. USDT (Tether): transfer/approve 函数不返回 bool 值（无返回值）
2. BNB: approve 要求先将 allowance 设为 0 再设新值
3. 部分代币 Transfer 事件的 indexed 参数数量不同
4. 部分代币 decimals() 返回 bytes32 而非 uint8
5. 部分代币 name()/symbol() 返回 bytes32 而非 string
6. 部分代币有 fee-on-transfer（转账扣税）

## 需要实现

### 1. SafeERC20Caller — 安全调用 ERC-20 合约
基于 Web3j，封装 ERC-20 合约的调用，处理非标准返回值：

```java
public class SafeERC20Caller {
    // 安全调用 transfer，兼容无返回值的情况
    // 逻辑：发送交易后检查 receipt.status，如果函数有返回值则检查 bool，无返回值只要 status=1 即视为成功
    CompletableFuture<TransactionReceipt> safeTransfer(String contractAddress, String to, BigInteger amount);
    
    // 安全调用 approve，兼容 BNB 类需要先清零的情况
    CompletableFuture<TransactionReceipt> safeApprove(String contractAddress, String spender, BigInteger amount);
    
    // 安全读取 balanceOf
    BigInteger safeBalanceOf(String contractAddress, String owner);
    
    // 安全读取 decimals，兼容返回 bytes32 的情况
    int safeDecimals(String contractAddress);
    
    // 安全读取 symbol，兼容 bytes32
    String safeSymbol(String contractAddress);
}
```

### 2. ERC20TransferEventParser — Transfer 事件解析器
```java
public class ERC20TransferEventParser {
    // 标准 Transfer(address indexed from, address indexed to, uint256 value)
    // event signature: 0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef
    
    // 解析逻辑：
    // 1. 检查 log.topics[0] == TRANSFER_EVENT_SIGNATURE
    // 2. 标准实现：topics.length == 3, from=topics[1], to=topics[2], value=data
    // 3. 非标准：topics.length == 2 或 data 中包含多个参数的情况也要兼容
    // 4. 返回 Optional<TransferEvent>，解析失败返回 empty 而非抛异常
    
    Optional<TransferEvent> parse(Log log, String contractAddress);
    List<TransferEvent> parseFromReceipt(TransactionReceipt receipt, String contractAddress);
}
```

### 3. TransferEvent 值对象
```java
@Data @Builder
public class TransferEvent {
    private String contractAddress;
    private String from;
    private String to;
    private BigInteger value;
    private String txHash;
    private long blockNumber;
    private int logIndex;
}
```

### 4. TokenMetadataReader — 代币元数据读取
- 读取合约的 name, symbol, decimals
- 兼容所有非标准返回值类型
- 读取失败时返回 null 而非抛异常，由上层决定是否拒绝该代币

### 5. Web3j 配置类
- 配置 HttpService 连接池
- 支持主备节点自动切换
- 连接超时、读取超时配置化

使用 Web3j 4.9.8，Java 8 兼容。所有异步操作使用 CompletableFuture。
每个类加必要的 SLF4J 日志（INFO 级别记关键操作，WARN 记兼容性回退，ERROR 记失败）。
```

---

### 会话 5：区块同步引擎

```
Claude Code Prompt:
```

```text
在 erc20-platform-blockchain 模块实现区块同步引擎。

## 核心需求
1. 从以太坊节点持续同步新区块
2. 检测链重组（reorg）并回滚受影响的数据
3. 解析区块中的 ERC-20 Transfer 事件
4. 通过 RocketMQ 发送事件消息供下游消费

## 已有上下文

数据库表：
- t_block_sync_progress: chain_id, last_synced_block, last_synced_block_hash, status
- t_block_record: block_number, block_hash, parent_hash, chain_id, tx_count, synced_at
- t_token_config: contract_address, decimals, deposit_confirm_blocks, enabled

已有组件：
- ERC20TransferEventParser.parse(Log log, String contractAddress) -> Optional<TransferEvent>
- TransferEvent: contractAddress, from, to, value, txHash, blockNumber, logIndex

## 需要实现

### 1. BlockSyncEngine — 区块同步主引擎
```java
@Component
public class BlockSyncEngine {
    // 定时任务，每 3 秒检查一次是否有新区块
    @Scheduled(fixedDelay = 3000)
    public void syncNewBlocks();
    
    // 单区块同步逻辑
    // 1. 获取 lastSyncedBlock + 1 的区块
    // 2. 验证 parentHash == 上一区块的 hash（重组检测）
    // 3. 如果不匹配，触发 reorg 处理
    // 4. 如果匹配，解析区块内所有相关交易的 ERC-20 事件
    // 5. 更新同步进度
    private void processBlock(long blockNumber);
}
```

### 2. ReorgHandler — 链重组处理器
```java
@Component
public class ReorgHandler {
    // 重组处理逻辑：
    // 1. 从当前区块向前回溯，找到分叉点（parentHash 匹配的最近区块）
    // 2. 将分叉点之后的所有区块标记为 REORGED
    // 3. 将这些区块关联的充值记录状态改为 REORGED
    // 4. 发送 MQ 消息通知账务服务进行冲正
    // 5. 重置同步进度到分叉点
    // 6. 发送告警
    // 最大回溯深度 50 个区块，超过则暂停同步并告警
    public void handleReorg(long currentBlockNumber, String expectedParentHash, String actualParentHash);
}
```

### 3. TransferEventExtractor — 从区块中提取 Transfer 事件
```java
@Component  
public class TransferEventExtractor {
    // 1. 获取区块内所有交易的 receipt
    // 2. 过滤出 logs 中 address 在 t_token_config 中已注册的合约
    // 3. 调用 ERC20TransferEventParser 解析
    // 4. 返回该区块所有解析成功的 TransferEvent 列表
    public List<TransferEvent> extractFromBlock(EthBlock.Block block);
}
```

### 4. BlockEventPublisher — 区块事件 MQ 发布
- Topic: BLOCK_TRANSFER_EVENT
- Tag: DEPOSIT / COLLECTION_CONFIRM
- 消息体: TransferEventMessage (JSON)
- 发送前保证本地事务已提交（事务消息或先落库后发送+补偿）

### 5. 配置项 (application.yml)
```yaml
blockchain:
  sync:
    chain-id: 1
    start-block: latest  # 或指定区块号
    batch-size: 5        # 每次最多同步几个区块
    poll-interval: 3000  # ms
    max-reorg-depth: 50
    rpc-url: http://localhost:8545
    backup-rpc-url: http://localhost:8546
```

注意事项：
- 使用分布式锁保证只有一个实例在同步（多实例部署场景）
- 区块同步必须严格顺序，不可跳块
- 每个区块处理完成后才更新进度
- 所有数据库操作在同一个事务中
- Java 8 兼容，不使用高版本特性
```

---

### 会话 6：Nonce 管理服务

```
Claude Code Prompt:
```

```text
在 erc20-platform-blockchain 模块实现 Nonce 管理服务。

## 背景
以太坊交易的 nonce 必须严格递增、不能有间隙。在高并发提现场景下，多个提现请求同时需要广播交易，必须正确分配 nonce。

## 已有上下文

数据库表：
- t_nonce_record: chain_id, wallet_address, current_nonce(已确认的最大nonce), pending_nonce(已分配但未确认的最大nonce)

已有组件：
- DistributedLock: @DistributedLock(key, waitTime, leaseTime) 注解
- Redisson 已配置

## 需要实现

### 1. NonceManager — Nonce 管理器
```java
@Component
public class NonceManager {
    
    // 分配一个新的 nonce（原子操作）
    // 逻辑：
    // 1. 加分布式锁 (key = "nonce:" + chainId + ":" + address)
    // 2. 从 Redis 获取当前 pending_nonce
    // 3. 如果 Redis 无值，从链上 eth_getTransactionCount(address, "pending") 初始化
    // 4. 分配 nonce = pending_nonce，然后 pending_nonce++
    // 5. 同时更新数据库记录
    // 返回分配的 nonce
    public long allocateNonce(int chainId, String walletAddress);
    
    // 确认 nonce 已上链（交易确认后调用）
    // 更新 current_nonce = max(current_nonce, confirmedNonce)
    public void confirmNonce(int chainId, String walletAddress, long confirmedNonce);
    
    // 释放 nonce（交易失败/取消时调用）
    // 如果释放的是最新的 pending_nonce - 1，则回退 pending_nonce
    // 否则记录为 gap，后续可复用
    public void releaseNonce(int chainId, String walletAddress, long nonce);
    
    // 修复 nonce gap
    // 定时任务调用，检测是否有 gap，若有则用 0 value 自转账填充
    public void fixNonceGaps(int chainId, String walletAddress);
    
    // 重置 nonce（从链上重新同步，危险操作，仅管理后台手动触发）
    public void resetNonce(int chainId, String walletAddress);
}
```

### 2. NonceGapDetector — Nonce 间隙检测
```java
@Component
public class NonceGapDetector {
    // 定时检测（每 30 秒）
    // 1. 获取链上 confirmed nonce (eth_getTransactionCount "latest")
    // 2. 获取链上 pending nonce (eth_getTransactionCount "pending")
    // 3. 如果 pending - confirmed > 阈值（如 10），说明有大量 pending 交易
    // 4. 检查是否有 nonce gap（某个 nonce 对应的交易丢失）
    // 5. 如果发现 gap，通知 NonceManager 修复
    @Scheduled(fixedDelay = 30000)
    public void detectGaps();
}
```

### 3. Redis 数据结构设计
- Key: `nonce:pending:{chainId}:{address}` -> 当前 pending nonce 值
- Key: `nonce:gaps:{chainId}:{address}` -> Set，存放已知的 gap nonce 值
- Key: `nonce:allocated:{chainId}:{address}` -> Sorted Set，score=时间戳，value=nonce，记录已分配未确认的 nonce

### 4. Nonce 超时回收
- 分配出去的 nonce 如果 5 分钟内未广播上链，视为超时
- 超时的 nonce 加入 gaps 集合，下次分配时优先复用 gaps 中的 nonce

注意事项：
- 整个 allocateNonce 方法必须是原子的（分布式锁）
- 锁的 leaseTime 设为 5 秒，waitTime 设为 10 秒
- 所有操作记录详细日志，便于排查 nonce 问题
- Java 8 兼容
```

---

### 会话 7：Gas 策略服务

```
Claude Code Prompt:
```

```text
在 erc20-platform-blockchain 模块实现 Gas 策略服务。

## 背景
以太坊 EIP-1559 后，Gas 定价有两种模式：
1. Legacy: gasPrice
2. EIP-1559: maxFeePerGas + maxPriorityFeePerGas

需要支持动态 Gas 定价，并能对卡住的交易进行加速（replacement）。

## 已有上下文
- Web3j 4.9.8 已配置
- NonceManager 可分配/释放 nonce
- 应用配置在 application.yml

## 需要实现

### 1. GasStrategy 接口与实现
```java
public interface GasStrategy {
    GasPrice getGasPrice(GasPriority priority);
    GasPrice getReplacementGasPrice(GasPrice originalGasPrice);
}

@Data @Builder
public class GasPrice {
    private BigInteger gasPrice;         // legacy 模式
    private BigInteger maxFeePerGas;     // EIP-1559
    private BigInteger maxPriorityFeePerGas; // EIP-1559
    private boolean eip1559;
}

public enum GasPriority {
    LOW, MEDIUM, HIGH, URGENT
}
```

### 2. EIP1559GasStrategy
- 调用 eth_feeHistory 获取最近几个区块的 baseFee 和 priorityFee
- LOW: baseFee * 1.1 + percentile(10) priorityFee
- MEDIUM: baseFee * 1.25 + percentile(50) priorityFee  
- HIGH: baseFee * 1.5 + percentile(75) priorityFee
- URGENT: baseFee * 2 + percentile(95) priorityFee
- replacement: 原始 maxFeePerGas * 1.15 + 原始 maxPriorityFeePerGas * 1.15（至少 +10%）

### 3. LegacyGasStrategy
- 调用 eth_gasPrice 获取建议 gasPrice
- LOW: suggestedGasPrice * 0.9
- MEDIUM: suggestedGasPrice * 1.0
- HIGH: suggestedGasPrice * 1.3
- URGENT: suggestedGasPrice * 1.8
- replacement: 原始 gasPrice * 1.15（至少 +10%）

### 4. GasEstimator — Gas Limit 估算
```java
@Component
public class GasEstimator {
    // 估算 ERC-20 transfer 的 gas limit
    // 调用 eth_estimateGas，加 20% buffer
    // 如果估算失败（合约可能 revert），返回默认值 80000
    public BigInteger estimateERC20Transfer(String contractAddress, String from, String to, BigInteger amount);
    
    // 估算 ETH 转账 gas limit（固定 21000）
    public BigInteger estimateEthTransfer();
}
```

### 5. GasPriceCache — Gas 价格缓存
- 每 15 秒刷新一次 gas price
- 使用 Redis 缓存，避免频繁 RPC 调用
- 如果 RPC 不可用，使用最近一次有效值

### 6. StuckTransactionHandler — 卡住交易处理
```java
@Component
public class StuckTransactionHandler {
    // 定时扫描（每 60 秒）
    // 1. 查找状态为 PENDING 且广播时间超过 N 分钟的交易
    // 2. 检查链上该 nonce 对应的交易状态
    // 3. 如果链上无此 nonce 的 pending 交易（被丢弃），以相同 nonce 重发
    // 4. 如果链上有但 gas price 过低，以更高 gas price 替换
    // 5. 更新原交易状态为 REPLACED，记录新 tx_hash
    @Scheduled(fixedDelay = 60000)
    public void handleStuckTransactions();
}
```

### 7. 配置
```yaml
blockchain:
  gas:
    strategy: eip1559  # 或 legacy
    max-gas-price: 100000000000  # 100 Gwei 上限，防止 gas 暴涨时损失
    stuck-timeout-minutes: 5     # 超过 5 分钟视为卡住
    max-replacement-count: 3     # 最多替换 3 次
    gas-limit-buffer-percent: 20 # gas limit 估算 buffer
```

注意事项：
- BigInteger 比较和运算注意精度
- Gas 价格有上限保护，超过阈值暂停出账并告警
- Java 8 兼容
```

---

### 会话 8：钱包服务（签名与广播）

```
Claude Code Prompt:
```

```text
在 erc20-platform-blockchain 模块实现钱包服务，负责交易构造、签名和广播。

## 已有上下文
- NonceManager.allocateNonce(chainId, address) -> long
- NonceManager.confirmNonce / releaseNonce
- GasStrategy.getGasPrice(priority) -> GasPrice
- GasEstimator.estimateERC20Transfer(...) -> BigInteger
- SafeERC20Caller（处理非标准 ERC-20）
- t_transaction_record 表存储所有链上交易
- t_wallet_config 表存储钱包信息

## 需要实现

### 1. WalletService — 钱包核心服务
```java
@Service
public class WalletService {
    
    // 发送 ERC-20 转账交易
    // 流程：allocateNonce -> 构造交易 -> 签名 -> 广播 -> 落库
    // 如果广播失败，释放 nonce 并抛异常
    public TransactionRecord sendERC20Transfer(
        String fromAddress, 
        String toAddress, 
        String contractAddress, 
        BigInteger amount, 
        GasPriority gasPriority
    );
    
    // 发送 ETH 转账（用于给用户地址补充 Gas 做归集）
    public TransactionRecord sendEthTransfer(
        String fromAddress,
        String toAddress,
        BigInteger amountWei,
        GasPriority gasPriority  
    );
    
    // 替换交易（加速/取消）
    public TransactionRecord replaceTransaction(
        String txHash,
        boolean cancel  // true=发送 0 value 自转账取消，false=用更高 gas 重发
    );
    
    // 查询交易状态
    public TxStatus queryTransactionStatus(String txHash);
}
```

### 2. TransactionBuilder — 交易构造器
```java
@Component
public class TransactionBuilder {
    // 构造 ERC-20 transfer 的 raw transaction
    // 编码 transfer(address,uint256) 函数调用
    RawTransaction buildERC20Transfer(String contract, String to, BigInteger amount, 
                                       long nonce, GasPrice gasPrice, BigInteger gasLimit);
    
    // 构造 ETH 转账
    RawTransaction buildEthTransfer(String to, BigInteger value,
                                     long nonce, GasPrice gasPrice, BigInteger gasLimit);
}
```

### 3. TransactionSigner — 交易签名
```java
public interface TransactionSigner {
    // 签名交易，返回签名后的 hex 字符串
    String sign(RawTransaction rawTx, int chainId);
}

// 本地私钥实现（开发/测试用）
@Component
@Profile("dev")
public class LocalKeySigner implements TransactionSigner { ... }

// 生产环境预留 KMS/HSM 接口
@Component  
@Profile("prod")
public class KmsTransactionSigner implements TransactionSigner { ... }
```

### 4. TransactionBroadcaster — 交易广播
```java
@Component
public class TransactionBroadcaster {
    // 广播已签名交易
    // 1. 调用 eth_sendRawTransaction
    // 2. 如果返回错误（nonce too low, already known, underpriced），分类处理
    // 3. 成功则返回 txHash
    // 4. 支持多节点广播（增加上链概率）
    public String broadcast(String signedTxHex);
}
```

### 5. TransactionConfirmTracker — 交易确认追踪
```java
@Component
public class TransactionConfirmTracker {
    // 定时扫描 PENDING 状态的交易
    // 检查是否已上链并达到确认数要求
    @Scheduled(fixedDelay = 5000)
    public void trackPendingTransactions();
    
    // 单笔交易确认检查
    // 1. eth_getTransactionReceipt
    // 2. 如果 receipt 存在且 status=1，计算确认数
    // 3. 确认数达标则更新状态为 CONFIRMED
    // 4. status=0 则标记 FAILED
    // 5. receipt 不存在 -> 仍在 pending 或被丢弃
    private void checkTransaction(TransactionRecord record);
}
```

### 6. 错误分类处理
广播时可能遇到的错误及处理策略：
- "nonce too low": nonce 已被使用，需要重新分配
- "replacement transaction underpriced": gas 加价不够，需要至少 +10%
- "already known": 交易已在 mempool，忽略
- "insufficient funds": 余额不足，暂停并告警
- 其他错误: 记录并告警

注意事项：
- 签名接口抽象化，方便对接不同的密钥管理方案
- 广播成功后立即落库，保证 crash 后可恢复
- Java 8 兼容
```

---

### 会话 9：充值服务

```
Claude Code Prompt:
```

```text
在 erc20-platform-service 模块实现充值服务。

## 已有上下文

数据库表：
- t_user_address: user_id, address, address_index, token_id, status
- t_deposit_record: user_id, token_id, address, tx_hash, block_number, block_hash, log_index, amount, amount_exponent, confirm_blocks, required_confirm_blocks, status, credited, idempotent_key
- t_token_config: contract_address, decimals, deposit_confirm_blocks, min_deposit_amount, enabled
- t_account_balance: user_id, token_id, available_balance, available_exponent
- t_account_flow: 账务流水

已有组件：
- TransferEvent: contractAddress, from, to, value, txHash, blockNumber, logIndex
- AmountUtil: toMinUnit, toHumanReadable, fromChainAmount
- IdempotentKeyGenerator: deposit -> txHash + "_" + logIndex
- 区块同步引擎会通过 RocketMQ (Topic: BLOCK_TRANSFER_EVENT, Tag: DEPOSIT) 发送 TransferEvent 消息

## 需要实现

### 1. AddressService — 地址管理服务
```java
@Service
public class AddressService {
    // 为用户分配充值地址
    // 1. 检查用户是否已有该 token 的充值地址
    // 2. 如果有，直接返回
    // 3. 如果没有，从预生成地址池中分配一个
    // 4. 地址池为空时，触发批量生成
    public String allocateDepositAddress(long userId, long tokenId);
    
    // 批量预生成地址（HD 钱包派生）
    // 使用 BIP-44 路径: m/44'/60'/0'/0/{index}
    // 生成后入库，状态 AVAILABLE
    public void preGenerateAddresses(int count);
    
    // 根据地址查找用户（充值识别时使用）
    public Optional<UserAddress> findByAddress(String address);
}
```

### 2. DepositService — 充值核心服务
```java
@Service
public class DepositService {
    
    // 处理区块同步发来的 Transfer 事件（MQ Consumer）
    // 1. 检查 to 地址是否是平台地址（查 t_user_address）
    // 2. 如果不是平台地址，跳过
    // 3. 检查合约地址是否是已注册 token
    // 4. 幂等检查（idempotent_key = txHash_logIndex）
    // 5. 创建 DepositRecord，状态 CONFIRMING
    // 6. 金额转换：chainAmount -> 平台最小单位
    @RocketMQMessageListener(topic = "BLOCK_TRANSFER_EVENT", selectorExpression = "DEPOSIT")
    public void onTransferEvent(TransferEventMessage message);
    
    // 确认数更新（区块同步引擎每同步一个新区块后触发）
    // 1. 查询所有 CONFIRMING 状态的充值记录
    // 2. 计算 confirmBlocks = currentBlockNumber - depositBlockNumber
    // 3. 如果 confirmBlocks >= requiredConfirmBlocks，触发入账
    public void updateConfirmations(long currentBlockNumber);
    
    // 充值入账
    // 1. 验证充值记录状态
    // 2. 调用 AccountService 增加余额（事务内）
    // 3. 更新充值记录状态为 SUCCESS，credited = true
    // 4. 记录账务流水
    // 整个操作在一个数据库事务中
    @Transactional
    public void creditDeposit(long depositId);
}
```

### 3. DepositConfirmJob — 充值确认定时任务
```java
@Component
public class DepositConfirmJob {
    // 每 10 秒执行一次
    // 查询所有 CONFIRMING 状态的充值
    // 获取当前最新区块号
    // 更新确认数
    // 达标的执行入账
    @Scheduled(fixedDelay = 10000)
    public void confirmDeposits();
}
```

### 4. 充值去重和幂等
- 使用 idempotent_key (tx_hash + "_" + log_index) 作为唯一索引
- 数据库层面保证：同一笔链上 transfer 事件只入库一次
- MQ 消费端使用幂等表防止重复消费

### 5. 最小充值金额过滤
- 低于 token_config.min_deposit_amount 的充值不入账
- 但仍然记录在 deposit_record 中（状态标记为 BELOW_MINIMUM），方便对账

### 6. 充值重组处理
- 收到 REORG 消息后，将受影响的 deposit_record 状态改为 REORGED
- 如果已入账（credited=true），需要冲正：扣减余额，记录反向流水

注意事项：
- 所有入账操作必须有事务保证
- 余额变更使用乐观锁（version 字段）
- 金额转换必须精确，使用 BigInteger 计算
- Java 8 兼容
```

---

### 会话 10：提现服务

```
Claude Code Prompt:
```

```text
在 erc20-platform-service 模块实现提现服务。

## 已有上下文

数据库表：
- t_withdraw_record: request_id(唯一), user_id, token_id, to_address, amount, amount_exponent, fee_amount, fee_exponent, status(PENDING_REVIEW/APPROVED/SIGNING/BROADCASTING/PENDING_CONFIRM/SUCCESS/FAILED/REJECTED), tx_hash, nonce, gas_price, gas_limit, block_number, retry_count, last_error, reviewed_by, reviewed_at
- t_account_balance: user_id, token_id, available_balance, frozen_balance
- t_account_flow: 账务流水

已有组件：
- WalletService.sendERC20Transfer(from, to, contract, amount, priority) -> TransactionRecord
- WalletService.replaceTransaction(txHash, cancel) -> TransactionRecord
- NonceManager（nonce 分配与管理）
- GasStrategy（gas 定价）
- AccountService（余额操作）
- AmountUtil（金额转换）

## 需要实现

### 1. WithdrawService — 提现核心服务
```java
@Service
public class WithdrawService {
    
    // 创建提现申请
    // 1. 幂等检查 (request_id 唯一索引)
    // 2. 校验参数：地址格式、金额 > 最小提现金额、token 是否支持
    // 3. 检查余额是否充足 (available_balance >= amount + fee)
    // 4. 冻结金额：available_balance -= (amount + fee), frozen_balance += (amount + fee)
    // 5. 创建提现记录，状态 PENDING_REVIEW
    // 6. 记录账务流水 (FREEZE)
    // 7. 发送 MQ 消息通知风控审核
    // 以上在同一个事务中
    @Transactional
    public WithdrawRecord createWithdraw(WithdrawRequest request);
    
    // 审核通过
    // 1. 校验当前状态必须是 PENDING_REVIEW
    // 2. 更新状态为 APPROVED
    // 3. 发送 MQ 消息到出账队列
    @Transactional
    public void approve(long withdrawId, String operator);
    
    // 审核拒绝
    // 1. 校验状态
    // 2. 解冻金额
    // 3. 状态改为 REJECTED
    // 4. 记录流水 (UNFREEZE)
    @Transactional
    public void reject(long withdrawId, String operator, String reason);
    
    // 执行出账（从 MQ 消费）
    // 1. 加分布式锁 (key = "withdraw:" + withdrawId)
    // 2. 校验状态为 APPROVED
    // 3. 更新状态为 SIGNING
    // 4. 计算链上金额 (AmountUtil.toChainAmount)
    // 5. 调用 WalletService.sendERC20Transfer
    // 6. 更新状态为 BROADCASTING，记录 tx_hash, nonce
    // 7. 如果发送失败，根据错误类型决定重试或标记 FAILED
    public void executeWithdraw(long withdrawId);
    
    // 提现确认回调（TransactionConfirmTracker 确认交易后回调）
    // 1. 更新状态为 SUCCESS
    // 2. 扣减冻结金额 (frozen_balance -= amount + fee)
    // 3. 记录流水 (WITHDRAW, WITHDRAW_FEE)
    @Transactional
    public void confirmWithdraw(long withdrawId, String txHash, long blockNumber);
    
    // 提现失败处理
    // 1. 更新状态为 FAILED
    // 2. 解冻金额
    // 3. 记录流水 (UNFREEZE)
    // 4. 释放 nonce
    @Transactional
    public void failWithdraw(long withdrawId, String reason);
}
```

### 2. WithdrawExecuteConsumer — 提现出账 MQ 消费者
```java
@Component
@RocketMQMessageListener(topic = "WITHDRAW_EXECUTE", consumerGroup = "withdraw-execute-group")
public class WithdrawExecuteConsumer {
    // 消费审核通过的提现消息
    // 调用 WithdrawService.executeWithdraw
    // 消费失败重试策略：最多 3 次，间隔递增
}
```

### 3. WithdrawRetryJob — 提现重试定时任务
```java
@Component
public class WithdrawRetryJob {
    // 每 30 秒执行
    // 1. 扫描状态为 BROADCASTING 且超过 10 分钟未确认的提现
    // 2. 检查交易状态
    //    - 如果链上已确认（可能是推送丢失），直接走确认流程
    //    - 如果链上不存在（被丢弃），重新出账
    //    - 如果链上 pending（gas 低），尝试替换加速
    // 3. retry_count >= 3 则标记 FAILED 并告警，等待人工介入
    @Scheduled(fixedDelay = 30000)
    public void retryStuckWithdraws();
}
```

### 4. 并发安全保证
- 同一笔提现的状态变更必须加分布式锁
- 余额操作使用数据库乐观锁
- MQ 消费幂等：提现状态不对直接跳过

### 5. 状态机约束
合法的状态流转：
- PENDING_REVIEW -> APPROVED (审核通过)
- PENDING_REVIEW -> REJECTED (审核拒绝)
- APPROVED -> SIGNING (开始签名)
- SIGNING -> BROADCASTING (广播中)
- BROADCASTING -> PENDING_CONFIRM (已广播待确认)
- PENDING_CONFIRM -> SUCCESS (链上确认)
- BROADCASTING/PENDING_CONFIRM -> FAILED (失败)
- FAILED -> APPROVED (人工重试，重新入出账队列)

不符合状态机的操作直接拒绝并记录日志。

注意事项：
- 提现是最敏感的操作，每一步都必须有日志和审计记录
- 解冻操作金额必须与冻结金额精确匹配
- Java 8 兼容
```

---

### 会话 11：账务服务

```
Claude Code Prompt:
```

```text
在 erc20-platform-service 模块实现账务服务。

## 核心职责
- 管理用户账户余额（可用余额、冻结余额）
- 记录所有资金变动流水
- 保证资金安全：任何余额变动必须有对应的流水
- 支持对账：流水 replay 后必须等于当前余额

## 已有上下文

数据库表：
- t_account_balance: user_id, token_id, available_balance(long), available_exponent, frozen_balance(long), frozen_exponent, version
- t_account_flow: user_id, token_id, flow_type, amount(long), amount_exponent, direction(IN/OUT), before_balance, after_balance, biz_id, biz_type, idempotent_key, created_at

枚举：
- FlowType: DEPOSIT, WITHDRAW, WITHDRAW_FEE, FREEZE, UNFREEZE, COLLECTION_FEE, ADJUSTMENT
- FlowDirection: IN, OUT

## 需要实现

### 1. AccountService — 账务核心服务
```java
@Service
public class AccountService {
    
    // 增加可用余额（充值入账时调用）
    // 1. 幂等检查 (idempotent_key)
    // 2. 乐观锁更新 available_balance
    // 3. 记录流水 (direction=IN, before/after balance)
    // 4. 如果乐观锁失败，重试（最多3次）
    @Transactional
    public void increaseAvailable(AccountOperateRequest request);
    
    // 冻结余额（提现申请时调用）
    // 1. 检查 available_balance >= amount
    // 2. available_balance -= amount, frozen_balance += amount
    // 3. 记录两条流水：FREEZE OUT (available) 和 FREEZE IN (frozen)
    // 实际简化为一条 FREEZE 类型流水
    @Transactional
    public void freeze(AccountOperateRequest request);
    
    // 解冻余额（提现拒绝/失败时调用）
    // frozen_balance -= amount, available_balance += amount
    @Transactional
    public void unfreeze(AccountOperateRequest request);
    
    // 扣减冻结余额（提现成功后调用）
    // frozen_balance -= amount
    @Transactional
    public void decreaseFrozen(AccountOperateRequest request);
    
    // 查询余额
    public AccountBalance getBalance(long userId, long tokenId);
}

@Data @Builder
public class AccountOperateRequest {
    private long userId;
    private long tokenId;
    private long amount;        // 最小单位
    private int amountExponent;
    private FlowType flowType;
    private String bizId;       // 关联业务单号
    private String bizType;     // DEPOSIT/WITHDRAW 等
    private String idempotentKey;
}
```

### 2. AccountFlowService — 流水服务
```java
@Service
public class AccountFlowService {
    // 记录流水（AccountService 内部调用）
    // before_balance 和 after_balance 从当前余额快照获取
    void recordFlow(AccountFlow flow);
    
    // 查询用户流水（分页）
    PageResult<AccountFlow> queryFlows(long userId, long tokenId, int page, int size);
    
    // 对账：重放流水验证余额一致性
    // 从第一条流水开始累加，验证最终结果 == 当前余额
    boolean verifyBalance(long userId, long tokenId);
}
```

### 3. 幂等保证
- idempotent_key 在 t_account_flow 表上有唯一索引
- 相同 idempotent_key 的操作直接返回成功（视为已处理）
- 捕获 DuplicateKeyException 判定为幂等命中

### 4. 乐观锁实现
```sql
UPDATE t_account_balance 
SET available_balance = available_balance + #{amount},
    version = version + 1,
    updated_at = NOW()
WHERE user_id = #{userId} 
  AND token_id = #{tokenId} 
  AND version = #{version}
  AND available_balance + #{amount} >= 0  -- 防止余额为负
```

### 5. 对账 Job
```java
@Component
public class AccountReconcileJob {
    // 每天凌晨执行全量对账
    // 抽样每小时执行增量对账
    // 不一致时发送告警，不自动修复
    @Scheduled(cron = "0 0 2 * * ?")
    public void fullReconcile();
}
```

### 6. 资金安全红线
- 余额不允许为负（SQL 条件保证）
- 每笔流水必须记录 before_balance 和 after_balance
- 任何绕过 AccountService 直接操作余额表的行为都是 BUG
- 管理后台调账必须有独立的 ADJUSTMENT 流水类型和审批记录

注意事项：
- 所有余额操作必须在事务内
- 乐观锁冲突时重试，重试仍失败则抛异常
- 流水表只追加不修改不删除
- Java 8 兼容
```

---

### 会话 12：风控服务

```
Claude Code Prompt:
```

```text
在 erc20-platform-service 模块实现风控服务。

## 核心职责
- 提现审核（自动+人工）
- 提现限额管理
- 异常行为检测
- 地址黑名单

## 已有上下文
- WithdrawRecord: userId, tokenId, toAddress, amount, amountExponent, status
- t_token_config: min_withdraw_amount
- Redis 已配置 (Redisson)
- RocketMQ 用于异步通知

## 需要实现

### 1. RiskControlService — 风控核心服务
```java
@Service
public class RiskControlService {
    
    // 提现风控审核
    // 返回审核结果：AUTO_PASS / NEED_MANUAL_REVIEW / REJECT
    public RiskResult checkWithdraw(WithdrawRecord record);
}
```

### 2. 风控规则引擎
使用责任链模式，每个规则独立判断：

```java
public interface RiskRule {
    RiskResult check(WithdrawRecord record);
    int order(); // 执行顺序
}
```

规则列表：
1. **AddressBlacklistRule** — 目标地址是否在黑名单中
2. **AmountLimitRule** — 单笔/单日/单月提现额度
   - 单笔上限 (per token 配置)
   - 24h 累计上限
   - 超过阈值需人工审核
3. **FrequencyRule** — 提现频率
   - 同一用户 1 小时内不超过 N 次
   - 同一目标地址 24h 内不超过 M 次
4. **NewAddressRule** — 首次使用的提现地址需人工审核
5. **LargeAmountRule** — 大额提现强制人工审核

### 3. 限额管理
```java
@Service
public class WithdrawLimitService {
    // Redis 记录用户提现累计
    // Key: withdraw:daily:{userId}:{tokenId}:{date} -> 累计金额
    // Key: withdraw:hourly:{userId}:{tokenId}:{hour} -> 次数
    // TTL: daily key 48h, hourly key 2h
    
    public boolean checkAndAccumulate(long userId, long tokenId, long amount);
    public void rollback(long userId, long tokenId, long amount); // 提现失败时回退
}
```

### 4. 地址黑名单
```java
@Service
public class AddressBlacklistService {
    // 黑名单存 Redis Set: blacklist:address
    // 同时持久化到数据库
    // 支持手动添加/移除
    // 支持从外部源同步（预留接口）
    
    public boolean isBlacklisted(String address);
    public void addToBlacklist(String address, String reason, String operator);
    public void removeFromBlacklist(String address, String operator);
}
```

### 5. 风控配置（可动态调整）
```yaml
risk:
  withdraw:
    auto-pass-max-amount: 10000  # 低于此金额自动通过（最小单位）
    daily-limit: 100000          # 单日累计上限
    hourly-max-count: 5          # 每小时最多提现次数
    new-address-review: true     # 新地址是否需要人工审核
    large-amount-threshold: 50000 # 大额阈值
```

### 6. 自动审核 + 人工审核流程
- 满足所有自动通过条件 -> AUTO_PASS -> 直接进入出账队列
- 命中任一人工审核条件 -> NEED_MANUAL_REVIEW -> 等待管理员操作
- 命中拒绝条件（黑名单等）-> REJECT -> 直接拒绝并解冻

注意事项：
- 风控规则要可扩展，新增规则只需实现接口并注册
- 限额使用 Redis 原子操作保证并发安全
- 人工审核需要有超时机制（超过 24h 未审核发送提醒）
- Java 8 兼容
```

---

### 会话 13：归集服务

```
Claude Code Prompt:
```

```text
在 erc20-platform-service 模块实现归集服务。

## 背景
用户充值到各自独立的地址后，需要将代币归集到平台热钱包，便于统一管理和出账。归集涉及：
1. ERC-20 token 归集（先确保用户地址有足够 ETH 做 gas）
2. ETH gas 补充（从热钱包向用户地址转入少量 ETH）
3. 归集策略（达到一定金额才归集，避免小额频繁归集浪费 gas）

## 已有上下文
- WalletService.sendERC20Transfer / sendEthTransfer
- t_user_address: 所有用户充值地址
- t_collection_task: 归集任务表
- t_wallet_config: HOT/COLD/COLLECTION 钱包地址
- t_deposit_record: 充值记录
- SafeERC20Caller.safeBalanceOf: 查询代币余额
- NonceManager, GasStrategy 已实现

## 需要实现

### 1. CollectionService — 归集核心服务
```java
@Service
public class CollectionService {
    
    // 扫描需要归集的地址
    // 条件：地址代币余额 >= 归集阈值
    // 定时扫描或充值成功后触发
    public List<CollectionTask> scanForCollection(long tokenId);
    
    // 执行单个归集任务
    // 流程：
    // 1. 查询用户地址的 ETH 余额
    // 2. 如果 ETH 不足以支付 gas，先补充 ETH
    // 3. 等待 ETH 补充交易确认
    // 4. 发送 ERC-20 transfer 将代币转到热钱包
    // 5. 更新归集任务状态
    public void executeCollection(long taskId);
    
    // 批量归集（多个地址同时归集）
    // 考虑 nonce 并发分配
    public void batchCollection(List<Long> taskIds);
}
```

### 2. CollectionTrigger — 归集触发器
```java
@Component
public class CollectionTrigger {
    
    // 充值确认后自动触发检查
    // 监听充值成功事件，检查地址余额是否达到归集阈值
    @RocketMQMessageListener(topic = "DEPOSIT_CONFIRMED", consumerGroup = "collection-trigger")
    public void onDepositConfirmed(DepositConfirmedMessage message);
    
    // 定时全量扫描（每 4 小时一次，作为兜底）
    @Scheduled(cron = "0 0 */4 * * ?")
    public void scheduledScan();
}
```

### 3. GasSupplyService — Gas 补充服务
```java
@Service
public class GasSupplyService {
    // 估算归集所需 gas 费用
    // ERC-20 transfer 约 65000 gas
    // 补充金额 = estimatedGas * currentGasPrice * 1.5 (buffer)
    public BigInteger estimateRequiredGas(String contractAddress);
    
    // 从热钱包向目标地址补充 ETH
    public TransactionRecord supplyGas(String toAddress, BigInteger amount);
}
```

### 4. 归集策略配置
```yaml
collection:
  enabled: true
  # 各 token 归集阈值（最小单位）
  thresholds:
    USDT: 100000000   # 100 USDT (6 decimals)
    USDC: 100000000   # 100 USDC
  # gas 补充 buffer 倍数
  gas-buffer-multiplier: 1.5
  # 归集目标地址（热钱包）
  target-address: "0x..."
  # 每批次最大归集数
  batch-size: 20
  # 归集间隔（同一地址两次归集最小间隔）
  min-interval-hours: 4
```

### 5. 冷热钱包互转
```java
@Service
public class WalletTransferService {
    // 热钱包余额超过阈值时，转入冷钱包
    // 热钱包余额低于阈值时，发出告警（冷钱包转入需人工操作）
    
    @Scheduled(fixedDelay = 300000) // 5分钟检查一次
    public void checkHotWalletBalance();
}
```

### 6. 归集任务状态机
- PENDING -> GAS_SUPPLYING -> GAS_CONFIRMED -> COLLECTING -> SUCCESS
- 任何环节失败 -> FAILED (可手动重试)
- GAS_SUPPLYING 超时 -> 检查链上状态决定下一步

注意事项：
- 归集需要控制并发，避免同时给太多地址补充 gas 导致热钱包 nonce 拥堵
- 使用令牌桶限流，每秒最多发起 N 笔归集
- Gas 补充和代币归集是两笔独立交易，中间可能失败需要分步处理
- Java 8 兼容
```

---

### 会话 14：MQ 消息定义与消费者整合

```
Claude Code Prompt:
```

```text
在 erc20-platform-mq 模块整合所有 RocketMQ 消息的定义、生产者和消费者。

## 已有上下文
各业务模块已实现核心逻辑，现在需要统一整合 MQ 通信：
- 区块同步 -> 充值服务: TransferEvent 消息
- 提现审核通过 -> 出账: 提现执行消息
- 充值确认 -> 归集: 充值确认消息
- 各模块 -> 告警: 告警消息
- 交易确认 -> 提现/归集: 交易状态变更消息

## 需要实现

### 1. Topic 与 Tag 统一定义
```java
public class MqConstants {
    // Topic
    public static final String TOPIC_BLOCK_EVENT = "BLOCK_TRANSFER_EVENT";
    public static final String TOPIC_WITHDRAW = "WITHDRAW_EXECUTE";
    public static final String TOPIC_DEPOSIT_CONFIRMED = "DEPOSIT_CONFIRMED";
    public static final String TOPIC_TX_STATUS_CHANGED = "TX_STATUS_CHANGED";
    public static final String TOPIC_ALERT = "PLATFORM_ALERT";
    public static final String TOPIC_COLLECTION = "COLLECTION_TASK";
    
    // Tag
    public static final String TAG_DEPOSIT = "DEPOSIT";
    public static final String TAG_WITHDRAW_APPROVED = "APPROVED";
    public static final String TAG_TX_CONFIRMED = "CONFIRMED";
    public static final String TAG_TX_FAILED = "FAILED";
}
```

### 2. 消息体定义 (所有 Message DTO)
```java
@Data @Builder
public class TransferEventMessage {
    private String contractAddress;
    private String from;
    private String to;
    private String value; // BigInteger.toString()
    private String txHash;
    private long blockNumber;
    private String blockHash;
    private int logIndex;
    private long timestamp;
}

@Data @Builder
public class WithdrawExecuteMessage {
    private long withdrawId;
    private long userId;
    private long tokenId;
    private String toAddress;
    private long amount;
    private int amountExponent;
    private String requestId; // 幂等键
}

@Data @Builder
public class DepositConfirmedMessage {
    private long depositId;
    private long userId;
    private long tokenId;
    private String address;
    private long amount;
    private int amountExponent;
}

@Data @Builder
public class TxStatusChangedMessage {
    private String txHash;
    private String status; // CONFIRMED / FAILED
    private long blockNumber;
    private String blockHash;
    private String bizType; // WITHDRAW / COLLECTION / GAS_SUPPLY
    private long bizId;
}

@Data @Builder
public class AlertMessage {
    private String alertType;
    private String alertLevel; // INFO/WARN/CRITICAL
    private String content;
    private String source; // 产生告警的模块
    private long timestamp;
}
```

### 3. 统一消息发送器
```java
@Component
public class MqProducer {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    // 发送普通消息
    public void send(String topic, String tag, Object message, String bizKey);
    
    // 发送延时消息
    public void sendDelay(String topic, String tag, Object message, String bizKey, int delayLevel);
    
    // 发送顺序消息（按 hashKey 保证顺序）
    public void sendOrderly(String topic, String tag, Object message, String bizKey, String hashKey);
}
```

### 4. 消费者列表
整理所有消费者，使用统一的异常处理和幂等逻辑：
1. DepositEventConsumer - 消费 BLOCK_TRANSFER_EVENT:DEPOSIT
2. WithdrawExecuteConsumer - 消费 WITHDRAW_EXECUTE:APPROVED
3. CollectionTriggerConsumer - 消费 DEPOSIT_CONFIRMED
4. TxStatusConsumer - 消费 TX_STATUS_CHANGED:CONFIRMED/FAILED
5. AlertConsumer - 消费 PLATFORM_ALERT

### 5. 消费者基类（统一幂等+异常处理）
```java
public abstract class BaseConsumer<T> {
    // 模板方法
    // 1. 反序列化消息
    // 2. 幂等检查 (Redis set: mq:consumed:{consumerGroup}:{msgId})
    // 3. 调用子类的 doConsume 方法
    // 4. 标记已消费
    // 5. 异常时判断是否需要重试
    protected abstract void doConsume(T message);
    protected abstract String getIdempotentKey(T message);
}
```

### 6. 消息可靠性保证
- 生产端：发送失败重试 3 次
- 消费端：消费失败重试 (RocketMQ 默认重试机制，最多 16 次)
- 幂等：Redis + 数据库唯一索引双重保证
- 消息丢失兜底：定时任务扫描补偿（如充值未推送的、提现审核通过未出账的）

### 7. 补偿任务
```java
@Component
public class MqCompensationJob {
    // 每 5 分钟扫描一次
    // 1. 充值 CONFIRMING 超过 30 分钟未更新确认数 -> 重新推送
    // 2. 提现 APPROVED 超过 5 分钟未进入 SIGNING -> 重新推送出账消息
    // 3. 归集 PENDING 超过 10 分钟未开始 -> 重新触发
    @Scheduled(fixedDelay = 300000)
    public void compensate();
}
```

注意事项：
- 消息体中金额统一用 String 传递 BigInteger，避免 JSON 序列化精度问题
- 所有消息都带 timestamp 字段，方便追踪延迟
- Consumer Group 命名规范: {module}-{function}-group
- Java 8 兼容
```

---

### 会话 15：REST API 层

```
Claude Code Prompt:
```

```text
在 erc20-platform-api 模块实现 REST API 层。

## 已有上下文
- AddressService.allocateDepositAddress(userId, tokenId)
- DepositService (充值查询)
- WithdrawService.createWithdraw / approve / reject
- AccountService.getBalance
- AccountFlowService.queryFlows

## 需要实现

### 1. 用户端 API (UserController)

```
POST /api/v1/deposit/address       - 获取充值地址
GET  /api/v1/deposit/records       - 查询充值记录（分页）
GET  /api/v1/deposit/{id}          - 查询单笔充值详情
POST /api/v1/withdraw/create       - 创建提现申请
GET  /api/v1/withdraw/records      - 查询提现记录（分页）
GET  /api/v1/withdraw/{id}         - 查询单笔提现详情
GET  /api/v1/account/balance       - 查询余额
GET  /api/v1/account/flows         - 查询账务流水（分页）
```

### 2. 管理端 API (AdminController)

```
GET  /api/admin/v1/withdraw/pending-review   - 待审核提现列表
POST /api/admin/v1/withdraw/{id}/approve     - 审核通过
POST /api/admin/v1/withdraw/{id}/reject      - 审核拒绝
GET  /api/admin/v1/deposit/records           - 所有充值记录
GET  /api/admin/v1/wallet/balances           - 钱包余额概览
POST /api/admin/v1/collection/trigger        - 手动触发归集
GET  /api/admin/v1/block/sync-status         - 区块同步状态
POST /api/admin/v1/nonce/reset               - 重置 Nonce（危险）
GET  /api/admin/v1/alerts                    - 告警列表
POST /api/admin/v1/alerts/{id}/handle        - 处理告警
POST /api/admin/v1/token/add                 - 新增代币
PUT  /api/admin/v1/token/{id}/config         - 修改代币配置
```

### 3. 统一请求/响应格式
```java
// 统一响应
@Data
public class Result<T> {
    private int code;       // 0=成功，其他=错误码
    private String message;
    private T data;
    private long timestamp;
}

// 分页响应
@Data
public class PageResult<T> {
    private List<T> list;
    private long total;
    private int page;
    private int size;
}
```

### 4. 请求参数校验
- 使用 javax.validation 注解
- 自定义校验器：EthAddress（以太坊地址格式校验）
- 金额参数：正数、不超过上限

### 5. 全局异常处理
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // BizException -> 对应错误码
    // MethodArgumentNotValidException -> 参数校验失败
    // DuplicateKeyException -> 重复请求（幂等）
    // OptimisticLockException -> 系统繁忙，请重试
    // Exception -> 系统异常
}
```

### 6. 接口幂等
- 提现创建接口通过 request_id 实现幂等
- 审核接口通过状态机保证幂等（非法状态转换直接拒绝）

### 7. 安全
- 用户端接口需要 token 认证（预留 Filter，从 Header 提取 userId）
- 管理端接口需要管理员权限（预留 Filter）
- 本次只实现 Filter 骨架，不实现完整认证逻辑

### 8. API 文档
- 集成 Swagger/Knife4j
- 每个接口有中文描述

注意事项：
- Controller 层不包含业务逻辑，仅做参数转换和调用 Service
- 所有入参出参使用独立的 VO/DTO，不直接暴露 Entity
- Java 8 兼容
```

---

### 会话 16：集成测试与异常场景覆盖

```
Claude Code Prompt:
```

```text
为 erc20-platform 项目编写关键路径的单元测试和集成测试。

## 测试重点

### 1. 金额计算测试 (AmountUtilTest)
- 正常转换：12.34 元 (exponent=2) -> 最小单位 1234
- 链上金额转换：USDT 100 (decimals=6, exponent=6) -> BigInteger("100000000")
- 边界值：金额为 0、最大值 Long.MAX_VALUE
- 精度验证：不同 exponent 互转不丢精度

### 2. ERC-20 事件解析测试 (ERC20TransferEventParserTest)
- 标准 Transfer 事件解析（3 个 indexed topic）
- USDT 非标准事件解析（无返回值的 transfer 产生的 log）
- topics 长度异常时返回 empty
- data 为空或长度不对时返回 empty
- contractAddress 不匹配时返回 empty

### 3. Nonce 管理测试 (NonceManagerTest)
- 正常分配：连续分配 nonce 递增
- 并发分配：模拟 10 个线程同时分配，nonce 不重复
- 释放与复用：释放的 nonce 被下次分配使用
- 链上同步：初始化时从链上获取正确值

### 4. 充值流程集成测试 (DepositIntegrationTest)
- 正常充值：Transfer 事件 -> 创建记录 -> 确认数达标 -> 入账
- 幂等测试：同一事件重复消费不重复入账
- 重组测试：入账后重组 -> 余额冲正
- 最小金额过滤：低于阈值不入账

### 5. 提现流程集成测试 (WithdrawIntegrationTest)
- 正常提现：创建 -> 审核 -> 出账 -> 确认 -> 成功
- 余额不足：创建时拒绝
- 审核拒绝：余额解冻
- 出账失败：释放 nonce、解冻余额
- 并发提现：余额扣减不超卖
- 幂等：相同 request_id 不重复创建

### 6. 账务一致性测试 (AccountServiceTest)
- 增加余额后流水正确
- 冻结/解冻后余额一致
- 乐观锁冲突时重试成功
- 幂等操作不重复记录流水
- 对账验证：replay 流水 == 当前余额

### 7. Gas 策略测试 (GasStrategyTest)
- EIP-1559 各优先级价格合理
- 替换价格至少 +10%
- 上限保护生效

## 测试框架
- JUnit 5 + Mockito
- 集成测试使用 H2 内存数据库 + 嵌入式 Redis (embedded-redis)
- Web3j 使用 MockWebServer 模拟 RPC 响应
- RocketMQ 测试用 @RocketMQMessageListener 手动调用

## 测试配置
- src/test/resources/application-test.yml
- 测试 profile 使用 H2 + 嵌入式 Redis

请为上述场景编写完整的测试代码，放在对应模块的 src/test/java 目录下。
```

---

### 会话 17：监控、告警与运维接口

```
Claude Code Prompt:
```

```text
为 erc20-platform 添加监控指标采集、健康检查和告警通知。

## 需要实现

### 1. 健康检查端点 (Spring Boot Actuator)
- /actuator/health 包含:
  - 数据库连接状态
  - Redis 连接状态
  - RocketMQ 连接状态
  - 以太坊节点连接状态（最新区块号，落后主网区块数）
  - 区块同步状态（同步延迟）

### 2. 业务监控指标 (Micrometer)
```java
@Component
public class BusinessMetrics {
    // 计数器
    - deposit.count (tag: token, status)        // 充值笔数
    - withdraw.count (tag: token, status)       // 提现笔数
    - collection.count (tag: token, status)     // 归集笔数
    - block.synced.count                        // 已同步区块数
    - reorg.count                               // 重组次数
    
    // 仪表
    - hot.wallet.balance (tag: token)           // 热钱包余额
    - block.sync.delay                          // 区块同步延迟（秒）
    - pending.withdraw.count                    // 待处理提现数
    - pending.nonce.count (tag: address)        // pending 中的 nonce 数
    
    // 直方图
    - deposit.confirm.duration                  // 充值确认耗时
    - withdraw.process.duration                 // 提现处理耗时
    - gas.price.gwei                            // Gas 价格分布
}
```

### 3. AlertService — 告警服务
```java
@Service
public class AlertService {
    // 发送告警
    // 1. 落库 (t_alert_record)
    // 2. 根据级别决定通知方式
    //    - INFO: 仅记录
    //    - WARN: 发送到告警 MQ topic
    //    - CRITICAL: 发送 MQ + 标记为需立即处理
    // 3. 同级别同类型告警 10 分钟内去重
    public void alert(String type, AlertLevel level, String content);
}
```

### 4. 需要告警的场景
- 区块同步延迟 > 30 个区块 (CRITICAL)
- 链重组发生 (CRITICAL)
- 热钱包余额低于阈值 (WARN)
- 热钱包余额低于紧急阈值 (CRITICAL)
- 提现失败且重试次数用完 (CRITICAL)
- Nonce gap 检测到 (WARN)
- Gas 价格超过上限 (WARN)
- 大额提现待审核 (INFO)
- 对账不一致 (CRITICAL)
- RPC 节点不可用，切换到备用 (WARN)

### 5. 配置
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  health:
    ethereum:
      enabled: true

alert:
  dedup-interval-minutes: 10
  critical-notify: true
```

注意事项：
- 告警去重避免轰炸
- 监控指标不影响主业务性能（异步采集）
- 健康检查有超时保护（单个检查超时 3 秒则标记 DOWN）
- Java 8 兼容
```

---

## 会话间依赖关系

```
会话 1 (项目骨架)
  ├── 会话 2 (数据库 Schema)
  │     └── 会话 3 (领域模型)
  │           ├── 会话 4 (ERC-20 兼容层)
  │           │     ├── 会话 5 (区块同步)
  │           │     ├── 会话 6 (Nonce 管理)
  │           │     ├── 会话 7 (Gas 策略)
  │           │     └── 会话 8 (钱包服务)
  │           ├── 会话 9  (充值服务)  ← 依赖 4,5
  │           ├── 会话 10 (提现服务)  ← 依赖 6,7,8
  │           ├── 会话 11 (账务服务)
  │           └── 会话 12 (风控服务)
  ├── 会话 13 (归集服务) ← 依赖 8,9
  ├── 会话 14 (MQ 整合) ← 依赖 5,9,10,13
  ├── 会话 15 (REST API) ← 依赖 9,10,11
  ├── 会话 16 (测试)     ← 依赖全部
  └── 会话 17 (监控告警) ← 依赖全部
```

---

## 使用说明

1. **严格按顺序执行**：每个会话产出后续会话的依赖
2. **新会话 = 新对话**：每个 prompt 在一个全新的 Claude Code 对话中执行，避免上下文溢出
3. **验证产出**：每个会话结束后，编译确认 `mvn compile -pl <module>` 无报错
4. **中间产物检查**：如果某个会话的产出有问题，在同一会话内修复后再开始下一个
5. **上下文管理**：每个 prompt 开头的"已有上下文"部分提供了该会话需要的前序信息，无需加载全部代码

---

## 关键设计决策总结

| 问题 | 决策 | 理由 |
|------|------|------|
| 金额存储 | long + exponent 双字段 | 避免浮点精度问题，链上转换无损 |
| ERC-20 兼容 | SafeERC20Caller 封装 | 统一处理 USDT 等非标准实现 |
| Nonce 管理 | Redis + 分布式锁 | 高并发下保证 nonce 不重复不间隙 |
| 重组处理 | parentHash 链式验证 | 逐区块验证，发现不匹配立即回滚 |
| 幂等 | DB 唯一索引 + Redis Set | 双重保证，DB 是最终防线 |
| 提现状态 | 有限状态机 | 非法转换直接拒绝，避免并发导致的数据不一致 |
| 消息可靠性 | 重试 + 补偿扫描 | MQ 保证 at-least-once，业务层保证幂等 |
| Gas 策略 | EIP-1559 + 上限保护 | 动态定价 + 防止 gas 暴涨时资金损失 |

---

## 后续扩展方向（不在本期实现）

- 多链支持（BSC, Polygon, Arbitrum）
- MPC 分布式签名
- 实时汇率与法币估值
- 完整 KYC/AML 流程集成
- 前端管理面板
- Prometheus + Grafana 监控面板配置
