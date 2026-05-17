# erc20-platform-mq 模块

消息队列集成模块，基于 RocketMQ 实现模块间异步解耦。

## 模块职责

- 消息生产者封装（同步发送 + 重试 + 顺序消息）
- 消息消费者定义（泛型基类 + Redis 幂等去重）
- MQ 补偿任务（卡住充值/提现的恢复）
- Gateway 接口实现（AlertMessagePublisher、WithdrawMessagePublisher）

## 包结构

```
com.erc20.platform.mq
├── MqProducer.java                  — 统一消息发送封装（send/sendDelay/sendOrderly）
├── MqConstants.java                 — MQ 常量定义（Topics/Tags/Groups/Redis）
├── BaseConsumer.java                — 泛型消费者基类（幂等去重 + 异常分级）
├── DepositEventConsumer.java        — 充值事件消费（tag=DEPOSIT）
├── WithdrawExecuteConsumer.java     — 提现执行消费（tag=APPROVED）
├── TxStatusConsumer.java            — 交易状态变更消费
├── AlertConsumer.java               — 告警消费（持久化到 DB）
├── CollectionTriggerConsumer.java   — 充值确认→归集触发
├── MqCompensationJob.java           — 消息补偿定时任务
├── AlertMessagePublisherImpl.java   — AlertMessagePublisher 网关实现
└── WithdrawMessagePublisherImpl.java — WithdrawMessagePublisher 网关实现
```

## Topic 设计

| Topic | 说明 | 生产者 | 消费者 | Tag 过滤 |
|-------|------|--------|--------|----------|
| `BLOCK_TRANSFER_EVENT` | 区块中检测到的 Transfer 事件 | BlockSyncEngine | DepositEventConsumer | `DEPOSIT` |
| `WITHDRAW_EXECUTE` | 提现执行指令 | WithdrawMessagePublisherImpl / MqCompensationJob | WithdrawExecuteConsumer | `APPROVED` |
| `DEPOSIT_CONFIRMED` | 充值确认通知 | DepositConfirmJob | CollectionTriggerConsumer | 无 |
| `TX_STATUS_CHANGED` | 交易状态变更 | TransactionConfirmTracker | TxStatusConsumer | 无 |
| `PLATFORM_ALERT` | 平台告警 | AlertMessagePublisherImpl | AlertConsumer | 无 |
| `COLLECTION_TASK` | 归集任务触发 | CollectionTriggerService | （预留，当前无消费者） | — |

## 核心组件

### MqProducer（消息生产者）

统一消息发送封装，三个方法：

```java
// 同步发送，失败自动重试（最多 3 次，间隔 1s）
mqProducer.send(topic, tag, key, payload);

// 延迟消息（RocketMQ delayLevel）
mqProducer.sendDelay(topic, tag, key, payload, delayLevel);

// 顺序消息（按 hashKey 投递到同一队列）
mqProducer.sendOrderly(topic, tag, key, payload, hashKey);
```

- 消息 key 设置到 RocketMQ KEYS header，用于消费端幂等去重
- payload 通过 `JSON.toJSONString` 序列化
- destination 格式: `topic:tag`（tag 为空时仅用 topic）

### BaseConsumer\<T\>（消费者基类）

泛型抽象基类，所有消费者继承并指定消息 DTO 类型：

```java
public abstract class BaseConsumer<T> {
    // 构造器：RedissonClient + consumerGroup + messageType
    protected BaseConsumer(RedissonClient redissonClient, 
                           String consumerGroup, Class<T> messageType);

    // 核心消费入口（由 onMessage 调用）
    public void handleMessage(String json, String messageKey);

    // 子类实现：返回消息的唯一去重 key
    protected abstract String getMessageKey(T message);

    // 子类实现：业务处理逻辑
    protected abstract void doConsume(T message);
}
```

**幂等机制：**
- Redis key 格式: `mq:consumed:{consumerGroup}:{messageKey}`
- RBucket 存值 "1"，TTL = 24 小时
- 消费前检查 key 是否存在，存在则跳过

**异常分级处理：**
- `BizException`（不可重试）→ 标记 Redis key + 吞掉异常（消息不再投递）
- `RuntimeException`（可重试）→ 重新抛出，触发 RocketMQ 重新投递

### 消费者列表

| Consumer | 消费 Topic | Consumer Group | Tag 过滤 | 消息类型 | 处理逻辑 |
|----------|-----------|----------------|----------|----------|----------|
| `DepositEventConsumer` | BLOCK_TRANSFER_EVENT | deposit-event-consumer-group | DEPOSIT | TransferEventDTO | `depositService.onTransferEvent(message)` |
| `WithdrawExecuteConsumer` | WITHDRAW_EXECUTE | withdraw-execute-consumer-group | APPROVED | WithdrawExecuteMessage | `withdrawService.executeWithdraw(message.getWithdrawId())` |
| `TxStatusConsumer` | TX_STATUS_CHANGED | withdraw-tx-status-consumer-group | 无 | TxStatusChangedMessage | 处理提现确认/失败 + 通知归集服务 |
| `AlertConsumer` | PLATFORM_ALERT | platform-alert-consumer-group | 无 | AlertMessage | 持久化到 t_alert_record |
| `CollectionTriggerConsumer` | DEPOSIT_CONFIRMED | deposit-confirmed-consumer-group | 无 | DepositConfirmedMessage | `triggerService.onDepositConfirmed(message)` |

