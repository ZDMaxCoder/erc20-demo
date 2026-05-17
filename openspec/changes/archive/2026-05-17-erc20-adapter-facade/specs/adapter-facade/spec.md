## ADDED Requirements

### Requirement: ERC20Adapter interface provides unified read operations

ERC20Adapter 接口 SHALL 提供以下读操作方法，委托 SafeERC20Caller 实现：
- `balanceOf(String contract, String owner)` 返回 BigInteger
- `decimals(String contract)` 返回 int
- `symbol(String contract)` 返回 String
- `name(String contract)` 返回 String
- `allowance(String contract, String owner, String spender)` 返回 BigInteger

所有读操作不经过准入检查（只读不涉及资金安全）。

#### Scenario: balanceOf returns token balance
- **WHEN** 调用 `erc20Adapter.balanceOf(usdtContract, userAddress)`
- **THEN** 返回该地址在该合约上的 token 余额（BigInteger）

#### Scenario: decimals returns token precision
- **WHEN** 调用 `erc20Adapter.decimals(usdtContract)`
- **THEN** 返回 token 精度（如 USDT 返回 6）

#### Scenario: allowance returns approved amount
- **WHEN** 调用 `erc20Adapter.allowance(contract, owner, spender)`
- **THEN** 返回 owner 授权给 spender 的剩余额度

### Requirement: ERC20Adapter interface provides unified write operations

ERC20Adapter 接口 SHALL 提供以下写操作方法：
- `safeTransfer(String fromAddress, String contract, String toAddress, BigInteger amount)` 返回 txHash
- `safeApprove(String ownerAddress, String contract, String spender, BigInteger amount)` 返回 txHash

写操作 MUST 在执行前通过 TokenAdmissionGateway 准入检查。

#### Scenario: safeTransfer executes full pipeline and returns txHash
- **WHEN** 调用 `erc20Adapter.safeTransfer(hotWallet, usdtContract, userAddress, amount)` 且 token 已通过准入
- **THEN** 执行准入检查→预检→nonce分配→签名→广播，返回交易哈希

#### Scenario: safeTransfer rejects non-admitted token
- **WHEN** 调用 `erc20Adapter.safeTransfer(hotWallet, rebasingToken, userAddress, amount)` 且 token 未通过准入
- **THEN** 抛出 `TokenAdmissionRejectedException`，不分配 nonce，不广播

#### Scenario: safeApprove applies reset-to-zero strategy when required
- **WHEN** 调用 `erc20Adapter.safeApprove(owner, bnbContract, spender, newAmount)` 且 token 标记为 APPROVE_RACE_CONDITION
- **THEN** 先发送 approve(spender, 0) 交易，再发送 approve(spender, newAmount) 交易

### Requirement: ERC20Adapter interface provides confirm operations

ERC20Adapter 接口 SHALL 提供确认方法：
- `confirmTransfer(String txHash, String contract, BigInteger expectedAmount, String toAddress)` 返回 TransferResult

#### Scenario: confirmTransfer delegates to TransferConfirmer
- **WHEN** 调用 `erc20Adapter.confirmTransfer(txHash, contract, expectedAmount, toAddress)`
- **THEN** 委托 TransferConfirmer.confirm() 执行多层确认并返回 TransferResult

### Requirement: ERC20Adapter interface provides governance queries

ERC20Adapter 接口 SHALL 提供治理查询方法：
- `getTokenProfile(String contract)` 返回 TokenRiskProfile
- `isTokenAdmitted(String contract)` 返回 boolean

#### Scenario: getTokenProfile returns registered profile
- **WHEN** 调用 `erc20Adapter.getTokenProfile(usdtContract)` 且 token 已注册
- **THEN** 返回包含 capabilities 和 riskLevel 的 TokenRiskProfile 对象

#### Scenario: isTokenAdmitted returns false for rejected token
- **WHEN** 调用 `erc20Adapter.isTokenAdmitted(rebasingToken)` 且 token 标记为 REBASING
- **THEN** 返回 false

### Requirement: DefaultERC20Adapter delegates to internal components

DefaultERC20Adapter SHALL 组合以下内部组件：
- SafeERC20Caller（读操作）
- TokenAdmissionGateway（准入检查）
- SafeTransferExecutor（写操作执行）
- TransferConfirmer（确认操作）
- TokenRiskProfileRegistry（治理查询）

DefaultERC20Adapter MUST 作为 Spring @Service 注册，业务层通过依赖注入获取。

#### Scenario: DefaultERC20Adapter is injectable as Spring bean
- **WHEN** Spring 容器启动
- **THEN** DefaultERC20Adapter 作为 ERC20Adapter 接口的实现注入到依赖方

#### Scenario: business layer uses only ERC20Adapter interface
- **WHEN** WithdrawTransactionSenderImpl 需要发送 ERC-20 转账
- **THEN** 通过注入的 ERC20Adapter 调用 safeTransfer，不直接依赖 WalletService
