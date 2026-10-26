# ping-user：用户与权限服务

`ping-user` 是整个权限底座的“人”和“权限”都在的地方：后台用户、C 端用户、登录、RBAC、部门与数据范围、接口鉴权规则、审计日志、在线会话，全部由它提供。

它最终只打成**一个可启动服务** `user-service`，但对内拆成了 6 个子模块。拆分的目的是让“契约”“认证原语”“后台域”“App 域”“启动装配”各自独立，改动时能一眼看出影响范围。

## 1. 子模块一览

| 模块 | 职责 | 依赖 | 产物 |
| --- | --- | --- | --- |
| [`user-common`](user-common/README.md) | 用户域公共常量、枚举（数据范围、会话 key、`UserType`） | `ping-core` | 普通 jar |
| [`user-auth`](user-auth/README.md) | 认证原语：密码策略、登录失败锁定、算术验证码 | `user-common`、Redis | 普通 jar |
| [`user-client`](user-client/README.md) | **对外 Dubbo 契约**：`PermissionDubboClient`、`UserDubboClient`、`AppUserDubboClient` 及 DTO | 仅 Lombok | 普通 jar（供 gateway / order 引用） |
| [`user-pc`](user-pc/README.md) | 后台用户域：登录、RBAC、部门、数据范围、审计、接口资源/白名单、在线用户 | `user-auth`、`user-client`、`ping-web-starter`、MyBatis-Plus、Dubbo | 普通 jar |
| [`user-app`](user-app/README.md) | C 端用户域：`app_user` 注册/登录、App 登录态、App 用户 Dubbo 实现 | `user-auth`、`user-client`、`ping-web-starter`、MyBatis-Plus、Dubbo | 普通 jar |
| [`user-service`](user-service/README.md) | **启动装配模块**：`main` 方法、`@MapperScan`、线程池、MyBatis-Plus、Nacos 引导配置 | 上面全部模块 + 各 starter | 可执行 Spring Boot jar |

依赖方向（箭头表示“依赖”）：

```
                    ┌──────────────────────────────┐
                    │        user-service          │  启动 / 装配
                    └───────┬───────────┬──────────┘
                            │           │
              ┌─────────────┘           └──────────────┐
              ▼                                        ▼
        ┌───────────┐                            ┌───────────┐
        │  user-pc  │                            │ user-app  │
        └──┬──┬──┬──┘                            └──┬──┬──┬──┘
           │  │  │                                  │  │  │
           │  │  └──────────────┐      ┌────────────┘  │  │
           │  │                 ▼      ▼               │  │
           │  │            ┌──────────────┐            │  │
           │  │            │  user-auth   │            │  │
           │  │            └──────┬───────┘            │  │
           │  │                   ▼                    │  │
           │  │            ┌──────────────┐            │  │
           │  └───────────▶│ user-common  │◀───────────┘  │
           │               └──────┬───────┘               │
           │                      ▼                       │
           │               ┌──────────────┐               │
           └──────────────▶│  ping-core   │◀──────────────┘
                           └──────────────┘
                   （user-pc / user-app 同时依赖 user-client）
```

几条必须守住的规则：

1. **`user-client` 不能被业务模块反向依赖。** 它只放接口和 DTO，供 gateway、order 等外部服务引用；一旦让它依赖 `user-pc`，就会形成循环，并且外部服务会被迫拖进 MyBatis、Spring Security 等实现依赖。
2. **`user-common` 只放“多个模块都要用且没有业务行为”的东西。** 不要往里面塞 Service、Mapper 或某个 Controller 专用的 DTO。
3. **`user-pc` 和 `user-app` 互不依赖。** 两套用户表、两套登录域、两套 Controller；需要复用认证原语时都依赖 `user-auth`。
4. **`user-service` 不写业务逻辑。** 它只负责把需要的东西装配起来；新增业务代码应落在 `user-pc` / `user-app`。

## 2. 最容易混淆的三个边界

### 2.1 `user-client` 是“对外契约”，`user-pc` / `user-app` 是“对内实现”

`user-client` 里的接口是**给别的服务调用 user-service 用的**，不是 user-service 自己用的。

```java
// user-client：只有接口和 DTO，没有实现
public interface UserDubboClient {
    String getUserNameById(Long userId);
}
```

```java
// user-pc：实现该契约，并通过 @DubboService 暴露
@DubboService
public class UserDubboServiceImpl implements UserDubboClient {
    @Override
    public String getUserNameById(Long userId) { ... }
}
```

