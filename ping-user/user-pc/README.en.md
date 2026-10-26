# user-pc: Backend User Domain

`user-pc` is the core business module of the permission foundation and is responsible for everything related to **backend users (`sys_user`)**:

- Backend login/logout/password change/registration;
- RBAC: users, roles, permissions;
- Departments and data scope;
- API resources (`sys_api_resource`) and API whitelist (`sys_api_whitelist`);
- Login logs, operation logs, online users;
- External Dubbo implementations: `PermissionDubboClient`, `UserDubboClient`.

It depends on the authentication primitives of `user-auth`, the contracts of `user-client`, and the security capabilities of `ping-web-spring-boot-starter`, but it **does not handle startup** — the `main` method is in `user-service`.

## 1. Package Structure

```
com.ping.user.pc
├── annotation/        @Audit
├── audit/             AuditContext (records audit details)
├── config/            CurrentUser, DbPermissionProvider, WebMvcConfig, ApiCoverageChecker
├── controller/        REST endpoints under /user/pc/**
├── entity/            sys_* table entities
├── interceptor/       AuditInterceptor
├── job/               DataRetentionJob (log retention cleanup)
├── mapper/            MyBatis-Plus Mapper + XML
├── model/             request/response DTOs
└── service/
    ├── impl/          business implementations, PermissionCacheServiceImpl, DataScopeServiceImpl
    └── impl/dubbo/    PermissionDubboServiceImpl, UserDubboServiceImpl
```

## 2. API Overview

All endpoints are prefixed with `/user/pc` and are accessed through the gateway (default `http://localhost:9999`).

| Controller | Path | Description |
| --- | --- | --- |
| `AuthController` | `/user/pc/auth` | Login, logout, password change, current user, captcha, registration |
| `UserController` | `/user/pc/users` | User pagination/detail/create-update-delete/reset password |
| `RoleController` | `/user/pc/roles` | Role pagination/detail/create-update-delete/assign permissions |
| `PermissionController` | `/user/pc/permissions` | Permission tree/create-update-delete |
| `DeptController` | `/user/pc/depts` | Department tree/create-update-delete |
| `ApiResourceController` | `/user/pc/api-resources` | Maintain API permission rules |
| `WhitelistController` | `/user/pc/whitelist` | Maintain API whitelist |
| `LoginLogController` | `/user/pc/login-logs` | Query login logs |
| `OperationLogController` | `/user/pc/operation-logs` | Query/export operation logs |
| `OnlineController` | `/user/pc/online` | Online user list/force logout |

Strategies in `AuthController`:

```java
@PublicApi  @GetMapping("/config")     // public configuration for the login page
@PublicApi  @GetMapping("/captcha")    // captcha
@PublicApi  @PostMapping("/login")     // login
@PublicApi  @PostMapping("/register")  // registration (controlled by registration-enabled)
@LoginOnlyApi @PostMapping("/logout")
@LoginOnlyApi @PostMapping("/password")
@LoginOnlyApi @GetMapping("/me")
@LoginOnlyApi @GetMapping("/current")
```

## 3. Login Flow

```java
// Simplified flow of AuthController.login
if (loginAttemptService.isLocked(username)) return 429;
if (captchaEnabled && !captchaService.verify(...)) return 400;

SysUser user = authService.findByUsernameOrNull(username);
if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
    int remaining = loginAttemptService.recordFailure(username);
    return 401; // Same message for unknown user and wrong password to prevent account enumeration
}
if (user.getStatus() == 0) return 403;

StpUtil.login(user.getId());
permissionCacheService.cachePerms(user.getId());  // write ROLES / PERMS to the token session
loginAttemptService.clear(username);
StpUtil.getTokenSession().set("FORCE_PWD_CHANGE", force ? "1" : "0");
StpUtil.getTokenSession().set(UserConstants.SESSION_IP, clientIp);
StpUtil.getTokenSession().set(UserConstants.SESSION_USER_AGENT, ua);
```

Key points:

- **The token session is the data source for gateway authorization.** On login, `ROLES` / `PERMS` must be written into it; otherwise the gateway's `StpUtil.checkPermission` will never find the permissions.
- **The forced password change flag is also written to the token session.** Based on it, the gateway only allows the password change, logout, and current user endpoints.
- **Both successful and failed logins write login logs and audit logs.**
- The wrong-password message includes "N attempts remaining", but an unknown user and a wrong password use the same message to avoid revealing whether an account exists.

## 4. RBAC and Permission Cache

### 4.1 Data Model

```
sys_user ──< sys_user_role >── sys_role ──< sys_role_permission >── sys_permission
                                  │
                                  └──< sys_role_dept >── sys_dept
```

