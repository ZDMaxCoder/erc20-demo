# Project Context

## 项目概述

ERC-20 中心化充提平台 — 支持 ERC-20 代币充值和提现的中心化服务。

- GitHub: https://github.com/ZDMaxCoder/erc20-demo
- 状态: 全部 14 个功能模块已实现完毕（2026-05-15），安全审计修复已完成（2026-05-16）

## 技术约束

- Java 8（不使用更高版本特性）
- Maven 多模块（8 个子模块）
- Spring Boot 2.7.18
- Web3j 4.9.8（以太坊交互）
- MyBatis-Plus 3.5.3（ORM）
- RocketMQ 2.2.3（异步消息）
- Redisson 3.23.5（分布式锁 + Redis 操作）
- 金额模型: `long amount + int exponent`，禁止使用浮点数
- 所有地址统一小写存储

## 开发规范

- TDD 工作流: Red → Green → Refactor
- 测试环境: H2 内存数据库 + 嵌入式 Redis（无外部依赖）
- 幂等设计: DB 唯一索引 + Redis Set 双保证
- 并发控制: Redisson 分布式锁 + 乐观锁（version 字段）
- 状态流转: WithdrawStatus 使用状态机，禁止非法跳转

## 构建命令

```bash
mvn clean package -DskipTests   # 编译打包
mvn test                         # 运行所有测试
mvn test -pl erc20-platform-service -Dtest=WithdrawServiceTest  # 单模块单测试
```

## 模块依赖关系

```
common → domain → dal → service → blockchain → mq → api/admin
```

详细文档在 `docs/` 目录下（每个模块一个 md 文件）。

## OpenSpec 变更管理

所有功能设计文档在 `openspec/changes/` 目录下，每个 change 包含：
- `proposal.md` — 需求提案
- `design.md` — 详细设计（含接口签名和实现上下文）
- `tasks.md` — TDD 任务清单

执行顺序（依赖感知）：
001→002→004→005→003→006→009→010→007→008→011→012→013→015

安全修复 changes：
- `security-audit-fixes` — 全量安全审计修复（64 个 TDD 任务，已完成）
- `safeerc20-remove-retry` — SafeERC20Caller 移除底层重试（已完成）

多链扩展 changes：
- `multichain-idempotent-key` — 幂等键多链改造，所有 key 包含 chainId（已完成）

## 自动化脚本

- `scripts/run-changes.sh` — 自动执行所有 OpenSpec changes
  - 用法: `./scripts/run-changes.sh [start] [end]`
  - 使用 `claude -p --dangerously-skip-permissions --output-format text`
  - 日志输出到 `scripts/logs/`

- `scripts/run-security-fixes.sh` — 安全审计修复分轮执行
  - 用法: `./scripts/run-security-fixes.sh [start_round] [end_round]`
  - 12 轮独立 session，每轮 3-7 个 TDD 任务
  - 日志输出到 `scripts/logs/security-fixes/`

- `.claude/commands/apply-change.md` — 交互式执行单个 change
- `.claude/commands/apply-next.md` — 自动找到并执行下一个未完成的 change

## 关键设计决策

1. **金额存储**: 使用 long + exponent 而非 BigDecimal，避免序列化问题和精度丢失。溢出时抛出 AmountOverflowException
2. **ERC-20 兼容**: SafeERC20Caller 处理 USDT（无返回值）、BNB（approve需先置零）、bytes32 返回值。底层不重试，失败抛 ChainCallException 由上游处理
3. **Token 类型安全**: TokenConfig.tokenType 分类（STANDARD/FEE_ON_TRANSFER/REBASING/UNSUPPORTED），非 STANDARD 类型在充值和归集环节拒绝处理
4. **Nonce 管理**: Redis pending_nonce + gaps(SortedSet) + allocated(ScoredSortedSet)，分布式锁保护
5. **区块重组**: parentHash 校验检测 reorg，ReorgHandler 委托 DepositService/WithdrawService 回退余额
6. **提现状态机**: PENDING_REVIEW→APPROVED→SIGNING→BROADCASTING→PENDING_CONFIRM→SUCCESS/FAILED。创建时自动风控评估（RiskControlService）
7. **交易确认**: TransactionConfirmTracker 验证 Transfer 事件存在性，actualAmount 传递至确认环节做金额校验
8. **消息幂等**: BaseConsumer 基类统一实现 Redis 消费去重（TTL=24h）
9. **归集流程**: 余额检测→Gas不足时先补Gas→确认后再发起归集转账
10. **链上对账**: ChainReconcileJob 每日查询链上 balanceOf 与平台账面对比
11. **多链幂等**: IdempotentKeyGenerator 所有方法包含 chainId 参数，格式 `{chainId}_{txHash}_{logIndex}` / `WD_{chainId}_{requestId}` / `COL_{chainId}_{...}`，DepositRecord/WithdrawRecord 均含 chainId 字段

## 数据库

- Schema 由 Flyway 管理（10 个迁移文件，V1-V10）
- 位置: `erc20-platform-dal/src/main/resources/db/migration/`
- 关键表: t_token_config, t_user_address, t_deposit_record, t_withdraw_record, t_transaction_record, t_nonce_record, t_account_balance, t_account_flow, t_block_record, t_block_sync_progress, t_wallet_config, t_collection_task, t_alert_record, t_address_blacklist