```java
// order-service：只依赖 user-client，不依赖 user-pc
@DubboReference(check = false)
private UserDubboClient userDubboClient;
```

所以：

- 想给外部服务新增一个 RPC 能力 → 改 **`user-client` 的接口** + `user-pc` / `user-app` 的实现。
- 想新增一个 HTTP 接口 → 只改 `user-pc` / `user-app`，不需要动 `user-client`。
- gateway 依赖 `user-client` 只是为了拿到 `PermissionDubboClient` 和 `UserInfoDTO`，它不会加载 user-service 的数据库代码。

### 2.2 PC 登录域与 App 登录域是两套 token

| | 后台（PC） | C 端（App） |
| --- | --- | --- |
| 用户表 | `sys_user` | `app_user` + `app_user_identity` |
| 登录工具 | `StpUtil` | `StpAppUtil`（`new StpLogic("app")`） |
| 登录接口 | `POST /user/pc/auth/login` | `POST /user/app/auth/login` |
| 注解 | `@LoginOnlyApi` | `@AppLoginOnlyApi` |
| 会话里的权限 | `ROLES` / `PERMS` 两个 key | 通常只放 `APP_PHONE`，无后台权限 |
| 典型接口 | `/user/pc/users/**` | `/user/app/auth/me`、`/order/app/**` |

两套 token 在 Redis 中按 `loginType` 隔离。**拿 PC token 调 App 接口会 401，反之亦然**，这不是 bug，而是设计目标。

一个常见的错误写法：

```java
// 错误：App 接口里用了后台登录工具
@AppLoginOnlyApi
@GetMapping("/me")
public ApiResponse<?> me() {
    Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId())); // 这里永远拿不到 App 用户
}
```

正确写法：

```java
@AppLoginOnlyApi
@GetMapping("/me")
public ApiResponse<?> me() {
    Long userId = Long.valueOf(String.valueOf(StpAppUtil.getLoginId()));
}
```

如果只想写“当前登录人”而不关心来自哪个域，用统一上下文：

```java
Long userId = UserContextHolder.getUserId();   // 后台/ App 都会被填充
boolean isApp = UserContextHolder.isAppUser(); // 依据 OperatorType.APP（App token），不依赖角色字符串
OperatorType type = UserContextHolder.getOperatorType(); // PC / APP / SYSTEM / ANONYMOUS
String userName = UserContextHolder.getUserName(); // 登录时写入 token session 的 NICKNAME 快照
```

`NICKNAME` 在登录时写入 token session（后台优先昵称，缺失时用用户名；App 优先昵称，缺失时用手机号）。订单等业务表用它做操作人名称快照；旧 token 没有该值时，业务层可按用户域回查一次。

### 2.3 `user-pc` 和 `user-app` 不是“PC 端和 App 端的 Controller 目录”

它们各自拥有**独立的表、独立的业务服务、独立的 Dubbo 实现**：

- `user-pc` 管理 `sys_user` 以及 RBAC、部门、审计等后台能力；它本身的接口前缀是 `/user/pc/**`。
- `user-app` 管理 `app_user`，只做 C 端注册/登录/当前用户；接口前缀是 `/user/app/**`。

## 3. 一次请求是怎么走的

### 3.1 后台登录

```
前端 POST /user/pc/auth/login
  → gateway：@PublicApi 命中，直接放行（不校验 token）
  → user-service：AuthController.login
      ├─ 账号锁定检查（Redis）
      ├─ 验证码校验（可选）
      ├─ BCrypt 校验密码
      ├─ StpUtil.login(userId)
      ├─ PermissionCacheService.cachePerms(userId)
      │     └─ 把 ROLES / PERMS 写入 token session（共享 Redis）
      └─ 返回 ping-token
```

之后的请求：

```
GET /user/pc/users
  → gateway：StpUtil.checkLogin()
      → 查 token session 的 ROLES / PERMS（SaStpInterfaceImpl）
      → 按 sys_api_resource 中的规则校验 system:user:list
  → user-service：SaTokenAuthFilter 再桥接 Spring Security
      → @PreAuthorize("@authz.has('system:user:list')") 二次校验
```

网关负责“能不能进来”，服务内的 `@PreAuthorize` 负责“绕过网关直连时也不能乱来”。

### 3.2 App 登录

```
App POST /user/app/auth/login
  → gateway：@PublicApi 放行
  → user-service：AppAuthServiceImpl 校验手机号/密码
      → StpAppUtil.login(userId)
      → token session 写入 APP_PHONE
  → 返回 App token
```

