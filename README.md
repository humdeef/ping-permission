# ping-permission

Ping Permission：基于 Spring Boot 4 + Spring Cloud +Sa Token+ Dubbo + Vue 3 的 RBAC 权限底座，易接入、易二开。

- 内置 RBAC、PC/App 双登录域、接口级鉴权、数据权限、审计与在线会话。
- 菜单与前端路由由后端权限树生成：新增页面只加一条 `sys_permission` 记录，前端按 `menu-{id}` 动态注册路由，不再维护菜单表。
- 权限逻辑集中在 `ping-user` 与 `ping-common`；PC/App 及公共模块之间禁止跨域引用，由 `ModuleBoundaryTest` 校验。
- `examples/ping-order` 是接入模板：`order-service` 只依赖 `order-client`、`user-client` 和公共 starter，复制这两个模块即可新增业务。

## 界面预览

| 登录页 | 登录成功页（面包屑 + 多页签） |
| --- | --- |
| <img src="images/login.png" alt="登录页" width="440"> | <img src="images/login_success.png" alt="登录成功页" width="440"> |

## 内置账号

首次启动时 Flyway 种子数据会创建四个后台账号，角色与数据范围各不相同，可直接用来验证 RBAC 与数据权限：

| 账号 | 密码 | 角色 | 部门 | 数据范围 |
| --- | --- | --- | --- | --- |
| `admin` | `123456` | 超级管理员 | 总公司 | 全部数据 |
| `tom` | `123456` | BU 中心负责人 | 研发中心 | 本部门及下级（研发中心 → 开发部 / 测试部） |
| `jerry` | `123456` | 部门负责人 | 开发部 | 本部门 |
| `lisa` | `123456` | 普通用户 | 运营中心 | 仅本人 |

> 四个账号的密码统一为 `123456`：`lisa` 在 V1 种子里的哈希与其余三个不同，由 `V2__reset_lisa_password.sql` 在启动时重置——该迁移只在密码仍是初始值时生效，不会覆盖已修改过的密码。
>
> 以上是后台（PC）账号。移动端 / C 端是另一套 `app_user` 账号体系，通过 `POST /user/app/auth/register` 自助注册，与后台账号、token 完全隔离。

## 适用对象

**适合：**

- **需要 PC 后台 + App 双端的团队**：后台管理员与 C 端用户是两套账号体系，希望登录、鉴权、会话互不干扰；
- **做中后台产品 / 内部平台的团队**：需要 RBAC、菜单权限、按钮权限、接口权限、部门数据范围与审计日志；
- **需要持续二开的交付型团队**：权限内核开箱即用，新业务按 `examples/ping-order` 模板复制模块，不改内核；
- **想学习完整权限链路的开发者**：网关鉴权、Sa-Token 双登录域、Dubbo 上下文透传、MyBatis-Plus 数据权限都能在本工程跑通。

**不太适合：**

- 单服务、无网关 / 无注册中心需求的极简项目：引入 Nacos、Dubbo、Gateway 的运维成本偏高；
- 非 Java / Spring 技术栈团队：本项目基于 Spring Boot 4 + Spring Cloud + Dubbo；
- 期望开箱即用的低代码 / 工作流 / 报表平台：本项目提供的是权限底座与示例业务，不包含这些能力；
- 需要原生多租户隔离的项目：当前模型是单库 + 部门数据范围，未内置 `tenant_id` 机制，需要自行扩展。

## 项目结构

本仓库把原先分散的 5 个仓库合并为一个工程：