- `sys_permission`: menu/button permission definitions, with `code` such as `system:user:list`;
- `sys_api_resource`: API mappings, `path + method → permission_code`, decoupled from permission definitions;
- `sys_api_whitelist`: endpoints exempt from login;
- `sys_role_dept`: role custom data scope;
- `sys_dept.ancestors`: department tree path, used for "own department and sub-departments".

### 4.2 Permission Cache

`PermissionCacheServiceImpl` writes role/permission codes into the Sa-Token session of the current token:

```java
StpUtil.getTokenSession().set("ROLES", "admin,ops");
StpUtil.getTokenSession().set("PERMS", "system:user:list,system:role:list");
```

- Written on login;
- `/auth/me` refreshes them with the latest data from the database;
- When permission relations change, the relevant Services call `invalidateCache(userIds)` to kick those users offline; after logging in again they receive the new cache.

Why not query the database/Dubbo in real time at the gateway? Because the gateway is WebFlux and authorization runs on Netty event loop threads; synchronous RPC would drag down throughput. The cache is stored in shared Redis, and the gateway only performs O(1) reads.

### 4.3 Method-Level Permissions

```java
@PreAuthorize("@authz.has('system:user:list')")
```

`@authz` is the `AuthorizationService` provided by `ping-web-spring-boot-starter`:

- `has(permission)`: administrators (the `admin` role or `*:*:*`) pass directly; otherwise the permission code is compared;
- `hasRole(roleCode)`: compares `ROLE_xxx`;
- `isAdmin()`: determines whether the user is an administrator.

`SaTokenAuthFilter` has already bridged the roles/permissions in the token session into Spring Security authorities:

```
ROLES: admin          → ROLE_admin
PERMS: system:user:list → system:user:list
```

## 5. Data Scope

`DataScopeServiceImpl.resolve(userId)` takes the union of the `data_scope` values of all the user's roles:

| Value | Scope |
| --- | --- |
| 1 | All |
| 2 | Own department |
| 3 | Own department and sub-departments |
| 4 | Self only |
| 5 | Custom departments (`sys_role_dept`) |

Rules:

- Any role that is `admin` or `SCOPE_ALL` → all;
- Multiple roles are unioned by department;
- If no department scope matches → falls back to "self only";

`canAccessUser(scope, target)` determines whether the target user is accessible; `visibleDeptIds(userId)` is used for the visible scope on the department page.

**How it is applied**: Services no longer pass `Scope` into every query. Instead:

1. `DataScopeConfig` in `user-service` wraps `DataScopeService` as `DataScopeProvider` and registers it with the container;
2. Mappers are annotated with `@DataScope`;
3. MyBatis-Plus's `DataPermissionInterceptor` automatically appends the scope to the SQL.

```java
@DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
IPage<SysUser> selectScopedPage(Page<SysUser> page,
                                @Param(Constants.WRAPPER) Wrapper<SysUser> wrapper);
```

Callers only care about business conditions:

```java
IPage<SysUser> p = userService.listScoped(page, size, username, nickname, deptId, includeChildren);
```

Other backend business services obtain the same rules through `DataScopeDubboClient` from `user-client` and no longer depend on `sys_role` / `sys_dept` themselves. Pure App-domain endpoints filter by `user_id` and do not need to integrate the data scope.

**Note**: Data scope is "row-level filtering", which is different from endpoint permissions ("whether this endpoint can be called"). A user may have the `system:user:list` permission but can only see users in their own department.

## 6. Audit

### 6.1 Annotation + Interceptor

```java
@Audit(action = "Create role", targetType = "Role")
@PostMapping
public ApiResponse<SysRole> save(@RequestBody @Valid RoleRequest req) { ... }
```

After the request completes, `AuditInterceptor`:

- reads the `@Audit` on the method;
- takes the target ID from the path variable `{id}`;
- takes details supplemented by the business logic from `AuditContext`;
- writes to `sys_operation_log`.

It **does not record request parameters**, to prevent sensitive information such as passwords from being persisted. When change details need to be recorded, call `AuditContext` in the Service to write a summary.

### 6.2 Log Cleanup

`DataRetentionJob` cleans up login logs and operation logs according to `ping.audit.retention-days` (default 180 days).

## 7. External Dubbo Implementations

| Implementation | Contract | Description |
| --- | --- | --- |
| `PermissionDubboServiceImpl` | `PermissionDubboClient` | Query user roles/permissions; provides gateway authorization rules and whitelist |
| `UserDubboServiceImpl` | `UserDubboClient` | Query backend username/nickname by ID |

`PermissionDubboServiceImpl.listApiPermissionRules()` reads `sys_api_resource` (`status=1`), not `sys_permission.path`:

```java
// Rule source: sys_api_resource
new ApiPermissionRuleDTO(
    resource.getPermissionCode(),
    resource.getMethod() == null ? "*" : resource.getMethod(),
    resource.getPath());
```

