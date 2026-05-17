# erc20-platform-blockchain 模块

链上交互模块，封装所有与以太坊区块链的交互逻辑，包括 ERC-20 适配层、钱包管理、交易构建/签名/广播、区块同步、事件监控、Gas 策略、健康检查。

## 模块职责

- ERC-20 统一适配层（读/写/确认/准入/风控）
- 交易生命周期管理（构建→签名→广播→确认）
- Nonce 分配与回收（含 gap 检测）
- 区块同步与 Transfer 事件提取
- 链上事件监控与自动熔断
- Gas 估算与优先级策略（EIP-1559 / Legacy）
- 链上对账（balanceOf vs 平台账面）
- Web3j 连接管理（主/备 RPC 故障转移）
- Actuator 健康检查（节点连通性 + 区块同步延迟）

## 包结构

```
com.erc20.platform.blockchain
├── adapter/                    # ERC-20 适配层（核心）
│   ├── ERC20Adapter.java       # 业务层唯一入口接口
│   ├── DefaultERC20Adapter.java # 门面实现，组合所有内部组件
│   ├── TokenAdmissionGateway.java # 准入网关，拒绝高风险 token
│   ├── TokenRiskProfileRegistry.java # 从 DB 加载 token profile，ConcurrentHashMap 缓存
│   ├── BalanceDiffChecker.java  # 余额差值检查（fee-on-transfer 检测）
│   ├── TransferConfirmer.java   # 四层确认（receipt→event→amount→balance-diff）
│   ├── ConsecutiveFailureBreaker.java # 连续失败断路器，自动禁用 token
│   ├── TokenMetadataCache.java  # token 元数据内存缓存（decimals/symbol/name）
│   ├── exception/              # 适配层异常体系
│   │   ├── ERC20AdapterException.java        # 基类
│   │   ├── TokenAdmissionRejectedException.java # 准入拒绝
│   │   ├── TransferPreCheckFailedException.java # 预检失败
│   │   ├── TransferRevertedException.java     # 链上 revert
│   │   ├── TokenPausedException.java          # token 暂停
│   │   ├── TokenBlacklistedException.java     # 地址黑名单
│   │   ├── AmountMismatchException.java       # 金额不一致
│   │   └── TransferEventMissingException.java # 无 Transfer 事件
│   ├── model/                  # 适配层数据模型
│   │   ├── CallResult.java     # 预检结果（五态）
│   │   ├── CallOutcome.java    # 预检结果枚举
│   │   ├── TransferResult.java # 确认结果（四态+详情）
│   │   ├── TransferOutcome.java # 确认结果枚举
│   │   └── TokenRiskProfile.java # token 风险画像
│   └── rpc/                    # 底层 RPC 交互
│       ├── ERC20RpcClient.java # eth_call 预检（transfer/approve）
│       └── ReturnValueDecoder.java # 返回值语义解码
├── config/                     # Web3j 配置
│   ├── Web3jConfig.java        # @Configuration，OkHttpClient 连接池，主/备 RPC bean
│   └── Web3jProvider.java      # 主/备 Web3j 故障转移 Provider
├── erc20/                      # 底层 ERC-20 操作
│   ├── SafeERC20Caller.java    # 安全读接口（balanceOf/decimals/symbol/name/allowance）
│   ├── ERC20TransferEventParser.java # Transfer 事件解析
│   ├── ChainCallException.java # RPC 层异常
│   ├── TokenMetadata.java      # DTO（name/symbol/decimals）
│   ├── TokenMetadataReader.java # 通过 SafeERC20Caller 读取 token 元数据
│   └── TransferEvent.java      # Transfer 事件 DTO
├── gas/                        # Gas 策略
│   ├── GasStrategy.java        # 策略接口（getGasPrice/getReplacementGasPrice）
│   ├── EIP1559GasStrategy.java # EIP-1559 gas 定价（fee history）
│   ├── LegacyGasStrategy.java  # Legacy gas 定价
│   ├── GasConfig.java          # @Configuration 条件 bean 选择策略
│   ├── GasProperties.java      # @ConfigurationProperties(prefix="blockchain.gas")
│   ├── GasEstimator.java       # eth_estimateGas 估算
│   ├── GasPrice.java           # DTO（gasPrice/maxFeePerGas/maxPriorityFeePerGas/eip1559）
│   ├── GasPriority.java        # 优先级枚举: LOW, MEDIUM, HIGH, URGENT
│   ├── GasPriceCache.java      # Redis + 本地缓存，定时刷新
│   ├── GasCalculationHelper.java # 内部计算辅助（cap/multiplier）
│   └── StuckTransactionHandler.java # 定时检测并替换卡住的交易
├── health/                     # Actuator 健康检查
│   ├── BlockSyncHealthIndicator.java  # 区块同步延迟健康指示器
│   └── EthereumHealthIndicator.java   # 以太坊节点连通性健康指示器
├── monitor/                    # 链上事件监控
│   └── AdminEventMonitor.java  # Paused/Upgraded 事件 → 自动禁用 token + 清缓存
├── nonce/                      # Nonce 管理
│   ├── NonceManager.java       # Redis pending_nonce + gaps + allocated
│   ├── NonceGapDetector.java   # 定时检测 nonce gap，触发告警
│   └── NonceRedisOperations.java # Nonce Redis 操作封装（pending/gaps/allocated keys）
├── reconcile/                  # 链上对账
│   └── ChainReconcileJob.java  # 定时比对链上 balanceOf 与平台账面
├── sync/                       # 区块同步
│   ├── BlockSyncEngine.java    # 区块遍历 + reorg 检测
│   ├── BlockEventPublisher.java # 事件发布接口
│   ├── RocketMQBlockEventPublisher.java # RocketMQ 实现
│   ├── TransferEventExtractor.java # 事件提取接口
│   ├── TransferEventExtractorImpl.java # 从区块提取 Transfer 事件
│   ├── TransferEventMessage.java # Transfer 事件 MQ 消息 DTO
│   ├── BlockSyncProperties.java # @ConfigurationProperties(prefix="blockchain.sync")
│   └── ReorgHandler.java       # 重组回退
└── wallet/                     # 钱包与交易管理
    ├── WalletService.java      # 交易编排（nonce+sign+broadcast+NONCE_TOO_LOW恢复）
    ├── SafeTransferExecutor.java # 适配层写操作委托
    ├── WithdrawTransactionSenderImpl.java # 提现发送（通过 ERC20Adapter）
    ├── CollectionTransactionSenderImpl.java # 归集发送（通过 ERC20Adapter）
    ├── TransactionBuilder.java # Raw Transaction 构造
    ├── TransactionSigner.java  # 签名接口
    ├── KmsTransactionSigner.java # @Profile("prod") KMS 签名实现
    ├── LocalKeySigner.java     # @Profile("dev") 本地私钥签名实现
    ├── TransactionBroadcaster.java # 广播 + 错误分类
    ├── BroadcastResult.java    # 广播结果 DTO（success/txHash/errorType）
    ├── BroadcastErrorType.java # 广播错误枚举: NONE, NONCE_TOO_LOW, ALREADY_KNOWN, UNDERPRICED, INSUFFICIENT_FUNDS, UNKNOWN
    ├── TransactionConfirmTracker.java # 定时轮询确认
    └── TxStatusChangedMessage.java # MQ 消息体
```

