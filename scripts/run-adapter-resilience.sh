#!/bin/bash
#
# ERC-20 Adapter Resilience (Phase 4) — 逐任务执行脚本
#
# 每个 task 使用独立的 Claude Code session，保证上下文空间足够。
#
# 用法：
#   ./scripts/run-adapter-resilience.sh          # 从头开始，自动跳过已完成任务
#   ./scripts/run-adapter-resilience.sh 3.1      # 从 task 3.1 开始
#   ./scripts/run-adapter-resilience.sh 3.1 4.2  # 只执行 3.1 到 4.2
#

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CHANGE_NAME="erc20-adapter-resilience"
CHANGE_DIR="$PROJECT_DIR/openspec/changes/$CHANGE_NAME"
TASKS_FILE="$CHANGE_DIR/tasks.md"
LOG_DIR="$PROJECT_DIR/scripts/logs/$CHANGE_NAME"
mkdir -p "$LOG_DIR"

# ============================================================
# 工具函数
# ============================================================
get_task_id() {
  echo "$1" | grep -oE '^[0-9]+\.[0-9]+'
}

is_task_done() {
  local task_id="$1"
  grep -qE "^\- \[x\] ${task_id} " "$TASKS_FILE" && return 0
  return 1
}

get_group_name() {
  local task_id="$1"
  local group_num="${task_id%%.*}"
  grep -E "^## ${group_num}\." "$TASKS_FILE" | sed 's/^## [0-9]*\. //'
}

# ============================================================
# 根据 task group 确定相关的 spec 文件
# ============================================================
get_spec_for_group() {
  local group_num="$1"
  case "$group_num" in
    1) echo "specs/consecutive-failure-breaker/spec.md" ;;
    2) echo "specs/token-metadata-cache/spec.md" ;;
    3) echo "specs/transfer-event-verification/spec.md" ;;
    4) echo "specs/consecutive-failure-breaker/spec.md" ;;
    5) echo "specs/chain-reconciliation/spec.md" ;;
    6) echo "specs/token-metadata-cache/spec.md" ;;
    7) echo "" ;;  # 编译验证，无 spec
  esac
}

