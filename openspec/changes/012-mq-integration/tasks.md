## 1. Message Definitions (Setup)

- [x] 1.1 Create MqConstants class with all topic and tag string constants.
- [x] 1.2 Create all message DTO classes with @Data @Builder: TransferEventMessage, WithdrawExecuteMessage, DepositConfirmedMessage, TxStatusChangedMessage, AlertMessage.

## 2. RED — MQ Producer Tests

- [x] 2.1 Write MqProducerTest: send with valid message → RocketMQTemplate.syncSend called with correct destination (topic:tag), correct JSON body, correct message key.
- [x] 2.2 Write test: send fails 2 times then succeeds on 3rd → message eventually sent (retry logic).
- [x] 2.3 Write test: send fails all 3 retries → exception thrown, logged at ERROR.
- [x] 2.4 Write test: sendOrderly uses syncSendOrderly with correct hashKey.

## 3. GREEN — MQ Producer

- [x] 3.1 Implement MqProducer: send (sync, 3 retries, 1s interval), sendDelay (with delayLevel), sendOrderly (with hashKey). Log INFO on success, ERROR on final failure.
- [x] 3.2 Run tests — all pass.

## 4. RED — Base Consumer Tests

- [x] 4.1 Write BaseConsumerTest: first message → doConsume called, key added to Redis dedup set.
- [x] 4.2 Write test: same message key second time → doConsume NOT called (idempotent skip).
- [x] 4.3 Write test: doConsume throws retryable exception → exception propagated for MQ retry.
- [x] 4.4 Write test: doConsume throws non-retryable exception → consumed (no retry), logged ERROR.

## 5. GREEN — Base Consumer

- [x] 5.1 Implement abstract BaseConsumer<T>: deserialize → check Redis "mq:consumed:{group}:{key}" → call doConsume → add to Redis (TTL 24h) → handle exceptions.
- [x] 5.2 Run tests — all pass.

## 6. Consumer Implementations (Setup — thin wrappers already tested via services)

- [x] 6.1 Implement DepositEventConsumer: extends BaseConsumer<TransferEventDTO>, calls DepositService.onTransferEvent.
- [x] 6.2 Implement WithdrawExecuteConsumer: extends BaseConsumer<WithdrawExecuteMessage>, calls WithdrawService.executeWithdraw.
- [x] 6.3 Implement CollectionTriggerConsumer: extends BaseConsumer<DepositConfirmedMessage>, triggers collection scan.
- [x] 6.4 Implement TxStatusConsumer: extends BaseConsumer<TxStatusChangedMessage>, routes by bizType.
- [x] 6.5 Implement AlertConsumer: extends BaseConsumer<AlertMessage>, persists to t_alert_record.

## 7. RED — Compensation Job Tests

- [x] 7.1 Write MqCompensationJobTest: deposit CONFIRMING for >30min → compensation triggered (message re-published).
- [x] 7.2 Write test: withdraw APPROVED for >5min → re-publish execute message.
- [x] 7.3 Write test: already compensated 5 times → not compensated again (max limit).

## 8. GREEN — Compensation Job

- [x] 8.1 Implement MqCompensationJob @Scheduled(fixedDelay=300000): scan stuck records, re-publish, increment compensation count, respect max 5.
- [x] 8.2 Run tests — all pass.

## 9. REFACTOR & Verify

- [x] 9.1 Verify all consumer groups follow naming convention. All tests pass.
- [x] 9.2 mvn test passes for mq module.
