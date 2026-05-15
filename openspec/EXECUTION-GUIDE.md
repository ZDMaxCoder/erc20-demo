# OpenSpec Execution Guide — ERC-20 Platform

## Overview

This project uses OpenSpec to manage 14 incremental changes that build the complete ERC-20 deposit/withdrawal platform. Every change follows **TDD (Test-Driven Development)**: RED → GREEN → REFACTOR. Tests are embedded within each change, not a separate phase.

## TDD Workflow

Each change's `tasks.md` follows this pattern:

```
## N. Feature — RED (Write Tests First)
- [ ] Write test: expected behavior A
- [ ] Write test: expected behavior B
- [ ] Write test: edge case C

## N+1. Feature — GREEN (Implement to Pass)
- [ ] Implement feature to make tests pass
- [ ] Run tests — all pass

## Last. REFACTOR
- [ ] Clean up, extract helpers, verify all tests still pass
```

**The rule: No implementation code is written before its test exists.**

## Execution Steps

### For each change:

```bash
# Step 1: Open a NEW Claude Code session
cd /path/to/erc20-platform

# Step 2: Paste this prompt:
```

```
请阅读 openspec/changes/<change-name>/ 目录下的 proposal.md、design.md 和 tasks.md，
然后严格按照 TDD 流程（Red→Green→Refactor）逐项实现 tasks.md 中的任务。

规则：
- RED 阶段：先写测试，运行确认测试失败（红色）
- GREEN 阶段：写最少的实现代码使测试通过（绿色）
- REFACTOR 阶段：重构代码，确保测试仍通过
- 严格按照 design.md "Implementation Context" 提供的接口签名
- 每完成一个任务标记为 [x]
- Java 8 兼容
- 完成后运行 mvn test 验证所有测试通过
```

```bash
# Step 3: After all tasks done, archive
# Run: /opsx:archive
```

## Change Dependency Graph

```
001-project-foundation
 ├── 002-erc20-compatibility
 │    ├── 003-block-sync-engine
 │    ├── 006-wallet-service ←── 004-nonce-management
 │    │                      ←── 005-gas-strategy
 │    └── (provides TransferEventParser to 003, 007)
 ├── 004-nonce-management
 ├── 005-gas-strategy
 ├── 009-account-service
 └── 010-risk-control

007-deposit-service ←── 003, 009
008-withdraw-service ←── 006, 009, 010
011-collection-service ←── 006, 007
012-mq-integration ←── (all message-producing modules)
013-rest-api ←── 007, 008, 009
015-monitoring ←── all
```

## Recommended Execution Order

| # | Change | Depends On | Key Tests |
|---|--------|-----------|-----------|
| 1 | `001-project-foundation` | — | AmountUtil, AddressUtil, IdempotentKey |
| 2 | `002-erc20-compatibility` | 001 | TransferEventParser (standard+non-standard), SafeERC20Caller |
| 3 | `004-nonce-management` | 001 | Sequential alloc, concurrent alloc, gap reuse |
| 4 | `005-gas-strategy` | 001, 004 | EIP-1559 pricing, replacement ≥10%, cap |
| 5 | `003-block-sync-engine` | 001, 002 | Reorg detection, parentHash verification |
| 6 | `006-wallet-service` | 002, 004, 005 | Send pipeline, error classification, confirm |
| 7 | `009-account-service` | 001 | Balance ops, idempotent, optimistic lock, reconcile |
| 8 | `010-risk-control` | 001 | Rule engine, limits, blacklist |
| 9 | `007-deposit-service` | 002, 003, 009 | Detect→confirm→credit, idempotent, reorg reversal |
| 10 | `008-withdraw-service` | 006, 009, 010 | Create→freeze→execute→confirm, state machine |
| 11 | `011-collection-service` | 006, 007 | Gas supply, threshold scan, state machine |
| 12 | `012-mq-integration` | all producers | Producer retry, consumer idempotent, compensation |
| 13 | `013-rest-api` | 007, 008, 009 | Validation, exception handling, endpoints |
| 14 | `015-monitoring` | all | Health checks, alert dedup |

## Context Window Management

1. **Each change is session-independent.** The `design.md → "Implementation Context"` section provides all needed interfaces from previous changes. No need to load full codebase.

2. **TDD helps context management.** Tests define expected behavior concisely. When context runs low, the tests serve as living documentation of what's already been decided.

3. **If context runs low during a large change:**
   - Finish the current RED-GREEN pair (never leave tests without implementation)
   - Start new session: `继续实现 openspec/changes/<name>/tasks.md 中未完成的任务。已完成的测试在 src/test 中可以参考。`

4. **Verification command:** `mvn test -pl <module>` after each change.

## TDD Benefits for This Project

| Concern | How TDD Helps |
|---------|--------------|
| Amount precision | Tests verify exact conversions before implementation |
| Idempotency | Tests assert duplicate operations are no-ops |
| Concurrency | Tests simulate multi-thread races |
| State machine | Tests enumerate valid/invalid transitions |
| Reorg handling | Tests set up reorg scenario, verify reversal |
| Non-standard ERC-20 | Tests cover each known non-standard pattern |

## Team Collaboration

- **Code review**: Reviewers read tests first to understand intent, then verify implementation matches
- **New team members**: Tests serve as executable specification — `mvn test` proves the system works
- **Regression**: Every future change must pass all existing tests
- **Parallel work**: Independent changes (e.g., 004+005, 009+010) can be developed by different team members simultaneously

## After All Changes Are Archived

Run full test suite to confirm integration:
```bash
mvn clean test
```

All tests passing = system is correct per specification. Any future change that breaks a test has introduced a regression.