# ============================================================
# 构造单个 task 的 prompt
# ============================================================
build_task_prompt() {
  local task_id="$1"
  local task_desc="$2"
  local group_num="${task_id%%.*}"
  local group_name
  group_name=$(get_group_name "$task_id")
  local spec_file
  spec_file=$(get_spec_for_group "$group_num")

  local spec_instruction=""
  if [ -n "$spec_file" ]; then
    spec_instruction="3. 阅读 openspec/changes/${CHANGE_NAME}/${spec_file} 了解本任务对应的需求规范和测试场景。
"
  fi

  cat <<EOF
你正在执行 ERC-20 适配层弹性增强（change: ${CHANGE_NAME}）。

## 当前任务

**Task ${task_id}**: ${task_desc}

所属模块组: ${group_name}

## 准备工作

1. 阅读 openspec/changes/${CHANGE_NAME}/design.md 了解技术决策和实现上下文。
2. 阅读 openspec/changes/${CHANGE_NAME}/tasks.md 了解任务上下文（已完成的任务表示已有的代码）。
${spec_instruction}4. 阅读 openspec/changes/${CHANGE_NAME}/proposal.md 了解变更动机。
5. 检查项目中已有的适配层代码了解现有风格:
   - erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/
   - erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/wallet/
   - erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/monitor/
   - erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/reconcile/

## 执行规则

- 严格 TDD 流程：RED（写测试，运行确认失败）→ GREEN（最少实现使测试通过）→ REFACTOR
- Java 8 兼容，不使用更高版本特性（不使用 lambda 以外的 Java 8+ 特性）
- 只完成当前这一个 task，不要超出范围
- 完成后在 tasks.md 中将该任务的 \`- [ ]\` 改为 \`- [x]\`
- 测试使用 JUnit 5 + Mockito（@ExtendWith(MockitoExtension.class)）
- 测试命名: 被测方法_场景_期望结果
- 完成后运行对应模块的编译验证

## 包路径约定

- 适配层组件: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/
- 适配层异常: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/exception/
- 适配层模型: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/model/
- RPC 客户端: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/rpc/
- 钱包服务: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/wallet/
- 监控: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/monitor/
- 对账: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/reconcile/
- 枚举: erc20-platform-common/src/main/java/com/erc20/platform/common/enums/
- 实体: erc20-platform-domain/src/main/java/com/erc20/platform/domain/entity/
- Flyway 迁移: erc20-platform-dal/src/main/resources/db/migration/
- 测试: 对应模块的 src/test/java/ 下同包结构

## 关键已有组件（请先阅读再修改）

- TokenRiskProfile: adapter/model/TokenRiskProfile.java（已有 builder、requiresBalanceDiff/requiresApproveReset）
- TokenRiskProfileRegistry: adapter/TokenRiskProfileRegistry.java（ConcurrentHashMap 缓存 + invalidate）
- TokenAdmissionGateway: adapter/TokenAdmissionGateway.java（checkAdmission + isAdmitted）
- TransferConfirmer: adapter/TransferConfirmer.java（四层确认，两个 confirm 重载）
- TransactionConfirmTracker: wallet/TransactionConfirmTracker.java（5s 定时扫描）
- DefaultERC20Adapter: adapter/DefaultERC20Adapter.java（门面实现）
- SafeERC20Caller: erc20/SafeERC20Caller.java（读操作 + bytes32 兼容）
- AdminEventMonitor: monitor/AdminEventMonitor.java（Paused/Upgraded 自动熔断）
- ChainReconcileJob: reconcile/ChainReconcileJob.java（每日对账）
- TokenConfig: domain/entity/TokenConfig.java（含 depositConfirmBlocks 字段）

## 质量要求

- 新增值对象 fields 使用 private final
- 提供有意义的 toString()
- 所有地址统一小写存储
- Redis key 使用有意义的前缀命名
- 配置参数使用 @Value 注入，提供合理默认值

只执行 Task ${task_id}，完成后停止。
EOF
}

# ============================================================
# 主流程
# ============================================================
START_TASK="${1:-}"
END_TASK="${2:-}"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ERC-20 Adapter Resilience (Phase 4) — 逐任务执行             ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Change:  $CHANGE_NAME"
echo "║  Project: $PROJECT_DIR"
echo "║  Logs:    $LOG_DIR"
if [ -n "$START_TASK" ]; then
  echo "║  Range:   ${START_TASK} → ${END_TASK:-end}"
fi
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# 统计
total=$(grep -c '^\- \[' "$TASKS_FILE" 2>/dev/null)
total=${total:-0}
done_count=$(grep -c '^\- \[x\]' "$TASKS_FILE" 2>/dev/null)
done_count=${done_count:-0}
echo "  当前进度: ${done_count}/${total} 完成"
echo ""

completed=0
skipped=0
failed=0
started=false

if [ -z "$START_TASK" ]; then
  started=true
fi

# 逐行读取所有任务
while IFS= read -r line; do
  if echo "$line" | grep -qE '^\- \[[ x]\] [0-9]+\.[0-9]+'; then
    task_id=$(echo "$line" | grep -oE '[0-9]+\.[0-9]+' | head -1)
    task_desc=$(echo "$line" | sed -E 's/^- \[[ x]\] [0-9]+\.[0-9]+ //')

    # 处理起始位置
    if [ "$started" = false ]; then
      if [ "$task_id" = "$START_TASK" ]; then
        started=true
      else
        continue
      fi
    fi

    # 处理结束位置
    if [ -n "$END_TASK" ]; then
      if echo -e "${task_id}\n${END_TASK}" | sort -V | tail -1 | grep -q "^${task_id}$" && [ "$task_id" != "$END_TASK" ]; then
        break
      fi
    fi

    # 检查是否已完成
    if echo "$line" | grep -q '^\- \[x\]'; then
      echo "[SKIP] Task ${task_id} — 已完成"
      ((skipped++))
      continue
    fi

    # 执行任务
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "[START] Task ${task_id}: ${task_desc:0:70}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    PROMPT=$(build_task_prompt "$task_id" "$task_desc")
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    LOG_FILE="$LOG_DIR/task-${task_id}_${TIMESTAMP}.log"

    echo "  [LOG] $LOG_FILE"
    echo ""

    # 执行 Claude Code（每个 task 独立 session）
    if claude -p "$PROMPT" \
      --dangerously-skip-permissions \
      --output-format text \
      2>&1 | tee "$LOG_FILE"; then

      # 检查 task 是否标记完成
      if is_task_done "$task_id"; then
        echo ""
        echo "  [PASS] Task ${task_id} 完成 ✓"
        ((completed++))
      else
        echo ""
        echo "  [PARTIAL] Task ${task_id} 执行完成但未标记 [x]"
        ((failed++))

        read -p "  继续? (y/n/r=重试) " -n 1 -r
        echo
        if [[ "$REPLY" =~ ^[Rr]$ ]]; then
          ((failed--))
          echo "  重试 Task ${task_id}..."
          if claude -p "$PROMPT" \
            --dangerously-skip-permissions \
            --output-format text \
            2>&1 | tee "${LOG_FILE}.retry"; then
            if is_task_done "$task_id"; then
              echo "  [PASS] Task ${task_id} 重试成功 ✓"
              ((completed++))
            else
              echo "  [FAIL] Task ${task_id} 重试仍未完成"
              ((failed++))
            fi
          fi
        elif [[ ! "$REPLY" =~ ^[Yy]$ ]]; then
          echo "  已停止。重启: ./scripts/run-adapter-resilience.sh $task_id"
          break
        fi
      fi
    else
      echo ""
      echo "  [FAIL] Task ${task_id} — Claude Code 执行异常"
      ((failed++))

      read -p "  继续? (y/n/r=重试) " -n 1 -r
      echo
      if [[ "$REPLY" =~ ^[Rr]$ ]]; then
        ((failed--))
        continue
      elif [[ ! "$REPLY" =~ ^[Yy]$ ]]; then
        echo "  已停止。重启: ./scripts/run-adapter-resilience.sh $task_id"
        break
      fi
    fi
  fi
done < "$TASKS_FILE"

# 最终验证
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  最终验证：mvn compile + test"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$PROJECT_DIR"
echo "  [1/3] mvn compile..."
if mvn compile -q 2>&1 | tail -3; then
  echo "  [PASS] 全量编译通过 ✓"
else
  echo "  [WARN] 编译有问题"
fi

echo "  [2/3] mvn test -pl erc20-platform-blockchain..."
if mvn test -pl erc20-platform-blockchain -q 2>&1 | tail -5; then
  echo "  [PASS] blockchain 模块测试通过 ✓"
else
  echo "  [WARN] blockchain 模块测试有失败"
fi

echo "  [3/3] mvn test -pl erc20-platform-service..."
if mvn test -pl erc20-platform-service -q 2>&1 | tail -5; then
  echo "  [PASS] service 模块测试通过 ✓"
else
  echo "  [WARN] service 模块测试有失败"
fi

# 统计
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  执行结果                                                    ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  完成: ${completed}  |  失败: ${failed}  |  跳过: ${skipped}                  ║"
echo "╚══════════════════════════════════════════════════════════════╝"

remaining=$(grep -c '^\- \[ \]' "$TASKS_FILE" 2>/dev/null)
remaining=${remaining:-0}
echo ""
echo "  任务进度: $((total - remaining))/${total} 完成"

if [ "$remaining" -eq 0 ]; then
  echo ""
  echo "  所有任务已完成！"
  echo "  下一步: openspec archive $CHANGE_NAME"
fi
