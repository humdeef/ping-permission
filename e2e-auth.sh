#!/usr/bin/env bash
# ============================================================
# 端到端鉴权用例（真实链路）
#
#   浏览器/客户端 → 网关（Sa-Token 认证 + 接口权限规则 + 数据权限）
#                 → Dubbo 调用 user-service（@PreAuthorize）
#                 → MySQL
#
# 覆盖的链路环节（任何一环配错都会在这里失败）：
#   1. 网关认证：无 token / 伪造 token 一律 401；
#   2. 登录：账号密码校验、签发 token、返回 forceChangePassword；
#   3. 接口权限：规则来自 user-service 的 sys_api_resource（经 Dubbo 加载），
#      有权限 200、无权限 403；
#   4. fail-closed：未在 sys_api_resource 登记的接口返回 403，而不是放行；
#   5. 数据权限：同一接口下，不同数据范围的用户看到的数据量不同；
#   6. 会话失效：logout 之后同一个 token 立即 401。
#
# 前置条件：整套服务已在运行（./start.sh 或 docker compose up）。
# 与前缀为 *IT.java 的 Testcontainers 集成测试互补：
#   - *IT.java 自带 MySQL 容器，验证迁移、SQL 等单服务行为，`mvn verify` 即可跑；
#   - 本脚本验证跨进程的真实链路（Nacos 配置 + Dubbo + 网关路由），
#     无法在单元测试里模拟，因此单独作为 CI 的一个任务执行。
#
# 用法：
#   ./e2e-auth.sh                       # 默认访问 http://localhost:9999
#   ./e2e-auth.sh http://host:9999      # 指定网关地址
#   E2E_READY_TIMEOUT=300 ./e2e-auth.sh # 首次启动较慢时放宽就绪等待（秒）
#
# 依赖：curl、python3（解析 JSON 与集合比较），无其他第三方工具。
# ============================================================
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# 读取本地配置（可选）：与 start.sh 使用同一份 .env，端口保持一致
if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a
fi

# 需要管理员账号（数据范围最大）与一个受限账号（缺部分接口权限）
ADMIN_USER="${E2E_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-123456}"
LIMITED_USER="${E2E_LIMITED_USER:-tom}"
LIMITED_PASSWORD="${E2E_LIMITED_PASSWORD:-123456}"
READY_TIMEOUT="${E2E_READY_TIMEOUT:-180}"

case "${1:-}" in
  -h|--help) sed -n '2,35p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
  "") ;;
  *) BASE_URL="${1%/}" ;;
esac
BASE_URL="${BASE_URL:-http://localhost:${GATEWAY_HOST_PORT:-9999}}"

# 未在 sys_api_resource 里登记的接口：网关必须 fail-closed（403），不能放行
UNREGISTERED_PATH="/user/pc/e2e-unregistered-probe"
# 需要 system:online:list 的接口：管理员有、受限账号没有
ADMIN_ONLY_PATH="/user/pc/online"
# 两个账号都有 system:user:list 的接口：用来看数据范围差异
SHARED_PATH="/user/pc/users?pageSize=100"

FAILURES=0
PASSED=0
STATUS=""
BODY=""

log()  { printf '\033[1;34m[e2e]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[e2e]\033[0m %s\n' "$*" >&2; exit 1; }
pass() { PASSED=$((PASSED + 1)); printf '  \033[32m✓\033[0m %s\n' "$*"; }
fail() {
  FAILURES=$((FAILURES + 1))
  printf '  \033[31m✗\033[0m %s\n' "$*" >&2
  printf '    HTTP %s  %s\n' "${STATUS:-?}" "$(printf '%s' "$BODY" | head -c 400)" >&2
}
step() { printf '\n\033[1;36m%s\033[0m\n' "$*"; }

# ------------------------------------------------------------------
# HTTP / JSON 辅助
# ------------------------------------------------------------------

