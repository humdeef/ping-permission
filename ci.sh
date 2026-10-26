#!/usr/bin/env bash
#
# 本地 / 任意 CI 平台可执行的统一门禁，与 .github/workflows/ci.yml 保持一致：
#   1. 后端 mvn verify（编译、单元测试、集成测试、打包、JaCoCo）
#   2. 阿里巴巴 Java 开发手册检查（p3c-pmd）
#   3. 前端 npm ci + ESLint + 构建
#   4. Trivy 依赖漏洞扫描（HIGH/CRITICAL，可修复）
#   5. 端到端鉴权用例（可选，需整套服务已启动）
#
# 集成测试（*IT.java）由 Testcontainers 启动 MySQL 容器，需要 Docker；没有 Docker 时会自动跳过。
#
# 用法：
#   ./ci.sh                 # 完整执行（第 5 步默认跳过）
#   E2E=1 ./ci.sh           # 追加端到端鉴权用例（先 ./start.sh 把服务跑起来）
#   SKIP_TRIVY=1 ./ci.sh    # 本机没装 Trivy 时跳过扫描
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "==> [1/5] 后端：mvn -B verify"
mvn -B -ntp verify

echo "==> [2/5] 代码规范：mvn -B -Pali-p3c compile pmd:check"
# 先 compile：单独执行 pmd:check 不经过编译阶段，Maven 不会用 reactor 的输出，
# 多模块依赖会去本地仓库找（干净环境里找不到），release 版本尤其明显。
mvn -B -ntp -Pali-p3c compile pmd:check

echo "==> [3/5] 前端：npm ci && npm run lint && npm run build"
(
    cd ping-web
    npm ci
    npm run lint
    npm run build
)

echo "==> [4/5] 依赖扫描：Trivy"
if [ "${SKIP_TRIVY:-0}" = "1" ]; then
    echo "SKIP_TRIVY=1，已跳过依赖扫描"
elif command -v trivy >/dev/null 2>&1; then
    trivy fs \
        --scanners vuln \
        --severity HIGH,CRITICAL \
        --ignore-unfixed \
        --exit-code 1 \
        --skip-dirs '**/node_modules' \
        --skip-dirs '**/target' \
        --skip-dirs 'ping-web/dist' \
        .
else
    echo "未找到 trivy，无法执行依赖扫描。安装：https://trivy.dev/latest/getting-started/installation/" >&2
    echo "仅本机临时跳过可设置 SKIP_TRIVY=1。" >&2
    exit 1
fi

if [ "${E2E:-0}" = "1" ]; then
    echo "==> [5/5] 端到端鉴权：./e2e-auth.sh"
    bash "$ROOT_DIR/e2e-auth.sh"
else
    echo "==> [5/5] 端到端鉴权：跳过（需整套服务已启动，用 E2E=1 ./ci.sh 执行）"
fi

echo "==> 全部通过"
