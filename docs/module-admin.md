# erc20-platform-admin 模块

管理后台 API 服务，提供运维和管理功能。

## 模块职责

- 提现审批管理（审批/拒绝）
- 代币配置管理
- 钱包管理（Nonce 重置、余额查看）
- 监控告警管理
- 管理员认证

## API 接口

### 提现管理 `/admin/withdraw`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pending` | 查询待审批提现列表 |
| POST | `/{id}/approve` | 审批通过 |
| POST | `/{id}/reject` | 拒绝提现 |
| GET | `/records` | 查询所有提现记录 |

### 代币管理 `/admin/token`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 代币列表 |
| POST | `/add` | 添加新代币 |
| PUT | `/{id}/config` | 更新代币配置 |
| POST | `/{id}/enable` | 启用代币 |
| POST | `/{id}/disable` | 禁用代币 |

### 钱包管理 `/admin/wallet`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 钱包列表 |
| GET | `/{address}/balance` | 查询链上余额 |
| POST | `/nonce/reset` | 重置 Nonce |

### 监控管理 `/admin/monitor`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/alerts` | 告警列表 |
| POST | `/alerts/{id}/handle` | 处理告警 |
| GET | `/sync-progress` | 区块同步进度 |
| GET | `/stats` | 平台统计数据 |

## 核心组件

### AdminAuthFilter

管理员认证过滤器：
- 验证管理员 Token（Header: `X-Admin-Token`）
- 权限校验
- 操作审计日志

### Controller 列表

| Controller | 职责 |
|------------|------|
| `AdminWithdrawController` | 提现审批流程 |
| `AdminTokenController` | 代币配置 CRUD |
| `AdminWalletController` | 钱包状态与 Nonce 管理 |
| `AdminMonitorController` | 监控、告警、统计 |

## 请求对象

| 类 | 用途 |
|----|------|
| `TokenAddRequest` | 添加代币（symbol, contractAddress, decimals...） |
| `TokenConfigUpdateRequest` | 更新配置（费率、阈值、确认块数） |
| `RejectRequest` | 拒绝提现（reason） |
| `NonceResetRequest` | Nonce 重置（chainId, address） |
| `AlertHandleRequest` | 处理告警（处理意见） |

## 配置

```yaml
server:
  port: 8081

admin:
  auth:
    token: ${ADMIN_TOKEN}
```

## 测试覆盖

2 个 Controller 测试类：
- `AdminWalletControllerTest`
- `AdminWithdrawControllerTest`

## 依赖关系

- 依赖 `erc20-platform-service`、`erc20-platform-blockchain`、`erc20-platform-dal`
- Spring Boot Web
- Swagger