之后的 App 请求：

```
GET /order/app/queryOrderByUser
  → gateway：@AppLoginOnlyApi 命中
      → StpAppUtil.checkLogin()（只校验 App 登录，不查后台权限）
  → order-service：AppTokenAuthFilter 注入 ROLE_APP + UserContext
```

### 3.3 跨服务 Dubbo 调用

```
order-service 收到请求
  → SaTokenAuthFilter / AppTokenAuthFilter 写入 UserContextHolder（含 OperatorType、昵称）
  → userDubboClient.getUserNameById(1L)
      → UserContextConsumerFilter 把 userId/roles/perms/operator-type/operator-name 放进 RPC attachment
      → user-service 的 UserContextProviderFilter 恢复 UserContextHolder
      → UserDubboServiceImpl 执行业务
```

附件名：`ping-user-id`、`ping-user-roles`、`ping-user-perms`、`ping-operator-type`、`ping-operator-name`。`ping-operator-type` 是 `OperatorType` 的数值编码（1 后台、2 App、3 系统、4 匿名），用来避免只靠权限集合误判调用方身份。

注意：**Dubbo 提供方线程没有 HTTP 请求上下文，不能调用 `StpUtil.getLoginId()`**。需要当前用户时，调用方应显式传参，或从 `UserContextHolder` 读取（由 filter 透传）。

## 4. 快速开始

```bash
# 构建全部后端模块
mvn clean install -DskipTests

# 只启动 user-service（不要加 -am，否则会在聚合模块上执行 run 而报“找不到主类”）
mvn -pl ping-user/user-service spring-boot:run

# 配置命名空间默认跟随 APP_ENV（dev）；如需 public：
# NACOS_CONFIG_NAMESPACE="" mvn -pl ping-user/user-service spring-boot:run

# 或者用 Docker 一键起整套
./start.sh
```

> 本地启动失败最常见的原因：Nacos 里没有 `application-dev.yml` / `user-service-dev.yml`。
> 默认读取 `dev` 命名空间（跟随 `APP_ENV`）；如果配置发布在 public，设置 `NACOS_CONFIG_NAMESPACE=""`。
> 配置读不到时，日志会先出现 `config[... ] is empty`，随后报 `Failed to configure a DataSource`。

默认端口：`user-service` 8099，网关 9999，前端 5173。

验证后台登录：

```bash
curl -s -X POST http://localhost:9999/user/pc/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
```

返回中的 `data.token` 就是后续请求要带的 token：

```bash
curl -s http://localhost:9999/user/pc/users \
  -H 'ping-token: Bearer <token>'
```

> `sa-token.token-name` 配置为 `ping-token`，`token-prefix` 为 `Bearer`，所以请求头是 `ping-token: Bearer xxx`。如果改过 Nacos 公共配置，以实际配置为准。

验证 App 注册/登录：

```bash
curl -s -X POST http://localhost:9999/user/app/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000001","password":"Abc12345!","nickname":"小P"}'

curl -s -X POST http://localhost:9999/user/app/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000001","password":"Abc12345!"}'
```

## 5. 常见开发任务

### 5.1 新增一个后台接口

以“查询某个用户的详情”为例：

```java
@RestController
@RequestMapping("/user/pc/users")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/{id}/profile")
    @PreAuthorize("@authz.has('system:user:list')")   // 服务内二次校验
    public ApiResponse<UserDetailResponse> profile(@PathVariable Long id) {
        ...
    }
}
```

还需要让网关知道这个接口需要什么权限。二选一：

**方式 A：登记到 `sys_api_resource`（推荐，权限可在后台页面维护）**

```sql
INSERT INTO sys_api_resource(permission_code, path, method, status, description, sort)
VALUES ('system:user:list', '/user/pc/users/*/profile', 'GET', 1, '用户详情', 10);
```

**方式 B：用注解声明策略（无需改表）**

```java
@LoginOnlyApi               // 仅要求后台登录，不校验具体权限
@GetMapping("/{id}/profile")
public ApiResponse<UserDetailResponse> profile(@PathVariable Long id) { ... }
```

> 如果这个接口是“任何登录用户都能看”，用 `@LoginOnlyApi`；如果是“必须拥有某个权限码”，用 `@PreAuthorize` + `sys_api_resource`。**不要**为了省事给敏感接口加 `@LoginOnlyApi`，它会让网关跳过权限码校验。

生效时间：

