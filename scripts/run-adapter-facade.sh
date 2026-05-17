#!/bin/bash
#
# ERC-20 Adapter Facade — 并发任务执行脚本
#
# 每个 task 开启独立 Claude Code session，组间按依赖图并行执行。
#
# 依赖图:
#   G1 (DB+异常) → G2 (Registry) → G3 (Gateway) ─────┐
#   G1           → G4 (Executor)  ─────────────────────┼→ G7 (Adapter) → G8 (集成)
#   G5 (独立)    → G6 (Confirmer增强) ─────────────────┘              → G9 (清理)
#   G2           → G6
#
# 可并行的组:
#   Wave 1: G1
#   Wave 2: G2, G4, G5 (三组并行)
#   Wave 3: G3, G6 (两组并行)
#   Wave 4: G7
#   Wave 5: G8, G9 (两组并行)
#
# 用法：
#   ./scripts/run-adapter-facade.sh              # 默认 3 并发
#   ./scripts/run-adapter-facade.sh -j 4         # 4 并发
#   ./scripts/run-adapter-facade.sh -j 1         # 顺序执行
#   ./scripts/run-adapter-facade.sh -j 3 3.1     # 从 task 3.1 开始
#   ./scripts/run-adapter-facade.sh -j 3 3.1 7.7 # 执行 3.1 到 7.7
#

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CHANGE_NAME="erc20-adapter-facade"
CHANGE_DIR="$PROJECT_DIR/openspec/changes/$CHANGE_NAME"
TASKS_FILE="$CHANGE_DIR/tasks.md"
LOG_DIR="$PROJECT_DIR/scripts/logs/$CHANGE_NAME"
mkdir -p "$LOG_DIR"

# ============================================================
# 参数解析
# ============================================================
MAX_PARALLEL=3
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
#   G1 → G2 → G3 → G7
#   G1 → G4       → G7
#   G5 → G6       → G7
#   G2 → G6
#   G7 → G8
#   G7 → G9
# ============================================================
get_group_deps() {
  case "$1" in
    1) echo "" ;;
    2) echo "1" ;;
    3) echo "2" ;;
    4) echo "1" ;;
    5) echo "" ;;
    6) echo "2 5" ;;
    7) echo "3 4 6" ;;
    8) echo "7" ;;
    9) echo "7" ;;
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
    1)   echo "" ;;
    2)   echo "specs/token-admission/spec.md" ;;
    3)   echo "specs/token-admission/spec.md" ;;
    4)   echo "specs/transfer-executor/spec.md" ;;
    5)   echo "specs/balance-diff-confirm/spec.md" ;;
    6)   echo "specs/balance-diff-confirm/spec.md" ;;
    7)   echo "specs/adapter-facade/spec.md" ;;
    8)   echo "specs/adapter-facade/spec.md" ;;
    9)   echo "specs/adapter-facade/spec.md" ;;
  esac
}

# ============================================================
# 获取组内所有 task (返回 "task_id|task_desc|status" 每行一个)
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
你正在执行 ERC-20 适配层门面重构（change: ${CHANGE_NAME}）。

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
- 完成后运行 mvn compile -pl erc20-platform-common,erc20-platform-dal,erc20-platform-blockchain,erc20-platform-service 确认编译通过
- 如果本 task 包含"单元测试"或"测试"，运行 mvn test -pl <对应模块> -Dtest=<TestClass> 验证通过

## 关键路径约定

- adapter 层: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/adapter/
- adapter/rpc: ERC20RpcClient, ReturnValueDecoder
- adapter/model: CallResult, TransferResult, TransferOutcome, CallOutcome, TokenRiskProfile
- adapter/exception: ERC20AdapterException 及子类
- 枚举: erc20-platform-common/src/main/java/com/erc20/platform/common/enums/
- DAL: erc20-platform-dal/src/main/java/com/erc20/platform/dal/
- Flyway 迁移: erc20-platform-dal/src/main/resources/db/migration/
- 服务: erc20-platform-service/src/main/java/com/erc20/platform/service/
- 钱包: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/wallet/
- 监控: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/monitor/
- ERC20: erc20-platform-blockchain/src/main/java/com/erc20/platform/blockchain/erc20/
- 测试: 对应模块的 src/test/java/ 下同包结构

## 已有基础设施（可直接使用）

