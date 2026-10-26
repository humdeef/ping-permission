#!/usr/bin/env bash
# ============================================================
# ping-permission 一键构建 + Docker 启动
#
#   ./start.sh                 构建后端/前端并启动全部容器
#   ./start.sh --skip-build    跳过构建，直接用已有产物启动
#   ./start.sh down            停止并移除容器（保留数据卷）
#   ./start.sh clean           停止并移除容器 + 数据卷（数据库会重新初始化）
#   ./start.sh restart         重启全部容器
#   ./start.sh logs [服务名]    查看日志（默认全部）
#   ./start.sh status          查看容器状态
#   ./start.sh version         打印当前版本（取自根 pom.xml 的 <revision>）
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# 读取本地配置（可选）：端口、密码等
if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a
fi

APP_ENV="${APP_ENV:-dev}"
WEB_HOST_PORT="${WEB_HOST_PORT:-5173}"
GATEWAY_HOST_PORT="${GATEWAY_HOST_PORT:-9999}"
NACOS_CONSOLE_HOST_PORT="${NACOS_CONSOLE_HOST_PORT:-18080}"
NACOS_HOST_PORT="${NACOS_HOST_PORT:-18848}"
MYSQL_HOST_PORT="${MYSQL_HOST_PORT:-13306}"
REDIS_HOST_PORT="${REDIS_HOST_PORT:-16379}"

log()  { printf '\033[1;34m[ping]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[ping]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[ping]\033[0m %s\n' "$*" >&2; exit 1; }

# 镜像 tag 与 Maven 版本同源：都取根 pom.xml 的 <revision>，
# 这样 pom、镜像 tag、发布 tag 不会各写各的。.env 里的 IMAGE_TAG 可覆盖。
image_tag_from_pom() {
  sed -n 's/.*<revision>\(.*\)<\/revision>.*/\1/p' "$ROOT_DIR/pom.xml" | head -n 1
}
IMAGE_TAG="${IMAGE_TAG:-$(image_tag_from_pom)}"
[ -n "$IMAGE_TAG" ] || die "未能从 pom.xml 读取 <revision>，请检查根 POM"
export IMAGE_TAG

require_docker() {
  command -v docker >/dev/null 2>&1 || die "未找到 docker，请先安装 Docker Desktop"
  docker info >/dev/null 2>&1 || die "Docker 未运行，请先启动 Docker Desktop"
  docker compose version >/dev/null 2>&1 || die "未找到 docker compose（需要 Docker Compose v2）"
}

# 优先使用 JDK 21（macOS 通过 java_home 自动定位）
setup_jdk() {
  if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  fi
  if [ -n "${JAVA_HOME:-}" ]; then
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
  command -v java >/dev/null 2>&1 || die "未找到 java，请先安装 JDK 21（Maven Wrapper 同样需要 JDK）"
  log "JDK: $(java -version 2>&1 | head -n 1)"
  resolve_maven
}

# Maven 不要求预装：优先用本机的 mvn，找不到时回退到仓库自带的 Wrapper。
# Wrapper 首次运行会下载锁定的 Maven 版本（3.9.14）到 ~/.m2/wrapper，之后离线可用。
resolve_maven() {
  if command -v mvn >/dev/null 2>&1; then
    MVN_CMD="mvn"
  elif [ -x "$ROOT_DIR/mvnw" ]; then
    MVN_CMD="$ROOT_DIR/mvnw"
    log "未找到 mvn，改用仓库自带的 Maven Wrapper（首次运行需联网下载 Maven）"
  else
    die "未找到 mvn，请安装 Maven 3.9+，或确认仓库根目录的 ./mvnw 可执行（Windows 用 mvnw.cmd）"
  fi
}

build_backend() {
  log "构建后端（Maven，跳过测试）..."
  # Boot 4.1 起 -DskipTests 不再跳过测试的 AOT 处理，打包用 maven.test.skip 更彻底
  "$MVN_CMD" -B -Dmaven.test.skip=true clean package
}

build_frontend() {
  log "构建前端（Vite）..."
  command -v npm >/dev/null 2>&1 || die "未找到 npm，请先安装 Node.js 18+"
  cd "$ROOT_DIR/ping-web"
  if [ ! -d node_modules ]; then
    npm ci --no-audit --no-fund
  fi
  npm run build
  cd "$ROOT_DIR"
}

print_urls() {
  cat <<EOF

  ✅ ping-permission 已启动（环境：${APP_ENV}，版本：${IMAGE_TAG}）

     前端页面      http://localhost:${WEB_HOST_PORT}
     网关 API      http://localhost:${GATEWAY_HOST_PORT}
     Nacos 控制台   http://localhost:${NACOS_CONSOLE_HOST_PORT}/   (Nacos 3.x 控制台在根路径)
     MySQL         localhost:${MYSQL_HOST_PORT}   (root/${MYSQL_ROOT_PASSWORD:-123456}, 数据库 ${MYSQL_DATABASE:-ping})
     Redis         localhost:${REDIS_HOST_PORT}

     配置来源      init-sql/nacos-mysql.sql（Nacos 使用 MySQL 存储）
       application-${APP_ENV}.yml
       gateway-service-${APP_ENV}.yml / user-service-${APP_ENV}.yml / order-service-${APP_ENV}.yml

     默认账号      admin / 123456   （普通用户 tom / 123456）

     查看日志      ./start.sh logs
     停止服务      ./start.sh down

EOF
}

cmd="${1:-up}"
# 允许 ./start.sh --skip-build 这种写法，等价于 ./start.sh up --skip-build
if [ "$cmd" = "--skip-build" ]; then
  set -- up --skip-build
  cmd=up
fi
case "$cmd" in
  up)
    shift || true
    skip_build=0
    for arg in "$@"; do
      case "$arg" in
        --skip-build) skip_build=1 ;;
        *) die "未知参数: $arg" ;;
      esac
    done

    require_docker
    if [ "$skip_build" -eq 0 ]; then
      setup_jdk
      build_backend
      build_frontend
    else
      warn "跳过构建，直接使用现有 target/*.jar 与 ping-web/dist"
      [ -f ping-gateway/gateway-service/target/gateway-service.jar ] || die "缺少 gateway-service.jar，请先执行完整构建"
      [ -f ping-user/user-service/target/user-service.jar ] || die "缺少 user-service.jar，请先执行完整构建"
      [ -f examples/ping-order/order-service/target/order-service.jar ] || die "缺少 order-service.jar，请先执行完整构建"
      [ -d ping-web/dist ] || die "缺少 ping-web/dist，请先执行完整构建"
    fi

    log "构建镜像并启动容器..."
    docker compose up -d --build
    print_urls
    ;;
  down)
    require_docker
    docker compose down
    log "已停止（数据卷保留，如需清空请执行 ./start.sh clean）"
    ;;
  clean)
    require_docker
    docker compose down -v
    log "已停止并删除数据卷"
    ;;
  restart)
    require_docker
    docker compose restart
    ;;
  logs)
    require_docker
    shift || true
    docker compose logs -f --tail=200 "$@"
    ;;
  status|ps)
    require_docker
    docker compose ps
    ;;
  build)
    # 只构建，不启动
    require_docker
    setup_jdk
    build_backend
    build_frontend
    docker compose build
    log "构建完成"
    ;;
  version)
    # 供脚本复用：版本与镜像 tag 都来自根 pom 的 <revision>
    echo "$IMAGE_TAG"
    ;;
  -h|--help|help)
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    die "未知命令: $cmd（可用: up | down | clean | restart | logs | status | build | version）"
    ;;
esac