## 核心交互流程

### 提现链路

```
WithdrawService → WithdrawTransactionSenderImpl → ERC20Adapter.safeTransfer()
  → TokenAdmissionGateway.checkAdmission()    # 准入检查
  → SafeTransferExecutor.executeTransfer()     # 预检+执行
    → ERC20RpcClient.preCheckTransfer()        # eth_call 模拟
    → WalletService.sendERC20TransferInternal() # nonce+sign+broadcast
```

### 确认链路

```
TransactionConfirmTracker (每5秒轮询)
  → TransferConfirmer.confirm()
    → Layer 1: receipt status
    → Layer 2: Transfer event 解析
    → Layer 3: expectedAmount vs actualAmount
    → Layer 4: balance-diff (按需，fee-on-transfer token)
  → TxStatusChangedMessage → RocketMQ
  → WithdrawService.confirmWithdraw() / failWithdraw()
```

### 准入策略

| 条件 | 结果 |
|------|------|
| admissionPassed = false | 拒绝 |
| riskLevel = CRITICAL | 拒绝 |
| REBASING + !autoProcessingAllowed | 拒绝 |
| FEE_ON_TRANSFER + !autoProcessingAllowed | 拒绝 |
| 其他 | 通过 |

### Gas 策略

```
GasProperties.strategy 决定使用 EIP1559 或 Legacy 策略
  → GasPriceCache 定时从链上获取并缓存 gas price
  → TransactionBuilder 构造交易时获取当前 gas price
  → StuckTransactionHandler 定时扫描超时交易，用 replacement gas price 加速
```

### 断路器机制

```
ConsecutiveFailureBreaker
  → 连续 N 次转账失败后自动禁用 token
  → 触发告警通知运维
  → 需管理员手动重新启用
```

## 配置项

