# user-pc：后台用户域

`user-pc` 是权限底座的核心业务模块，负责**后台用户（`sys_user`）**相关的一切：

- 后台登录/登出/改密/注册；
- RBAC：用户、角色、权限；
- 部门与数据范围；
- 接口资源（`sys_api_resource`）与接口白名单（`sys_api_whitelist`）；
- 登录日志、操作日志、在线用户；
- 对外 Dubbo 实现：`PermissionDubboClient`、`UserDubboClient`。

它依赖 `user-auth` 的认证原语、`user-client` 的契约、`ping-web-spring-boot-starter` 的安全能力，但**不负责启动**——`main` 方法在 `user-service`。

## 1. 包结构

```
com.ping.user.pc
├── annotation/        @Audit
├── audit/             AuditContext（记录审计详情）
├── config/            CurrentUser、DbPermissionProvider、WebMvcConfig、ApiCoverageChecker
├── controller/        /user/pc/** 的 REST 接口
├── entity/            sys_* 表实体
├── interceptor/       AuditInterceptor
├── job/               DataRetentionJob（日志保留清理）
├── mapper/            MyBatis-Plus Mapper + XML
├── model/             请求/响应 DTO
└── service/
    ├── impl/          业务实现、PermissionCacheServiceImpl、DataScopeServiceImpl
    └── impl/dubbo/    PermissionDubboServiceImpl、UserDubboServiceImpl
```

## 2. 接口一览

所有接口前缀 `/user/pc`，经网关访问（默认 `http://localhost:9999`）。

| Controller | 路径 | 说明 |
| --- | --- | --- |
| `AuthController` | `/user/pc/auth` | 登录、登出、改密、当前用户、验证码、注册 |
| `UserController` | `/user/pc/users` | 用户分页/详情/增删改/重置密码 |
| `RoleController` | `/user/pc/roles` | 角色分页/详情/增删改/分配权限 |
| `PermissionController` | `/user/pc/permissions` | 权限树/增删改 |
| `DeptController` | `/user/pc/depts` | 部门树/增删改 |
| `ApiResourceController` | `/user/pc/api-resources` | 接口权限规则维护 |
| `WhitelistController` | `/user/pc/whitelist` | 接口白名单维护 |
| `LoginLogController` | `/user/pc/login-logs` | 登录日志查询 |
| `OperationLogController` | `/user/pc/operation-logs` | 操作日志查询/导出 |
| `OnlineController` | `/user/pc/online` | 在线用户列表/强制下线 |

`AuthController` 中的策略：

```java
@PublicApi  @GetMapping("/config")     // 登录页公开配置
@PublicApi  @GetMapping("/captcha")    // 验证码
@PublicApi  @PostMapping("/login")     // 登录
@PublicApi  @PostMapping("/register")  // 注册（受 registration-enabled 控制）
@LoginOnlyApi @PostMapping("/logout")
@LoginOnlyApi @PostMapping("/password")
@LoginOnlyApi @GetMapping("/me")
@LoginOnlyApi @GetMapping("/current")
```

## 3. 登录流程

```java
// AuthController.login 的简化流程
if (loginAttemptService.isLocked(username)) return 429;
if (captchaEnabled && !captchaService.verify(...)) return 400;

SysUser user = authService.findByUsernameOrNull(username);
if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
    int remaining = loginAttemptService.recordFailure(username);
    return 401; // 用户不存在与密码错误统一提示，避免账号枚举
}
if (user.getStatus() == 0) return 403;

StpUtil.login(user.getId());
permissionCacheService.cachePerms(user.getId());  // ROLES / PERMS 写入 token session
loginAttemptService.clear(username);
StpUtil.getTokenSession().set("FORCE_PWD_CHANGE", force ? "1" : "0");
StpUtil.getTokenSession().set(UserConstants.SESSION_IP, clientIp);
StpUtil.getTokenSession().set(UserConstants.SESSION_USER_AGENT, ua);
```

关键点：

- **token session 是网关鉴权的数据源。** 登录时必须把 `ROLES` / `PERMS` 写进去，否则网关的 `StpUtil.checkPermission` 永远查不到权限。
- **强制改密标记也写在 token session。** 网关据此只放行改密/退出/当前用户三个接口。
- **登录成功/失败都会写登录日志和审计日志。**
- 密码错误提示包含“还可尝试 N 次”，但用户不存在与密码错误使用同一个提示，避免暴露账号是否存在。

## 4. RBAC 与权限缓存

### 4.1 数据模型

```
sys_user ──< sys_user_role >── sys_role ──< sys_role_permission >── sys_permission
                                  │
                                  └──< sys_role_dept >── sys_dept
```

- `sys_permission`：菜单/按钮权限定义，`code` 如 `system:user:list`；
- `sys_api_resource`：接口映射，`path + method → permission_code`，与权限定义解耦；
- `sys_api_whitelist`：免登录接口；
- `sys_role_dept`：角色自定义数据范围；
- `sys_dept.ancestors`：部门树路径，用于“本部门及下级”。