# http METHOD PATH [TOKEN] [JSON_BODY] -> 结果写入 STATUS / BODY
http() {
  local method="$1" path="$2" token="${3:-}" payload="${4:-}"
  local args=(-s --max-time 15 -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  [ -n "$token" ] && args+=(-H "ping-token: Bearer $token")
  [ -n "$payload" ] && args+=(-H 'Content-Type: application/json' -d "$payload")
  STATUS="$(curl "${args[@]}" 2>/dev/null || echo "000")"
  BODY="$(cat "$BODY_FILE" 2>/dev/null || true)"
}

login() {
  http POST /user/pc/auth/login "" "{\"username\":\"$1\",\"password\":\"$2\"}"
}

# json_field 'data.token'：按点号取字段，取不到输出空串
json_field() {
  printf '%s' "$BODY" | python3 -c '
import sys, json
try:
    cur = json.load(sys.stdin)
except ValueError:
    sys.exit(0)
for key in sys.argv[1].split("."):
    if isinstance(cur, dict) and key in cur:
        cur = cur[key]
    elif isinstance(cur, list) and key.isdigit() and int(key) < len(cur):
        cur = cur[int(key)]
    else:
        sys.exit(0)
if isinstance(cur, (dict, list)):
    print(json.dumps(cur, ensure_ascii=False))
elif cur is not None:
    print(cur)
' "$1"
}

# 分页结果 -> "total deptId,deptId,..."，用于比较数据范围
scope_of() {
  printf '%s' "$BODY" | python3 -c '
import sys, json
try:
    data = json.load(sys.stdin).get("data") or {}
except ValueError:
    sys.exit(0)
rows = data.get("list") or []
depts = sorted({str(row.get("deptId")) for row in rows if row.get("deptId") is not None})
print(data.get("total", len(rows)), ",".join(depts))
'
}

# 判断实际可见范围是否严格小于全量范围（部门集合是子集，且至少少一个部门）
is_strict_subset() {
  python3 -c '
import sys
restricted = set(filter(None, sys.argv[1].split(",")))
full = set(filter(None, sys.argv[2].split(",")))
sys.exit(0 if restricted < full else 1)
' "$1" "$2"
}

# ------------------------------------------------------------------
# 前置：等整套链路就绪
# ------------------------------------------------------------------
# 服务进程健康不代表配置与权限规则已加载：网关的规则来自 Dubbo（首次刷新有间隔），
# 所以这里以「管理员能拿到 token 且能查出用户列表」作为真正就绪的判据。
wait_ready() {
  local waited=0
  log "等待 ${BASE_URL} 就绪（最多 ${READY_TIMEOUT}s）..."
  while [ "$waited" -lt "$READY_TIMEOUT" ]; do
    login "$ADMIN_USER" "$ADMIN_PASSWORD"
    local token
    token="$(json_field 'data.token')"
    if [ "$STATUS" = "200" ] && [ -n "$token" ]; then
      http GET "$SHARED_PATH" "$token"
      if [ "$STATUS" = "200" ]; then
        log "就绪（等待 ${waited}s）"
        return 0
      fi
    fi
    sleep 3
    waited=$((waited + 3))
    printf '.'
  done
  printf '\n'
  die "等待超时：${BASE_URL} 未就绪。排查：./start.sh status / ./start.sh logs gateway user-service"
}

command -v curl >/dev/null 2>&1 || die "缺少 curl"
command -v python3 >/dev/null 2>&1 || die "缺少 python3（仅用于解析 JSON，无其他依赖）"

BODY_FILE="$(mktemp /tmp/ping-e2e.XXXXXX)"
trap 'rm -f "$BODY_FILE"' EXIT

wait_ready

# ------------------------------------------------------------------
# 1. 网关认证：没有有效 token 一律 401
# ------------------------------------------------------------------
step "1/6 网关认证（Sa-Token）"

http GET "$SHARED_PATH"
if [ "$STATUS" = "401" ]; then
  pass "未携带 token 访问受保护接口 → 401"
else
  fail "未携带 token 应当 401"
fi

http GET "$SHARED_PATH" "not-a-real-token"
if [ "$STATUS" = "401" ]; then
  pass "伪造 token → 401"
else
  fail "伪造 token 应当 401"
fi

# ------------------------------------------------------------------
# 2. 登录：签发 token
# ------------------------------------------------------------------
step "2/6 PC 域登录"

login "$ADMIN_USER" "$ADMIN_PASSWORD"
ADMIN_TOKEN="$(json_field 'data.token')"
TOKEN_NAME="$(json_field 'data.tokenName')"
FORCE_CHANGE="$(json_field 'data.forceChangePassword')"
if [ "$STATUS" = "200" ] && [ -n "$ADMIN_TOKEN" ]; then
  pass "${ADMIN_USER} 登录 → 200，拿到 token"
else
  fail "${ADMIN_USER} 登录应当 200 且返回 token"
fi
if [ "$TOKEN_NAME" = "ping-token" ]; then
  pass "响应中的 tokenName = ping-token（与鉴权头一致）"
else
  fail "tokenName 应为 ping-token，实际为 '${TOKEN_NAME}'"
fi
if [ "$FORCE_CHANGE" = "False" ]; then
  pass "种子账号未标记强制改密"
else
  fail "种子账号不应要求强制改密，实际 forceChangePassword=${FORCE_CHANGE}"
fi

login "$LIMITED_USER" "$LIMITED_PASSWORD"
LIMITED_TOKEN="$(json_field 'data.token')"
if [ "$STATUS" = "200" ] && [ -n "$LIMITED_TOKEN" ]; then
  pass "${LIMITED_USER} 登录 → 200，拿到 token"
else
  fail "${LIMITED_USER} 登录应当 200 且返回 token"
fi

# ------------------------------------------------------------------
# 3. 接口权限：网关规则 + 服务端 @PreAuthorize 两层都要过
# ------------------------------------------------------------------
step "3/6 接口级鉴权"

http GET "$SHARED_PATH" "$ADMIN_TOKEN"
if [ "$STATUS" = "200" ]; then
  pass "${ADMIN_USER} 访问 ${SHARED_PATH%%\?*} → 200"
else
  fail "${ADMIN_USER} 应当有 system:user:list 权限"
fi

http GET "$SHARED_PATH" "$LIMITED_TOKEN"
if [ "$STATUS" = "200" ]; then
  pass "${LIMITED_USER} 访问 ${SHARED_PATH%%\?*} → 200（有该权限码）"
else
  fail "${LIMITED_USER} 应当有 system:user:list 权限"
fi

http GET "$ADMIN_ONLY_PATH" "$ADMIN_TOKEN"
if [ "$STATUS" = "200" ]; then
  pass "${ADMIN_USER} 访问 ${ADMIN_ONLY_PATH} → 200"
else
  fail "${ADMIN_USER} 应当有 system:online:list 权限"
fi

http GET "$ADMIN_ONLY_PATH" "$LIMITED_TOKEN"
if [ "$STATUS" = "403" ]; then
  pass "${LIMITED_USER} 访问 ${ADMIN_ONLY_PATH} → 403（缺少权限码）"
else
  fail "${LIMITED_USER} 缺少 system:online:list，应当 403"
fi

# 未登记接口必须拒绝，避免新增接口忘记登记权限后静默放行
http GET "$UNREGISTERED_PATH" "$ADMIN_TOKEN"
if [ "$STATUS" = "403" ]; then
  pass "未登记接口 ${UNREGISTERED_PATH} → 403（fail-closed）"
else
  fail "未登记接口必须 fail-closed 返回 403"
fi

# ------------------------------------------------------------------
# 4. 数据权限：同一个接口，两个账号看到的数据范围不同
# ------------------------------------------------------------------
step "4/6 数据权限（部门范围）"

http GET "$SHARED_PATH" "$ADMIN_TOKEN"
read -r ADMIN_TOTAL ADMIN_DEPTS <<<"$(scope_of)"
http GET "$SHARED_PATH" "$LIMITED_TOKEN"
read -r LIMITED_TOTAL LIMITED_DEPTS <<<"$(scope_of)"

if [ -n "${ADMIN_TOTAL:-}" ] && [ -n "${LIMITED_TOTAL:-}" ] \
   && [ "$ADMIN_TOTAL" -gt 0 ] && [ "$LIMITED_TOTAL" -gt 0 ]; then
  pass "两个账号都能查到数据（${ADMIN_USER}=${ADMIN_TOTAL} 条，${LIMITED_USER}=${LIMITED_TOTAL} 条）"
else
  fail "两个账号都应能查到数据：${ADMIN_USER}=${ADMIN_TOTAL:-?}，${LIMITED_USER}=${LIMITED_TOTAL:-?}"
fi

if [ -n "${ADMIN_TOTAL:-}" ] && [ -n "${LIMITED_TOTAL:-}" ] && [ "$LIMITED_TOTAL" -lt "$ADMIN_TOTAL" ]; then
  pass "${LIMITED_USER} 可见条数少于 ${ADMIN_USER}（数据范围生效）"
else
  fail "数据范围未生效：${LIMITED_USER}=${LIMITED_TOTAL:-?} 应少于 ${ADMIN_USER}=${ADMIN_TOTAL:-?}"
fi

if is_strict_subset "${LIMITED_DEPTS:-}" "${ADMIN_DEPTS:-}"; then
  pass "${LIMITED_USER} 的部门集合是 ${ADMIN_USER} 的真子集（${LIMITED_DEPTS:-空} ⊂ ${ADMIN_DEPTS:-空}）"
else
  fail "部门范围未生效：${LIMITED_USER}=[${LIMITED_DEPTS:-}]，${ADMIN_USER}=[${ADMIN_DEPTS:-}]"
fi

# ------------------------------------------------------------------
# 5. 会话失效：logout 之后 token 立刻不可用
# ------------------------------------------------------------------
step "5/6 会话失效"

http POST /user/pc/auth/logout "$LIMITED_TOKEN"
if [ "$STATUS" = "200" ]; then
  pass "${LIMITED_USER} 退出登录 → 200"
else
  fail "退出登录应当 200"
fi

http GET "$SHARED_PATH" "$LIMITED_TOKEN"
if [ "$STATUS" = "401" ]; then
  pass "退出后用旧 token 访问 → 401"
else
  fail "退出后旧 token 应当立即失效（401）"
fi

# 管理员会话不受影响：单 token 失效不能影响其他会话
http GET "$SHARED_PATH" "$ADMIN_TOKEN"
if [ "$STATUS" = "200" ]; then
  pass "${ADMIN_USER} 的会话未受影响"
else
  fail "${ADMIN_USER} 的会话不应被其他账号的退出影响"
fi

# ------------------------------------------------------------------
# 汇总
# ------------------------------------------------------------------
step "6/6 结果"
if [ "$FAILURES" -eq 0 ]; then
  log "全部通过：${PASSED} 项"
  exit 0
fi
printf '\033[1;31m[e2e]\033[0m 失败 %s 项，通过 %s 项\n' "$FAILURES" "$PASSED" >&2
printf '排查建议：./start.sh logs gateway user-service；确认 sys_api_resource 已登记新接口。\n' >&2
exit 1