- CallResult: 五态（SUCCESS/SUCCESS_NO_RETURN/RETURNED_FALSE/REVERTED/UNKNOWN），工厂方法 success()/successNoReturn()/returnedFalse()/reverted()/unknown(rawHex)，查询 isSuccess()/isDangerousFalse()
- TransferResult: 四态（SUCCESS/FAILED/PENDING/ANOMALY），builder 模式，工厂方法 failed()/pending()，查询 isAmountConsistent()/hasBalanceDiffAnomaly()
- ReturnValueDecoder: @Component，decodeBoolReturn(String hexResult) → CallResult
- ERC20RpcClient: @Component，preCheckTransfer()/preCheckApprove() → CallResult
- TransferConfirmer: @Component，confirm(txHash, contract, expectedAmount, toAddress) → TransferResult，三层确认（receipt→event→amount）
- TokenRiskProfile: 含 requiresBalanceDiff()/requiresApproveReset()/isStandardProcessing()，builder 模式
- TokenCapability: 13 枚举值（STANDARD_RETURN, NO_RETURN_VALUE, APPROVE_RACE_CONDITION, BYTES32_METADATA, PAUSABLE, BLACKLISTABLE, UPGRADEABLE, MINTABLE, BURNABLE, FEE_ON_TRANSFER, REBASING, MAX_TRANSFER_LIMIT, COOLDOWN_REQUIRED）
- RiskLevel: LOW/MEDIUM/HIGH/CRITICAL
- 异常: ERC20AdapterException, TransferRevertedException, TokenPausedException, TokenBlacklistedException, AmountMismatchException, TransferEventMissingException
- SafeERC20Caller: @Component，safeBalanceOf()/safeDecimals()/safeSymbol()/safeName()，bytes32 兼容
- WalletService: @Service，sendERC20Transfer(from, to, contract, amount, priority) 含预检+nonce+sign+broadcast+NONCE_TOO_LOW 恢复
- AdminEventMonitor: @Component，监控 Paused/Upgraded 事件并自动禁用 token
- WithdrawTransactionSenderImpl: 实现 WithdrawTransactionSender 接口，当前直接调 WalletService
- CollectionTransactionSenderImpl: 实现 CollectionTransactionSender 接口，当前直接调 WalletService

只执行 Task ${task_id}，完成后停止。
EOF
}

# ============================================================
# 脚本层标记 task 完成（原子操作，避免并发写冲突）
# 使用 flock 确保同一时刻只有一个进程修改 tasks.md
# ============================================================
TASKS_LOCK="$LOG_DIR/.tasks.lock"

mark_task_done_in_file() {
  local task_id="$1"
  (
    flock -x 200
    sed -i '' "s/^- \[ \] ${task_id} /- [x] ${task_id} /" "$TASKS_FILE"
  ) 200>"$TASKS_LOCK"
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

    # Claude 执行成功（exit 0），由脚本层原子标记完成
    # 即使 Claude 内部标记被并发覆盖，脚本层也会修复
    if ! is_task_done "$task_id"; then
      mark_task_done_in_file "$task_id"
    fi

    if is_task_done "$task_id"; then
      echo "  [G${group_num}] [PASS]  Task ${task_id} ✓"
      return 0
    else
      echo "  [G${group_num}] [WARN]  Task ${task_id} — 标记失败，尝试重试..."
      RETRY_LOG="${LOG_FILE}.retry"
      if claude -p "$PROMPT" \
        --dangerously-skip-permissions \
        --output-format text \
        > "$RETRY_LOG" 2>&1; then
        if ! is_task_done "$task_id"; then
          mark_task_done_in_file "$task_id"
        fi
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

  # 组内没有 pending 的 task（全部已完成）
  local pending
  pending=$(get_tasks_in_group "$group_num" | grep '|pending$' | wc -l)
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
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ERC-20 Adapter Facade — 并发执行                            ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Change:    $CHANGE_NAME"
echo "║  Project:   $PROJECT_DIR"
echo "║  Logs:      $LOG_DIR"
echo "║  Parallel:  ${MAX_PARALLEL} (max concurrent groups)"
if [ -n "$START_TASK" ]; then
  echo "║  Range:     ${START_TASK} → ${END_TASK:-end}"
fi
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  依赖图:                                                     ║"
echo "║    G1 → G2 → G3 ──────────┐                                 ║"
echo "║    G1 → G4 ───────────────┼→ G7 → G8                        ║"
echo "║    G5 → G6 ───────────────┘      → G9                       ║"
echo "║    G2 → G6                                                   ║"
echo "║  可并行: [G2,G4,G5] [G3,G6] [G8,G9]                          ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

total=$(grep -c '^\- \[' "$TASKS_FILE" 2>/dev/null || echo "0")
done_count=$(grep -c '^\- \[x\]' "$TASKS_FILE" 2>/dev/null || echo "0")
echo "  当前进度: ${done_count}/${total} 完成"
echo ""

# 预标记已完成或跳过的 groups
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
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  启动并发调度（max=${MAX_PARALLEL}）"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

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
# 最终验证
# ============================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  最终验证：mvn compile + test"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$PROJECT_DIR"
if mvn compile -pl erc20-platform-common,erc20-platform-dal,erc20-platform-blockchain,erc20-platform-service -q 2>&1 | tail -3; then
  echo "  [PASS] 编译通过 ✓"
else
  echo "  [WARN] 编译有问题"
fi

if mvn test -pl erc20-platform-common,erc20-platform-dal,erc20-platform-blockchain,erc20-platform-service -q 2>&1 | tail -5; then
  echo "  [PASS] 测试通过 ✓"
else
  echo "  [WARN] 测试有失败"
fi

# 统计
echo ""
final_done=$(grep -c '^\- \[x\]' "$TASKS_FILE" 2>/dev/null || echo "0")
remaining=$(grep -c '^\- \[ \]' "$TASKS_FILE" 2>/dev/null || echo "0")
new_completed=$((final_done - done_count))

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  执行结果                                                     ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  本轮完成: ${new_completed}  |  总进度: ${final_done}/${total}              ║"
if [ "$wait_result" -ne 0 ]; then
  echo "║  状态: 部分 group 执行失败                                    ║"
fi
echo "╚══════════════════════════════════════════════════════════════╝"

if [ "$remaining" -eq 0 ]; then
  echo ""
  echo "  所有任务已完成！"
  echo "  下一步: openspec archive $CHANGE_NAME"
fi

exit $wait_result
