#!/bin/bash
#
# 安全审计修复 — 分轮批量执行脚本
#
# 将 60+ 个 TDD 任务拆分为 12 个独立 session，每个 session 有充足的上下文空间。
# 每轮只聚焦 1-2 个任务组（3-7 个子任务），确保代码质量。
#
# 用法：
#   ./scripts/run-security-fixes.sh          # 从头开始，自动跳过已完成轮次
#   ./scripts/run-security-fixes.sh 5        # 从第 5 轮开始
#   ./scripts/run-security-fixes.sh 3 5      # 只执行第 3-5 轮
#

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CHANGE_DIR="$PROJECT_DIR/openspec/changes/security-audit-fixes"
TASKS_FILE="$CHANGE_DIR/tasks.md"
LOG_DIR="$PROJECT_DIR/scripts/logs/security-fixes"
mkdir -p "$LOG_DIR"

# ============================================================
# 轮次定义：每轮指定 task group 编号和关联的 spec 文件
# ============================================================

declare -a ROUND_NAMES
declare -a ROUND_GROUPS
declare -a ROUND_SPECS
declare -a ROUND_CONTEXT

ROUND_NAMES[1]="Foundation: ErrorCodes, Exceptions, AmountUtil overflow"
ROUND_GROUPS[1]="1"
ROUND_SPECS[1]="specs/amount-overflow-protection/spec.md"
ROUND_CONTEXT[1]="重点文件: erc20-platform-common/.../util/AmountUtil.java, .../enums/ErrorCode.java, .../enums/DepositStatus.java, erc20-platform-domain/.../entity/TokenConfig.java"

ROUND_NAMES[2]="ReorgHandler: deposit + withdrawal fund reversal"
ROUND_GROUPS[2]="2"
ROUND_SPECS[2]="specs/reorg-fund-reversal/spec.md"
ROUND_CONTEXT[2]="重点文件: erc20-platform-blockchain/.../sync/ReorgHandler.java, erc20-platform-service/.../DepositService.java, .../WithdrawService.java"

ROUND_NAMES[3]="TransactionConfirmTracker: Transfer event verification"
ROUND_GROUPS[3]="3"
ROUND_SPECS[3]="specs/transfer-event-verification/spec.md"
ROUND_CONTEXT[3]="重点文件: erc20-platform-blockchain/.../wallet/TransactionConfirmTracker.java, .../wallet/TxStatusChangedMessage.java, .../erc20/ERC20TransferEventParser.java"

ROUND_NAMES[4]="WithdrawService: risk control integration"
ROUND_GROUPS[4]="4"
ROUND_SPECS[4]="specs/withdraw-risk-integration/spec.md"
ROUND_CONTEXT[4]="重点文件: erc20-platform-service/.../WithdrawService.java, .../risk/RiskControlService.java"

ROUND_NAMES[5]="Withdrawal confirmation amount verification"
ROUND_GROUPS[5]="5"
ROUND_SPECS[5]="specs/transfer-event-verification/spec.md"
ROUND_CONTEXT[5]="重点文件: erc20-platform-service/.../WithdrawService.java, erc20-platform-mq/.../TxStatusConsumer.java"

ROUND_NAMES[6]="WithdrawRetryJob + GasEstimator enhancements"
ROUND_GROUPS[6]="6,7"
ROUND_SPECS[6]="specs/withdraw-risk-integration/spec.md specs/erc20-caller-hardening/spec.md"
ROUND_CONTEXT[6]="重点文件: erc20-platform-service/.../WithdrawRetryJob.java, erc20-platform-blockchain/.../gas/GasEstimator.java, .../wallet/WalletService.java"

ROUND_NAMES[7]="Token type safety + mint filtering + deposit overflow"
ROUND_GROUPS[7]="8"
ROUND_SPECS[7]="specs/token-type-safety/spec.md specs/amount-overflow-protection/spec.md"
ROUND_CONTEXT[7]="重点文件: erc20-platform-service/.../DepositService.java, .../CollectionService.java, erc20-platform-domain/.../entity/TokenConfig.java"

ROUND_NAMES[8]="MqCompensationJob: verify before credit"
ROUND_GROUPS[8]="9"
ROUND_SPECS[8]="specs/chain-reconciliation/spec.md"
ROUND_CONTEXT[8]="重点文件: erc20-platform-mq/.../MqCompensationJob.java, erc20-platform-service/.../DepositService.java"

