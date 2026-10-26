# 参与贡献

感谢你愿意改进 ping-permission。本文说明分支、提交、验证与发布流程，目标是让改动**可评审、可回溯、可回滚**。

参与本项目即表示你同意遵守 [行为准则](CODE_OF_CONDUCT.md)。

## 可以贡献什么

- 缺陷修复、文档纠错（欢迎直接提 PR，小改动不必先开 issue）；
- 新功能：**先开 issue 说明场景和方案**，避免方向不一致导致返工；
- 业务示例（`examples/` 下新增一个可复制的接入模板）；
- 依赖升级、构建与 CI 优化。

## 环境准备

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 编译与运行 |
| Maven | 3.9+ | 仓库根目录统一构建 |
| Node.js | 22 | 仅 `ping-web` 需要 |
| Docker | — | 一键启动基础设施；集成测试（Testcontainers）也依赖它 |

```bash
mvn clean install -DskipTests     # 首次构建，安装各模块到本地仓库
docker compose up -d              # 需要联调时启动 MySQL / Redis / Nacos
```

## 分支与提交

| 分支 | 用途 |
| --- | --- |
| `master` | 发布分支，只接受来自 `develop` 的合并 |
| `develop` | 开发主分支，日常 PR 的目标分支 |
| `feature/<简述>`、`fix/<简述>` | 特性与修复分支，从 `develop` 切出 |

提交信息使用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：

```
<type>(<scope>): <简述>

<可选正文：为什么这么改，而不是改了什么>

<可选 footer：Closes #12 / BREAKING CHANGE: ...>
```

常用 `type`：`feat`、`fix`、`docs`、`refactor`、`test`、`build`、`ci`、`chore`。
`scope` 用模块名，例如 `user-service`、`gateway`、`order`、`web`。

一个提交只做一件事；格式化、重命名等无行为变更的改动单独提交，方便评审时跳过。

## 提交前自检

至少执行以下检查，全部通过再提 PR：

```bash
./ci.sh                    # 后端 + 代码规范 + 前端 + 依赖扫描，与 CI 完全一致
SKIP_TRIVY=1 ./ci.sh       # 本机没装 Trivy 时

# 或者分步执行
mvn verify                 # 单元测试 + 集成测试（需要 Docker）+ 打包 + 覆盖率
mvn -Pali-p3c compile pmd:check   # 阿里巴巴 Java 开发手册，0 违规才通过（需先 compile）
cd ping-web && npm ci && npm run lint && npm run build

# 改动涉及登录、权限、网关路由或数据权限时，再跑一遍真实链路
./start.sh --skip-build    # 或 ./start.sh
./e2e-auth.sh
```

### 测试怎么写

| 场景 | 放哪里 | 命名 |
| --- | --- | --- |
| 纯逻辑、无外部依赖 | 各模块 `src/test/java` | `*Test.java`（surefire） |
| 需要真实 MySQL | 启动模块 `src/test/java` | `*IT.java`（failsafe，仅 `mvn verify`） |
| 需要完整 Nacos/MySQL/Redis | 启动模块 | `*ApplicationTests.java` + `PING_IT=true` |
| 跨进程鉴权链路（网关 → Dubbo → 服务） | 根目录 `e2e-auth.sh` | 追加到现有用例，或新增独立脚本 |

集成测试用 Testcontainers 启动容器，不依赖本机或 Docker Compose 的服务；
没有 Docker 的环境会自动跳过，所以不要在 `*IT.java` 里连接 `localhost:3306`。

`e2e-auth.sh` 相反，它必须打在已启动的整套服务上，因此不做成 JUnit：鉴权链路的关键一环
（网关规则经 Dubbo 从 `sys_api_resource` 加载、Spring Cloud Gateway 路由）只能在真实进程间验证。
CI 中由独立任务 `E2E / auth chain` 执行；新增受保护接口时，记得在 `sys_api_resource` 登记权限码，
并考虑在脚本里补一条「有权限 200 / 无权限 403」的断言。

### 代码规范

