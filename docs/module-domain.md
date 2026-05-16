# erc20-platform-domain 模块

领域实体模块，定义平台所有数据库表对应的实体类。

## 模块职责

- 定义所有 MyBatis-Plus 实体类
- 统一金额存储模型（long + exponent）
- 提供实体构建能力（@Builder）

## 实体列表

| 实体类 | 对应表 | 说明 |
|--------|--------|------|
| `TokenConfig` | t_token_config | 代币配置（合约地址、decimals、费率、开关） |
| `UserAddress` | t_user_address | 用户充值地址绑定 |
| `DepositRecord` | t_deposit_record | 充值记录 |
| `WithdrawRecord` | t_withdraw_record | 提现记录 |
| `TransactionRecord` | t_transaction_record | 链上交易记录 |
| `NonceRecord` | t_nonce_record | Nonce 管理记录 |
| `BlockRecord` | t_block_record | 已同步区块记录 |
| `BlockSyncProgress` | t_block_sync_progress | 区块同步进度 |
| `AccountBalance` | t_account_balance | 账户余额（可用+冻结） |
| `AccountFlow` | t_account_flow | 账户流水 |
| `WalletConfig` | t_wallet_config | 钱包配置（热钱包/冷钱包） |
| `CollectionTask` | t_collection_task | 归集任务 |
| `AlertRecord` | t_alert_record | 告警记录 |
| `AddressBlacklist` | t_address_blacklist | 地址黑名单 |

## 金额存储模型

所有涉及金额的字段使用 `long amount + int amountExponent` 组合：

```java
// 示例：12.34 USDT（exponent=6）
// amount = 12340000, amountExponent = 6
private long amount;
private int amountExponent;
```

**优势**：
- 避免浮点精度丢失
- 支持不同精度代币统一存储
- 与链上 decimals 解耦

## 关键实体字段说明

### TokenConfig
```
id, symbol, contractAddress, chainId, decimals, amountExponent,
enabled, minDepositAmount, withdrawFeeAmount, collectionThreshold,
depositConfirmBlocks
```

### AccountBalance
```
userId, tokenId, availableBalance, frozenBalance, amountExponent, version
```

其中 `version` 字段用于乐观锁控制并发更新。

### DepositRecord
```
txHash, logIndex, idempotentKey, chainId, userId, tokenId,
fromAddress, toAddress, amount, amountExponent, status,
blockNumber, blockHash, confirmations, requiredConfirmations, credited
```

### WithdrawRecord
```
requestId, idempotentKey, chainId, userId, tokenId, toAddress,
amount, amountExponent, feeAmount, status, txHash,
retryCount, errorMessage
```

## 注解使用

所有实体使用：
- `@TableName` — MyBatis-Plus 表名映射
- `@Data @Builder @NoArgsConstructor @AllArgsConstructor` — Lombok

## 依赖关系

- 依赖 `erc20-platform-common`（枚举引用）
- MyBatis-Plus 注解
- Lombok
