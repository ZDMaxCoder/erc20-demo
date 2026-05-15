# erc20-platform-service 模块

业务逻辑核心模块，实现充值、提现、归集、账户、风控等核心业务。

## 模块职责

- 充值流程（事件接收 → 幂等去重 → 确认入账）
- 提现流程（创建 → 风控 → 审批 → 签名广播 → 确认）
- 归集流程（扫描 → Gas补给 → 归集转账）
- 账户余额管理（乐观锁 + 幂等）
- 风控规则引擎
- 监控指标采集

## 包结构

```
service/
├── DepositService.java          — 充值服务
├── DepositConfirmJob.java       — 充值确认定时任务
├── WithdrawService.java         — 提现服务
├── WithdrawStateMachine.java    — 提现状态机
├── WithdrawRetryJob.java        — 提现重试
├── AccountService.java          — 账户余额服务
├── AccountFlowService.java      — 流水查询
├── AccountReconcileJob.java     — 对账任务
├── CollectionService.java       — 归集服务
├── CollectionScanJob.java       — 归集扫描任务
├── CollectionTriggerService.java — 归集触发
├── GasSupplyService.java        — Gas补给服务
├── AddressService.java          — 地址分配服务
├── HdWalletService.java         — HD钱包派生
├── WalletTransferService.java   — 钱包转账封装
├── AlertService.java            — 告警服务
├── dto/                         — 内部数据传输对象
├── gateway/                     — 网关接口（MQ发布、链交互抽象）
├── risk/                        — 风控子模块
└── monitoring/                  — 监控指标
```

## 核心服务详解

### DepositService（充值服务）

**充值流程**：

```
Transfer事件 → onTransferEvent()
  ├── 地址匹配（是否平台地址）
  ├── 代币匹配（是否已配置且启用）
  ├── 幂等检查（txHash + logIndex）
  ├── 金额转换（链上 → 平台）
  ├── 最小金额检查
  └── 创建充值记录（状态=CONFIRMING）

确认定时任务 → creditDeposit()
  ├── 确认块数达标
  ├── 乐观锁入账（increaseAvailable）
  └── 标记 credited=1, status=SUCCESS
```

**Reorg 处理**：
- 已入账充值：扣减余额 + 记录调整流水
- 未入账充值：标记 REORGED 状态

### WithdrawService（提现服务）

**提现状态机**：

```
PENDING_REVIEW → APPROVED → SIGNING → BROADCASTING → PENDING_CONFIRM → SUCCESS
      ↓                                                                    
   REJECTED                                                  FAILED
```

**关键方法**：

| 方法 | 说明 |
|------|------|
| `createWithdraw` | 创建提现（幂等检查 → 冻结余额 → 创建记录） |
| `approve/reject` | 审批/拒绝（状态流转 + 资金操作） |
| `executeWithdraw` | 执行提现（分布式锁 → 发送链上交易） |
| `confirmWithdraw` | 确认提现（扣减冻结 + 扣手续费） |
| `failWithdraw` | 提现失败（解冻资金） |

**幂等保证**：requestId 唯一索引 + 分布式锁

### AccountService（账户服务）

基于乐观锁的余额操作，所有操作幂等：

| 方法 | 说明 |
|------|------|
| `increaseAvailable` | 增加可用余额（充值入账） |
| `decreaseAvailable` | 减少可用余额（调整扣减） |
| `freeze` | 冻结（available → frozen） |
| `unfreeze` | 解冻（frozen → available） |
| `decreaseFrozen` | 扣减冻结（提现成功） |

**并发控制**：
- 乐观锁重试（最多 3 次）
- 幂等键去重（AccountFlow 唯一索引）
- 每笔操作记录完整流水（before/after 快照）

### CollectionService（归集服务）

自动将用户地址的代币归集到热钱包：

```
扫描阶段：
  scanForCollection() → 检查余额 > 阈值 → 创建归集任务

执行阶段：
  executeCollection()
    ├── ETH余额足够 → 直接发送归集交易
    └── ETH不足 → 先补Gas → 确认后再归集

状态流转：
  PENDING → GAS_SUPPLYING → GAS_CONFIRMED → COLLECTING → SUCCESS/FAILED
```

**防重复**：
- 同地址同代币不创建重复活跃任务
- 最小归集间隔控制（configurable hours）

### RiskControlService（风控服务）

规则引擎模式，按优先级执行：

| 规则 | 优先级 | 说明 |
|------|--------|------|
| `AddressBlacklistRule` | 1 | 黑名单地址直接拒绝 |
| `AmountLimitRule` | 2 | 单笔金额超限拒绝 |
| `LargeAmountRule` | 3 | 大额需人工审核 |
| `FrequencyRule` | 4 | 高频提现需人工审核 |
| `NewAddressRule` | 5 | 新地址首次提现需审核 |

**风控结果**：
- `PASS` — 直接通过
- `NEED_MANUAL_REVIEW` — 需人工审核（聚合多条原因）
- `REJECT` — 直接拒绝（遇到即停止后续规则）

### GasSupplyService

为归集任务补充 Gas：
- 估算所需 Gas（estimateGas * gasPrice）
- 从热钱包发送 ETH 到用户地址
- 确认到账后触发归集

### 定时任务

| 任务 | 间隔 | 说明 |
|------|------|------|
| `DepositConfirmJob` | 10s | 检查 CONFIRMING 充值是否达到确认块数 |
| `WithdrawRetryJob` | 60s | 重试失败的提现（retryCount < 3） |
| `CollectionScanJob` | 配置化 | 扫描需要归集的地址 |
| `AccountReconcileJob` | 1h | 余额对账 |

### 监控指标（BusinessMetrics）

通过 Micrometer 暴露业务指标：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `deposit.count` | Counter | 充值成功计数 |
| `withdraw.count` | Counter | 提现成功计数 |
| `collection.count` | Counter | 归集成功计数 |
| `block.synced` | Counter | 已同步区块数 |

## Gateway 接口

定义下游依赖的抽象接口，由其他模块实现：

| 接口 | 实现模块 | 说明 |
|------|----------|------|
| `WithdrawTransactionSender` | blockchain | 提现交易发送 |
| `WithdrawMessagePublisher` | mq | 提现消息发布 |
| `CollectionTransactionSender` | blockchain | 归集交易发送 |
| `AlertMessagePublisher` | mq | 告警消息发布 |

## 测试覆盖

16 个测试类，覆盖：
- 充值全流程（事件处理、确认、Reorg）
- 提现状态机流转
- 账户余额并发操作
- 归集扫描与执行
- 风控规则组合
- 重试任务

## 依赖关系

- 依赖 `erc20-platform-common`、`erc20-platform-domain`、`erc20-platform-dal`
- Redisson（分布式锁）
- Spring Transaction
