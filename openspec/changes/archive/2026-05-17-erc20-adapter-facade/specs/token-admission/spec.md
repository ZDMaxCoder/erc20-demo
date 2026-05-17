## ADDED Requirements

### Requirement: TokenAdmissionGateway rejects high-risk tokens

TokenAdmissionGateway SHALL 在每次写操作（transfer/approve）前检查 token 准入状态。以下条件 MUST 触发拒绝并抛出 `TokenAdmissionRejectedException`：
1. `TokenRiskProfile.admissionPassed` 为 false
2. `TokenRiskProfile.riskLevel` 为 CRITICAL
3. token 具有 REBASING 能力标签且 `autoProcessingAllowed` 为 false
4. token 具有 FEE_ON_TRANSFER 能力标签且 `autoProcessingAllowed` 为 false

#### Scenario: Admitted standard token passes gateway
- **WHEN** 调用 `admissionGateway.checkAdmission(usdtContract, "transfer")` 且 USDT 的 profile 为 admissionPassed=true, riskLevel=LOW
- **THEN** 方法正常返回，不抛异常

#### Scenario: Non-admitted token is rejected
- **WHEN** 调用 `admissionGateway.checkAdmission(unknownToken, "transfer")` 且 token 的 admissionPassed=false
- **THEN** 抛出 `TokenAdmissionRejectedException`，message 包含 "has not passed admission review"

#### Scenario: CRITICAL risk level token is rejected
- **WHEN** 调用 `admissionGateway.checkAdmission(criticalToken, "transfer")` 且 token 的 riskLevel=CRITICAL
- **THEN** 抛出 `TokenAdmissionRejectedException`，message 包含 "CRITICAL"

#### Scenario: REBASING token is rejected
- **WHEN** 调用 `admissionGateway.checkAdmission(rebasingToken, "transfer")` 且 token capabilities 包含 REBASING 且 autoProcessingAllowed=false
- **THEN** 抛出 `TokenAdmissionRejectedException`，message 包含 "REBASING"

#### Scenario: FEE_ON_TRANSFER with autoProcessing allowed passes
- **WHEN** 调用 `admissionGateway.checkAdmission(feeToken, "transfer")` 且 token capabilities 包含 FEE_ON_TRANSFER 但 autoProcessingAllowed=true
- **THEN** 方法正常返回，不抛异常

### Requirement: TokenRiskProfileRegistry loads profiles from database

TokenRiskProfileRegistry SHALL 从 t_token_config 表加载 token 的 capabilities 和 risk_level 字段，构建 TokenRiskProfile 对象。

加载规则：
- `capabilities` 字段为逗号分隔的 TokenCapability 枚举名，null 或空视为 `STANDARD_RETURN`
- `risk_level` 字段为 RiskLevel 枚举名，null 视为 `LOW`
- `enabled=1` 映射为 `admissionPassed=true`
- `riskLevel <= MEDIUM` 映射为 `autoProcessingAllowed=true`

#### Scenario: Load profile for configured token
- **WHEN** 调用 `registry.getProfile(usdtContract)` 且 DB 中 t_token_config 有 capabilities='NO_RETURN_VALUE', risk_level='LOW', enabled=1
- **THEN** 返回 TokenRiskProfile 包含 capabilities={NO_RETURN_VALUE}, riskLevel=LOW, admissionPassed=true, autoProcessingAllowed=true

#### Scenario: Unknown token gets CRITICAL default profile
- **WHEN** 调用 `registry.getProfile(unknownContract)` 且 DB 中无此合约记录
- **THEN** 返回 TokenRiskProfile 包含 capabilities={}, riskLevel=CRITICAL, admissionPassed=false, autoProcessingAllowed=false

#### Scenario: Profile is cached after first load
- **WHEN** 连续两次调用 `registry.getProfile(sameContract)`
- **THEN** 第二次不触发 DB 查询，返回缓存的同一对象

#### Scenario: Cache invalidation on token disable
- **WHEN** AdminEventMonitor 禁用某 token 后调用 `registry.invalidate(contract)`
- **THEN** 下次 getProfile 重新从 DB 加载

### Requirement: TokenAdmissionRejectedException provides rejection context

TokenAdmissionRejectedException SHALL 继承 ERC20AdapterException，包含：
- `contractAddress` (String): 被拒绝的合约地址
- `reason` (String): 拒绝原因描述

#### Scenario: Exception carries rejection details
- **WHEN** 准入拒绝抛出 TokenAdmissionRejectedException
- **THEN** 异常的 getMessage() 包含合约地址和拒绝原因，getContractAddress() 和 getReason() 可单独获取

### Requirement: Database schema supports token risk profile

t_token_config 表 SHALL 通过 Flyway V11 迁移新增：
- `capabilities` VARCHAR(512) DEFAULT NULL：逗号分隔的 TokenCapability 枚举名
- `risk_level` VARCHAR(16) DEFAULT 'LOW'：RiskLevel 枚举名

迁移 MUST 向后兼容（nullable，有默认值）。

#### Scenario: Migration adds columns without breaking existing data
- **WHEN** 执行 V11 迁移
- **THEN** 已有 t_token_config 记录的 capabilities 为 NULL，risk_level 为 'LOW'

#### Scenario: Existing tokens continue to work with null capabilities
- **WHEN** TokenRiskProfileRegistry 加载一个 capabilities=NULL 的 token
- **THEN** 视为 STANDARD_RETURN，admissionPassed 由 enabled 字段决定