### TxStatusConsumer 处理逻辑

该消费者承担双重职责：

1. **提现确认/失败**：通过 txHash 查询 WithdrawRecord
   - `TxStatus.CONFIRMED` → `withdrawService.confirmWithdraw(id, txHash, blockNumber, actualAmount, isAnomaly, anomalyReason)`
   - `TxStatus.FAILED` → `withdrawService.failWithdraw(id, "Transaction failed on-chain")`
2. **归集状态通知**：无论是否为提现交易，始终调用 `collectionTriggerService.onTxStatusChanged(txHash, toStatus)`

### Gateway 实现

本模块实现 service 模块定义的网关接口，桥接业务层与 RocketMQ：

| 实现类 | 实现接口 | 说明 |
|--------|----------|------|
| `AlertMessagePublisherImpl` | `AlertMessagePublisher` | 发送到 PLATFORM_ALERT topic（tag=null） |
| `WithdrawMessagePublisherImpl` | `WithdrawMessagePublisher` | 发送到 WITHDRAW_EXECUTE topic（tag=APPROVED） |

### MqCompensationJob（消息补偿）

定时任务（`@Scheduled(fixedDelay = 300000)`，每 5 分钟），处理卡住的业务记录：

**充值补偿**（CONFIRMING 超 30 分钟）：
1. 查询链上 receipt
2. 无 receipt → 发送 CRITICAL 告警（DEPOSIT_TX_MISSING）
3. 有 receipt，确认数达标 → `depositService.creditDeposit(id)` 直接入账
4. 有 receipt，确认数不足 → 更新 `updatedAt`（重置超时窗口）+ WARN 告警

**提现补偿**（APPROVED 超 5 分钟）：
1. 检查 `retryCount >= 5` → 跳过（避免无限重试）
2. 重新发送 WithdrawExecuteMessage 到 WITHDRAW_EXECUTE topic
3. 递增 retryCount，更新 updatedAt

### MqConstants

集中定义所有 MQ 常量：

```java
// Topics (6)
public static final String TOPIC_BLOCK_TRANSFER_EVENT = "BLOCK_TRANSFER_EVENT";
public static final String TOPIC_WITHDRAW_EXECUTE = "WITHDRAW_EXECUTE";
public static final String TOPIC_DEPOSIT_CONFIRMED = "DEPOSIT_CONFIRMED";
public static final String TOPIC_TX_STATUS_CHANGED = "TX_STATUS_CHANGED";
public static final String TOPIC_PLATFORM_ALERT = "PLATFORM_ALERT";
public static final String TOPIC_COLLECTION_TASK = "COLLECTION_TASK";

// Tags (4)
public static final String TAG_DEPOSIT = "DEPOSIT";
public static final String TAG_APPROVED = "APPROVED";
public static final String TAG_CONFIRMED = "CONFIRMED";
public static final String TAG_FAILED = "FAILED";

// Consumer Groups (7)
public static final String GROUP_DEPOSIT_EVENT = "deposit-event-consumer-group";
public static final String GROUP_WITHDRAW_EXECUTE = "withdraw-execute-consumer-group";
public static final String GROUP_DEPOSIT_CONFIRMED = "deposit-confirmed-consumer-group";
public static final String GROUP_TX_STATUS_WITHDRAW = "withdraw-tx-status-consumer-group";
public static final String GROUP_TX_STATUS_COLLECTION = "collection-tx-status-consumer-group";
public static final String GROUP_PLATFORM_ALERT = "platform-alert-consumer-group";
public static final String GROUP_COLLECTION_TASK = "collection-task-consumer-group";

// Redis idempotency
public static final String REDIS_CONSUMED_PREFIX = "mq:consumed:";
public static final long REDIS_CONSUMED_TTL_HOURS = 24;
```

## 消息流转图

```
BlockSyncEngine
    ↓ [BLOCK_TRANSFER_EVENT, tag=DEPOSIT]
DepositEventConsumer → DepositService.onTransferEvent()
    ↓ (确认后)
DepositConfirmJob → [DEPOSIT_CONFIRMED]
    ↓
CollectionTriggerConsumer → CollectionTriggerService.onDepositConfirmed()

WithdrawService.approve()
    ↓ [WITHDRAW_EXECUTE, tag=APPROVED] (via WithdrawMessagePublisherImpl)
WithdrawExecuteConsumer → WithdrawService.executeWithdraw()

TransactionConfirmTracker
    ↓ [TX_STATUS_CHANGED]
TxStatusConsumer
    ├→ WithdrawService.confirmWithdraw() / failWithdraw()
    └→ CollectionTriggerService.onTxStatusChanged()

AlertService
    ↓ [PLATFORM_ALERT] (via AlertMessagePublisherImpl)
AlertConsumer → AlertRecordMapper.insert()
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

- 依赖 `erc20-platform-common`（枚举、工具类）
- 依赖 `erc20-platform-service`（WithdrawService、DepositService、CollectionTriggerService、网关接口定义）
- 依赖 `erc20-platform-dal`（WithdrawRecordMapper、DepositRecordMapper、TokenConfigMapper、AlertRecordMapper）
- 依赖 `erc20-platform-domain`（实体类）
- 依赖 `erc20-platform-blockchain`（TxStatusChangedMessage、Web3j）
- RocketMQ Spring Boot Starter 2.2.3
- Redisson（消费去重）
