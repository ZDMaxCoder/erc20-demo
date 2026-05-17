## 1. 异常体系

- [x] 1.1 创建 `ERC20AdapterException` 基类（RuntimeException，含 message + cause 构造器），路径 `erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/exception/ERC20AdapterException.java`，编写单元测试验证构造器和继承关系
- [x] 1.2 创建 `TransferRevertedException`（含 contractAddress + revertReason 字段），编写单元测试验证字段访问
- [x] 1.3 创建 `TokenPausedException`（含 contractAddress 字段）和 `TokenBlacklistedException`（含 contractAddress + address 字段），编写单元测试
- [x] 1.4 创建 `AmountMismatchException`（含 expectedAmount + actualAmount BigInteger 字段）和 `TransferEventMissingException`（含 txHash 字段），编写单元测试

## 2. CallResult 模型

- [x] 2.1 创建 `CallOutcome` 枚举（SUCCESS / SUCCESS_NO_RETURN / RETURNED_FALSE / REVERTED / UNKNOWN），路径 `adapter/model/CallOutcome.java`
- [x] 2.2 创建 `CallResult` 不可变值对象（含 outcome + rawValue 字段，工厂方法 success()/successNoReturn()/returnedFalse()/reverted()/unknown(rawHex)，查询方法 isSuccess()/isDangerousFalse()），编写单元测试覆盖所有场景

## 3. TransferResult 模型

- [x] 3.1 创建 `TransferOutcome` 枚举（SUCCESS / FAILED / PENDING / ANOMALY），路径 `adapter/model/TransferOutcome.java`
- [x] 3.2 创建 `TransferResult` 不可变值对象（含 outcome/txHash/blockNumber/actualAmount/expectedAmount/balanceDiff/anomalyReason/events 字段，builder 模式构建，工厂方法 failed()/pending()，查询方法 isAmountConsistent()/hasBalanceDiffAnomaly()），编写单元测试覆盖 spec 中全部 6 个场景

## 4. TokenCapability 和 RiskLevel 枚举

- [x] 4.1 创建 `TokenCapability` 枚举（13 个值：STANDARD_RETURN / NO_RETURN_VALUE / APPROVE_RACE_CONDITION / BYTES32_METADATA / PAUSABLE / BLACKLISTABLE / UPGRADEABLE / MINTABLE / BURNABLE / FEE_ON_TRANSFER / REBASING / MAX_TRANSFER_LIMIT / COOLDOWN_REQUIRED），路径 `erc20-platform-common/src/main/java/com/erc20/platform/common/enums/TokenCapability.java`，编写单元测试验证枚举值数量和 EnumSet 用法
- [x] 4.2 创建 `RiskLevel` 枚举（LOW / MEDIUM / HIGH / CRITICAL，按顺序定义以支持 compareTo），路径 `erc20-platform-common/src/main/java/com/erc20/platform/common/enums/RiskLevel.java`，编写单元测试验证 compareTo 排序

## 5. TokenRiskProfile 模型

- [x] 5.1 创建 `TokenRiskProfile` 不可变值对象（含 contractAddress/capabilities/riskLevel/admissionPassed/lastAuditTime/autoProcessingAllowed 字段，builder 模式），路径 `adapter/model/TokenRiskProfile.java`
- [x] 5.2 实现 `requiresBalanceDiff()` 方法（FEE_ON_TRANSFER 能力 或 riskLevel >= HIGH 时返回 true），编写单元测试覆盖 spec 中 3 个场景
- [x] 5.3 实现 `requiresApproveReset()` 和 `isStandardProcessing()` 方法，编写单元测试覆盖 spec 中对应场景

## 6. ReturnValueDecoder

- [x] 6.1 创建 `ReturnValueDecoder` 类（Spring @Component），实现 `decodeBoolReturn(String hexResult)` 方法，路径 `adapter/rpc/ReturnValueDecoder.java`。实现逻辑：null/"0x"/空/短于64字符 → SUCCESS_NO_RETURN；64字符值为1 → SUCCESS；值为0 → RETURNED_FALSE；其他 → UNKNOWN
- [x] 6.2 编写 `ReturnValueDecoderTest` 单元测试，覆盖 spec 中全部 8 个场景（标准 true / null / 0x / 空字符串 / 全零 / 短数据 / 非标准值 0x02 / 大非标准值）

## 7. 编译验证

- [x] 7.1 运行 `mvn compile -pl erc20-platform-common,erc20-platform-blockchain` 确认编译通过，运行 `mvn test -pl erc20-platform-common,erc20-platform-blockchain` 确认所有新增测试通过
