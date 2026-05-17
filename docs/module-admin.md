# erc20-platform-admin 模块

管理后台 API 服务，提供运维和管理功能。

## 模块职责

- 提现审批管理（查看待审批、审批通过、拒绝）
- 代币配置管理（添加、更新配置含启用/禁用）
- 钱包管理（余额查看、Nonce 重置、手动触发归集）
- 监控管理（区块同步状态、告警查看与处理）
- 操作者身份传递（X-Operator header）

## 包结构

```
com.erc20.platform.admin
├── Erc20AdminApplication.java       — Spring Boot 启动类
├── config/
│   ├── GlobalExceptionHandler.java  — 全局异常处理（7 种异常）
│   └── SwaggerConfig.java          — Swagger 文档配置
├── controller/
│   ├── AdminWithdrawController.java — 提现审批（/api/admin/v1/withdraw）
│   ├── AdminTokenController.java    — 代币配置（/api/admin/v1/token）
│   ├── AdminWalletController.java   — 钱包与归集（/api/admin/v1）
│   └── AdminMonitorController.java  — 监控告警（/api/admin/v1）
├── filter/
│   └── AdminAuthFilter.java        — 操作者身份提取过滤器
└── vo/request/
    ├── TokenAddRequest.java         — 添加代币
    ├── TokenConfigUpdateRequest.java — 更新配置（含启用/禁用）
    ├── RejectRequest.java           — 拒绝提现
    ├── NonceResetRequest.java       — Nonce 重置
    └── AlertHandleRequest.java      — 处理告警
```

## API 接口

### 提现管理 `/api/admin/v1/withdraw`

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/pending-review` | 查询待审批提现列表 | `page`(默认1), `size`(默认10) |
| POST | `/{id}/approve` | 审批通过 | PathVariable `id`, RequestAttribute `operator` |
| POST | `/{id}/reject` | 拒绝提现 | PathVariable `id`, RequestAttribute `operator`, Body: RejectRequest |

### 代币管理 `/api/admin/v1/token`

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/add` | 添加新代币 | Body: TokenAddRequest |
| PUT | `/{id}/config` | 更新代币配置（含启用/禁用） | PathVariable `id`, Body: TokenConfigUpdateRequest |

启用/禁用功能通过 `PUT /{id}/config` 的 `enabled` 字段控制（1=启用，0=禁用），无独立端点。

### 钱包管理 `/api/admin/v1`

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/wallet/balances` | 查询所有启用钱包 | 无 |
| POST | `/collection/trigger` | 手动触发批量归集 | 无 |
| POST | `/nonce/reset` | 重置指定钱包 Nonce | Body: NonceResetRequest |

### 监控管理 `/api/admin/v1`

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/block/sync-status` | 区块同步进度 | 无 |
| GET | `/alerts` | 未处理告警列表 | 无 |
| POST | `/alerts/{id}/handle` | 标记告警已处理 | PathVariable `id`, Body(可选): AlertHandleRequest |

## 核心组件

### AdminAuthFilter

操作者身份提取过滤器（`@Order(1)`）：
- 读取 HTTP Header `X-Operator`
- 存在时设置 request attribute `"operator"` 为该值
- 不存在时默认为 `"system"`
- 不做 token 校验和权限检查（认证由网关/外部系统负责）

### GlobalExceptionHandler

全局异常处理（`@RestControllerAdvice`）：

| 异常类型 | HTTP 状态 | 处理方式 |
|----------|-----------|----------|
| `BizException` | 200 | 返回 `Result.fail(code, message)` |
| `MethodArgumentNotValidException` | 200 | 拼接所有字段错误，返回 PARAM_ERROR |
| `BindException` | 200 | 拼接所有字段错误，返回 PARAM_ERROR |
| `ConstraintViolationException` | 200 | 拼接所有约束违规消息，返回 PARAM_ERROR |
| `DuplicateKeyException` | 200 | 视为幂等成功，返回 `Result.success()` |
| `OptimisticLockingFailureException` | 200 | 返回 SYSTEM_ERROR "Please retry" |
| `Exception`（兜底） | 200 | 记录堆栈日志，返回 SYSTEM_ERROR |

### SwaggerConfig

Swagger 2 配置（`@EnableSwagger2WebMvc`），自动生成管理后台 API 文档。

## 请求对象

### TokenAddRequest

| 字段 | 类型 | 校验 |
|------|------|------|
| `tokenName` | String | @NotBlank |
| `tokenSymbol` | String | @NotBlank |
| `contractAddress` | String | @NotBlank + @EthAddress |
| `decimals` | Integer | @NotNull |
| `chainId` | Integer | @NotNull |
| `depositConfirmBlocks` | Integer | @NotNull + @Positive |
| `minDepositAmount` | Long | @NotNull |
| `minWithdrawAmount` | Long | @NotNull |
| `withdrawFeeAmount` | Long | @NotNull |
| `collectionThreshold` | Long | @NotNull |

### TokenConfigUpdateRequest

部分更新，所有字段可选（非 null 才更新）：

| 字段 | 类型 |
|------|------|
| `depositConfirmBlocks` | Integer |
| `minDepositAmount` | Long |
| `minWithdrawAmount` | Long |
| `withdrawFeeAmount` | Long |
| `collectionThreshold` | Long |
| `enabled` | Integer |

### NonceResetRequest

| 字段 | 类型 | 校验 |
|------|------|------|
| `chainId` | Integer | @NotNull |
| `walletAddress` | String | @NotBlank |

### RejectRequest

| 字段 | 类型 | 校验 |
|------|------|------|
| `reason` | String | @NotBlank |

### AlertHandleRequest

| 字段 | 类型 | 校验 |
|------|------|------|
| `remark` | String | 无（可选） |

## 配置

```yaml
server:
  port: 8081

spring:
  application:
    name: erc20-admin
  profiles:
    active: dev
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

## 测试覆盖

2 个 Controller 测试类：
- `AdminWalletControllerTest`
- `AdminWithdrawControllerTest`

## 依赖关系

- 依赖 `erc20-platform-service`（WithdrawService、CollectionService）
- 依赖 `erc20-platform-blockchain`（NonceManager）
- 依赖 `erc20-platform-dal`（各 Mapper）
- 依赖 `erc20-platform-domain`（实体类）
- 依赖 `erc20-platform-common`（Result、ErrorCode、AddressUtil、@EthAddress）
- Spring Boot Web
- Swagger (springfox)
