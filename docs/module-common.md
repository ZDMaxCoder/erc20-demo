# erc20-platform-common 模块

通用基础模块，提供全平台共用的工具类、注解、异常体系和枚举定义。

## 模块职责

- 金额转换工具（避免浮点精度问题）
- 以太坊地址校验与标准化
- 幂等键生成
- 分布式锁注解 + AOP 切面
- 通用重试模板
- 统一返回值与错误码
- 业务枚举定义

## 核心类说明

### AmountUtil

金额转换工具，所有计算基于 `BigDecimal` / `BigInteger`，杜绝浮点运算。

| 方法 | 说明 | 示例 |
|------|------|------|
| `toMinUnit(BigDecimal, int)` | 人类可读金额 → 最小单位 | `toMinUnit(12.34, 6)` → `12340000L` |
| `toHumanReadable(long, int)` | 最小单位 → 人类可读 | `toHumanReadable(12340000L, 6)` → `12.34` |
| `toChainAmount(long, int, int)` | 平台金额 → 链上金额(BigInteger) | 处理 exponent 与 decimals 差异 |
| `fromChainAmount(BigInteger, int, int)` | 链上金额 → 平台金额 | 逆向转换 |

### AddressUtil

以太坊地址校验与标准化工具。

| 方法 | 说明 |
|------|------|
| `isValid(String)` | 校验地址格式（0x前缀、40位十六进制、支持 checksum） |
| `normalize(String)` | 统一转为小写格式 |

### IdempotentKeyGenerator

幂等键生成器，保证同一业务操作不重复执行。所有方法均包含 chainId 参数，确保多链场景下幂等键不冲突。

| 方法 | 格式 | 用途 |
|------|------|------|
| `depositKey(chainId, txHash, logIndex)` | `{chainId}_{txHash}_{logIndex}` | 充值去重 |
| `withdrawKey(chainId, requestId)` | `WD_{chainId}_{requestId}` | 提现去重 |
| `collectionKey(chainId, address, tokenId, blockNumber)` | `COL_{chainId}_{address}_{tokenId}_{blockNumber}` | 归集去重 |

### @DistributedLock 注解

基于 Redisson 的分布式锁注解，通过 AOP 自动加锁/释放。

```java
@DistributedLock(key = "'user:' + #userId", waitTime = 3, leaseTime = 10)
public void doSomething(String userId) { ... }
```

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `key` | 必填 | SpEL 表达式，支持方法参数引用 |
| `waitTime` | 3秒 | 等待获取锁的超时时间 |
| `leaseTime` | 10秒 | 锁自动释放时间 |

### RetryTemplate

通用重试模板，支持指数退避。

```java
RetryTemplate retry = new RetryTemplate(3, 1000); // 最多重试3次，初始间隔1秒
String result = retry.execute(() -> callRemoteApi());
```

| 参数 | 说明 |
|------|------|
| `maxRetries` | 最大重试次数 |
| `initialBackoffMs` | 初始退避时间（毫秒） |
| `multiplier` | 退避乘数（默认 2.0） |
| `retryableExceptions` | 可重试的异常类型集合 |

### ErrorCode 错误码

| 范围 | 类别 | 示例 |
|------|------|------|
| 10000-19999 | 系统级错误 | SYSTEM_ERROR, PARAM_ERROR, NOT_FOUND, DUPLICATE_REQUEST |
| 20000-29999 | 业务级错误 | INSUFFICIENT_BALANCE, AMOUNT_TOO_SMALL, TOKEN_DISABLED |
| 30000-39999 | 基础设施错误 | LOCK_ACQUIRE_FAILED |
| 40000-49999 | 链交互错误 | CHAIN_ERROR, NONCE_CONFLICT, BROADCAST_FAILED |

### Result\<T\>

统一 API 返回格式：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "timestamp": 1715760000000
}
```

## 枚举定义

| 枚举 | 说明 | 值 |
|------|------|-----|
| `DepositStatus` | 充值状态 | CONFIRMING, SUCCESS, BELOW_MINIMUM, REORGED |
| `WithdrawStatus` | 提现状态 | PENDING_REVIEW, APPROVED, REJECTED, SIGNING, BROADCASTING, PENDING_CONFIRM, SUCCESS, FAILED |
| `TxStatus` | 交易状态 | PENDING, CONFIRMED, FAILED, REPLACED |
| `WalletType` | 钱包类型 | HOT, COLD, GAS |
| `FlowType` | 流水类型 | DEPOSIT, WITHDRAW, WITHDRAW_FEE, FREEZE, UNFREEZE, ADJUSTMENT |
| `FlowDirection` | 流向 | IN, OUT |
| `AlertLevel` | 告警级别 | INFO, WARN, CRITICAL |
| `CollectionTaskStatus` | 归集任务状态 | PENDING, GAS_SUPPLYING, GAS_CONFIRMED, COLLECTING, SUCCESS, FAILED |
| `AddressStatus` | 地址状态 | UNBOUND, BOUND |

## 依赖关系

本模块无其他平台模块依赖，仅依赖：
- Redisson（分布式锁）
- Lombok
- Spring AOP