### 4.2 权限缓存

`PermissionCacheServiceImpl` 把角色/权限编码写入当前 token 的 Sa-Token session：

```java
StpUtil.getTokenSession().set("ROLES", "admin,ops");
StpUtil.getTokenSession().set("PERMS", "system:user:list,system:role:list");
```

- 登录时写入；
- `/auth/me` 会用数据库最新数据刷新；
- 权限关系变更时，相关 Service 调用 `invalidateCache(userIds)`，把这些用户踢下线，重登后拿到新缓存。

为什么不在网关实时查库/Dubbo？因为网关是 WebFlux，鉴权发生在 Netty 事件循环上，同步 RPC 会拖垮吞吐。缓存放共享 Redis，网关只做 O(1) 读取。

### 4.3 方法级权限

```java
@PreAuthorize("@authz.has('system:user:list')")
```

`@authz` 是 `ping-web-spring-boot-starter` 提供的 `AuthorizationService`：

- `has(permission)`：管理员（`admin` 角色或 `*:*:*`）直接通过，否则比对权限码；
- `hasRole(roleCode)`：比对 `ROLE_xxx`；
- `isAdmin()`：判断管理员。

`SaTokenAuthFilter` 已把 token session 中的角色/权限桥接成 Spring Security authorities：

```
ROLES: admin          → ROLE_admin
PERMS: system:user:list → system:user:list
```

## 5. 数据范围

`DataScopeServiceImpl.resolve(userId)` 根据用户所有角色的 `data_scope` 取并集：

| 值 | 范围 |
| --- | --- |
| 1 | 全部 |
| 2 | 本部门 |
| 3 | 本部门及下级 |
| 4 | 仅本人 |
| 5 | 自定义部门（`sys_role_dept`） |

规则：

- 任一角色是 `admin` 或 `SCOPE_ALL` → 全部；
- 多个角色取部门并集；
- 没有命中任何部门范围 → 退化为“仅本人”；
`canAccessUser(scope, target)` 判断能否访问目标用户；`visibleDeptIds(userId)` 用于部门页可见范围。

**应用方式**：不再由 Service 把 `Scope` 传进每个查询，而是：

1. `user-service` 的 `DataScopeConfig` 把 `DataScopeService` 包装成 `DataScopeProvider` 注册到容器；
2. Mapper 上标 `@DataScope`；
3. MyBatis-Plus 的 `DataPermissionInterceptor` 自动把范围拼进 SQL。

```java
@DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
IPage<SysUser> selectScopedPage(Page<SysUser> page,
                                @Param(Constants.WRAPPER) Wrapper<SysUser> wrapper);
```

调用侧只关心业务条件：

```java
IPage<SysUser> p = userService.listScoped(page, size, username, nickname, deptId, includeChildren);
```

其他后台业务服务通过 `user-client` 的 `DataScopeDubboClient` 获取同一份规则，不再重复依赖 `sys_role` / `sys_dept`。纯 App 域的接口按 `user_id` 过滤，不需要接入数据范围。

**注意**：数据范围是“行级过滤”，和接口权限（“能不能调这个接口”）是两件事。一个用户可能有 `system:user:list` 权限，但只能看到本部门用户。

## 6. 审计

### 6.1 注解 + 拦截器

```java
@Audit(action = "新增角色", targetType = "角色")
@PostMapping
public ApiResponse<SysRole> save(@RequestBody @Valid RoleRequest req) { ... }
```

`AuditInterceptor` 在请求完成后：

- 读取方法上的 `@Audit`；
- 从路径变量 `{id}` 取目标 ID；
- 从 `AuditContext` 取业务补充的详情；
- 写 `sys_operation_log`。

它**不记录请求参数**，避免密码等敏感信息落库。需要记录变更详情时，在 Service 里调用 `AuditContext` 写入摘要。

### 6.2 日志清理

`DataRetentionJob` 按 `ping.audit.retention-days`（默认 180 天）清理登录日志和操作日志。

## 7. 对外 Dubbo 实现

| 实现类 | 契约 | 说明 |
| --- | --- | --- |
| `PermissionDubboServiceImpl` | `PermissionDubboClient` | 查用户角色/权限；提供网关鉴权规则和白名单 |
| `UserDubboServiceImpl` | `UserDubboClient` | 按 ID 查后台用户名/昵称 |

`PermissionDubboServiceImpl.listApiPermissionRules()` 读取的是 `sys_api_resource`（`status=1`），不是 `sys_permission.path`：

```java
// 规则来源：sys_api_resource
new ApiPermissionRuleDTO(
    resource.getPermissionCode(),
    resource.getMethod() == null ? "*" : resource.getMethod(),
    resource.getPath());
```

`UserDubboServiceImpl` 的注释明确写着：**Dubbo 提供方线程没有 HTTP 上下文，不能调用 `StpUtil`**。需要当前用户时用 `UserContextHolder` 或调用方显式传参。