| 配置 | 说明 | 默认值 |
|------|------|--------|
| `web3j.client-address` | 主以太坊节点 RPC 地址 | - |
| `web3j.backup-address` | 备用 RPC 地址 | - |
| `blockchain.confirm-interval-ms` | 确认轮询间隔 | 5000 |
| `blockchain.chain-id` | 链 ID | 1 |
| `blockchain.gas.strategy` | Gas 策略（eip1559/legacy） | legacy |
| `blockchain.gas.max-gas-price` | Gas 价格上限 | - |
| `blockchain.gas.stuck-timeout` | 交易卡住超时阈值 | - |
| `blockchain.sync.start-block` | 起始同步区块 | - |
| `blockchain.sync.batch-size` | 批量同步区块数 | - |

## 非标准 Token 处理策略

适配层对非标准 ERC-20 Token 的处理分为五个治理层级：

| 层级 | 含义 | 示例 |
|------|------|------|
| **代码层兼容** | 适配层自动处理，业务层无感知 | 无返回值 Token、bytes32 metadata |
| **准入拒绝** | TokenAdmissionGateway 拦截，拒绝写操作 | rebasing、ERC-777 |
| **自动熔断** | 链上事件触发自动禁用 | paused、proxy upgraded |
| **预检拦截+监控** | eth_call 预检可发现，需配合运营处理 | blacklist、地址限制 |
| **人工处置** | 需运营或开发介入 | 事件缺失、合约升级后 ABI 变更 |

### 策略矩阵

| 场景 | 处理策略 | 治理层级 | 实现组件 | 说明 |
|------|----------|----------|----------|------|
| transfer 不返回 bool (USDT) | 空返回值视为成功 | 代码层兼容 | ReturnValueDecoder → SUCCESS_NO_RETURN | 标准兼容处理，无需额外配置 |
| transferFrom 不返回 bool | 同上 | 代码层兼容 | ReturnValueDecoder | 与 transfer 共用解码逻辑 |
| approve 不返回 bool | 同上 | 代码层兼容 | ReturnValueDecoder | 与 transfer 共用解码逻辑 |
| 返回 false 不 revert | 预检检测到 RETURNED_FALSE 后阻断发送 | 代码层兼容 | ERC20RpcClient.preCheck → CallResult.returnedFalse() | 不会发出交易，通过 TransferPreCheckFailedException 告知上游 |
| approve race condition (BNB) | 自动先 approve(0) 再设新值 | 代码层兼容 | DefaultERC20Adapter + APPROVE_RACE_CONDITION 标签 | 通过 TokenRiskProfile.requiresApproveReset() 判断 |
| decimals 返回 bytes32 | 自动 fallback 解码 | 代码层兼容 | SafeERC20Caller.decodeBytes32AsInt | 先尝试 Uint8 解码，失败后 bytes32 解码 |
| symbol/name 返回 bytes32 (MKR) | 自动 fallback 解码 | 代码层兼容 | SafeERC20Caller.decodeBytes32AsString | 先尝试 Utf8String 解码，失败后 bytes32 解码 |
| 多条 Transfer event | 累加所有事件金额 | 代码层兼容 | TransferConfirmer + ERC20TransferEventParser | 解析 receipt 中所有 Transfer 事件并求和 |
| fee-on-transfer (PAXG) | balance-diff 检测实际到账 | 预检拦截+监控 | BalanceDiffChecker + FEE_ON_TRANSFER 标签 | 金额不一致时转 ANOMALY 状态，通知运营；未配置 autoProcessingAllowed 时准入拒绝 |
| rebasing (stETH) | 拒绝接入 | 准入拒绝 | TokenAdmissionGateway + REBASING 标签 | 余额随时间变化无法稳定记账，autoProcessingAllowed=false 时一律拒绝 |
| blacklist/whitelist (USDC) | 预检可捕获 revert | 预检拦截+监控 | ERC20RpcClient.preCheckTransfer → revert | 抛出 TransferPreCheckFailedException；建议维护地址黑名单辅助判断 |
| paused/frozen | 链上 Paused 事件触发自动禁用 | 自动熔断 | AdminEventMonitor → 监听 Paused(address) 事件 | 自动设 enabled=0，清 TokenRiskProfileRegistry 缓存，发 CRITICAL 告警 |
| proxy/upgradeable | 链上 Upgraded 事件触发自动禁用 | 自动熔断 | AdminEventMonitor → 监听 Upgraded(address) 事件 | 清 TokenMetadataCache + TokenRiskProfileRegistry，等人工验证新实现后重新启用 |
| mintable/burnable | 仅标签标注，不影响转账 | 监控 | TokenCapability.MINTABLE/BURNABLE | 可结合对账发现异常增发/销毁 |
| ERC-777 hook/callback | 拒绝接入 | 准入拒绝 | 上币审核阶段拒绝 | reentrancy 风险，不适合标准 CEX 处理模型 |
| Transfer 事件缺失 | 确认失败，等待人工处理 | 人工处置 | TransferConfirmer → FAILED + TransferEventMissingException | 交易可能成功但无法程序化确认，需人工查链 |
| 特定地址限制 (单笔上限等) | 预检可部分捕获 | 预检拦截+监控 | ERC20RpcClient.preCheckTransfer + MAX_TRANSFER_LIMIT 标签 | 超限时 eth_call revert；建议运营配置单笔上限 |
| ERC-20 事件参数非标 | 无法自动处理 | 人工处置 | 上币审核阶段识别 | 非标事件无法被 ERC20TransferEventParser 解析，应拒绝接入 |

