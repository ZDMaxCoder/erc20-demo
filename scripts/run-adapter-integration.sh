#!/bin/bash
#
# ERC-20 Adapter Integration — 并发任务执行脚本
#
# 支持按组并发执行（组内任务顺序执行，组间按依赖图并行）。
#
# 用法：
#   ./scripts/run-adapter-integration.sh              # 默认 2 并发
#   ./scripts/run-adapter-integration.sh -j 3         # 3 并发
#   ./scripts/run-adapter-integration.sh -j 1         # 退化为顺序执行
#   ./scripts/run-adapter-integration.sh -j 3 3.1     # 3 并发，从 task 3.1 开始
#   ./scripts/run-adapter-integration.sh -j 3 3.1 4.2 # 3 并发，执行 3.1 到 4.2
#   ./scripts/run-adapter-integration.sh 3.1          # 从 task 3.1 开始（顺序兼容模式）
#   ./scripts/run-adapter-integration.sh 3.1 4.2      # 只执行 3.1 到 4.2（顺序兼容模式）
#

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CHANGE_NAME="erc20-adapter-integration"
CHANGE_DIR="$PROJECT_DIR/openspec/changes/$CHANGE_NAME"
TASKS_FILE="$CHANGE_DIR/tasks.md"
LOG_DIR="$PROJECT_DIR/scripts/logs/$CHANGE_NAME"
mkdir -p "$LOG_DIR"

# ============================================================
# 参数解析
# ============================================================
MAX_PARALLEL=2
START_TASK=""
END_TASK=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -j|--jobs)
      MAX_PARALLEL="$2"
      shift 2
      ;;
    -j*)
      MAX_PARALLEL="${1#-j}"
      shift
      ;;
    *)
      if [ -z "$START_TASK" ]; then
        START_TASK="$1"
      else
        END_TASK="$1"
      fi
      shift
      ;;
  esac
done

if [ "$MAX_PARALLEL" -lt 1 ]; then
  MAX_PARALLEL=1
fi

# ============================================================
# 组间依赖图定义
#
#   Group 2 → Group 3 → Group 4 → Group 5
#   Group 2 → Group 6 (独立)
#   Group 2 → Group 7 → Group 8
#   Group 2 → Group 8
#   All    → Group 9
# ============================================================
get_group_deps() {
  case "$1" in
    1) echo "" ;;
    2) echo "1" ;;
    3) echo "2" ;;
    4) echo "3" ;;
    5) echo "4" ;;
    6) echo "2" ;;
    7) echo "2" ;;
    8) echo "2 7" ;;
    9) echo "3 4 5 6 7 8" ;;
    *) echo "" ;;
  esac
}

# ============================================================
# 工具函数
# ============================================================
get_task_id() {
  echo "$1" | grep -oE '^[0-9]+\.[0-9]+'
}

is_task_done() {
  local task_id="$1"
  grep -qE "^\- \[x\] ${task_id} " "$TASKS_FILE"
}

get_group_name() {
  local task_id="$1"
  local group_num="${task_id%%.*}"
  grep -E "^## ${group_num}\." "$TASKS_FILE" | sed 's/^## [0-9]*\. //'
}

get_specs_for_group() {
  local group_num="$1"
  case "$group_num" in
    1)   echo "specs/withdraw-amount-guard/spec.md" ;;
    2)   echo "specs/erc20-rpc-client/spec.md" ;;
    3)   echo "specs/transfer-confirmer/spec.md" ;;
    4)   echo "specs/transfer-event-verification/spec.md" ;;
    5)   echo "specs/withdraw-amount-guard/spec.md" ;;
    6)   echo "specs/token-auto-fuse/spec.md specs/admin-event-monitoring/spec.md" ;;
    7)   echo "specs/nonce-auto-recovery/spec.md" ;;
    8)   echo "specs/transfer-pre-check/spec.md" ;;
    9)   echo "" ;;
  esac
}

