# erc20-platform-mq 模块

消息队列集成模块，基于 RocketMQ 实现模块间异步解耦。

## 模块职责

- 消息生产者封装（带幂等保证）
- 消息消费者定义（带去重和重试）
- MQ 补偿任务（消息可靠投递）
- 消费幂等性（Redis Set 去重）

## Topic 设计

| Topic | 说明 | 生产者 | 消费者 |
|-------|------|--------|--------|
| `BLOCK_TRANSFER_EVENT` | 区块中检测到的 Transfer 事件 | BlockSyncEngine | DepositEventConsumer |
| `WITHDRAW_EXECUTE` | 提现执行指令 | WithdrawService | WithdrawExecuteConsumer |
| `DEPOSIT_CONFIRMED` | 充值确认通知 | DepositConfirmJob | （外部系统） |
| `TX_STATUS_CHANGED` | 交易状态变更 | TransactionConfirmTracker | TxStatusConsumer |
| `PLATFORM_ALERT` | 平台告警 | AlertService | AlertConsumer |
| `COLLECTION_TASK` | 归集任务触发 | CollectionTriggerService | CollectionTriggerConsumer |

## 核心组件

### MqProducer（消息生产者）

统一消息发送封装：

```java
mqProducer.sendSync(topic, tag, key, payload);
mqProducer.sendDelayed(topic, tag, key, payload, delayLevel);
```

- 同步发送确保投递成功
- 消息 key 用于幂等去重
- 支持延迟消息（重试场景）

### BaseConsumer（消费者基类）

所有消费者的公共逻辑：

- **消费幂等**：Redis Set 记录已消费的 messageId（TTL=24h）
- **异常处理**：捕获异常记录日志，返回 RECONSUME_LATER
- **结构化**：子类只需实现 `doConsume(message)` 方法

```java
public abstract class BaseConsumer {
    // Redis key: mq:consumed:{group}:{messageId}
    protected boolean isConsumed(String messageId);
    protected void markConsumed(String messageId);
    protected abstract void doConsume(Message message);
}
```

### 消费者列表

| Consumer | 消费 Topic | 处理逻辑 |
|----------|-----------|----------|
| `DepositEventConsumer` | BLOCK_TRANSFER_EVENT | 调用 DepositService.onTransferEvent() |
| `WithdrawExecuteConsumer` | WITHDRAW_EXECUTE | 调用 WithdrawService.executeWithdraw() |
| `TxStatusConsumer` | TX_STATUS_CHANGED | 根据状态调用 confirm/fail |
| `AlertConsumer` | PLATFORM_ALERT | 调用 AlertService 记录告警 |
| `CollectionTriggerConsumer` | COLLECTION_TASK | 调用 CollectionService.executeCollection() |

### MqCompensationJob（消息补偿）

定时任务（每 5 分钟），处理卡住的业务记录：

- **充值补偿**：CONFIRMING 超 30 分钟的充值，查询链上确认数，达标才入账（不强制入账）
- **提现补偿**：APPROVED 超 5 分钟的提现，重新发送执行消息（最多 5 次）
- 链上查询失败时不操作，等待下次轮询

### MqConstants

集中定义所有 MQ 常量：

```java
// Topics
public static final String TOPIC_BLOCK_TRANSFER_EVENT = "BLOCK_TRANSFER_EVENT";
public static final String TOPIC_WITHDRAW_EXECUTE = "WITHDRAW_EXECUTE";
...

// Consumer Groups
public static final String GROUP_DEPOSIT_EVENT = "deposit-event-consumer-group";
public static final String GROUP_WITHDRAW_EXECUTE = "withdraw-execute-consumer-group";
...

// Redis idempotency
public static final String REDIS_CONSUMED_PREFIX = "mq:consumed:";
public static final long REDIS_CONSUMED_TTL_HOURS = 24;
```

## 消息流转图

```
BlockSyncEngine
    ↓ [BLOCK_TRANSFER_EVENT]
DepositEventConsumer → DepositService.onTransferEvent()
    ↓ (确认后)
DepositConfirmJob → [DEPOSIT_CONFIRMED] → 外部系统

WithdrawService.approve()
    ↓ [WITHDRAW_EXECUTE]
WithdrawExecuteConsumer → WithdrawService.executeWithdraw()

TransactionConfirmTracker
    ↓ [TX_STATUS_CHANGED]
TxStatusConsumer → WithdrawService.confirmWithdraw() / CollectionService.markSuccess()

AlertService
    ↓ [PLATFORM_ALERT]
AlertConsumer → 记录告警 / 通知
```

## 配置

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: erc20-platform-producer
    send-message-timeout: 3000
    retry-times-when-send-failed: 2
```

## 测试覆盖

3 个测试类：
- `MqProducerTest` — 消息发送逻辑
- `BaseConsumerTest` — 幂等去重逻辑
- `MqCompensationJobTest` — 补偿重试逻辑

## 依赖关系

- 依赖 `erc20-platform-common`、`erc20-platform-service`
- RocketMQ Spring Boot Starter 2.2.3
- Redisson（消费去重）