```
ping-permission/
├── pom.xml              # Maven 聚合工程（统一构建 4 个后端项目）
├── ping-common/         # 公共组件：ping-core + web/dubbo/log/id/data-scope 五个 starter
├── ping-gateway/        # Spring Cloud Gateway 网关（认证、鉴权、路由、链路追踪）
├── ping-user/           # 用户与权限服务（RBAC、登录域、审计、在线会话）
│   ├── user-common/
│   ├── user-auth/
│   ├── user-pc/
│   ├── user-app/
│   ├── user-client/
│   └── user-service/
├── examples/            # 示例业务模块（只作接入演示，可直接复制或删除）
│   └── ping-order/
│       ├── order-client/
│       └── order-service/
├── ping-web/            # 前端（Vue 3 + Vite + Element Plus）
├── init-sql/            # MySQL 初始化脚本（nacos-mysql.sql；业务表由 Flyway 迁移创建）
├── docker-compose.yml   # 一键启动编排
├── start.sh             # 一键构建 + 启动脚本（macOS / Linux / Git Bash）
├── start.ps1            # Windows PowerShell 版，子命令与 start.sh 一致
├── start.cmd            # Windows 批处理入口（绕过 PowerShell 执行策略）
└── e2e-auth.sh          # 端到端鉴权用例（登录 → 网关 → 服务 → 数据权限）
```

## 模块文档

每个模块都有独立 README，说明职责、用法、示例和常见坑：

| 模块 | 文档 | 说明 |
| --- | --- | --- |
| 公共组件 | [ping-common](ping-common/README.md) | 核心模型与 5 个 Spring Boot Starter |
| 网关 | [ping-gateway](ping-gateway/README.md) | 路由、认证、接口级鉴权 |
| 用户与权限 | [ping-user](ping-user/README.md) | 双登录域、RBAC、数据权限、审计 |
| 示例业务 | [examples](examples/README.md) | 示例业务模块索引：订单示例等（可复制/删除） |
| 前端 | [ping-web](ping-web/README.md) | Vue 3 管理后台 |

子模块：

- `ping-common`：[ping-core](ping-common/ping-core/README.md)、[ping-web-spring-boot-starter](ping-common/ping-web-spring-boot-starter/README.md)、[ping-dubbo-spring-boot-starter](ping-common/ping-dubbo-spring-boot-starter/README.md)、[ping-log-spring-boot-starter](ping-common/ping-log-spring-boot-starter/README.md)、[ping-id-spring-boot-starter](ping-common/ping-id-spring-boot-starter/README.md)、[ping-data-scope-spring-boot-starter](ping-common/ping-data-scope-spring-boot-starter/README.md)
- `ping-user`：[user-common](ping-user/user-common/README.md)、[user-auth](ping-user/user-auth/README.md)、[user-client](ping-user/user-client/README.md)、[user-pc](ping-user/user-pc/README.md)、[user-app](ping-user/user-app/README.md)、[user-service](ping-user/user-service/README.md)
- `ping-gateway`：[gateway-service](ping-gateway/gateway-service/README.md)
- `examples/ping-order`：[order-client](examples/ping-order/order-client/README.md)、[order-service](examples/ping-order/order-service/README.md)

## 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 21 |
| Maven | 3.9+（可选用仓库自带的 `./mvnw`，未安装 Maven 时 `start.sh` 会自动回退） |
| Node.js | 20.19+ / 22.13+（`ping-web`；ESLint 10 要求） |
| MySQL | 8.x（库名 `ping`） |
| Redis | 7.x |
| Nacos | 2.x / 3.x（Docker 编排使用 3.1.1） |

> 项目统一使用 **JDK 21**。如果本机默认 JDK 不是 21，构建前先切换：
>
> ```bash
> # macOS
> export JAVA_HOME=$(/usr/libexec/java_home -v 21)
>
> # 或单次构建
> JAVA_HOME=/path/to/jdk-21 mvn clean install -DskipTests
> ```

## Docker 一键启动

> 前提：已安装 Docker Desktop（含 Docker Compose v2），本机具备 JDK 21、Node.js；Maven 未安装时用仓库自带的 `./mvnw`，`start.sh` 会自动回退。
> 构建在宿主机完成，镜像只负责运行，避免容器内重复下载依赖。

```bash
chmod +x start.sh   # 首次执行需要赋权
./start.sh          # 构建后端 + 前端，然后启动全部容器
```

