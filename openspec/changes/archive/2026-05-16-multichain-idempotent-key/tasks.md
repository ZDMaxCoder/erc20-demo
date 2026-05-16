## 1. IdempotentKeyGenerator 改造

- [x] 1.1 修改 IdempotentKeyGenerator.depositKey 方法签名，新增 int chainId 参数，生成格式 `{chainId}_{txHash}_{logIndex}`
- [x] 1.2 修改 IdempotentKeyGenerator.withdrawKey 方法签名，新增 int chainId 参数，生成格式 `WD_{chainId}_{requestId}`
- [x] 1.3 修改 IdempotentKeyGenerator.collectionKey 方法签名，新增 int chainId 参数，生成格式 `COL_{chainId}_{fromAddress}_{tokenId}_{blockNumber}`
- [x] 1.4 更新 IdempotentKeyGeneratorTest 所有测试用例，验证新格式及多链隔离

## 2. 实体与数据库

- [x] 2.1 DepositRecord 实体新增 Integer chainId 字段
- [x] 2.2 WithdrawRecord 实体新增 Integer chainId 字段
- [x] 2.3 新增 Flyway V10 迁移脚本，为 t_deposit_record 和 t_withdraw_record 添加 chain_id INT NOT NULL 列

## 3. Service 层适配

- [x] 3.1 DepositService.processDeposit 调用 depositKey 时传入 tokenConfig.getChainId()，并在构建 DepositRecord 时设置 chainId
- [x] 3.2 WithdrawService.createWithdraw 调用 withdrawKey 时传入 tokenConfig.getChainId()，并在构建 WithdrawRecord 时设置 chainId
- [x] 3.3 更新 DepositServiceTest 验证 chainId 正确传递和幂等键格式
- [x] 3.4 更新 WithdrawServiceTest 验证 chainId 正确传递和幂等键格式