# ============================================================
# 获取组内所有 task (返回 "task_id|task_desc" 每行一个)
# ============================================================
get_tasks_in_group() {
  local group_num="$1"
  grep -E "^\- \[[ x]\] ${group_num}\.[0-9]+" "$TASKS_FILE" | while IFS= read -r line; do
    local tid
    tid=$(echo "$line" | grep -oE '[0-9]+\.[0-9]+' | head -1)
    local desc
    desc=$(echo "$line" | sed -E 's/^- \[[ x]\] [0-9]+\.[0-9]+ //')
    local status="pending"
    echo "$line" | grep -q '^\- \[x\]' && status="done"
    echo "${tid}|${desc}|${status}"
  done
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
  local spec_files
  spec_files=$(get_specs_for_group "$group_num")

  local spec_instruction=""
  if [ -n "$spec_files" ]; then
    local spec_list=""
    for sf in $spec_files; do
      spec_list="${spec_list}   - openspec/changes/${CHANGE_NAME}/${sf}
"
    done
    spec_instruction="3. 阅读以下 spec 了解本任务对应的需求规范和测试场景：
${spec_list}"
  fi

  cat <<EOF
你正在执行 ERC-20 适配层业务集成与安全加固（change: ${CHANGE_NAME}）。

## 当前任务

**Task ${task_id}**: ${task_desc}

所属模块组: ${group_name}

## 准备工作

1. 阅读 openspec/changes/${CHANGE_NAME}/design.md 了解技术决策、接口设计和实现上下文。
2. 阅读 openspec/changes/${CHANGE_NAME}/tasks.md 了解任务上下文（已完成的任务表示已有的代码）。
${spec_instruction}4. 检查项目中相关源码以了解现有实现和代码风格。

## 执行规则

- 严格 TDD 流程：RED（写测试，运行确认失败）→ GREEN（最少实现使测试通过）→ REFACTOR
- Java 8 兼容，不使用更高版本特性
- 只完成当前这一个 task，不要超出范围
- 完成后在 tasks.md 中将该任务的 - [ ] 改为 - [x]
- 测试使用 JUnit 5 + Mockito
- 测试命名: 被测方法_场景_期望结果
- 金额模型: long amount + int exponent，禁止浮点数
- 所有地址统一小写存储
- 不需要考虑旧代码兼容，直接按最优方案实现
- 完成后运行 mvn compile -pl erc20-platform-common,erc20-platform-blockchain,erc20-platform-service 确认编译通过
- 如果本 task 包含"编写单元测试"或"编写测试"，运行 mvn test -pl <对应模块> -Dtest=<TestClass> 验证通过

## 关键路径约定

- adapter 层: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/
- adapter/rpc: ERC20RpcClient, ReturnValueDecoder
- adapter/model: CallResult, TransferResult, TransferOutcome, CallOutcome
- adapter/exception: ERC20AdapterException 及子类
- 枚举: erc20-platform-common/src/main/java/com/erc20/platform/common/enums/
- 服务: erc20-platform-service/src/main/java/com/erc20/platform/service/
- 钱包: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/wallet/
- 监控: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/monitor/
- 测试: 对应模块的 src/test/java/ 下同包结构

## 已有基础设施（Phase 1 已完成，可直接使用）

- CallResult: 五态（SUCCESS/SUCCESS_NO_RETURN/RETURNED_FALSE/REVERTED/UNKNOWN），工厂方法 success()/successNoReturn()/returnedFalse()/reverted()/unknown(rawHex)，查询 isSuccess()/isDangerousFalse()
- TransferResult: 四态（SUCCESS/FAILED/PENDING/ANOMALY），builder 模式，工厂方法 failed()/pending()，查询 isAmountConsistent()/hasBalanceDiffAnomaly()
- ReturnValueDecoder: @Component，decodeBoolReturn(String hexResult) → CallResult
- TokenRiskProfile: 含 requiresBalanceDiff()/requiresApproveReset()/isStandardProcessing()
- 异常: ERC20AdapterException, TransferRevertedException, TokenPausedException, TokenBlacklistedException, AmountMismatchException, TransferEventMissingException

只执行 Task ${task_id}，完成后停止。
EOF
}

# ============================================================
# 执行单个 task（带重试）
# 返回: 0=成功, 1=失败
# ============================================================
execute_task() {
  local task_id="$1"
  local task_desc="$2"
  local group_num="${task_id%%.*}"

  PROMPT=$(build_task_prompt "$task_id" "$task_desc")
  TIMESTAMP=$(date +%Y%m%d_%H%M%S)
  LOG_FILE="$LOG_DIR/task-${task_id}_${TIMESTAMP}.log"

  echo "  [G${group_num}] [START] Task ${task_id}: ${task_desc:0:60}..."
  echo "  [G${group_num}] [LOG]   $LOG_FILE"

  if claude -p "$PROMPT" \
    --dangerously-skip-permissions \
    --output-format text \
    > "$LOG_FILE" 2>&1; then

    if is_task_done "$task_id"; then
      echo "  [G${group_num}] [PASS]  Task ${task_id} ✓"
      return 0
    else
      echo "  [G${group_num}] [RETRY] Task ${task_id} — 未标记完成，自动重试..."
      RETRY_LOG="${LOG_FILE}.retry"
      if claude -p "$PROMPT" \
        --dangerously-skip-permissions \
        --output-format text \
        > "$RETRY_LOG" 2>&1; then
        if is_task_done "$task_id"; then
          echo "  [G${group_num}] [PASS]  Task ${task_id} 重试成功 ✓"
          return 0
        fi
      fi
      echo "  [G${group_num}] [FAIL]  Task ${task_id} — 重试仍未完成"
      return 1
    fi
  else
    echo "  [G${group_num}] [FAIL]  Task ${task_id} — Claude Code 执行异常"
    return 1
  fi
}

