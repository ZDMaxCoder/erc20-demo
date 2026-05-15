# ERC-20 中心化充提平台

支持 ERC-20 代币充值和提现的中心化平台，基于 Java 8 和 Spring Boot 构建。

## 项目架构

多模块 Maven 项目，职责清晰分层（点击查看各模块详细文档）：

| 模块 | 说明 | 详细文档 |
|------|------|----------|
| erc20-platform-common | 通用工具（AmountUtil、AddressUtil、分布式锁、枚举） | [查看](docs/module-common.md) |
| erc20-platform-domain | 实体类（MyBatis-Plus） | [查看](docs/module-domain.md) |
| erc20-platform-dal | 数据访问层（Mapper 接口、Flyway 迁移脚本） | [查看](docs/module-dal.md) |
| erc20-platform-blockchain | 链交互（Web3j、ERC-20 兼容性、Nonce/Gas 管理） | [查看](docs/module-blockchain.md) |
| erc20-platform-service | 业务逻辑（充值、提现、归集、账户、风控） | [查看](docs/module-service.md) |
| erc20-platform-mq | RocketMQ 生产者和消费者 | [查看](docs/module-mq.md) |
| erc20-platform-api | REST API（Spring Boot 应用入口） | [查看](docs/module-api.md) |
| erc20-platform-admin | 管理后台 API | [查看](docs/module-admin.md) |

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 1.8 |
| Spring Boot | 2.7.18 |
| Web3j | 4.9.8 |
| MyBatis-Plus | 3.5.3 |
| RocketMQ | 2.2.3 |
| Redisson | 3.23.5 |
| MySQL | 5.7+ |
| Redis | 6.0+ |

## 核心功能

- **充值检测** — 区块扫描 + 重组检测（parentHash 校验），可配置确认块数
- **提现处理** — 状态机生命周期管理，幂等提交，自动重试与补偿
- **ERC-20 兼容** — 处理非标代币（USDT 无返回值、BNB approve-需先置零、bytes32 返回值）
- **Nonce 管理** — 基于 Redis 的分布式 Nonce 分配，Gap 检测与回收
- **Gas 策略** — 支持 EIP-1559，基于百分位的动态定价，卡住交易自动加速替换
- **账户体系** — 乐观锁余额操作，long + exponent 金额模型（无浮点数）
- **风控系统** — 可配置规则（金额限制、频率、白名单），告警升级
- **归集服务** — 自动将用户地址资金归集到热钱包
- **监控告警** — 健康检查、告警记录、管理后台

## 金额模型

所有金额使用 `long minUnitValue + int exponent` 存储，避免浮点精度问题：

```java
// 12.34 USDT（decimals=6, exponent=6）
// 存储为：amount=12340000L, exponent=6
AmountUtil.toMinUnit(12.34, 6);           // → 12340000L
AmountUtil.toHumanReadable(12340000L, 6); // → 12.34
```

## 快速开始

### 环境要求

- JDK 8
- Maven 3.6+
- MySQL 5.7+
- Redis 6.0+
- RocketMQ 4.9+

### 编译

```bash
mvn clean package -DskipTests
```

### 测试

```bash
mvn test
```

测试使用 H2 内存数据库和嵌入式 Redis，无需外部依赖。

### 启动

```bash
# 在 application.yml 中配置 MySQL/Redis/RocketMQ/RPC 地址
java -jar erc20-platform-api/target/erc20-platform-api.jar --spring.profiles.active=dev
```

## 配置说明

`application.yml` 关键配置项：

```yaml
blockchain:
  rpc-url: https://mainnet.infura.io/v3/YOUR_KEY
  rpc-backup-url: https://eth.llamarpc.com
  chain-id: 1
  confirmation-blocks: 12

redis:
  address: redis://127.0.0.1:6379

rocketmq:
  name-server: 127.0.0.1:9876
```

## 数据库

Schema 由 Flyway 管理，启动时自动执行迁移。迁移文件位于：

```
erc20-platform-dal/src/main/resources/db/migration/
```

## 开发说明

本项目采用 TDD（Red → Green → Refactor）工作流构建，每个功能模块均有完整的单元测试和集成测试覆盖。

## License

MIT
