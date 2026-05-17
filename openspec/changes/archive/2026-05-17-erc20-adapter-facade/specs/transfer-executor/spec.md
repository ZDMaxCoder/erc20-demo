## ADDED Requirements

### Requirement: SafeTransferExecutor encapsulates ERC-20 write pipeline

SafeTransferExecutor SHALL 封装 ERC-20 写操作的完整流程：
1. 调用 ERC20RpcClient 进行 eth_call 预检
2. 预检通过后委托 WalletService 内部方法完成 nonce分配→构建→签名→广播
3. 预检失败时抛出 TransferPreCheckFailedException

SafeTransferExecutor MUST 不负责准入检查（由上层 DefaultERC20Adapter 负责）。

#### Scenario: Transfer with successful precheck
- **WHEN** 调用 `executor.executeTransfer(from, contract, to, amount)` 且 preCheckTransfer 返回 SUCCESS
- **THEN** 委托 WalletService.sendERC20TransferInternal() 完成交易，返回 txHash

#### Scenario: Transfer with precheck returning RETURNED_FALSE
- **WHEN** 调用 `executor.executeTransfer(from, contract, to, amount)` 且 preCheckTransfer 返回 RETURNED_FALSE
- **THEN** 抛出 TransferPreCheckFailedException，包含 callResult 信息，不分配 nonce

#### Scenario: Transfer with precheck returning REVERTED
- **WHEN** 调用 `executor.executeTransfer(from, contract, to, amount)` 且 preCheckTransfer 返回 REVERTED
- **THEN** 抛出 TransferPreCheckFailedException，包含 callResult 信息，不分配 nonce

#### Scenario: Approve with successful precheck
- **WHEN** 调用 `executor.executeApprove(owner, contract, spender, amount)` 且 preCheckApprove 返回 SUCCESS
- **THEN** 委托 WalletService.sendApproveInternal() 完成交易，返回 txHash

### Requirement: TransferPreCheckFailedException provides failure context

TransferPreCheckFailedException SHALL 继承 ERC20AdapterException，包含：
- `contractAddress` (String): 失败的合约地址
- `callResult` (CallResult): 预检返回的完整结果对象

#### Scenario: Exception carries precheck result
- **WHEN** 预检失败抛出 TransferPreCheckFailedException
- **THEN** 可通过 getCallResult() 获取 CallResult，其 outcome 为 RETURNED_FALSE 或 REVERTED

### Requirement: WalletService exposes internal methods for SafeTransferExecutor

WalletService SHALL 新增以下包级可见方法（不加 public 修饰符）：
- `sendERC20TransferInternal(String from, String to, String contract, BigInteger amount)` — 跳过预检，直接执行 nonce→build→sign→broadcast→persist，返回 txHash
- `sendApproveInternal(String owner, String contract, String spender, BigInteger amount)` — approve 操作的 nonce→build→sign→broadcast→persist，返回 txHash

这些方法 MUST 保留 NONCE_TOO_LOW 自动恢复机制。

#### Scenario: sendERC20TransferInternal skips precheck
- **WHEN** SafeTransferExecutor 调用 `walletService.sendERC20TransferInternal(from, to, contract, amount)`
- **THEN** 不执行 ERC20RpcClient.preCheckTransfer()，直接 nonce 分配→构建→签名→广播

#### Scenario: sendApproveInternal builds approve transaction
- **WHEN** SafeTransferExecutor 调用 `walletService.sendApproveInternal(owner, contract, spender, amount)`
- **THEN** 编码 approve(address,uint256) 函数调用，完成签名和广播

#### Scenario: Internal methods retain NONCE_TOO_LOW recovery
- **WHEN** sendERC20TransferInternal 广播时遇到 NONCE_TOO_LOW 错误
- **THEN** 自动 reset nonce→重新分配→重新广播（与原 sendERC20Transfer 行为一致）

### Requirement: DefaultERC20Adapter applies approve-reset-to-zero strategy

当 token 的 TokenRiskProfile.requiresApproveReset() 返回 true 且请求的 amount > 0 时，DefaultERC20Adapter.safeApprove() SHALL：
1. 先调用 SafeTransferExecutor.executeApprove(owner, contract, spender, ZERO) 将 allowance 置零
2. 再调用 SafeTransferExecutor.executeApprove(owner, contract, spender, amount) 设置新额度

当 requiresApproveReset() 为 false 或 amount == 0 时，直接执行单次 approve。

#### Scenario: Approve with reset for APPROVE_RACE_CONDITION token
- **WHEN** 调用 safeApprove(owner, bnbContract, spender, 1000) 且 token 标记为 APPROVE_RACE_CONDITION
- **THEN** 依次广播两笔交易：approve(spender, 0) 和 approve(spender, 1000)

#### Scenario: Approve without reset for standard token
- **WHEN** 调用 safeApprove(owner, usdtContract, spender, 1000) 且 token 无 APPROVE_RACE_CONDITION 标记
- **THEN** 只广播一笔交易：approve(spender, 1000)

#### Scenario: Approve zero amount skips reset
- **WHEN** 调用 safeApprove(owner, bnbContract, spender, 0) 即使 token 标记为 APPROVE_RACE_CONDITION
- **THEN** 只广播一笔交易：approve(spender, 0)
