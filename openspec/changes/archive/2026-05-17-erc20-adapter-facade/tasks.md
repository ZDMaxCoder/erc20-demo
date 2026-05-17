## 1. 数据库与异常基础

- [x] 1.1 新增 Flyway V11 迁移: t_token_config 表增加 capabilities(VARCHAR 512) 和 risk_level(VARCHAR 16 DEFAULT 'LOW') 字段
- [x] 1.2 TokenConfig 实体类增加 capabilities 和 riskLevel 字段，含 getter/setter
- [x] 1.3 新增 TokenAdmissionRejectedException（继承 ERC20AdapterException，含 contractAddress + reason 字段），含单元测试
- [x] 1.4 新增 TransferPreCheckFailedException（继承 ERC20AdapterException，含 contractAddress + callResult 字段），含单元测试

## 2. TokenRiskProfileRegistry

- [x] 2.1 新增 TokenRiskProfileRegistry 类（@Component），注入 TokenConfigMapper，实现 getProfile(contract) 方法：从 DB 加载 capabilities/risk_level，构建 TokenRiskProfile；未知 token 返回 CRITICAL 默认 profile
- [x] 2.2 实现 ConcurrentHashMap 缓存 + invalidate(contract) 方法
- [x] 2.3 TokenRiskProfileRegistry 单元测试：覆盖标准 token 加载、未知 token 默认值、缓存命中、缓存失效 4 个场景

## 3. TokenAdmissionGateway

- [x] 3.1 新增 TokenAdmissionGateway 类（@Component），注入 TokenRiskProfileRegistry，实现 checkAdmission(contract, operation) 方法：检查 admissionPassed、CRITICAL 风险、REBASING/FEE_ON_TRANSFER 拒绝逻辑
- [x] 3.2 实现 isAdmitted(contract) 便捷方法
- [x] 3.3 TokenAdmissionGateway 单元测试：覆盖标准通过、未准入拒绝、CRITICAL 拒绝、REBASING 拒绝、FEE_ON_TRANSFER autoProcessing 通过 5 个场景

## 4. SafeTransferExecutor

- [x] 4.1 WalletService 新增 sendERC20TransferInternal 包级方法：从现有 sendERC20Transfer 提取核心逻辑（跳过预检，保留 nonce+sign+broadcast+NONCE_TOO_LOW 恢复）
- [x] 4.2 WalletService 新增 sendApproveInternal 包级方法：编码 approve(address,uint256) 函数调用，执行 nonce+sign+broadcast
- [x] 4.3 新增 SafeTransferExecutor 类（@Component，与 WalletService 同包），注入 ERC20RpcClient + WalletService，实现 executeTransfer(from, contract, to, amount) 方法：预检→成功则委托 internal→失败抛 TransferPreCheckFailedException
- [x] 4.4 SafeTransferExecutor 实现 executeApprove(owner, contract, spender, amount) 方法
- [x] 4.5 SafeTransferExecutor 单元测试：覆盖预检成功、预检 RETURNED_FALSE 拒绝、预检 REVERTED 拒绝、approve 预检成功 4 个场景
- [x] 4.6 WalletService 现有测试验证不被破坏（mvn test -pl erc20-platform-blockchain -Dtest=WalletServiceTest）

## 5. BalanceDiffChecker

- [x] 5.1 新增 BalanceDiffChecker 类（@Component），注入 SafeERC20Caller，实现 queryBalance(contract, address) 和 computeDiff(balanceBefore, balanceAfter) 方法
- [x] 5.2 BalanceDiffChecker 单元测试：覆盖正常差值、零差值、null 参数返回 null 3 个场景

## 6. TransferConfirmer 四层增强

- [x] 6.1 TransferConfirmer 注入 TokenRiskProfileRegistry + BalanceDiffChecker
- [x] 6.2 新增重载方法 confirm(txHash, contract, expectedAmount, toAddress, balanceBefore)：在三层通过后，当 requiresBalanceDiff()=true 且 balanceBefore!=null 时执行第四层 balance-diff 校验
- [x] 6.3 原三参数 confirm 方法委托新方法（balanceBefore 传 null）
- [x] 6.4 TransferConfirmer 第四层单元测试：覆盖标准 token 跳过、fee-on-transfer 检测 ANOMALY、balance-diff 匹配 SUCCESS、balanceBefore 为 null 跳过 4 个场景
- [x] 6.5 TransferConfirmer 现有测试验证不被破坏

## 7. ERC20Adapter 接口与实现

- [x] 7.1 新增 ERC20Adapter 接口：定义 balanceOf/decimals/symbol/name/allowance/safeTransfer/safeApprove/confirmTransfer/getTokenProfile/isTokenAdmitted 方法签名
- [x] 7.2 新增 DefaultERC20Adapter 实现类（@Service），注入 SafeERC20Caller + TokenAdmissionGateway + SafeTransferExecutor + TransferConfirmer + TokenRiskProfileRegistry
- [x] 7.3 实现读操作（委托 SafeERC20Caller）
- [x] 7.4 实现 safeTransfer（准入检查→委托 SafeTransferExecutor）
- [x] 7.5 实现 safeApprove（准入检查→requiresApproveReset 判断→先置零策略→委托 SafeTransferExecutor）
- [x] 7.6 实现 confirmTransfer（委托 TransferConfirmer）和治理查询方法
- [x] 7.7 DefaultERC20Adapter 单元测试：覆盖读操作委托、safeTransfer 全流程、准入拒绝、safeApprove 先置零、safeApprove 标准、confirmTransfer 委托 6 个场景

## 8. 业务层集成

- [x] 8.1 WithdrawTransactionSenderImpl 改为注入 ERC20Adapter，sendERC20Transfer 方法内调用 erc20Adapter.safeTransfer() 替代直接调用 WalletService
- [x] 8.2 CollectionTransactionSenderImpl 改为注入 ERC20Adapter，ERC-20 转账调用 erc20Adapter.safeTransfer() 替代直接调用 WalletService
- [x] 8.3 AdminEventMonitor 在禁用 token 后调用 TokenRiskProfileRegistry.invalidate(contract) 清除缓存
- [x] 8.4 业务层集成测试：验证 WithdrawTransactionSenderImpl 通过 ERC20Adapter 发送转账的完整链路（mock 底层 Web3j）
- [x] 8.5 全量测试通过验证：mvn test 确保所有模块测试通过

## 9. SafeERC20Caller 清理

- [x] 9.1 SafeERC20Caller 删除 safeTransfer()/safeApprove() stub 方法（抛 UnsupportedOperationException 的）
- [x] 9.2 SafeERC20Caller 新增 safeAllowance(contract, owner, spender) 方法实现（编码 allowance(address,address) 调用）
- [x] 9.3 SafeERC20Caller 相关单元测试更新
