## ADDED Requirements

### Requirement: BalanceDiffChecker computes actual received amount

BalanceDiffChecker SHALL 提供方法计算转账前后的余额差值：
- `queryBalance(String contract, String address)` — 查询当前链上余额
- `computeDiff(BigInteger balanceBefore, BigInteger balanceAfter)` — 计算差值（after - before），返回 BigInteger 或 null（当任一参数为 null 时）

#### Scenario: Compute positive diff for successful transfer
- **WHEN** 调用 `computeDiff(balanceBefore=1000, balanceAfter=1500)`
- **THEN** 返回 500

#### Scenario: Compute zero diff when no change
- **WHEN** 调用 `computeDiff(balanceBefore=1000, balanceAfter=1000)`
- **THEN** 返回 0

#### Scenario: Return null when before balance is null
- **WHEN** 调用 `computeDiff(null, balanceAfter=1000)`
- **THEN** 返回 null

#### Scenario: queryBalance delegates to SafeERC20Caller
- **WHEN** 调用 `queryBalance(contract, address)`
- **THEN** 委托 SafeERC20Caller.safeBalanceOf(contract, address) 并返回结果

### Requirement: TransferConfirmer supports optional fourth layer balance-diff

TransferConfirmer SHALL 支持可选的第四层 balance-diff 确认。当确认请求中提供了 `balanceBefore`（转账前收款方余额）且 token profile 的 `requiresBalanceDiff()` 为 true 时：
1. 在三层确认通过（outcome=SUCCESS）后，查询收款方当前余额（balanceAfter）
2. 计算 balanceDiff = balanceAfter - balanceBefore
3. 将 balanceDiff 与 expectedAmount 对比：
   - 差值 == expectedAmount → 维持 SUCCESS
   - 差值 < expectedAmount（fee-on-transfer 场景）→ outcome 变为 ANOMALY，actualAmount 设为 balanceDiff
   - 差值 > expectedAmount（异常增长）→ outcome 变为 ANOMALY

当 `balanceBefore` 未提供或 `requiresBalanceDiff()` 为 false 时，跳过第四层。

#### Scenario: Standard token skips balance-diff layer
- **WHEN** 确认标准 USDT transfer，token profile requiresBalanceDiff()=false
- **THEN** 三层确认通过后直接返回 SUCCESS，不查询 balanceOf

#### Scenario: Fee-on-transfer token detected via balance-diff
- **WHEN** 确认 fee-on-transfer token transfer，expectedAmount=1000，event amount=1000，但 balanceDiff=950
- **THEN** 返回 TransferResult with outcome=ANOMALY，actualAmount=950，anomalyReason 包含 "Balance diff mismatch"

#### Scenario: Balance-diff matches expected amount
- **WHEN** 确认 HIGH risk token transfer，expectedAmount=1000，balanceDiff=1000
- **THEN** 返回 TransferResult with outcome=SUCCESS，balanceDiff=1000

#### Scenario: BalanceBefore not provided skips fourth layer
- **WHEN** confirmTransfer 调用时 balanceBefore 参数为 null
- **THEN** 即使 token requiresBalanceDiff()=true，也跳过第四层，按三层结果返回

### Requirement: ConfirmRequest model supports balanceBefore parameter

TransferConfirmer.confirm() 方法签名 SHALL 扩展为接收可选的 balanceBefore 参数。为保持向后兼容：
- 保留原三参数方法签名作为重载（balanceBefore 传 null）
- 新增四参数方法签名：`confirm(String txHash, String contract, BigInteger expectedAmount, String toAddress, BigInteger balanceBefore)`

#### Scenario: Original three-param method continues to work
- **WHEN** 调用 `transferConfirmer.confirm(txHash, contract, expectedAmount, toAddress)` （原签名）
- **THEN** 等价于 confirm(txHash, contract, expectedAmount, toAddress, null)，不执行第四层

#### Scenario: Five-param method enables fourth layer
- **WHEN** 调用 `transferConfirmer.confirm(txHash, contract, expectedAmount, toAddress, balanceBefore)` 且 balanceBefore != null 且 token requiresBalanceDiff()
- **THEN** 执行完整四层确认

### Requirement: TransferConfirmer integrates TokenRiskProfileRegistry

TransferConfirmer SHALL 注入 TokenRiskProfileRegistry，在 confirm() 方法内部根据 contract 查询 profile 判断是否启用第四层。

#### Scenario: Registry returns profile indicating balance-diff required
- **WHEN** token profile 的 capabilities 包含 FEE_ON_TRANSFER
- **THEN** TransferConfirmer 在三层通过后自动启用第四层 balance-diff 验证

#### Scenario: Registry unavailable gracefully degrades
- **WHEN** TokenRiskProfileRegistry 返回 null profile（不应发生但防御性编程）
- **THEN** 跳过第四层，按三层结果返回
