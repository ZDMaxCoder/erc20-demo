#!/bin/bash
#
# 自动执行所有 OpenSpec changes（TDD 模式）
# 用法：
#   ./scripts/run-changes.sh              # 从头开始执行所有
#   ./scripts/run-changes.sh 004          # 从 004-nonce-management 开始
#   ./scripts/run-changes.sh 004 006      # 只执行 004 到 006
#

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CHANGES_DIR="$PROJECT_DIR/openspec/changes"
LOG_DIR="$PROJECT_DIR/scripts/logs"
mkdir -p "$LOG_DIR"

# 按推荐顺序排列（非目录字母序）
ORDERED_CHANGES=(
  "001-project-foundation"
  "002-erc20-compatibility"
  "004-nonce-management"
  "005-gas-strategy"
  "003-block-sync-engine"
  "006-wallet-service"
  "009-account-service"
  "010-risk-control"
  "007-deposit-service"
  "008-withdraw-service"
  "011-collection-service"
  "012-mq-integration"
  "013-rest-api"
  "015-monitoring"
)

# 构造 TDD prompt
build_prompt() {
  local change_name="$1"
  cat <<EOF
请阅读 openspec/changes/${change_name}/ 目录下的 proposal.md、design.md 和 tasks.md，然后严格按照 TDD 流程（Red→Green→Refactor）逐项实现 tasks.md 中的任务。

规则：
- RED 阶段：先写测试，运行确认测试失败（红色）
- GREEN 阶段：写最少的实现代码使测试通过（绿色）
- REFACTOR 阶段：重构代码，确保测试仍通过
- 严格按照 design.md "Implementation Context" 提供的接口签名
- 每完成一个任务标记为 [x]
- Java 8 兼容，不使用更高版本特性
- 完成后运行 mvn test 验证所有测试通过
- 如果遇到依赖前序模块的接口，查看项目中已有的代码获取实际签名
EOF
}

# 检查 change 是否已完成（tasks.md 中无未勾选项）
is_completed() {
  local change_name="$1"
  local tasks_file="$CHANGES_DIR/$change_name/tasks.md"
  if [ ! -f "$tasks_file" ]; then
    return 1
  fi
  # 如果没有未勾选的 checkbox，视为已完成
  if grep -q '^\- \[ \]' "$tasks_file"; then
    return 1  # 有未完成任务
  fi
  return 0  # 全部完成
}

# 确定起始和结束位置
START_PREFIX="${1:-}"
END_PREFIX="${2:-}"
started=false
if [ -z "$START_PREFIX" ]; then
  started=true
fi

echo "============================================"
echo " ERC-20 Platform — 自动化 TDD 构建"
echo " 项目目录: $PROJECT_DIR"
echo " 日志目录: $LOG_DIR"
echo "============================================"
echo ""

completed=0
failed=0
skipped=0

for change in "${ORDERED_CHANGES[@]}"; do
  # 处理起始位置
  if [ "$started" = false ]; then
    if [[ "$change" == ${START_PREFIX}* ]]; then
      started=true
    else
      continue
    fi
  fi

  # 检查是否已完成
  if is_completed "$change"; then
    echo "[SKIP] $change — 已完成"
    ((skipped++))
    # 处理结束位置
    if [ -n "$END_PREFIX" ] && [[ "$change" == ${END_PREFIX}* ]]; then
      break
    fi
    continue
  fi

  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "[START] $change"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  PROMPT=$(build_prompt "$change")
  LOG_FILE="$LOG_DIR/${change}_$(date +%Y%m%d_%H%M%S).log"

  # 使用 claude CLI 非交互执行（--dangerously-skip-permissions 跳过权限确认）
  if claude -p "$PROMPT" \
    --dangerously-skip-permissions \
    --output-format text \
    2>&1 | tee "$LOG_FILE"; then

    # 验证：运行 mvn test
    echo ""
    echo "[VERIFY] 运行 mvn test..."
    if (cd "$PROJECT_DIR" && mvn test -q 2>&1 | tail -5); then
      echo "[PASS] $change — 完成并通过测试"
      ((completed++))
    else
      echo "[WARN] $change — 实现完成但测试有问题，请检查日志: $LOG_FILE"
      ((failed++))
      # 询问是否继续
      read -p "继续执行下一个 change? (y/n) " -n 1 -r
      echo
      if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已停止。可以修复后用 ./scripts/run-changes.sh ${change:0:3} 继续。"
        exit 1
      fi
    fi
  else
    echo "[FAIL] $change — Claude Code 执行失败，日志: $LOG_FILE"
    ((failed++))
    read -p "继续执行下一个 change? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
      echo "已停止。"
      exit 1
    fi
  fi

  # 处理结束位置
  if [ -n "$END_PREFIX" ] && [[ "$change" == ${END_PREFIX}* ]]; then
    break
  fi
done

echo ""
echo "============================================"
echo " 执行完毕"
echo " 完成: $completed | 失败: $failed | 跳过: $skipped"
echo "============================================"
