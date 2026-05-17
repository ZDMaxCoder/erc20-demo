# erc20-platform-api 模块

面向用户的 REST API 服务，Spring Boot 应用入口。

## 模块职责

- 提供用户侧 REST API（充值查询、提现、账户）
- 请求参数校验
- 用户认证过滤器
- 全局异常处理
- Swagger API 文档

## 包结构

```
com.erc20.platform.api
├── Erc20PlatformApplication.java    — Spring Boot 启动类
├── config/
│   ├── GlobalExceptionHandler.java  — 全局异常处理
│   └── SwaggerConfig.java          — Swagger 文档配置
├── controller/
│   ├── AccountController.java       — 账户余额与流水
│   ├── DepositController.java       — 充值地址与记录
│   └── WithdrawController.java      — 提现操作与记录
├── filter/
│   └── UserAuthFilter.java         — 用户认证过滤器
├── vo/request/
│   ├── DepositAddressRequest.java   — 获取充值地址
│   ├── WithdrawCreateRequest.java   — 创建提现
│   └── FlowQueryRequest.java       — 流水查询
└── vo/response/
    ├── DepositAddressVO.java        — 充值地址
    ├── DepositRecordVO.java         — 充值记录
    ├── WithdrawCreateVO.java        — 提现创建结果
    ├── WithdrawRecordVO.java        — 提现记录
    ├── BalanceVO.java               — 余额信息
    └── AccountFlowVO.java           — 流水记录
```

## API 接口

### 充值接口 `/api/v1/deposit`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/address` | 获取/生成充值地址 |
| GET | `/records` | 查询充值记录（分页） |
| GET | `/{id}` | 查询充值详情 |

### 提现接口 `/api/v1/withdraw`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/create` | 创建提现请求 |
| GET | `/records` | 查询提现记录（分页） |
| GET | `/{id}` | 查询提现详情 |

**创建提现请求体**：

```json
{
  "requestId": "unique-request-id",
  "tokenId": 1,
  "toAddress": "0x...",
  "amount": 1000000
}
```

**响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "withdrawId": 12345
  }
}
```

### 账户接口 `/api/v1/account`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/balance` | 查询余额 |
| GET | `/flows` | 查询账户流水（分页） |

## 核心组件

### UserAuthFilter

用户认证过滤器：
- 仅对 `/api/v1/` 开头的路径生效
- 从 Header 中提取用户身份（`X-User-Id`）
- 设置到 `RequestAttribute` 供 Controller 使用
- 未认证请求返回 401

### GlobalExceptionHandler

全局异常处理（`@RestControllerAdvice`）：

| 异常类型 | 处理方式 |
|----------|----------|
| `BizException` | 返回业务错误码和消息 |
| `MethodArgumentNotValidException` | 返回参数校验错误详情（PARAM_ERROR） |
| `BindException` | 返回参数绑定错误详情（PARAM_ERROR） |
| `ConstraintViolationException` | 返回约束违规消息（PARAM_ERROR） |
| `DuplicateKeyException` | 视为幂等成功，返回 `Result.success()` |
| `OptimisticLockingFailureException` | 返回 SYSTEM_ERROR "Please retry" |
| `Exception`（兜底） | 返回 SYSTEM_ERROR，记录日志 |

### SwaggerConfig

Swagger 2 配置，自动生成 API 文档。访问路径：`/swagger-ui.html`

## 请求/响应对象

### Request VO

| 类 | 用途 | 校验规则 |
|----|------|----------|
| `DepositAddressRequest` | 获取充值地址 | tokenId 必填 |
| `WithdrawCreateRequest` | 创建提现 | requestId/tokenId/toAddress/amount 必填，@EthAddress 格式校验，@Positive 金额校验 |
| `FlowQueryRequest` | 流水查询（DTO 已定义但 Controller 使用 @RequestParam） | tokenId 必填，page(@Min 1)，size(@Min 1, @Max 100) |

### Response VO

| 类 | 用途 |
|----|------|
| `DepositAddressVO` | 充值地址 |
| `DepositRecordVO` | 充值记录 |
| `WithdrawCreateVO` | 提现创建结果 |
| `WithdrawRecordVO` | 提现记录 |
| `BalanceVO` | 余额信息 |
| `AccountFlowVO` | 流水记录 |

## 配置

```yaml
spring:
  application:
    name: erc20-platform
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

alert:
  dedup-interval-minutes: 10

---
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASS}
```

## 测试覆盖

3 个 Controller 测试类（MockMvc）：
- `AccountControllerTest`
- `DepositControllerTest`
- `WithdrawControllerTest`

## 依赖关系

- 依赖所有其他模块
- Spring Boot Web
- Spring Boot Validation
- Swagger (springfox)