# ============================================================
# 执行一个 group 内的所有 pending tasks（顺序）
# 返回: 0=全部成功, 1=有失败
# ============================================================
execute_group() {
  local group_num="$1"
  local group_result=0

  echo ""
  echo "  ┌─ Group ${group_num} 开始执行"

  while IFS='|' read -r tid desc status; do
    [ -z "$tid" ] && continue

    # 跳过已完成
    if [ "$status" = "done" ]; then
      echo "  [G${group_num}] [SKIP]  Task ${tid} — 已完成"
      continue
    fi

    # 范围过滤
    if [ -n "$START_TASK" ]; then
      if echo -e "${tid}\n${START_TASK}" | sort -V | head -1 | grep -q "^${tid}$" && [ "$tid" != "$START_TASK" ]; then
        continue
      fi
    fi
    if [ -n "$END_TASK" ]; then
      if echo -e "${tid}\n${END_TASK}" | sort -V | tail -1 | grep -q "^${tid}$" && [ "$tid" != "$END_TASK" ]; then
        echo "  [G${group_num}] [STOP]  到达结束位置"
        break
      fi
    fi

    if ! execute_task "$tid" "$desc"; then
      group_result=1
      echo "  [G${group_num}] [ABORT] 组内后续任务跳过"
      break
    fi
  done < <(get_tasks_in_group "$group_num")

  if [ "$group_result" -eq 0 ]; then
    echo "  └─ Group ${group_num} 全部完成 ✓"
  else
    echo "  └─ Group ${group_num} 存在失败 ✗"
  fi

  return $group_result
}

# ============================================================
# 并发调度器
#
# 基于依赖图，使用信号量控制最大并发数，
# 当一个 group 的所有前置 group 完成后立即调度。
# ============================================================

# 状态追踪文件（进程间通信）
STATUS_DIR="$LOG_DIR/.run-status-$$"
rm -rf "$STATUS_DIR"
mkdir -p "$STATUS_DIR"

# 信号量 — 使用 FIFO 模拟
SEMAPHORE="$STATUS_DIR/semaphore"
mkfifo "$SEMAPHORE"
exec 3<>"$SEMAPHORE"
for ((i=0; i<MAX_PARALLEL; i++)); do
  echo "x" >&3
done

mark_group_done() {
  touch "$STATUS_DIR/done_$1"
}

mark_group_failed() {
  touch "$STATUS_DIR/failed_$1"
}

is_group_done() {
  [ -f "$STATUS_DIR/done_$1" ]
}

is_group_failed() {
  [ -f "$STATUS_DIR/failed_$1" ]
}

is_group_skippable() {
  local group_num="$1"
  # 组内没有 pending 的 task（全部已完成或不在范围内）
  local pending
  pending=$(get_tasks_in_group "$group_num" | grep '|pending$' | wc -l)

  # 范围过滤
  if [ -n "$START_TASK" ]; then
    local start_group="${START_TASK%%.*}"
    if [ "$group_num" -lt "$start_group" ]; then
      return 0
    fi
  fi
  if [ -n "$END_TASK" ]; then
    local end_group="${END_TASK%%.*}"
    if [ "$group_num" -gt "$end_group" ]; then
      return 0
    fi
  fi

  [ "$pending" -eq 0 ]
}

wait_for_deps() {
  local group_num="$1"
  local deps
  deps=$(get_group_deps "$group_num")

  for dep in $deps; do
    while ! is_group_done "$dep" && ! is_group_failed "$dep"; do
      sleep 2
    done
    if is_group_failed "$dep"; then
      return 1
    fi
  done
  return 0
}

has_any_failure() {
  ls "$STATUS_DIR"/failed_* >/dev/null 2>&1
}

