## 1. Project Skeleton (Setup — no TDD needed for scaffolding)

- [x] 1.1 Create parent POM with dependencyManagement for all dependencies (Spring Boot 2.7.18, Web3j 4.9.8, MyBatis-Plus 3.5.3, RocketMQ 2.2.3, Redisson 3.23.5, Guava 32.1.3-jre, MapStruct 1.5.5.Final, Lombok). Configure maven-compiler-plugin source/target 1.8. Add JUnit 5 + Mockito + H2 + embedded-redis + MockWebServer as test dependencies.
- [x] 1.2 Create sub-modules: erc20-platform-common, erc20-platform-domain, erc20-platform-dal, erc20-platform-service, erc20-platform-blockchain, erc20-platform-mq, erc20-platform-api, erc20-platform-admin. Each module declares only needed dependencies without version.
- [x] 1.3 Create .gitignore, application.yml in api module with dev/test/prod profiles, application-test.yml with H2 + embedded Redis config, and Spring Boot main class.

## 2. Database Schema (Setup — no TDD needed for DDL)

- [x] 2.1 Create Flyway migration V1__init_token_and_address.sql: t_token_config and t_user_address with all fields per design.md.
- [x] 2.2 Create V2__init_deposit_and_block.sql: t_deposit_record, t_block_sync_progress, t_block_record.
- [x] 2.3 Create V3__init_withdraw_and_transaction.sql: t_withdraw_record, t_transaction_record, t_nonce_record.
- [x] 2.4 Create V4__init_account_and_wallet.sql: t_account_balance, t_account_flow, t_wallet_config, t_collection_task, t_alert_record.
- [x] 2.5 Add appropriate indexes on all tables with SQL comments explaining each index purpose.

## 3. Common Utilities — RED (Write Tests First)

- [x] 3.1 Write AmountUtilTest: toMinUnit(12.34, 2)==1234, toMinUnit(0, 2)==0, toHumanReadable(1234, 2)==12.34, toChainAmount(100000000L, 6, 6)==BigInteger("100000000"), fromChainAmount(BigInteger("100000000"), 6, 6)==100000000L, boundary Long.MAX_VALUE does not overflow, different exponent conversions preserve precision.
- [x] 3.2 Write AddressUtilTest: valid lowercase address passes, valid checksum address passes, invalid length fails, missing 0x prefix fails, invalid hex chars fail, normalize returns lowercase.
- [x] 3.3 Write IdempotentKeyGeneratorTest: depositKey("0xabc", 3)=="0xabc_3", withdrawKey("req123")=="WD_req123", collectionKey("0xaddr", 1, 100)=="COL_0xaddr_1_100".

## 4. Common Utilities — GREEN (Implement to Pass)

- [x] 4.1 Implement Result<T> (code, message, data, timestamp), BizException, ErrorCode enum.
- [x] 4.2 Implement AmountUtil: all methods using BigDecimal/BigInteger, no floating point. Run tests — all pass.
- [x] 4.3 Implement AddressUtil: validate + normalize. Run tests — all pass.
- [x] 4.4 Implement IdempotentKeyGenerator. Run tests — all pass.
- [x] 4.5 Implement @DistributedLock annotation + AOP aspect using Redisson.
- [x] 4.6 Implement RetryTemplate: configurable max retries, exponential backoff, retryable exception types.
- [x] 4.7 Define all enums: DepositStatus, WithdrawStatus, TxStatus, WalletType, FlowType, FlowDirection, AlertLevel.

## 5. Domain Entities (Setup — no TDD needed for POJOs)

- [x] 5.1 Create entity classes for all 13 tables in erc20-platform-domain module. Use MyBatis-Plus @TableName, Lombok @Data @Builder @NoArgsConstructor @AllArgsConstructor. Amount fields as long type with corresponding exponent field.
- [x] 5.2 Create MyBatis-Plus Mapper interfaces in erc20-platform-dal for all entities.

## 6. Verification

- [x] 6.1 Run mvn test — all tests pass. Run mvn compile — all modules compile.
