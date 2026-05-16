## Why

SafeERC20Caller 作为最底层的链上调用封装，当前内置了 3 次自动重试（500ms 间隔）。底层自动重试违反职责分离原则：底层不了解调用上下文，无法做出正确的重试决策。上游调用方（对账、归集、补偿）各自有不同的容错策略，应由它们自行决定是否重试以及如何处理失败。

## What Changes

- 移除 SafeERC20Caller.ethCall() 中的重试循环和 sleep 逻辑
- 新增 ChainCallException 异常类，携带 contract 地址和失败原因
- ethCall() 遇到 IOException 直接抛出 ChainCallException
- 各上游调用方增加 ChainCallException 处理逻辑（跳过/告警/等下次）

## Capabilities

### New Capabilities

- `chain-call-error-handling`: SafeERC20Caller 失败时抛出明确异常，上游调用方按业务场景自行处理

### Modified Capabilities

## Impact

- erc20-platform-blockchain: SafeERC20Caller（移除重试）、新增 ChainCallException
- erc20-platform-service: ChainReconcileJob、CollectionService 增加异常处理
- erc20-platform-mq: MqCompensationJob 增加异常处理