`UserDubboServiceImpl`'s comment states explicitly: **Dubbo provider threads have no HTTP context and must not call `StpUtil`**. When the current user is needed, use `UserContextHolder` or have the caller pass it explicitly.

## 8. Startup and Configuration

`user-pc` is not a runnable module; it is assembled by `user-service`:

```java
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan({"com.ping.user.pc.mapper", "com.ping.user.app.mapper"})
public class UserServiceApplication { ... }
```

The key configuration it depends on (in Nacos `user-service-${APP_ENV}.yml`):

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

> `dubbo.scan.base-packages` must cover `com.ping.user.pc.service`; otherwise `@DubboService` will not be scanned and gateway calls will report "no provider".

## 9. Pitfalls

### 9.1 Only adding `@PreAuthorize` without registering a gateway rule

`@PreAuthorize` only takes effect after the request reaches user-service. If the gateway first returns 403 "no permission rule configured for endpoint", the service never gets a chance to execute.

Both places must be configured:

1. Gateway rules: `sys_api_resource` (or annotation strategies);
2. In-service fallback: `@PreAuthorize`.

### 9.2 Adding `@LoginOnlyApi` to sensitive endpoints

The gateway checks the annotation before permission rules:

```java
if (annotatedApiRegistry.isLoginOnly(method, path)) {
    return; // allow any logged-in backend user, without checking permission codes
}
```

So `@LoginOnlyApi` is only suitable for endpoints where "being logged in is enough" (logout, password change, current user). Sensitive endpoints should use `sys_api_resource` + `@PreAuthorize`.

### 9.3 User permissions changed but did not take effect online

Permission caches live in the token session. Directly modifying `sys_role_permission` will not refresh logged-in users. You need to:

- call `PermissionCacheServiceImpl.invalidateCache(userIds)` to kick users out; or
- have users log in again; or
- call `/user/pc/auth/me` to trigger a cache refresh (refreshes only the current user).

When roles/permissions are modified from the admin page, the relevant Services usually already call `invalidateCache`; if you bypass the Services and modify the database directly, no kick-out action happens.

### 9.4 Data scope seems "not taking effect"

Troubleshoot in order:

1. **Whether the Mapper is annotated with `@DataScope`.** Queries without it (including `BaseMapper.selectPage` / `selectById`) are not filtered; those annotated with `@IgnoreDataScope` actively skip filtering.
2. **Whether `table` matches the table name in the SQL.** Conditions are only appended to matching tables; for joins, use `alias` to specify.
3. **Interceptor order.** `DataPermissionInterceptor` must be added before `PaginationInnerInterceptor`; otherwise the count SQL will not carry the conditions.
4. **Whether the plugin is attached when building `SqlSessionFactory` manually.** This project explicitly calls `factory.setPlugins(mybatisPlusInterceptor(...))` in `MyBatisPlusConfig`; if this step is missed, the annotations do not work at all.
5. **Whether the Provider returns `all`.** Administrators or `SCOPE_ALL` roles never get conditions appended; pure App-domain endpoints (such as `/order/app/**`) do not integrate `@DataScope` and are filtered by the business layer by `user_id`.
6. **Whether write operations are protected.** `SysUserMapper`'s `updateById` / `update` / `deleteById` / `delete` are all redeclared and annotated with `@DataScope`; the interceptor appends the same conditions to UPDATE / DELETE, and rows outside the data scope are affected 0 rows. When redeclaring, do not drop `@Param(Constants.ENTITY)` / `@Param(Constants.WRAPPER)`, otherwise parameter binding fails. `UserController.checkUserAccess` is still retained, so that unauthorized access returns 403 "no permission to access other users" early, rather than silently affecting 0 rows.

### 9.5 Audit logs not recorded

- The method must have `@Audit`;
- `AuditInterceptor` is registered on `/user/pc/**`; other prefixes will not trigger it;
- The request must go through Spring MVC (calling a Service directly does not trigger the interceptor);
- Log write failures are logged but do not affect the main flow.

### 9.6 `ApiCoverageChecker` reports unregistered endpoints

This is a warning, not a startup failure. It scans all Controllers and finds endpoints that have neither annotations nor registration in `sys_api_resource` / `sys_api_whitelist`. The gateway rejects these endpoints by default, so they should be handled before going live:

```
[ApiCoverage] 发现 N 个未登记策略的接口（网关默认拒绝）...
```

### 9.7 Data scope is wrong after updating the department tree

`sys_dept.ancestors` is a redundant path field. When adding/moving departments, the `ancestors` of sub-departments must be updated at the same time; otherwise "own department and sub-departments" will be calculated incorrectly. The relevant logic is in `DeptServiceImpl`; keep it consistent when writing custom SQL.

## 10. Dependency Usage

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-pc</artifactId>
</dependency>
```

Usually only `user-service` needs to depend on it directly. Other services that need user capabilities should depend on `user-client` instead of pulling in the entire backend domain.