ROUND_NAMES[9]="SafeERC20Caller hardening"
ROUND_GROUPS[9]="10"
ROUND_SPECS[9]="specs/erc20-caller-hardening/spec.md"
ROUND_CONTEXT[9]="重点文件: erc20-platform-blockchain/.../erc20/SafeERC20Caller.java"

ROUND_NAMES[10]="AlertService dedup + ChainReconcileJob"
ROUND_GROUPS[10]="11,12"
ROUND_SPECS[10]="specs/admin-event-monitoring/spec.md specs/chain-reconciliation/spec.md"
ROUND_CONTEXT[10]="重点文件: erc20-platform-service/.../AlertService.java, 新建 ChainReconcileJob.java"

ROUND_NAMES[11]="AdminEventMonitor + TransactionBuilder + EventParser fixes"
ROUND_GROUPS[11]="13,14,15"
ROUND_SPECS[11]="specs/admin-event-monitoring/spec.md specs/erc20-caller-hardening/spec.md"
ROUND_CONTEXT[11]="重点文件: 新建 AdminEventMonitor.java, erc20-platform-blockchain/.../wallet/TransactionBuilder.java, .../erc20/ERC20TransferEventParser.java, erc20-platform-service/.../DepositConfirmJob.java"

ROUND_NAMES[12]="Integration verification: compile + full test suite"
ROUND_GROUPS[12]="16"
ROUND_SPECS[12]=""
ROUND_CONTEXT[12]="运行 mvn clean compile -DskipTests 和 mvn test，修复所有编译错误和测试失败"

TOTAL_ROUNDS=12

# ============================================================
# 构造每轮 prompt
# ============================================================
build_prompt() {
  local round_num="$1"
  local round_name="${ROUND_NAMES[$round_num]}"
  local groups="${ROUND_GROUPS[$round_num]}"
  local specs="${ROUND_SPECS[$round_num]}"
  local context="${ROUND_CONTEXT[$round_num]}"

  # 构造 spec 读取指令
  local spec_instruction=""
  if [ -n "$specs" ]; then
    spec_instruction="然后阅读以下 spec 文件了解需求规范：
"
    for spec in $specs; do
      spec_instruction="${spec_instruction}- openspec/changes/security-audit-fixes/${spec}
"
    done
  fi

  cat <<EOF
你正在执行 ERC-20 平台安全审计修复（openspec change: security-audit-fixes）的第 ${round_num} 轮（共 ${TOTAL_ROUNDS} 轮）。

本轮目标：${round_name}

## 准备工作

1. 阅读 openspec/changes/security-audit-fixes/design.md 了解整体技术决策和接口签名。
2. ${spec_instruction}
3. 阅读 openspec/changes/security-audit-fixes/tasks.md，定位 Task Group ${groups} 中未勾选的任务（以 - [ ] 开头）。
4. ${context}

## 执行规则

- 严格 TDD 流程：RED（写测试，确认失败）→ GREEN（最少实现使测试通过）→ REFACTOR
- Java 8 兼容，不使用更高版本特性（如 lambda var, Optional.isEmpty, etc）
- 先阅读要修改的现有代码，理解当前实现后再写测试和修改
- 每完成一个子任务，立即在 tasks.md 中将 - [ ] 改为 - [x]
- 测试使用 JUnit 5 + Mockito（参考现有测试风格: @ExtendWith(MockitoExtension.class)）
- 测试命名: 被测方法_场景_期望结果
- 完成本轮所有任务后运行 mvn test -pl <相关模块> 验证通过
- 如果发现之前轮次引入的编译错误，优先修复

## 质量要求

- 不要跳过 RED 阶段，确保测试先写先跑失败
- 实现要最小化，不要超出任务描述范围
- 注意 null 安全和边界情况
- 参考 design.md "Implementation Context" 中的接口签名，确保兼容
- 新增/修改的代码要和现有代码风格一致（字段命名、异常处理模式等）

开始执行 Task Group ${groups} 中的未完成任务。
EOF
}

# ============================================================
# 检查某轮的任务是否已全部完成
# ============================================================
is_round_complete() {
  local round_num="$1"
  local groups="${ROUND_GROUPS[$round_num]}"

  # 将 groups (e.g., "6,7") 拆分为数组
  IFS=',' read -ra group_nums <<< "$groups"

  for gnum in "${group_nums[@]}"; do
    # 检查该 group 下是否有未完成任务
    # 任务格式: "- [ ] N.X ..." 其中 N 是 group 编号
    # 使用 -E (extended regex) 而非 -P (Perl)，macOS BSD grep 不支持 -P
    if grep -qE "^- \[ \] ${gnum}\.[0-9]+" "$TASKS_FILE"; then
      return 1  # 有未完成任务
    fi
  done
  return 0  # 全部完成
}