## 8. 启动与配置

`user-pc` 不是可启动模块，由 `user-service` 装配：

```java
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan({"com.ping.user.pc.mapper", "com.ping.user.app.mapper"})
public class UserServiceApplication { ... }
```

它依赖的关键配置（在 Nacos `user-service-${APP_ENV}.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://.../${MYSQL_DATABASE:ping}
mybatis-plus:
  mapper-locations: classpath*:mapper/*.xml
ping:
  security: ...
  audit:
    retention-days: 180
dubbo:
  scan:
    base-packages: com.ping.user.pc.service,com.ping.user.app.service
```

> `dubbo.scan.base-packages` 必须覆盖 `com.ping.user.pc.service`，否则 `@DubboService` 不会被扫描，网关调用会报“无提供者”。

## 9. 常见坑

### 9.1 只加 `@PreAuthorize`，没登记网关规则

`@PreAuthorize` 只在请求到达 user-service 后生效。网关如果先返回 403「接口未配置权限规则」，服务内根本没机会执行。

两处都要配：

1. 网关规则：`sys_api_resource`（或注解策略）；
2. 服务内兜底：`@PreAuthorize`。

### 9.2 给敏感接口加 `@LoginOnlyApi`

网关在权限规则之前检查注解：

```java
if (annotatedApiRegistry.isLoginOnly(method, path)) {
    return; // 任何后台登录用户都放行，不再校验权限码
}
```

所以 `@LoginOnlyApi` 只适合“登录即可”的接口（退出、改密、当前用户）。敏感接口应使用 `sys_api_resource` + `@PreAuthorize`。

### 9.3 用户权限改了，线上没生效

权限缓存在 token session。直接改 `sys_role_permission` 不会刷新已登录用户。需要：

- 调用 `PermissionCacheServiceImpl.invalidateCache(userIds)` 踢出用户；或
- 让用户重新登录；或
- 调用 `/user/pc/auth/me` 触发一次缓存刷新（只刷新当前用户）。

管理页面修改角色/权限时，相关 Service 通常已经调用了 `invalidateCache`；如果你绕过 Service 直接改库，就不会有踢出动作。

### 9.4 数据范围看起来“没生效”

按顺序排查：

1. **Mapper 是否标了 `@DataScope`。** 没标的查询（包括 `BaseMapper.selectPage` / `selectById`）不会被过滤；标了 `@IgnoreDataScope` 的会主动跳过。
2. **`table` 是否与 SQL 中的表名一致。** 条件只追加到匹配的表；join 时可用 `alias` 指定。
3. **拦截器顺序。** `DataPermissionInterceptor` 必须加在 `PaginationInnerInterceptor` 之前，否则 count SQL 不带条件。
4. **手工构建 `SqlSessionFactory` 时是否挂了插件。** 本项目在 `MyBatisPlusConfig` 里显式调用 `factory.setPlugins(mybatisPlusInterceptor(...))`，漏掉这一步注解完全不生效。
5. **Provider 是否返回了 `all`。** 管理员或 `SCOPE_ALL` 角色本来就不会追加条件；纯 App 域接口（如 `/order/app/**`）不接入 `@DataScope`，由业务层按 `user_id` 过滤。
6. **写操作是否受保护。** `SysUserMapper` 的 `updateById` / `update` / `deleteById` / `delete` 都重声明并标了 `@DataScope`，拦截器会把同一条件追加到 UPDATE / DELETE；数据范围之外的行影响 0 行。重声明时不能丢掉 `@Param(Constants.ENTITY)` / `@Param(Constants.WRAPPER)`，否则参数绑定会失败。`UserController.checkUserAccess` 仍然保留，用于让越权访问提前返回 403「无权访问其他用户」，而不是静默影响 0 行。

### 9.5 审计日志没有记录

- 方法上必须有 `@Audit`；
- `AuditInterceptor` 注册在 `/user/pc/**`，其他前缀不会触发；
- 请求必须经过 Spring MVC（直接调用 Service 不触发拦截器）；
- 日志写入失败会打日志，但不会影响主流程。

### 9.6 `ApiCoverageChecker` 报未登记接口

这是警告，不是启动失败。它扫描所有 Controller，找出既没有注解、也没有登记到 `sys_api_resource` / `sys_api_whitelist` 的接口。网关默认拒绝这些接口，所以上线前应处理：

```
[ApiCoverage] 发现 N 个未登记策略的接口（网关默认拒绝）...
```

### 9.7 部门树更新后数据范围不对

`sys_dept.ancestors` 是冗余路径字段。新增/移动部门时必须同步更新子部门的 `ancestors`，否则“本部门及下级”会算错。相关逻辑在 `DeptServiceImpl`，自定义 SQL 时要保持一致。

## 10. 依赖方式

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-pc</artifactId>
</dependency>
```

通常只有 `user-service` 需要直接依赖它。其他服务要调用用户能力时，应依赖 `user-client`，而不是把整个后台域拖进来。