首次启动会自动：导入 Nacos 表结构与配置数据（`init-sql/nacos-mysql.sql`）、启动 Redis/Nacos、按健康检查顺序拉起服务；业务库 `ping` 的表结构与种子数据由各服务启动时的 Flyway 迁移创建（`*/src/main/resources/db/migration/`，默认账号见 [内置账号](#内置账号)）。

| 入口 | 地址 |
| --- | --- |
| 前端 | http://localhost:5173 |
| 网关 API | http://localhost:9999 |
| 接口文档 | http://localhost:9999/docs （Swagger UI，聚合用户服务与订单服务） |
| Nacos 控制台 | http://localhost:18080（Nacos 3.x 控制台在根路径；开启鉴权时为 nacos/nacos） |
| MySQL | localhost:13306（root/123456，库 `ping`） |
| Redis | localhost:16379 |

> **登录入口**：后台（PC）用 `POST /user/pc/auth/login`（用户名 + 密码），移动端 / C 端用 `POST /user/app/auth/login`（手机号 + 密码）。登录响应体拿到 `token` 字段后，后续所有受保护请求都在 Header 里带上 `ping-token: Bearer <token>`，网关会据此校验身份与接口权限。两套账号、token 与注解完全隔离，详见 [ping-user/README.md](ping-user/README.md#22-pc-登录域与-app-登录域是两套-token)。

  ```bash
  # 示例：用 curl 登录并拿到 token（后台 admin / 123456）
  TOKEN=$(curl -sS -X POST http://localhost:9999/user/pc/auth/login \
      -H 'Content-Type: application/json' \
      -d '{"username":"admin","password":"123456"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
  echo "token = $TOKEN"

  # 带 ping-token 头访问受保护接口
  curl -sS http://localhost:9999/user/pc/sys/user/list -H "ping-token: Bearer $TOKEN"
  ```

```bash
./start.sh --skip-build   # 跳过构建，直接用已有 target/*.jar 与 ping-web/dist 启动
./start.sh logs           # 查看全部日志（可跟服务名：./start.sh logs gateway）
./start.sh status         # 查看容器状态
./start.sh restart        # 重启全部容器
./start.sh down           # 停止并移除容器（保留数据卷）
./start.sh clean          # 停止并删除数据卷（下次启动会重新初始化数据库）
```

- `.env`（可选）：集中设置环境（`APP_ENV`）、宿主机端口、MySQL 密码和 Nacos 鉴权等启动参数，由 `start.sh` 与 Docker Compose 自动读取；不创建时全部使用默认值。
- 端口默认避开了 MySQL/Redis/Nacos 常用端口，避免与本机服务冲突；需要调整时复制 `.env.example` 为 `.env` 后修改（若本机 `npm run dev` / 本地网关已占用 5173、9999，请先停止或改端口）。
- 编排文件：根目录 `docker-compose.yml`；前端镜像由 `ping-web/Dockerfile` + `nginx.conf` 提供静态资源，并把 `/api` 转发到网关 `/user/pc`。
- 服务启动顺序：`mysql/redis/nacos` → `user-service/order-service` → `gateway` → `web`，全部基于健康检查。

### Windows（PowerShell）

子命令与 `start.sh` 完全一致，不需要 Git Bash / WSL：

```powershell
.\start.ps1              # 构建后端/前端并启动
.\start.ps1 --skip-build
.\start.ps1 logs gateway
.\start.ps1 down
```

- 构建走 `mvnw.cmd` 与 `npm.cmd`：未安装 Maven 时自动回退到仓库自带的 Wrapper；
- 若提示「禁止运行脚本」，改用 `start.cmd`（内部带 `-ExecutionPolicy Bypass`），或执行 `powershell -ExecutionPolicy Bypass -File .\start.ps1`；
- 仍需 JDK 21、Node.js 20.19+ / 22.13+ 和运行中的 Docker Desktop（Linux 容器）。

## 构建

```bash
# 后端：在仓库根目录一次性构建全部模块
mvn clean install -DskipTests

# 只启动某个服务（先执行上面的 install；不要加 -am，否则会在聚合模块上执行 run 而报“找不到主类”）
mvn -pl ping-user/user-service spring-boot:run
mvn -pl ping-gateway/gateway-service spring-boot:run
mvn -pl examples/ping-order/order-service spring-boot:run

# 配置命名空间默认跟随 APP_ENV（即 dev）；如需使用 public 命名空间：
# NACOS_CONFIG_NAMESPACE="" mvn -pl ping-user/user-service spring-boot:run

# 前端
cd ping-web
npm install
npm run dev
```

> **未安装 Maven 时**把命令里的 `mvn` 换成 `./mvnw`（Windows 用 `mvnw.cmd`）：版本由 `.mvn/wrapper/maven-wrapper.properties` 锁定为 3.9.14，首次运行自动下载（需联网），之后用法与 `mvn` 一致。`./start.sh` 已内置该回退。
>
> **版本号只有一个来源**：根 `pom.xml` 的 `<revision>`。子模块用 `<parent><version>${revision}</version>` 引用，
> Docker 镜像 tag 由 `start.sh` 从同一处读取（`./start.sh version` 可查看），发布 tag 也应与之一致。
> 改版本时只改这一行；`flatten-maven-plugin` 会在 `install` / `deploy` 时把 `${revision}` 解析成具体版本号。

## 测试

```bash
# 单元测试 + 集成测试（集成测试用 Testcontainers 拉 MySQL 容器，需要 Docker）
mvn verify

# 只跑单元测试：不依赖 Nacos / MySQL / Redis
mvn test

# 只跑某个模块
mvn test -pl ping-common/ping-core

# 只跑某组集成测试
mvn verify -pl ping-user/user-service -Dit.test=UserSchemaMigrationIT

# 完整 Spring 上下文启动测试（需先启动 Nacos / MySQL / Redis）
PING_IT=true mvn test -pl ping-user/user-service -am

# 端到端鉴权用例（需整套服务已启动：./start.sh 之后执行）
./e2e-auth.sh
```

测试分四层：

| 层次 | 命名 | 由谁执行 | 依赖 |
| --- | --- | --- | --- |
| 单元测试 | `*Test.java` | surefire，`mvn test` / `mvn verify` | 无外部依赖 |
| 集成测试 | `*IT.java` | failsafe，仅 `mvn verify` | Docker（Testcontainers），无 Docker 时自动跳过 |
| 上下文启动测试 | `*ApplicationTests.java` | surefire，需 `PING_IT=true` | Nacos / MySQL / Redis，默认跳过 |
| 端到端鉴权 | `e2e-auth.sh` | CI 任务 `E2E / auth chain`，本地手动执行 | 已启动的整套服务（Nacos / MySQL / Redis / 网关） |

单元测试覆盖权限匹配、数据权限 SQL 改写、登录失败锁定、网关路径规范化与白名单、订单状态机和订单越权校验，以及 user-pc 的权限 / 用户 / 角色 / 部门 / 数据范围五个 service 层（Mockito 隔离 Mapper，不启动 Spring 上下文）。

集成测试（`UserSchemaMigrationIT`、`OrderSchemaMigrationIT`）用真实的 `mysql:8.0` 容器执行 Flyway 迁移，校验：

- 空库能建出全部表，种子数据行数与 V1 一致；
- 重复启动不会重复执行迁移，也不会重复插入数据；
- 存量库（有表、无迁移历史）先打 `baseline 0` 再补执行 V1，旧表保留；
- 两个服务共用 `ping` 库时，各自只写自己的 `flyway_schema_history_*`，互不干扰。

修改迁移脚本后请执行 `mvn verify`；加了新的表或种子数据，需要同步更新对应的 `*SchemaMigrationIT` 期望值。

`e2e-auth.sh` 走的是真实链路（网关 → Dubbo → user-service → MySQL），校验单元测试无法覆盖的装配问题：

- 无 token / 伪造 token 一律 401；
- `admin` / `tom` 能登录并拿到 `ping-token` 头的 token；
- 网关的接口权限规则（来自 `sys_api_resource`，经 Dubbo 加载）生效：有权限码 200、缺权限码 403；
- 未登记的接口返回 403（fail-closed），新增接口忘记登记会立刻暴露；
- 同一个用户列表接口，两个数据范围不同的账号可见条数与部门集合不同（数据权限生效）；
- 退出登录后旧 token 立即 401，且不影响其他账号的会话。

新增接口后请把权限码登记进 `sys_api_resource`，必要时在 `e2e-auth.sh` 里补一条断言。前面几步与容器状态无关，脚本会先等链路真正就绪（默认 180s，可用 `E2E_READY_TIMEOUT` 调整）。

## 代码规范

后端代码遵循[《阿里巴巴 Java 开发手册》](https://github.com/alibaba/p3c)。仓库通过 `ali-p3c` Maven Profile 接入官方 `p3c-pmd` 规则集，不改变默认构建：

```bash
# 只生成报告：各模块 target/pmd.xml
mvn -Pali-p3c pmd:pmd

# 违规即失败（CI 门禁使用）
# 前面加 compile：单独执行 pmd:check 不经过编译阶段，多模块依赖会去本地仓库找而解析失败
mvn -Pali-p3c compile pmd:check
```

当前全部模块为 0 违规；新增代码请在提交前执行上述检查。

前端使用 ESLint（flat config，见 `ping-web/eslint.config.js`），启用 `eslint-plugin-vue` 的 `flat/essential` 与 `@eslint/js` 推荐规则，只拦未定义变量、漏 `:key`、直接改 props 这类会引发运行时问题的写法，不检查排版：

```bash
cd ping-web
npm run lint      # CI 门禁
npm run lint:fix  # 自动修复可修复项
```

## CI

`.github/workflows/ci.yml`（GitHub Actions）在 push / PR 到 `master`、`develop` 时执行，任一任务失败即阻断合并：

| 任务 | 命令 | 说明 |
| --- | --- | --- |
| 后端 | `mvn -B verify` | 编译、单元测试、集成测试（Testcontainers 拉 MySQL 容器）、打包，并生成 JaCoCo 覆盖率 |
| 端到端鉴权 | `docker compose up` + `./e2e-auth.sh` | 拉起真实 Nacos / MySQL / Redis / 服务 / 网关，验证登录 → 网关鉴权 → 服务端鉴权 → 数据权限 |
| 代码规范 | `mvn -B -Pali-p3c compile pmd:check` | 阿里巴巴 Java 开发手册（p3c-pmd），有违规即失败 |
| 前端 | `npm ci && npm run lint && npm run build` | 按锁文件安装，ESLint 检查后构建 `ping-web` |
| 依赖扫描 | Trivy `fs` | HIGH/CRITICAL 且可修复的漏洞阻断构建 |

Surefire / Failsafe 测试结果和 `target/site/jacoco` 覆盖率报告作为构建产物上传，失败用例会显示在 PR 的检查中。
GitHub Actions 的 `ubuntu-latest` 自带 Docker，`*IT.java` 会真实执行；本地没有 Docker 时这些用例自动跳过，不影响 `mvn verify` 通过。
端到端鉴权任务失败时会打印各容器日志的尾部，便于定位是配置、路由还是数据问题。
`PING_IT` 上下文启动测试依赖 Nacos/MySQL/Redis 一整套基础设施，不在 CI 中执行；需要时在本地基础设施启动后手动运行。

> 仓库托管在 Gitee 时 GitHub Actions 不会触发：可把根目录的 `ci.sh` 配到 Gitee Go 的 `build@maven` 步骤，或在其他 CI 中直接执行该脚本。

本地复现 CI：

```bash
./ci.sh                    # 依次执行后端、代码规范、前端、依赖扫描
SKIP_TRIVY=1 ./ci.sh       # 本机未安装 Trivy 时跳过扫描
E2E=1 ./ci.sh              # 追加端到端鉴权（先 ./start.sh 把服务跑起来）
```

> 漏洞扫描有误报时，把确认不可利用的条目写进 `.trivyignore` 并注明原因和到期时间。

## 本地端口

| 服务 | 端口 |
| --- | --- |
| ping-gateway | 9999（管理端口 8081） |
| ping-user / user-service | 8099 |
| examples/ping-order / order-service | 8100（管理端口 8102） |
| ping-web（Vite） | 5173，`/api` 代理到网关 `http://localhost:9999` |

## 配置说明（Nacos 配置中心）

各服务的 `src/main/resources/application.yml` 只保留**启动引导配置**（服务名、Nacos 地址、配置导入）。运行配置存放在 Nacos 的 MySQL 存储中，由 `init-sql/nacos-mysql.sql` 在首次启动时初始化：

```
init-sql/
└── nacos-mysql.sql                        # Nacos 库 nacos_config：表结构 + 配置数据
```

业务库 `ping` 不在 `init-sql/` 中，而是由各服务启动时执行 Flyway 迁移创建（见下文“数据库迁移”）。

`nacos_config` 库中的配置：

| dataId | 内容 |
| --- | --- |
| `application-{dev,test,prod}.yml` | 公共配置：Redis、Sa-Token、Dubbo registry、Actuator、优雅停机 |
| `gateway-service-{dev,test,prod}.yml` | 网关：端口、路由、Sa-Token 白名单/鉴权规则 |
| `user-service-{dev,test,prod}.yml` | 用户服务：端口、数据源、MyBatis-Plus、Dubbo provider、登录安全策略 |
| `order-service-{dev,test,prod}.yml` | 订单服务：端口、Dubbo provider、Actuator |

- dataId = 配置文件名，group = `DEFAULT_GROUP`，应用读取默认命名空间下的配置。
- Nacos 使用 MySQL 存储（库 `nacos_config`）。`nacos-mysql.sql` 挂载到 MySQL 容器的 `/docker-entrypoint-initdb.d/`，**首次启动（空数据卷）时自动导入**，随后才启动 Nacos。
- SQL 中同时保留了本地 Nacos 的 `public` / `dev` / `test` / `prod` 命名空间配置副本；Docker 编排中的应用读取的是默认命名空间，这些副本可用于本地 Nacos 环境。
- 启动时通过 `spring.config.import` 加载 `application-${APP_ENV}.yml` 与 `${spring.application.name}-${APP_ENV}.yml`，缺失会直接启动失败（避免半套配置运行）。
- `APP_ENV` 决定环境（默认 `dev`），同时作为服务发现命名空间（`dev`/`test`/`prod`），Dubbo 使用 `dubbo-${env}`。
- dev/test/prod 目前内容一致，后续在 Nacos 控制台按环境修改即可。

### 修改配置

配置不再保存在仓库的 yml 文件里，修改流程如下：

1. 打开 Nacos 控制台（http://localhost:18080），在对应 dataId 上修改并发布。
2. 重新导出 `nacos_config`，让下次全新启动使用最新配置：

   ```bash
   docker exec ping-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --databases nacos_config \
     --single-transaction --no-tablespaces --set-gtid-purged=OFF \
     --default-character-set=utf8mb4 --skip-add-locks' > init-sql/nacos-mysql.sql
   ```

   > 已有的 MySQL 数据卷不会重复执行初始化脚本；更新 SQL 后需 `./start.sh clean` 重建，或手动导入到 `nacos_config` 库。

### 切换环境

```bash
# Docker
APP_ENV=test ./start.sh

# 本地单服务
APP_ENV=test mvn -pl ping-gateway/gateway-service spring-boot:run
```

> 本仓库为纯净版：当前不包含 Helm Chart（Kubernetes 部署见「后续规划」）；CI 配置见 `.github/workflows/ci.yml`，也可用根目录 `./ci.sh` 在任意平台执行，各服务目录保留自己的 `Dockerfile` 与启动脚本。
> Maven 版本统一在根 `pom.xml` 的 `<properties>` 和 `<dependencyManagement>` 中管理，子模块不写版本号；工程自身的版本只写在根 POM 的 `<revision>`。

## 接口文档（Swagger UI）

启动后访问 **http://localhost:9999/docs**，右上角可切换「用户服务 / 订单服务」两套接口。

- **入口在网关**：业务服务只输出 OpenAPI JSON（`/v3/api-docs`），UI 由网关统一提供，服务端口不对宿主机暴露。
- **新增接口自动收录**：springdoc 从 Controller 方法签名和 DTO 推导参数/响应结构，无需手写注解；需要中文分组时在 Controller 上加 `@Tag(name = "用户管理")`。
- **调试登录态**：本项目鉴权头是 `ping-token`（值为 `Bearer <token>`）。先调 `/user/pc/auth/login`（后台）或 `/user/app/auth/login`（移动端）拿到 token，再点右上角 **Authorize** 填入，之后所有请求都会带上。
- **基地址**：接口的 `servers` 固定为 `${PUBLIC_BASE_URL:http://localhost:9999}`，避免显示容器内网 IP。改了网关端口或用域名/反向代理访问时，通过 `PUBLIC_BASE_URL` 覆盖。
- **生产默认关闭**：`user-service-prod.yml`、`order-service-prod.yml`、`gateway-service-prod.yml` 中 `springdoc.*.enabled=false`，且网关不添加文档路由与放行规则。不要在 prod 打开——它会把完整接口面暴露出去。
- **本地 IDE 调试**：服务只输出 JSON，可直接看 `http://localhost:8099/v3/api-docs`；需要 UI 就把该服务配置里的 `springdoc.swagger-ui.enabled` 临时改成 `true`。

## 数据库迁移（Flyway）

业务库 `ping` 的表结构与种子数据由各服务启动时执行的 Flyway 迁移维护，不再依赖一份全量 SQL 初始化脚本：

```
ping-user/user-service/src/main/resources/db/migration/V1__init_user_schema.sql
examples/ping-order/order-service/src/main/resources/db/migration/V1__init_order_schema.sql
```

约定：

- **V1 是幂等基线**：用 `CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE` 编写，空库全新安装与已有库升级都会执行。
- **每个服务一张历史表**：`spring.flyway.table` 分别为 `flyway_schema_history_user` / `flyway_schema_history_order`。多个服务共用一个 `ping` 库时，各自的迁移互不干扰。
- **`baseline-on-migrate: true` + `baseline-version: 0`**：已有数据的库以 0 为基线，随后照常执行 V1，避免“库非空就跳过建表”。
- **已执行过的迁移文件只读**：需要改表结构时新增 `V2__xxx.sql`，不要修改 V1。
- **禁止 clean**：`clean-disabled: true`，防止误删数据。
- **gateway-service 不参与迁移**：它只依赖 Redis 与 Dubbo，没有 JDBC 数据源。

新增表或字段时，在对应服务的 `db/migration` 下追加版本文件，重启服务即可自动执行。若某次迁移失败（MySQL DDL 不支持回滚），历史表中会留下 `success=0` 的记录，需手动修复后 `DELETE FROM flyway_schema_history_xxx WHERE success=0`（等价于 `flyway repair`）再重启。

## 后续规划

> 以下为发展方向，不设具体时间表，优先级按 issue 反馈调整；已发布版本的变更见 [CHANGELOG.md](CHANGELOG.md)。

| 方向 | 说明 |
| --- | --- |
| 多租户 | 引入 `tenant_id` 与租户级数据隔离，覆盖权限、数据范围与会话；当前是单库 + 部门数据范围 |
| CI/CD | 依赖更新自动化（Dependabot，Maven / npm / GitHub Actions 分组）、覆盖率门禁（JaCoCo `check`）；tag 触发镜像构建 → Helm Chart 发布 → Kubernetes 部署（命名空间、健康探针、配置外置） |
| App 域对齐 | `user-app` 单元测试与 e2e 断言，并补齐登录失败锁定 / 验证码 / 登录日志 |
| 迁移运维 | Flyway `dryRun` 与回滚说明，降低生产变更风险 |
| 错误码与复用 | 统一错误码（替换 `RuntimeException` 文案）、`ApiCoverageChecker` 抽成通用 Starter |
| 前端与移动端 | 前端 TypeScript / 组件测试；uni-app x 移动端示例（登录 + 我的） |

## 参与贡献

| 文档 | 说明 |
| --- | --- |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 分支模型、提交规范、测试要求、数据库变更约定、发布流程 |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | 行为准则 |
| [SECURITY.md](SECURITY.md) | 漏洞报告渠道、响应时间，以及**部署时必须修改的默认值** |
| [CHANGELOG.md](CHANGELOG.md) | 版本更新记录 |

提交前请执行 `./ci.sh`（或 `mvn verify` + `mvn -Pali-p3c compile pmd:check`）。
单个贡献流程摘要：从 `develop` 切分支 → 提交（Conventional Commits）→ PR 关联 issue → CI 全绿后合并。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。
