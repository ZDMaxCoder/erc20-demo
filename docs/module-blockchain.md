# erc20-platform-blockchain 模块

区块链交互模块，封装所有与以太坊链的通信逻辑。

## 模块职责

- Web3j 连接管理（主备 RPC 自动切换）
- ERC-20 代币兼容性处理（非标代币适配）
- Nonce 分布式管理
- Gas 定价策略（EIP-1559 + Legacy）
- 区块同步引擎
- 交易构建、签名、广播、确认追踪

## 包结构

```
blockchain/
├── config/          — Web3j 连接配置与 Provider
├── erc20/           — ERC-20 解析与调用
├── gas/             — Gas 策略
├── health/          — 健康检查指标
├── nonce/           — Nonce 管理
├── sync/            — 区块同步引擎
└── wallet/          — 交易发送与确认
```

## 核心组件

### Web3jProvider（连接管理）

- 主备 RPC 双节点配置
- OkHttp 连接池（maxIdle=10, keepAlive=30s）
- 超时配置：connect=10s, read=30s
- 自动故障转移：RPC 异常时切换备用节点并记录 WARN 日志

```java
web3jProvider.sendWithFailover(web3j -> web3j.ethBlockNumber().send());
```

### ERC-20 兼容层

#### ERC20TransferEventParser

解析 Transfer 事件日志：
- 标准 ERC-20：3 个 topics（signature + from + to），data 为 value
- 非标检测：topics 数量不足时返回 `Optional.empty()`
- 地址自动标准化为小写

#### SafeERC20Caller

安全调用 ERC-20 合约，处理非标实现：

| 方法 | 非标适配 |
|------|----------|
| `safeBalanceOf` | 标准 uint256 解码 |
| `safeTransfer` | 处理无返回值（USDT）和 bool 返回值 |
| `safeApprove` | 非零 allowance 时先置零再 approve（BNB） |
| `safeDecimals` | uint8 → bytes32 fallback → 默认 18 |
| `safeSymbol` | string → bytes32（strip null padding）fallback |

#### TokenMetadataReader

批量读取代币元数据（name, symbol, decimals），单字段失败不影响其他字段。

### Nonce 管理

#### NonceManager

分布式 Nonce 分配器，保证多实例并发安全：

| 方法 | 说明 |
|------|------|
| `allocateNonce(chainId, address)` | 分配 nonce（优先复用 gap，否则递增） |
| `confirmNonce(chainId, address, nonce)` | 交易确认后更新 current_nonce |
| `releaseNonce(chainId, address, nonce)` | 交易失败归还 nonce |
| `resetNonce(chainId, address)` | 从链上重新同步 nonce 状态 |

**实现细节**：
- 使用 Redisson 分布式锁（key=`nonce:{chainId}:{address}`，lease=5s，wait=10s）
- Redis 数据结构：pending_nonce（RBucket）、gaps（RSortedSet）、allocated（RScoredSortedSet）
- DB 持久化 NonceRecord 作为兜底

#### NonceGapDetector

定时任务（每 30 秒），检测超过 5 分钟未确认的 nonce 并回收到 gaps 集合。

### Gas 策略

#### GasStrategy 接口

```java
public interface GasStrategy {
    GasPrice getGasPrice(GasPriority priority);
    GasPrice getReplacementGasPrice(GasPrice original);
}
```

#### EIP1559GasStrategy

- 基于 `eth_feeHistory` 获取 baseFee 和 priorityFee
- 按优先级（LOW/MEDIUM/HIGH）选取不同百分位
- replacement 价格 = 原价 * 1.125（最小加速比例）

#### LegacyGasStrategy

- 基于 `eth_gasPrice` 获取当前 gas price
- 按优先级乘以不同系数

#### GasPriceCache

缓存 gas 价格，避免频繁 RPC 调用。

#### StuckTransactionHandler

检测卡住的交易（超过配置时间未确认），自动发起加速替换。

### 区块同步

#### BlockSyncEngine

定时轮询新区块（默认 3 秒间隔），批量同步：

1. 获取同步进度（BlockSyncProgress）
2. 逐块获取 block 数据
3. **Reorg 检测**：比对 parentHash 与存储的上一块 hash
4. 提取 Transfer 事件
5. 发布事件到 MQ
6. 更新同步进度

#### ReorgHandler

检测到链重组时：
- 回滚受影响区块的 BlockRecord
- 标记相关充值记录为 REORGED
- 回退已入账金额

#### TransferEventExtractor

从区块中提取指定合约的 Transfer 事件，调用 ERC20TransferEventParser。

### 钱包服务

#### WalletService

交易发送核心服务：

| 方法 | 说明 |
|------|------|
| `sendERC20Transfer` | 发送 ERC-20 转账（分配nonce→构建tx→签名→广播→记录） |
| `sendEthTransfer` | 发送 ETH 转账 |
| `replaceTransaction` | 替换卡住的交易（加速或取消） |
| `queryTransactionStatus` | 查询交易链上状态 |

**异常处理**：广播失败时自动 releaseNonce，避免 nonce 泄漏。

#### TransactionBuilder

构建 RawTransaction（支持 EIP-1559 和 Legacy 两种格式）。

#### TransactionSigner

交易签名，支持本地密钥和 KMS 两种方式。

#### TransactionBroadcaster

广播已签名交易，解析 RPC 返回的错误类型（nonce too low、insufficient funds 等）。

#### TransactionConfirmTracker

定时轮询 pending 交易状态，确认后通知下游。

## 配置项

```yaml
blockchain:
  rpc-url: https://mainnet.infura.io/v3/YOUR_KEY
  rpc-backup-url: https://eth.llamarpc.com
  sync:
    chain-id: 1
    poll-interval: 3000
    batch-size: 10
    start-block: latest
    confirmation-blocks: 12
  gas:
    max-gas-price: 100000000000  # 100 Gwei
    replacement-multiplier: 1.125
```

## 测试覆盖

18 个测试类，覆盖：
- ERC-20 解析（标准/非标/边界）
- Nonce 并发分配与回收
- Gas 策略计算
- 区块同步与 Reorg 处理
- 交易广播与确认
- 健康检查

## 依赖关系

- 依赖 `erc20-platform-common`、`erc20-platform-domain`、`erc20-platform-dal`
- Web3j 4.9.8
- Redisson（分布式锁和 nonce 存储）
- 依赖 `erc20-platform-service`（BusinessMetrics）