- 注解策略：服务启动后写入 Redis，网关每 10 秒聚合一次，最多约 10~130 秒生效（Redis TTL 120 秒，每 30 秒续写）。
- `sys_api_resource` 规则：网关每 60 秒刷新一次，最多约 60 秒生效。
- 白名单 `sys_api_whitelist`：每 10 秒刷新。

### 5.2 新增一个 App 接口

```java
@RestController
@RequestMapping("/user/app/profile")
@AppLoginOnlyApi                 // 类级注解，类下所有方法都按 App 登录域校验
public class AppProfileController {

    @GetMapping("/me")
    public ApiResponse<AppUserResponse> me() {
        Long userId = Long.valueOf(String.valueOf(StpAppUtil.getLoginId()));
        ...
    }
}
```

不需要在 `sys_api_resource` 里登记，也不需要改网关配置：启动扫描后会自动注册到 Redis，网关按 `@AppLoginOnlyApi` 走 App 登录域。

### 5.3 新增一个跨服务 Dubbo 契约

1. 在 `user-client` 中新增接口和 DTO（DTO 必须 `implements Serializable`）。

```java
public interface UserDubboClient {
    String getUserNameById(Long userId);

    // 新增
    List<String> listUsernamesByIds(List<Long> userIds);
}
```

2. 在 `user-pc`（或 `user-app`）中实现并用 `@DubboService` 暴露：

```java
@DubboService
public class UserDubboServiceImpl implements UserDubboClient {
    @Override
    public List<String> listUsernamesByIds(List<Long> userIds) { ... }
}
```

3. 调用方只依赖 `user-client`：

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-client</artifactId>
</dependency>
```

```java
@DubboReference(check = false, timeout = 3000)
private UserDubboClient userDubboClient;
```

### 5.4 新增一个权限码

1. 在 `sys_permission` 新增一条记录（父级、编码、名称、排序等）。
2. 在 `sys_api_resource` 把“方法 + 路径”映射到该权限码。
3. 在角色管理页把权限分配给角色。
4. 用户重新登录（或等待权限变更触发的踢出后重登），让 `ROLES` / `PERMS` 写入新的 token session。

> 权限缓存在 token session 里；直接改数据库不会自动刷新已登录用户的权限。`PermissionCacheServiceImpl.invalidateCache()` 会踢出受影响用户，用户重登后拿到新权限。

### 5.5 新增一个后台页面（菜单由后端权限树驱动）

前端不再维护硬编码菜单表。登录后 `/user/pc/auth/me` 会返回当前用户可见的菜单树，前端据此动态注册路由并渲染侧边栏。

1. 在 `ping-web/src/views` 新增页面组件，例如 `Projects.vue`。
2. 在权限管理页新增一条 **菜单** 类型权限，并按需挂在某个目录下：

   | 字段 | 示例 | 说明 |
   | --- | --- | --- |
   | 权限编码 | `system:project` | 同时作为前端路由的权限码 |
   | 路由地址 | `/projects` | 必须是 `/` 开头 |
   | 组件名 | `Projects` | 对应 `views/Projects.vue`，不含 `.vue` |
   | 图标 | `Folder` | Element Plus 图标名，可留空 |
   | 菜单显示 | 开 | 关闭后仍注册路由，但侧边栏不显示（适合详情页） |

3. 在角色管理页把该菜单权限分配给角色。
4. 用户重新登录（或权限变更被踢出后重登），菜单与路由自动生效。

> 目录（type=1）只负责分组，通常不填路由/组件；菜单（type=2）必须填路由地址和组件名；按钮（type=3）不参与菜单树。前端组件名找不到时只会在控制台告警并跳过该菜单，不影响其他页面。

### 5.6 新增一个数据权限过滤的查询

数据权限分两层：

- **接口权限**：`@PreAuthorize` / 网关规则，回答「能不能调这个接口」；
- **数据权限**：`@DataScope` + MyBatis 拦截器，回答「能看这个接口里的哪些行」。

规则仍然来自 `sys_role.data_scope` / `sys_role_dept`，由 `DataScopeService` 统一解析；业务代码不参与范围计算。

适用范围是**后台组织内的业务表**（如 `sys_user`、后台项目/工单表）。纯 App 域的表（如 `ping_order`）不套用 `@DataScope`：它们按 `user_id` 过滤，不要为了使用数据权限硬加 `dept_id`，否则会让二开者误以为所有 App 表都必须带部门字段。

1. 确认表上有部门列（通常 `dept_id`）和归属人列（`create_by_id` / `user_id`）。只有归属人、没有部门列的表可以不传 `deptColumn`，只按归属人过滤。
2. 在 Mapper 上加注解（以后台项目表 `ping_project` 为例）：

```java
@DataScope(table = "ping_project", deptColumn = "dept_id",
        ownerColumn = "create_by_id",
        ownerTypeColumn = "create_by_type", ownerTypeValue = 1)
