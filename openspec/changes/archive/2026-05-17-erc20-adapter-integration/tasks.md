## 1. WithdrawStatus ANOMALY 状态

- [x] 1.1 在 `WithdrawStatus` 枚举中新增 `ANOMALY("ANOMALY", "Amount mismatch detected")` 值，编写单元测试验证枚举值存在和 code/description 正确
- [x] 1.2 在 `WithdrawStateMachine` 中添加 `PENDING_CONFIRM → ANOMALY` 转换规则，编写单元测试验证 canTransition 返回 true

## 2. ERC20RpcClient 实现

- [x] 2.1 创建 `ERC20RpcClient` 类（@Component，路径 `adapter/rpc/ERC20RpcClient.java`），注入 Web3j 和 ReturnValueDecoder，实现 `preCheckTransfer(String contract, String from, String to, BigInteger amount)` 方法：构造 transfer(to,amount) 的 Function，执行 eth_call，使用 ReturnValueDecoder 解码返回值，RPC error 含 "execution reverted" 时返回 CallResult.reverted()，其他 RPC error 抛 ChainCallException
- [x] 2.2 编写 `ERC20RpcClientTest` 单元测试（mock Web3j），覆盖 spec 中 4 个 preCheckTransfer 场景：标准 true、空返回（USDT）、返回 false、execution reverted
- [x] 2.3 实现 `preCheckApprove(String contract, String owner, String spender, BigInteger amount)` 方法，逻辑同 preCheckTransfer 但构造 approve(spender,amount) 的 Function
- [x] 2.4 编写 `ERC20RpcClientTest` 补充测试，覆盖 preCheckApprove 场景和 IOException 异常场景

## 3. TransferConfirmer 实现

- [x] 3.1 创建 `TransferConfirmer` 类（@Component，路径 `adapter/TransferConfirmer.java`），注入 Web3j 和 ERC20TransferEventParser，实现 `confirm(String txHash, String contract, BigInteger expectedAmount, String toAddress)` 方法的 Layer 1（receipt 获取和状态检查）
- [x] 3.2 编写 `TransferConfirmerTest` 单元测试（mock Web3j），覆盖：无 receipt→PENDING、receipt status 0x0→FAILED
- [x] 3.3 实现 Layer 2（Transfer event 解析）和 Layer 3（金额对比），receipt 成功但无 event→FAILED，有 event 则求和比较 expectedAmount
- [x] 3.4 编写 `TransferConfirmerTest` 补充测试，覆盖：无 Transfer event→FAILED、金额匹配→SUCCESS、金额不匹配→ANOMALY、多 event 求和→SUCCESS

## 4. TransactionConfirmTracker 重构

- [x] 4.1 重构 `TransactionConfirmTracker.checkConfirmation()`：移除内联的 receipt/event 验证逻辑，改为调用 `TransferConfirmer.confirm()`，根据返回的 TransferOutcome 决定 DB 更新和 MQ 发送
- [x] 4.2 修改 `TxStatusChangedMessage`：新增 `boolean anomaly` 和 `String anomalyReason` 字段，当 TransferResult outcome=ANOMALY 时设置为 true
- [x] 4.3 编写 `TransactionConfirmTrackerTest` 单元测试（mock TransferConfirmer），覆盖：SUCCESS→CONFIRMED+发消息、FAILED→FAILED+发消息、PENDING→不更新、ANOMALY→CONFIRMED+anomaly=true

## 5. WithdrawService 金额不一致阻断

- [x] 5.1 重构 `WithdrawService.doConfirmWithdraw()`：接收 actualAmount 后计算 expectedChainAmount，如果不相等则设置状态为 ANOMALY 而非 SUCCESS，不调用 decreaseFrozen，发 CRITICAL 告警
- [x] 5.2 重构 `WithdrawService.confirmWithdraw()` 入口：从 TxStatusChangedMessage 中读取 anomaly 标志，anomaly=true 时调用 ANOMALY 流程
- [x] 5.3 编写 `WithdrawServiceTest` 单元测试，覆盖：金额匹配→SUCCESS+decreaseFrozen、金额不匹配→ANOMALY+不调 decreaseFrozen+发告警、actualAmount 为 null→ANOMALY

## 6. AdminEventMonitor 自动熔断

- [x] 6.1 重构 `AdminEventMonitor.processLog()`：在检测到 Paused/Upgraded 事件后，调用 `token.setEnabled(0)` + `tokenConfigMapper.updateById(token)`，告警内容追加 "auto-disabled"
- [x] 6.2 编写 `AdminEventMonitorTest` 单元测试（mock tokenConfigMapper + alertService），覆盖：Paused→disable+告警、Upgraded→disable+告警、已 disabled→幂等无异常

## 7. WalletService NONCE_TOO_LOW 自动恢复

- [x] 7.1 重构 `WalletService.sendERC20Transfer()`：广播结果为 NONCE_TOO_LOW 时调用 resetNonce→重新 allocateNonce→rebuild→re-sign→re-broadcast，重试仍失败则 releaseNonce+抛异常
- [x] 7.2 编写 `WalletServiceTest` 单元测试（mock NonceManager + TransactionBroadcaster），覆盖：NONCE_TOO_LOW 重试成功、重试仍失败、非 NONCE_TOO_LOW 不重试
- [x] 7.3 重构 `WalletService.sendEthTransfer()` 应用同样的 NONCE_TOO_LOW 恢复逻辑，编写对应测试

## 8. WalletService 集成 ERC20RpcClient 预检

- [x] 8.1 在 `WalletService` 中注入 `ERC20RpcClient`，在 `sendERC20Transfer()` 的 nonce 分配前调用 `preCheckTransfer()`，preCheck 失败直接抛 BizException 不分配 nonce
- [x] 8.2 编写 `WalletServiceTest` 补充测试，覆盖：preCheck 成功→正常流程、preCheck RETURNED_FALSE→抛异常不分配 nonce、preCheck REVERTED→抛异常、ChainCallException→抛异常

## 9. 编译验证

- [x] 9.1 运行 `mvn compile` 确认全模块编译通过，运行 `mvn test -pl erc20-platform-common,erc20-platform-blockchain,erc20-platform-service` 确认所有新增和修改的测试通过