### 能力标签与治理关系

```
TokenCapability (t_token_config.capabilities)
├── STANDARD_RETURN          → 无特殊处理
├── NO_RETURN_VALUE          → ReturnValueDecoder 空返回兼容
├── APPROVE_RACE_CONDITION   → DefaultERC20Adapter 先置零
├── BYTES32_METADATA         → SafeERC20Caller fallback 解码
├── PAUSABLE                 → AdminEventMonitor 监听 Paused 事件
├── BLACKLISTABLE            → 预检 + 地址黑名单管理
├── UPGRADEABLE              → AdminEventMonitor 监听 Upgraded 事件
├── MINTABLE                 → 对账监控（异常增发）
├── BURNABLE                 → 对账监控（异常销毁）
├── FEE_ON_TRANSFER          → BalanceDiffChecker + 准入控制
├── REBASING                 → 准入拒绝
├── MAX_TRANSFER_LIMIT       → 预检 + 运营配置拆分
└── COOLDOWN_REQUIRED        → 预检 + 发送间隔控制
```

### 结果确认四层策略

适配层通过 `TransferConfirmer` 进行递进式结果确认：

| 层级 | 检查内容 | 失败结果 | 适用场景 |
|------|----------|----------|----------|
| Layer 1 | receipt.status == 0x1 | FAILED | 所有 token |
| Layer 2 | Transfer event 存在 | FAILED (TransferEventMissing) | 所有 token |
| Layer 3 | event.amount == expectedAmount | ANOMALY (AmountMismatch) | 所有 token |
| Layer 4 | balanceAfter - balanceBefore == expectedAmount | ANOMALY (BalanceDiff) | 仅 FEE_ON_TRANSFER 或 riskLevel >= HIGH 的 token |

Layer 4 由 `TokenRiskProfile.requiresBalanceDiff()` 控制是否启用。

### 连续失败断路器

`ConsecutiveFailureBreaker` 基于 Redis 计数器实现自动熔断：

```
交易确认 FAILED → recordFailure(contract)
  → failures++ 
  → if failures >= threshold (默认 5):
      → t_token_config.circuit_breaker_status = 'OPEN'
      → TokenRiskProfileRegistry.invalidate(contract)
      → 发送 CRITICAL 告警
      → 后续请求被 TokenAdmissionGateway 拦截

交易确认 SUCCESS → recordSuccess(contract)
  → failures = 0

管理员手动恢复 → resetBreaker(contract)
  → 删除 Redis key
  → t_token_config.circuit_breaker_status = 'CLOSED'
  → TokenRiskProfileRegistry.invalidate(contract)
```

### 上币审核建议

新 Token 接入前应完成以下验证：

1. **合约有效性** — 地址有 bytecode (extcodesize > 0)
2. **标准接口** — 支持 balanceOf/transfer/approve/transferFrom/decimals
3. **返回值格式** — 确认属于 bool 返回 / 无返回值 / bytes32 中的哪种
4. **能力探测** — 是否为 proxy、是否有 pause/blacklist 函数
5. **事件规范** — Transfer 事件签名和参数符合 ERC-20 标准
6. **Token 类型判定** — STANDARD / FEE_ON_TRANSFER / REBASING / UNSUPPORTED
7. **能力标签配置** — 根据探测结果写入 t_token_config.capabilities
8. **风险等级设定** — 根据能力标签综合判断写入 t_token_config.risk_level
9. **测试转账验证** — 在测试网或小额主网验证完整链路

## 依赖关系

- 依赖 `erc20-platform-common`（枚举、工具类）
- 依赖 `erc20-platform-dal`（TokenConfigMapper、TransactionRecordMapper）
- 依赖 `erc20-platform-service`（WithdrawTransactionSender/CollectionTransactionSender 接口定义）
- 外部依赖: Web3j 4.9.8, RocketMQ, OkHttp, Redisson