public interface BizProjectMapper extends BaseMapper<BizProject> {

    @Select("SELECT * FROM ping_project WHERE id = #{id}")
    BizProject selectScopedById(@Param("id") Long id);
}
```

3. 详情 / 修改 / 删除统一用带注解的查询取数：

```java
private BizProject requireProject(Long id) {
    BizProject project = projectMapper.selectScopedById(id); // 越权数据 = 查询不到
    if (project == null) {
        throw new IllegalArgumentException("项目不存在");
    }
    return project;
}
```

4. 跨服务时不要重复实现角色/部门解析：`user-client` 提供 `DataScopeDubboClient`，`user-pc` 中的 `DataScopeDubboServiceImpl` 负责返回 `DataScopeDTO`。业务服务实现自己的 `DataScopeProvider` 即可。

几条约定：

- `includeSelf = true`（默认）表示部门范围之外始终允许看本人创建的数据；「仅本人」角色会退化成只按归属人过滤。
- 后台用户和 App 用户 ID 可能数值相同。后台表如果确实会被 App 写入（如共用工单表），务必用 `ownerTypeColumn + ownerTypeValue` 限定；纯 App 表直接按 `user_id` 过滤，不要接入 `@DataScope`。
- 数据权限同时作用于 SELECT 和 UPDATE / DELETE（MyBatis-Plus 拦截器会改写写语句）；写路径仍建议先用带注解的查询取数，既能让越权表现为「不存在」，也能处理乐观锁冲突。用户域的 `SysUserMapper` 还额外重声明了 `updateById` / `update` / `deleteById` / `delete` 并标注 `@DataScope`，作为 Controller 校验之外的第二道防线。
- 分页 count SQL 会带上同样的条件，列表总数不会泄露范围外数据。
- 完整说明见 [ping-data-scope-spring-boot-starter](../ping-common/ping-data-scope-spring-boot-starter/README.md)。

## 6. 配置

`user-service` 的启动引导配置只有 `application.yml`（服务名、Nacos 地址、配置导入），运行配置在 Nacos 的 `user-service-${APP_ENV}.yml` 和 `application-${APP_ENV}.yml` 中。

登录安全相关配置前缀为 `ping.security`：

```yaml
ping:
  security:
    registration-enabled: false   # 是否开放后台自助注册
    default-role-code: guest      # 自助注册用户的默认角色
    login:
      max-attempts: 5             # 连续失败次数上限
      lock-minutes: 15            # 锁定时长（分钟）
    password:
      min-length: 8
      max-length: 64
      require-categories: 3       # 大写/小写/数字/特殊字符至少满足几类
    captcha:
      enabled: false              # 开启后登录必须携带验证码
      expire-minutes: 2
  audit:
    retention-days: 180           # 登录/操作日志保留天数