# ============================================================
# 确定需要执行的 groups
# ============================================================
get_active_groups() {
  local groups=""
  for g in 1 2 3 4 5 6 7 8 9; do
    if [ -n "$START_TASK" ]; then
      local start_group="${START_TASK%%.*}"
      if [ "$g" -lt "$start_group" ]; then
        continue
      fi
    fi
    if [ -n "$END_TASK" ]; then
      local end_group="${END_TASK%%.*}"
      if [ "$g" -gt "$end_group" ]; then
        continue
      fi
    fi
    groups="$groups $g"
  done
  echo "$groups"
}

# ============================================================
# 主流程
# ============================================================
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  ERC-20 Adapter Integration — 并发执行                    ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Change:    $CHANGE_NAME"
echo "║  Project:   $PROJECT_DIR"
echo "║  Logs:      $LOG_DIR"
echo "║  Parallel:  ${MAX_PARALLEL} (max concurrent groups)"
if [ -n "$START_TASK" ]; then
  echo "║  Range:     ${START_TASK} → ${END_TASK:-end}"
fi
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  依赖图: G2→G3→G4→G5                                     ║"
echo "║          G2→G6(独立) G2→G7→G8                             ║"
echo "║          All→G9                                          ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

total=$(grep -c '^\- \[' "$TASKS_FILE" 2>/dev/null || echo "0")
done_count=$(grep -c '^\- \[x\]' "$TASKS_FILE" 2>/dev/null || echo "0")
echo "  当前进度: ${done_count}/${total} 完成"
echo ""

# 预标记已完成或跳过的 groups（不在范围内或全部 done）
for g in 1 2 3 4 5 6 7 8 9; do
  if is_group_skippable "$g"; then
    mark_group_done "$g"
    echo "[SKIP] Group ${g} — 已完成或不在范围内"
  fi
done

# 获取需要执行的 groups
ACTIVE_GROUPS=$(get_active_groups)
PIDS=()

run_group_async() {
  local g="$1"

  # 已经 skip 的不再处理
  if is_group_done "$g"; then
    return
  fi

  (
    # 等待前置依赖
    if ! wait_for_deps "$g"; then
      echo "  [G${g}] [SKIP] 前置 group 失败，跳过"
      mark_group_failed "$g"
      exit 1
    fi

    # 获取信号量
    read -u 3 _token

    # 执行
    if execute_group "$g"; then
      mark_group_done "$g"
    else
      mark_group_failed "$g"
    fi

    # 释放信号量
    echo "x" >&3
  ) &
  PIDS+=($!)
}

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  启动并发调度（max=${MAX_PARALLEL}）"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

for g in $ACTIVE_GROUPS; do
  if ! is_group_done "$g"; then
    run_group_async "$g"
  fi
done

# 等待所有后台进程
wait_result=0
for pid in "${PIDS[@]}"; do
  if ! wait "$pid" 2>/dev/null; then
    wait_result=1
  fi
done

# 清理信号量
exec 3>&-
rm -rf "$STATUS_DIR"

# ============================================================
# 最终验证（Group 9 的职责或所有完成后）
# ============================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  最终验证：mvn compile + test"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$PROJECT_DIR"
if mvn compile -pl erc20-platform-common,erc20-platform-blockchain,erc20-platform-service -q 2>&1 | tail -3; then
  echo "  [PASS] 编译通过 ✓"
else
  echo "  [WARN] 编译有问题"
fi

if mvn test -pl erc20-platform-common,erc20-platform-blockchain,erc20-platform-service -q 2>&1 | tail -5; then
  echo "  [PASS] 测试通过 ✓"
else
  echo "  [WARN] 测试有失败"
fi

# 统计
echo ""
final_done=$(grep -c '^\- \[x\]' "$TASKS_FILE" 2>/dev/null || echo "0")
remaining=$(grep -c '^\- \[ \]' "$TASKS_FILE" 2>/dev/null || echo "0")
new_completed=$((final_done - done_count))

echo "╔══════════════════════════════════════════════════════════╗"
echo "║  执行结果                                                ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  本轮完成: ${new_completed}  |  总进度: ${final_done}/${total}            ║"
if [ "$wait_result" -ne 0 ]; then
  echo "║  状态: 部分 group 执行失败                                ║"
fi
echo "╚══════════════════════════════════════════════════════════╝"

if [ "$remaining" -eq 0 ]; then
  echo ""
  echo "  所有任务已完成！"
  echo "  下一步: openspec archive $CHANGE_NAME"
fi

exit $wait_result