- 后端遵守[《阿里巴巴 Java 开发手册》](https://github.com/alibaba/p3c)，`mvn -Pali-p3c compile pmd:check` 为门禁；
- 模块边界由 `ModuleBoundaryTest` 守护：`user-pc` 与 `user-app` 互不依赖，`user-common` / `user-auth` 不依赖具体领域模块；
- 新增公共能力优先进 `ping-common` 的 Starter，不要复制到多个业务模块；
- 注释解释「为什么」，不复述代码；类与公开方法写 Javadoc；
- 前端保持既有的 Vue 3 + Element Plus 风格，不引入第二套 UI 库；ESLint 门禁为 `cd ping-web && npm run lint`（配置见 `ping-web/eslint.config.js`，只拦运行时错误与 Vue 常见坑，不做排版检查）。

## 数据库变更

**已发布的迁移文件是只读的。** 需要改结构或数据时新增版本文件，不要修改 `V1__*.sql`：

```
ping-user/user-service/src/main/resources/db/migration/V2__add_xxx.sql
examples/ping-order/order-service/src/main/resources/db/migration/V2__add_yyy.sql
```

注意：

- 文件名是 `V2__说明.sql`（**两个下划线**）；版本号递增，不要跳号后补；
- `ping` 库由多个服务共用，**每个服务只建自己的表**，历史表也各自独立（`flyway_schema_history_user` / `_order`）；
- `V1` 是幂等基线（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`），新脚本请自行保证可重复执行或严格一次性；
- 新增表或种子数据后，同步更新对应 `*SchemaMigrationIT` 的期望表名与行数，否则 `mvn verify` 会失败；
- 接口权限码要写进 `sys_api_resource`，否则网关会 fail-closed 拒绝请求。

## 接口与文档

- Controller 加 `@Tag`，方法加 `@Operation`，保持 `http://localhost:9999/docs` 可读；
- 新增/修改接口要同步更新对应模块的 `README.md` 与 `README.en.md`；
- 对外行为变更（接口、配置项、默认值）必须写入 [CHANGELOG.md](CHANGELOG.md) 的 `[Unreleased]`。

## 提 PR

1. Fork 或直接在仓库内切分支（分支名见上文）；
2. 关联 issue：PR 描述里写 `Closes #<编号>`；
3. 描述清楚**改了什么、为什么改、怎么验证**；涉及界面改动的贴截图；
4. 确认 CI 全绿；Reviewer 提出修改后在同一分支追加提交，不要强推覆盖评审记录；
5. 合并方式用 squash 或 rebase，保持 `develop` 历史线性。

请勿提交：`.env`、`target/`、`node_modules/`、本地 IDE 配置（`.idea/` 中个人文件）、任何真实密钥。

## 发布

1. 确认 `develop` 通过 `./ci.sh`，把 `[Unreleased]` 的内容整理进新的 `[x.y.z] - YYYY-MM-DD` 小节；
2. 如需变更版本号，**只改根 `pom.xml` 的 `<revision>`**：子模块用 `<parent><version>${revision}</version>` 自动跟随，镜像 tag 由 `start.sh` 从同一处读取；
3. 合并到 `master`；
4. 打 tag 并推送（tag 必须与 `<revision>` 一致，可用 `./start.sh version` 确认）：

   ```bash
   ./start.sh version                       # 输出当前版本，例如 0.1.0
   git checkout master && git pull
   git tag -a v0.1.0 -m "ping-permission v0.1.0"
   git push origin v0.1.0
   ```

5. 在 Gitee / GitHub 的 Releases 页面按 tag 发布，正文直接取自 CHANGELOG 对应小节。

> 版本号只有一个来源：根 `pom.xml` 的 `<revision>`。发布时先把它改成目标版本（如 `x.y.z`），再合并、打同名 tag；
> 发布后的日常开发建议提升到下一个 `x.y.(z+1)-SNAPSHOT`，避免同一个版本号对应多份代码。
> `mvn clean install` 会通过 `flatten-maven-plugin` 把 `${revision}` 解析成具体版本，下游拿到的 POM 里不会残留 `${revision}`；
> 未发布到 Maven 中央仓库前，tag 只标记代码状态。