```

`PingSecurityProperties` 由 `user-service` 的 `SecurityConfig` 通过 `@EnableConfigurationProperties` 绑定。`user-auth` 本身不是 Spring Boot 自动配置模块，单独引用它时要自己开启配置绑定。

## 7. 常见坑

### 7.1 网关返回 403「接口未配置权限规则，拒绝访问」

这是 **fail-closed**：登录了，但该接口既没有注解策略，也没有登记到 `sys_api_resource`，网关默认拒绝。

```text
GET /user/pc/new-feature → 403 接口未配置权限规则，拒绝访问
```

处理方式：

- 加 `@PublicApi` / `@LoginOnlyApi` / `@AppLoginOnlyApi`；或
- 在 `sys_api_resource` 登记 `path + method + permission_code`；或
- 在 `sys_api_whitelist` 登记为免登录白名单（谨慎使用）。

启动日志里搜索 `[ApiCoverage]` 可以看到未登记策略的接口清单。

### 7.2 明明加了注解，网关还是拦截

先确认：

1. 注解加在 **Controller 方法或类** 上，而不是 Service 上；
2. 服务已启动且能连 Redis（注解扫描依赖 Redis，写不进去网关就看不到）；
3. 等待一个刷新周期（网关默认 10 秒聚合一次）；
4. 路径匹配到了实际请求：注解扫描会把 `{id}` 规范化为 `*`，但不会自动补 `/user/pc` 前缀；
5. 请求经过的是网关（9999），不是直连 `user-service`（8099）。

可以手工检查 Redis：

```bash
redis-cli -p 16379 --scan --pattern 'ping:gateway:api:*'
redis-cli -p 16379 smembers ping:gateway:api:login:user-service
```

### 7.3 `hasAuthority` 和 `@authz.has` 的区别

```java
@PreAuthorize("hasAuthority('system:user:list')")     // 只认权限码，管理员不会自动通过
@PreAuthorize("@authz.has('system:user:list')")       // 管理员（admin 角色或 *:*:*）直接通过
```

用户域内部统一推荐 `@authz.has(...)`，避免每个接口都写 `hasAnyRole('admin') or ...`。order-service 的 `OrderPcController` 用的是 `hasAuthority(...)`，属于示例代码；新接口建议统一用 `@authz`。

### 7.4 登录成功但网关说没权限

最常见的原因是 **token session 里没有 `ROLES` / `PERMS`**：

- 旧 token 是在权限缓存上线前签发的；
- 用户权限变更后缓存被清理，但客户端还在用旧 token；
- 登录时 `cachePerms` 抛异常被吞掉（看日志 `[PermCache]`）。

处理：重新登录。网关的 `SaStpInterfaceImpl` 只读 token session，不会在 WebFlux 事件循环里阻塞查库或走 Dubbo。

### 7.5 强制改密状态

管理员重置密码后，`sys_user.force_change_password = 1`。登录响应里 `forceChangePassword = true`，并且 token session 写入 `FORCE_PWD_CHANGE=1`。网关只放行：

```
/user/pc/auth/password
/user/pc/auth/logout
/user/pc/auth/me
```

其他接口一律 403「请先修改初始密码」。改密成功后 `StpUtil.logout(userId)` 会让当前 token 失效，需要重新登录。

### 7.6 `user-app` 的 ID 与 `sys_user` 的 ID 不是一套

`app_user.id` 由雪花算法生成（`DistributedIdGenerator`），`sys_user.id` 是数据库自增。两者可能数值相同，但**完全无关**。

- 查后台用户 → `UserDubboClient` / `sys_user`
- 查 App 用户 → `AppUserDubboClient` / `app_user`

不要用同一个 `userId` 在两个域之间互查。App 响应里 `userId` 返回字符串，也是为了避免 JS 对大整数精度丢失。

### 7.7 在 Dubbo 提供方里调用 `StpUtil`

```java
// 错误：Dubbo provider 线程没有 HTTP 会话
@DubboService
public class XxxImpl implements XxxClient {
    public void doSomething() {
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId())); // 可能抛异常
    }
}
```

正确方式：

- 调用方显式传 `userId`；或
- 依赖 `UserContextProviderFilter` 恢复的 `UserContextHolder`：

```java
Long callerId = UserContextHolder.getUserId();
boolean canEdit = UserContextHolder.hasPermission("system:user:edit");
```

前提是调用链路上有 `SaTokenAuthFilter` / `AppTokenAuthFilter` 写入上下文，并且异步线程使用了 TTL 包装（`user-service`、`order-service` 已提供 `TaskDecorator`）。

### 7.8 异步线程里拿不到用户上下文

`UserContextHolder` 基于 `TransmittableThreadLocal`，普通子线程可继承，但线程池必须包装：

```java
executor.submit(TtlRunnable.get(() -> {
    Long userId = UserContextHolder.getUserId(); // 需要 TTL 包装
}));
```

`user-service` 的 `AsyncExecutorConfig` 已给线程池加了 `TaskDecorator`；如果你自己 `new ThreadPoolExecutor(...)`，需要自己包 `TtlExecutors.getTtlExecutorService(...)` 或 `TtlRunnable.get(...)`。

### 7.9 Nacos 配置缺失导致启动失败

`application.yml` 只导入：

```
nacos:application-${APP_ENV:dev}.yml
nacos:${spring.application.name}-${APP_ENV:dev}.yml
```

配置缺失会快速失败，而不是“半套配置”启动。默认命名空间下应存在 `application-dev.yml` 和 `user-service-dev.yml`。Docker 首次启动由 `init-sql/nacos-mysql.sql` 导入。

## 8. 子模块文档

- [user-common](user-common/README.md)
- [user-auth](user-auth/README.md)
- [user-client](user-client/README.md)
- [user-pc](user-pc/README.md)
- [user-app](user-app/README.md)
- [user-service](user-service/README.md)
