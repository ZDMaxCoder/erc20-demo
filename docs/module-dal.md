# erc20-platform-dal 模块

数据访问层模块，包含所有 Mapper 接口和数据库迁移脚本。

## 模块职责

- 提供所有表的 MyBatis-Plus Mapper 接口
- 管理 Flyway 数据库迁移脚本
- 定义索引策略

## Mapper 接口

所有 Mapper 继承 `BaseMapper<Entity>`，开箱即用 CRUD：

| Mapper | 实体 | 说明 |
|--------|------|------|
| `TokenConfigMapper` | TokenConfig | 代币配置 |
| `UserAddressMapper` | UserAddress | 用户地址 |
| `DepositRecordMapper` | DepositRecord | 充值记录 |
| `WithdrawRecordMapper` | WithdrawRecord | 提现记录 |
| `TransactionRecordMapper` | TransactionRecord | 交易记录 |
| `NonceRecordMapper` | NonceRecord | Nonce 记录 |
| `BlockRecordMapper` | BlockRecord | 区块记录 |
| `BlockSyncProgressMapper` | BlockSyncProgress | 同步进度 |
| `AccountBalanceMapper` | AccountBalance | 账户余额 |
| `AccountFlowMapper` | AccountFlow | 账户流水 |
| `WalletConfigMapper` | WalletConfig | 钱包配置 |
| `CollectionTaskMapper` | CollectionTask | 归集任务 |
| `AlertRecordMapper` | AlertRecord | 告警记录 |
| `AddressBlacklistMapper` | AddressBlacklist | 地址黑名单 |

## 数据库迁移

迁移文件位于 `src/main/resources/db/migration/`：

| 迁移文件 | 内容 |
|----------|------|
| `V1__init_token_and_address.sql` | t_token_config, t_user_address |
| `V2__init_deposit_and_block.sql` | t_deposit_record, t_block_sync_progress, t_block_record |
| `V3__init_withdraw_and_transaction.sql` | t_withdraw_record, t_transaction_record, t_nonce_record |
| `V4__init_account_and_wallet.sql` | t_account_balance, t_account_flow, t_wallet_config, t_collection_task, t_alert_record |
| `V5__add_block_record_reorged.sql` | 添加 reorged 字段 |
| `V6__add_transaction_record_wallet_fields.sql` | 交易记录钱包相关字段 |
| `V7__init_address_blacklist.sql` | t_address_blacklist |
| `V8__add_deposit_service_fields.sql` | 充值服务补充字段 |
| `V9__add_token_type.sql` | 代币类型字段 |
| `V10__add_chain_id_to_deposit_and_withdraw.sql` | 充值/提现表添加 chain_id 列 |

## 索引策略

关键索引设计原则：
- 幂等键字段添加唯一索引（防重复）
- 状态+时间组合索引（任务查询）
- 用户+代币组合索引（余额/流水查询）
- 区块号索引（链数据查询）
- 地址索引（地址查询）

## 依赖关系

- 依赖 `erc20-platform-domain`（实体类）
- MyBatis-Plus
- Flyway（自动执行迁移）
