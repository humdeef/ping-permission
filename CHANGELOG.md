# 更新日志

本项目的所有重要变更都记录在这里。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

> **版本与 tag**：仓库尚未发布到 Maven 中央仓库。版本号只写在根 `pom.xml` 的 `<revision>`，
> 子模块与 Docker 镜像 tag 都跟随它；发布 tag 与 `<revision>` 保持一致（当前 `v0.1.0` ↔ `0.1.0`）。
> 发布流程见 [CONTRIBUTING.md](CONTRIBUTING.md#发布)。

## [Unreleased]

### 计划中

- 多租户（`tenant_id`）扩展点：当前是单库 + 部门数据范围，未内置租户隔离；
- CI/CD：依赖更新自动化（Dependabot）、JaCoCo `check` 覆盖率门禁，以及 tag 触发镜像构建 → Helm Chart 发布 → Kubernetes 部署（命名空间、健康探针、配置外置）；
- App 域对齐：`user-app` 单元测试与 e2e 断言，补齐登录失败锁定 / 验证码 / 登录日志；
- 迁移脚本的 `dryRun` / 回滚说明补充到各服务 README；
- 统一错误码、`ApiCoverageChecker` 抽成通用 Starter；前端 TypeScript / 组件测试与 uni-app x 移动端示例。

### 修复

- 种子账号 `lisa` 的密码未与其他账号统一：新增 `V2__reset_lisa_password.sql`，仅当密码仍是 V1 的初始哈希时重置为 `123456`，不会覆盖已修改过的密码；`UserSchemaMigrationIT` 同步校验迁移数量与重置结果。
- 普通用户角色（`role_id=2`）缺少查询类权限（`system:role`、`system:role:list`、`system:permission:list`），lisa 登录后点击「角色管理」「权限管理」会返回 403：新增 `V3__grant_user_query_permissions.sql` 补齐这三项查询权限；`sys_role_permission` 种子行数由 96 增至 99；`UserSchemaMigrationIT` 同步校验。

## [0.1.0] - 2026-10-26

测试体系、CI 门禁与依赖基线的阶段性版本：补齐端到端鉴权与 service 层单测，引入前端 ESLint，升级到 Spring Boot 4.1，并增强管理后台导航。

### 新增

- **端到端鉴权用例**：根目录新增 `e2e-auth.sh`，CI 新增 `E2E / auth chain` 任务。用真实的 Nacos / MySQL / Redis / 服务 / 网关验证「登录签发 token → 网关接口权限 → 服务端鉴权 → 数据权限 → 退出登录」，覆盖单元测试无法验证的装配环节：未登记接口 fail-closed、缺权限码返回 403、不同账号的数据范围差异、退出后 token 立即失效。
- `./start.sh version`：打印当前版本（取自根 POM 的 `<revision>`），供脚本复用。
- **user-pc service 层单元测试**：新增 `PermissionServiceImplTest`、`UserServiceImplTest`、`RoleServiceImplTest`、`DeptServiceImplTest`、`DataScopeServiceImplTest` 共 115 个用例。用 Mockito 隔离 Mapper，覆盖内置管理员保护、密码策略、角色变更踢出会话、部门 ancestors 重算与环检测、5 种数据范围解析等核心规则；`mvn test` 即可执行，无需 MySQL / Redis / Nacos。
- **前端 ESLint 门禁**：`ping-web` 接入 ESLint 10 flat config（`eslint.config.js`），启用 `eslint-plugin-vue` 的 `flat/essential` 与 `@eslint/js` 推荐规则，只拦未定义变量、漏 `:key`、直接改 props 等运行时坑，不做排版检查。新增 `npm run lint` / `npm run lint:fix`，并接入 `ci.sh` 第 3 步与 CI 的 `Frontend / lint + build` 任务。
- **管理后台导航增强**：登录页补充版权信息；主框架新增面包屑（首页 / 目录 / 当前页）与多页签——点击左侧菜单自动追加可关闭页签，关闭当前页签后回到相邻页签，带查询参数的页面切回时恢复最后一次地址。

### 变更

- **版本单一来源**：根 `pom.xml` 新增 `<revision>`（本版为 `0.1.0`），子模块与自身版本统一写 `${revision}`；引入 `flatten-maven-plugin`（`resolveCiFriendliesOnly`），安装/发布时解析为具体版本号。发布新版本只需改这一处。
- **镜像 tag 与版本同源**：`docker-compose.yml` 的镜像 tag 改为 `${IMAGE_TAG}`，由 `start.sh` 取 `<revision>` 注入；直接执行 `docker compose` 时为 `local`。
- `ci.sh` 增加可选的第 5 步端到端鉴权（`E2E=1 ./ci.sh`，需先启动整套服务）。
- **依赖升级**：Spring Boot `4.0.9` → `4.1.2`，Spring Cloud `2025.1.0` → `2025.1.4`（2025.1.2 起支持 Boot 4.1.x）。Spring Cloud Alibaba 保持 `2025.1.0.0`——它是 2025.1 train 当前唯一版本，官方矩阵仍标注 Boot 4.0.x，已在本工程用全量测试与 e2e 验证。
- **打包命令调整**：`start.sh` 与 CI 的 `-DskipTests` 改为 `-Dmaven.test.skip=true`。Boot 4.1 起 `-DskipTests` 不再跳过测试的 AOT 处理。

## [0.0.2] - 2026-10-26

首个打 tag 的版本：把原先分散的 5 个仓库合并为单一工程，并补齐迁移、文档、测试与社区文件。

### 新增

- **工程整合**：Maven 聚合工程统一构建 `ping-common`、`ping-gateway`、`ping-user`、`examples/ping-order`，`ping-web` 前端独立用 npm 构建。
- **权限底座**：RBAC、PC/App 双登录域、接口级鉴权（网关注册 `sys_api_resource`，未命中即 fail-closed）、部门数据范围、登录/操作审计、在线会话与强制下线。
- **动态菜单**：菜单与前端路由由后端 `sys_permission` 权限树生成，前端按 `menu-{id}` 动态注册路由，不再单独维护菜单表。
- **公共组件**：`ping-core` 与 `ping-web`、`ping-dubbo`、`ping-log`、`ping-id`、`ping-data-scope` 五个 Spring Boot Starter。
- **示例业务**：`examples/ping-order` 演示订单状态机、审计日志、乐观锁、Dubbo 权限兜底与 App/PC 双端接口，可直接复制为新业务模块。
- **一键启动**：`docker-compose.yml` + `start.sh`，编排 MySQL、Redis、Nacos、网关与两个服务，含健康检查与启动顺序。
- **数据库迁移（Flyway）**：`user-service`、`order-service` 各自维护 `db/migration` 与独立历史表（`flyway_schema_history_user` / `flyway_schema_history_order`），空库与存量库都能自动收敛；`init-sql/` 只保留 Nacos 配置。
- **在线接口文档**：各服务输出 OpenAPI JSON（`/v3/api-docs`），网关聚合 Swagger UI 于 `http://localhost:9999/docs`；生产环境默认关闭。
- **集成测试**：引入 Testcontainers，`UserSchemaMigrationIT` / `OrderSchemaMigrationIT` 用真实 `mysql:8.0` 容器校验建表、种子数据、重复执行幂等、存量库 baseline 与共用库隔离。
- **社区文件**：`CONTRIBUTING.md`、`SECURITY.md`、`CODE_OF_CONDUCT.md`、Issue/PR 模板与本更新日志。
- **CI**：GitHub Actions 依次执行 `mvn verify`、p3c-pmd、前端构建与 Trivy 扫描；仓库根目录的 `ci.sh` 提供等价门禁，可接入 Gitee Go 等其他 CI。

### 变更

- `mvn verify` 现在会执行 `*IT.java`（failsafe，需要 Docker；没有 Docker 时自动跳过），`mvn test` 仍只跑单元测试。
- `init-sql/init-user.sql` 已删除，业务表结构与种子数据的唯一来源是各服务的 Flyway 迁移脚本。

### 修复

- 登录与菜单接口报 `Unknown column 'component'`：`sys_permission` 缺少前端组件名字段，已在迁移脚本中补齐。

[Unreleased]: https://gitee.com/humdeef/ping-permission/compare/v0.0.2...develop
[0.0.2]: https://gitee.com/humdeef/ping-permission/releases/tag/v0.0.2