# ============================================================
# 主流程
# ============================================================
START_ROUND="${1:-1}"
END_ROUND="${2:-$TOTAL_ROUNDS}"

echo "╔══════════════════════════════════════════════════════════╗"
echo "║  ERC-20 安全审计修复 — 分轮批量执行                       ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Change:  security-audit-fixes                          ║"
echo "║  Rounds:  ${START_ROUND} → ${END_ROUND} (共 ${TOTAL_ROUNDS} 轮)                            ║"
echo "║  Project: $PROJECT_DIR"
echo "║  Logs:    $LOG_DIR"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

completed=0
skipped=0
failed=0

for round in $(seq "$START_ROUND" "$END_ROUND"); do
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  Round ${round}/${TOTAL_ROUNDS}: ${ROUND_NAMES[$round]}"
  echo "  Task Groups: ${ROUND_GROUPS[$round]}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # 检查是否已完成
  if is_round_complete "$round"; then
    echo "  [SKIP] 本轮任务已全部完成 ✓"
    ((skipped++))
    continue
  fi

  PROMPT=$(build_prompt "$round")
  TIMESTAMP=$(date +%Y%m%d_%H%M%S)
  LOG_FILE="$LOG_DIR/round-${round}_${TIMESTAMP}.log"

  echo "  [RUN]  启动 Claude Code session..."
  echo "  [LOG]  $LOG_FILE"
  echo ""

  # 执行 Claude Code
  if claude -p "$PROMPT" \
    --dangerously-skip-permissions \
    --output-format text \
    2>&1 | tee "$LOG_FILE"; then

    # 检查任务完成情况
    if is_round_complete "$round"; then
      echo ""
      echo "  [PASS] Round ${round} 完成 ✓"
      ((completed++))
    else
      echo ""
      echo "  [PARTIAL] Round ${round} 部分完成，剩余未勾选任务:"
      grep "^- \[ \]" "$TASKS_FILE" | head -5
      ((failed++))

      echo ""
      read -p "  继续下一轮? (y/n/r=重试本轮) " -n 1 -r
      echo
      if [[ "$REPLY" =~ ^[Rr]$ ]]; then
        echo "  重试 Round ${round}..."
        ((round--))  # 下次循环会再次执行本轮
        ((failed--))
        continue
      elif [[ ! "$REPLY" =~ ^[Yy]$ ]]; then
        echo "  已停止。重启: ./scripts/run-security-fixes.sh $round"
        exit 1
      fi
    fi
  else
    echo ""
    echo "  [FAIL] Claude Code 执行异常，日志: $LOG_FILE"
    ((failed++))

    read -p "  继续下一轮? (y/n/r=重试) " -n 1 -r
    echo
    if [[ "$REPLY" =~ ^[Rr]$ ]]; then
      ((round--))
      ((failed--))
      continue
    elif [[ ! "$REPLY" =~ ^[Yy]$ ]]; then
      echo "  已停止。重启: ./scripts/run-security-fixes.sh $round"
      exit 1
    fi
  fi
done

# 最终验证
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  最终验证：mvn clean test"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if (cd "$PROJECT_DIR" && mvn clean test -q 2>&1 | tail -10); then
  echo ""
  echo "  [PASS] 全量测试通过 ✓"
else
  echo ""
  echo "  [WARN] 测试有失败，请检查"
fi

# 统计
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  执行结果                                                ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  完成: ${completed}  |  部分完成: ${failed}  |  跳过: ${skipped}            ║"
echo "╚══════════════════════════════════════════════════════════╝"

# 汇总未完成任务
remaining=$(grep -c '^\- \[ \]' "$TASKS_FILE" 2>/dev/null || echo "0")
total=$(grep -c '^\- \[' "$TASKS_FILE" 2>/dev/null || echo "0")
echo ""
echo "  任务进度: $((total - remaining))/${total} 完成"

if [ "$remaining" -eq 0 ]; then
  echo ""
  echo "  🎉 所有安全审计修复任务已完成！"
  echo "  下一步: 运行 /opsx:archive security-audit-fixes 归档"
fi
