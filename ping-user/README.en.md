# ping-user: User and Permission Service

`ping-user` is where both the "people" and the "permissions" of the entire permission foundation live: backend users, consumer users, login, RBAC, departments and data scope, endpoint authorization rules, audit logs and online sessions are all provided by it.

It is ultimately packaged as **a single startup service** `user-service`, but is split internally into 6 submodules. The split keeps the "contract", "authentication primitives", "backend domain", "App domain" and "startup assembly" independent, so that the impact of a change is obvious at a glance.

## 1. Submodules at a Glance

| Submodule | Responsibility | Dependencies | Artifact |
| --- | --- | --- | --- |
| [`user-common`](user-common/README.en.md) | User-domain shared constants and enums (data scope, session keys, `UserType`) | `ping-core` | Plain jar |
| [`user-auth`](user-auth/README.en.md) | Authentication primitives: password policy, login failure lockout, arithmetic CAPTCHA | `user-common`, Redis | Plain jar |
| [`user-client`](user-client/README.en.md) | **External Dubbo contract**: `PermissionDubboClient`, `UserDubboClient`, `AppUserDubboClient` and DTOs | Lombok only | Plain jar (referenced by gateway / order) |
| [`user-pc`](user-pc/README.en.md) | Backend user domain: login, RBAC, departments, data scope, audit, API resources/whitelist, online users | `user-auth`, `user-client`, `ping-web-starter`, MyBatis-Plus, Dubbo | Plain jar |
| [`user-app`](user-app/README.en.md) | Consumer user domain: `app_user` registration/login, App login state, App user Dubbo implementation | `user-auth`, `user-client`, `ping-web-starter`, MyBatis-Plus, Dubbo | Plain jar |
| [`user-service`](user-service/README.en.md) | **Startup assembly module**: `main` method, `@MapperScan`, thread pools, MyBatis-Plus, Nacos bootstrap configuration | All modules above + starters | Executable Spring Boot jar |

Dependency direction (arrows mean "depends on"):

```
                    ┌──────────────────────────────┐
                    │        user-service          │  Startup / assembly
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
                   (user-pc / user-app also depend on user-client)
```

A few rules that must hold:

1. **`user-client` must not be depended on by business modules in reverse.** It contains only interfaces and DTOs, for external services such as gateway and order to reference; if it depended on `user-pc`, it would create a cycle and force external services to pull in implementation dependencies such as MyBatis and Spring Security.
2. **`user-common` only holds things "needed by multiple modules and with no business behavior".** Do not put Services, Mappers or DTOs specific to one Controller into it.
3. **`user-pc` and `user-app` must not depend on each other.** Two sets of user tables, two login domains, two sets of Controllers; when authentication primitives need to be reused, both depend on `user-auth`.
4. **`user-service` contains no business logic.** It only assembles what is needed; new business code belongs in `user-pc` / `user-app`.

## 2. The Three Most Confusing Boundaries

### 2.1 `user-client` Is the "External Contract"; `user-pc` / `user-app` Are the "Internal Implementations"

The interfaces in `user-client` are **for other services to call user-service**, not for user-service itself.

```java
// user-client: interfaces and DTOs only, no implementation
public interface UserDubboClient {
    String getUserNameById(Long userId);
}
```

```java
// user-pc: implements the contract and exposes it via @DubboService
@DubboService
public class UserDubboServiceImpl implements UserDubboClient {
    @Override
    public String getUserNameById(Long userId) { ... }
}
```

```java
// order-service: depends only on user-client, not user-pc
@DubboReference(check = false)
private UserDubboClient userDubboClient;
```

Therefore:

- To add an RPC capability for external services → change the **`user-client` interface** + the implementation in `user-pc` / `user-app`.
- To add an HTTP endpoint → change only `user-pc` / `user-app`; there is no need to touch `user-client`.
- gateway depends on `user-client` only to get `PermissionDubboClient` and `UserInfoDTO`; it does not load user-service's database code.

### 2.2 The PC Login Domain and App Login Domain Use Two Sets of Tokens

| | Backend (PC) | Consumer (App) |
| --- | --- | --- |
| User table | `sys_user` | `app_user` + `app_user_identity` |
| Login utility | `StpUtil` | `StpAppUtil` (`new StpLogic("app")`) |
| Login endpoint | `POST /user/pc/auth/login` | `POST /user/app/auth/login` |
| Annotation | `@LoginOnlyApi` | `@AppLoginOnlyApi` |
| Permissions in session | Two keys, `ROLES` / `PERMS` | Usually only `APP_PHONE`, with no backend permissions |
| Typical endpoints | `/user/pc/users/**` | `/user/app/auth/me`, `/order/app/**` |

The two sets of tokens are isolated in Redis by `loginType`. **Calling an App endpoint with a PC token returns 401, and vice versa.** This is not a bug but a design goal.

An incorrect pattern commonly seen:

```java
// Wrong: using the backend login utility in an App endpoint
@AppLoginOnlyApi
@GetMapping("/me")
public ApiResponse<?> me() {
    Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId())); // This will never get the App user
}
```

The correct pattern:

```java
@AppLoginOnlyApi
@GetMapping("/me")
public ApiResponse<?> me() {
    Long userId = Long.valueOf(String.valueOf(StpAppUtil.getLoginId()));
}
```

If you only want the "current logged-in user" without caring which domain it comes from, use the unified context:

```java
Long userId = UserContextHolder.getUserId();   // Filled for both backend and App
boolean isApp = UserContextHolder.isAppUser(); // Based on OperatorType.APP (App token), not a role string
OperatorType type = UserContextHolder.getOperatorType(); // PC / APP / SYSTEM / ANONYMOUS
String userName = UserContextHolder.getUserName(); // NICKNAME snapshot written to the token session at login
```

`NICKNAME` is written into the token session at login (backend prefers nickname, falling back to username; App prefers nickname, falling back to phone number). Business tables such as orders use it as an operator-name snapshot; for old tokens without this value, the business layer can look it up once by user domain.

### 2.3 `user-pc` and `user-app` Are Not "PC and App Controller Directories"

Each owns **independent tables, independent business services and independent Dubbo implementations**:

- `user-pc` manages `sys_user` and backend capabilities such as RBAC, departments and audit; its endpoint prefix is `/user/pc/**`.
- `user-app` manages `app_user` and only handles consumer registration/login/current user; its endpoint prefix is `/user/app/**`.

## 3. How a Request Flows

### 3.1 Backend Login

```
Frontend POST /user/pc/auth/login
  → gateway: @PublicApi match, pass through directly (no token validation)
  → user-service: AuthController.login
      ├─ Account lock check (Redis)
      ├─ CAPTCHA validation (optional)
      ├─ BCrypt password verification
      ├─ StpUtil.login(userId)
      ├─ PermissionCacheService.cachePerms(userId)
      │     └─ Write ROLES / PERMS to the token session (shared Redis)
      └─ Return ping-token
```

Subsequent requests:

```
GET /user/pc/users
  → gateway: StpUtil.checkLogin()
      → Read ROLES / PERMS from the token session (SaStpInterfaceImpl)
      → Validate system:user:list against the rules in sys_api_resource
  → user-service: SaTokenAuthFilter then bridges to Spring Security
      → @PreAuthorize("@authz.has('system:user:list')") second check
```

The gateway handles "can it get in"; `@PreAuthorize` inside the service handles "it must not misbehave even when connecting directly and bypassing the gateway".

### 3.2 App Login

```
App POST /user/app/auth/login
  → gateway: @PublicApi pass-through
  → user-service: AppAuthServiceImpl validates phone/password
      → StpAppUtil.login(userId)
      → token session writes APP_PHONE
  → return App token
```

Subsequent App requests:

```
GET /order/app/queryOrderByUser
  → gateway: @AppLoginOnlyApi match
      → StpAppUtil.checkLogin() (validates App login only, does not check backend permissions)
  → order-service: AppTokenAuthFilter injects ROLE_APP + UserContext
```

### 3.3 Cross-Service Dubbo Calls

```
order-service receives a request
  → SaTokenAuthFilter / AppTokenAuthFilter writes UserContextHolder (including OperatorType and nickname)
  → userDubboClient.getUserNameById(1L)
      → UserContextConsumerFilter puts userId/roles/perms/operator-type/operator-name into the RPC attachment
      → UserContextProviderFilter in user-service restores UserContextHolder
      → UserDubboServiceImpl executes the business logic
```

Attachment names: `ping-user-id`, `ping-user-roles`, `ping-user-perms`, `ping-operator-type`, `ping-operator-name`. `ping-operator-type` is the numeric code of `OperatorType` (1 backend, 2 App, 3 system, 4 anonymous), used to avoid misidentifying the caller identity based only on the permission set.

Note: **Dubbo provider threads have no HTTP request context and cannot call `StpUtil.getLoginId()`**. When the current user is needed, the caller should pass it explicitly or read it from `UserContextHolder` (propagated by the filter).

## 4. Quick Start

```bash
# Build all backend modules
mvn clean install -DskipTests

# Start only user-service (do not add -am, otherwise run executes on the aggregator and fails with "no main class")
mvn -pl ping-user/user-service spring-boot:run

# The config namespace follows APP_ENV (dev) by default; for public:
# NACOS_CONFIG_NAMESPACE="" mvn -pl ping-user/user-service spring-boot:run

# Or start the whole stack with Docker
./start.sh
```

> The most common reason local startup fails: `application-dev.yml` / `user-service-dev.yml` are missing from Nacos.
> The `dev` namespace is read by default (following `APP_ENV`); if the configuration is published in public, set `NACOS_CONFIG_NAMESPACE=""`.
> When the configuration cannot be read, the log first shows `config[... ] is empty`, then reports `Failed to configure a DataSource`.

Default ports: `user-service` 8099, gateway 9999, frontend 5173.

Verify backend login:

```bash
curl -s -X POST http://localhost:9999/user/pc/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
```

The `data.token` in the response is the token to include in subsequent requests:

```bash
curl -s http://localhost:9999/user/pc/users \
  -H 'ping-token: Bearer <token>'
```

> `sa-token.token-name` is configured as `ping-token`, and `token-prefix` as `Bearer`, so the header is `ping-token: Bearer xxx`. If the Nacos shared configuration has been changed, use the actual configuration.

Verify App registration/login:

```bash
curl -s -X POST http://localhost:9999/user/app/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000001","password":"Abc12345!","nickname":"Little P"}'

curl -s -X POST http://localhost:9999/user/app/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000001","password":"Abc12345!"}'
```

## 5. Common Development Tasks

### 5.1 Add a Backend Endpoint

Take "query the details of a specific user" as an example:

```java
@RestController
@RequestMapping("/user/pc/users")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/{id}/profile")
    @PreAuthorize("@authz.has('system:user:list')")   // Second authorization check inside the service
    public ApiResponse<UserDetailResponse> profile(@PathVariable Long id) {
        ...
    }
}
```

You also need to tell the gateway what permission this endpoint requires. Choose one:

**Option A: Register in `sys_api_resource` (recommended; permissions can be maintained on the backend page)**

```sql
INSERT INTO sys_api_resource(permission_code, path, method, status, description, sort)
VALUES ('system:user:list', '/user/pc/users/*/profile', 'GET', 1, 'User details', 10);
```

**Option B: Declare the policy with annotations (no table changes)**

```java
@LoginOnlyApi               // Requires backend login only; does not check specific permissions
@GetMapping("/{id}/profile")
public ApiResponse<UserDetailResponse> profile(@PathVariable Long id) { ... }
```

> If this endpoint is "viewable by any logged-in user", use `@LoginOnlyApi`; if it is "must have a specific permission code", use `@PreAuthorize` + `sys_api_resource`. **Do not** add `@LoginOnlyApi` to sensitive endpoints just for convenience; it makes the gateway skip permission-code checks.

Effective time:

- Annotation policies: written to Redis after service startup; the gateway aggregates once every 10 seconds, so they take effect in about 10–130 seconds at most (Redis TTL is 120 seconds, refreshed every 30 seconds).
- `sys_api_resource` rules: the gateway refreshes every 60 seconds, so they take effect in about 60 seconds at most.
- Whitelist `sys_api_whitelist`: refreshed every 10 seconds.

### 5.2 Add an App Endpoint

```java
@RestController
@RequestMapping("/user/app/profile")
@AppLoginOnlyApi                 // Class-level annotation; all methods in this class are checked against the App login domain
public class AppProfileController {

    @GetMapping("/me")
    public ApiResponse<AppUserResponse> me() {
        Long userId = Long.valueOf(String.valueOf(StpAppUtil.getLoginId()));
        ...
    }
}
```

There is no need to register it in `sys_api_resource`, and no need to change gateway configuration: it is registered to Redis automatically after startup scanning, and the gateway follows `@AppLoginOnlyApi` to use the App login domain.

### 5.3 Add a Cross-Service Dubbo Contract

1. Add the interface and DTO in `user-client` (the DTO must `implements Serializable`).

```java
public interface UserDubboClient {
    String getUserNameById(Long userId);

    // Added
    List<String> listUsernamesByIds(List<Long> userIds);
}
```

2. Implement it in `user-pc` (or `user-app`) and expose it with `@DubboService`:

```java
@DubboService
public class UserDubboServiceImpl implements UserDubboClient {
    @Override
    public List<String> listUsernamesByIds(List<Long> userIds) { ... }
}
```

3. Callers depend only on `user-client`:

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

### 5.4 Add a Permission Code

1. Add a record in `sys_permission` (parent, code, name, sort, etc.).
2. Map "method + path" to that permission code in `sys_api_resource`.
3. Assign the permission to roles on the role management page.
4. Have the user log in again (or wait for the forced logout triggered by the permission change, then log in again), so that `ROLES` / `PERMS` are written into the new token session.

> Permission cache lives in the token session; directly changing the database does not automatically refresh the permissions of logged-in users. `PermissionCacheServiceImpl.invalidateCache()` kicks out affected users, and they get the new permissions after logging in again.

### 5.5 Add a Backend Page (Menus Driven by the Backend Permission Tree)

The frontend no longer maintains a hard-coded menu table. After login, `/user/pc/auth/me` returns the menu tree visible to the current user; based on it, the frontend dynamically registers routes and renders the sidebar.

1. Add a page component under `ping-web/src/views`, for example `Projects.vue`.
2. Add a **menu**-type permission on the permission management page, and attach it under a directory as needed:

   | Field | Example | Description |
   | --- | --- | --- |
   | Permission code | `system:project` | Also serves as the permission code for the frontend route |
   | Route path | `/projects` | Must start with `/` |
   | Component name | `Projects` | Corresponds to `views/Projects.vue`, excluding `.vue` |
   | Icon | `Folder` | Element Plus icon name; may be left empty |
   | Menu visible | On | When off, the route is still registered but not shown in the sidebar (suitable for detail pages) |

3. Assign the menu permission to roles on the role management page.
4. Have the user log in again (or log in again after being kicked out by a permission change); the menu and route take effect automatically.

> Directories (type=1) only group items and usually have no route/component; menus (type=2) must have a route path and component name; buttons (type=3) are not part of the menu tree. If the frontend cannot find the component name, it only logs a warning to the console and skips that menu; other pages are unaffected.

### 5.6 Add a Query with Data Permission Filtering

Data permissions have two layers:

- **Endpoint permissions**: `@PreAuthorize` / gateway rules, answering "can this endpoint be called";
- **Data permissions**: `@DataScope` + MyBatis interceptor, answering "which rows in this endpoint can be seen".

Rules still come from `sys_role.data_scope` / `sys_role_dept` and are resolved centrally by `DataScopeService`; business code does not participate in scope calculation.

Scope applies to **business tables within the backend organization** (such as `sys_user`, backend project/work order tables). Pure App-domain tables (such as `ping_order`) do not use `@DataScope`: they are filtered by `user_id`. Do not force a `dept_id` just to use data permissions, otherwise customizers will mistakenly believe every App table must have a department field.

1. Confirm the table has a department column (usually `dept_id`) and an owner column (`create_by_id` / `user_id`). A table with only an owner and no department column can omit `deptColumn` and filter only by owner.
2. Add annotations on the Mapper (using the backend project table `ping_project` as an example):

```java
@DataScope(table = "ping_project", deptColumn = "dept_id",
        ownerColumn = "create_by_id",
        ownerTypeColumn = "create_by_type", ownerTypeValue = 1)
public interface BizProjectMapper extends BaseMapper<BizProject> {

    @Select("SELECT * FROM ping_project WHERE id = #{id}")
    BizProject selectScopedById(@Param("id") Long id);
}
```

3. Retrieve data through the annotated query for detail / update / delete alike:

```java
private BizProject requireProject(Long id) {
    BizProject project = projectMapper.selectScopedById(id); // Out-of-scope data = not found
    if (project == null) {
        throw new IllegalArgumentException("Project not found");
    }
    return project;
}
```

4. Across services, do not reimplement role/department resolution: `user-client` provides `DataScopeDubboClient`, and `DataScopeDubboServiceImpl` in `user-pc` returns `DataScopeDTO`. Business services only need to implement their own `DataScopeProvider`.

A few conventions:

- `includeSelf = true` (default) means data created by oneself is always visible beyond department scope; the "self only" role degenerates to filtering only by owner.
- Backend user and App user IDs may be numerically equal. If a backend table can indeed be written by App users (such as a shared work order table), be sure to constrain it with `ownerTypeColumn + ownerTypeValue`; pure App tables filter directly by `user_id` and should not use `@DataScope`.
- Data permissions apply to SELECT and UPDATE / DELETE (the MyBatis-Plus interceptor rewrites write statements); for write paths it is still recommended to fetch data with the annotated query first, so unauthorized access appears as "not found" and optimistic locking conflicts can also be handled. The user domain's `SysUserMapper` additionally redeclares `updateById` / `update` / `deleteById` / `delete` and annotates them with `@DataScope`, as a second line of defense beyond Controller validation.
- Pagination count SQL carries the same conditions, so the total count does not leak out-of-scope data.
- See the full explanation in [ping-data-scope-spring-boot-starter](../ping-common/ping-data-scope-spring-boot-starter/README.en.md).

## 6. Configuration

`user-service`'s startup bootstrap configuration is only `application.yml` (service name, Nacos address, config imports); runtime configuration lives in Nacos `user-service-${APP_ENV}.yml` and `application-${APP_ENV}.yml`.

The login security configuration prefix is `ping.security`:

```yaml
ping:
  security:
    registration-enabled: false   # Whether backend self-registration is enabled
    default-role-code: guest      # Default role for self-registered users
    login:
      max-attempts: 5             # Maximum consecutive failures
      lock-minutes: 15            # Lock duration (minutes)
    password:
      min-length: 8
      max-length: 64
      require-categories: 3       # At least how many of uppercase/lowercase/digit/special characters are required
    captcha:
      enabled: false              # When enabled, login must include a CAPTCHA
      expire-minutes: 2
  audit:
    retention-days: 180           # Retention days for login/operation logs
```

`PingSecurityProperties` is bound by `SecurityConfig` in `user-service` via `@EnableConfigurationProperties`. `user-auth` itself is not a Spring Boot auto-configuration module; when referencing it standalone, you must enable configuration binding yourself.

## 7. Pitfalls

### 7.1 Gateway Returns 403 "No permission rule configured for endpoint; access denied"

This is **fail-closed**: the user is logged in, but the endpoint has neither an annotation policy nor a registration in `sys_api_resource`, so the gateway denies it by default.

```text
GET /user/pc/new-feature → 403 No permission rule configured for endpoint; access denied
```

Handling:

- Add `@PublicApi` / `@LoginOnlyApi` / `@AppLoginOnlyApi`; or
- Register `path + method + permission_code` in `sys_api_resource`; or
- Register it in `sys_api_whitelist` as a login-free whitelist (use with caution).

Search for `[ApiCoverage]` in the startup logs to see the list of endpoints without registered policies.

### 7.2 Annotations Were Added, but the Gateway Still Blocks

First confirm:

1. The annotation is on the **Controller method or class**, not on the Service;
2. The service is started and can connect to Redis (annotation scanning depends on Redis; if it cannot write, the gateway cannot see it);
3. Wait for one refresh cycle (the gateway aggregates once every 10 seconds by default);
4. The path matches the actual request: annotation scanning normalizes `{id}` to `*`, but does not automatically add the `/user/pc` prefix;
5. The request goes through the gateway (9999), not directly to `user-service` (8099).

You can check Redis manually:

```bash
redis-cli -p 16379 --scan --pattern 'ping:gateway:api:*'
redis-cli -p 16379 smembers ping:gateway:api:login:user-service
```

### 7.3 The Difference Between `hasAuthority` and `@authz.has`

```java
@PreAuthorize("hasAuthority('system:user:list')")     // Permission code only; administrators do not pass automatically
@PreAuthorize("@authz.has('system:user:list')")       // Administrators (admin role or *:*:*) pass directly
```

Within the user domain, `@authz.has(...)` is uniformly recommended, to avoid writing `hasAnyRole('admin') or ...` on every endpoint. `OrderPcController` in order-service uses `hasAuthority(...)` as sample code; new endpoints should use `@authz` consistently.

### 7.4 Login Succeeds but the Gateway Says No Permission

The most common reason is **no `ROLES` / `PERMS` in the token session**:

- The old token was issued before the permission cache went live;
- The cache was cleared after the user's permissions changed, but the client is still using the old token;
- `cachePerms` threw during login and the exception was swallowed (see `[PermCache]` in the logs).

Fix: log in again. The gateway's `SaStpInterfaceImpl` only reads the token session and will not block the WebFlux event loop to query the database or call Dubbo.

### 7.5 Forced Password Change State

After an administrator resets a password, `sys_user.force_change_password = 1`. The login response contains `forceChangePassword = true`, and `FORCE_PWD_CHANGE=1` is written to the token session. The gateway only allows:

```
/user/pc/auth/password
/user/pc/auth/logout
/user/pc/auth/me
```

All other endpoints return 403 "Please change the initial password first". After a successful password change, `StpUtil.logout(userId)` invalidates the current token, so the user must log in again.

### 7.6 The `user-app` ID and the `sys_user` ID Are Not the Same Set

`app_user.id` is generated by the snowflake algorithm (`DistributedIdGenerator`), while `sys_user.id` is database auto-increment. They may have the same numeric value, but are **completely unrelated**.

- Query backend users → `UserDubboClient` / `sys_user`
- Query App users → `AppUserDubboClient` / `app_user`

Do not use the same `userId` to query across the two domains. `userId` is returned as a string in App responses, also to avoid JavaScript precision loss for large integers.

### 7.7 Calling `StpUtil` Inside a Dubbo Provider

```java
// Wrong: Dubbo provider threads have no HTTP session
@DubboService
public class XxxImpl implements XxxClient {
    public void doSomething() {
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId())); // May throw an exception
    }
}
```

The correct approach:

- The caller passes `userId` explicitly; or
- Rely on the `UserContextHolder` restored by `UserContextProviderFilter`:

```java
Long callerId = UserContextHolder.getUserId();
boolean canEdit = UserContextHolder.hasPermission("system:user:edit");
```

The prerequisite is that a `SaTokenAuthFilter` / `AppTokenAuthFilter` on the call path writes the context, and that async threads use TTL wrapping (`user-service` and `order-service` already provide a `TaskDecorator`).

### 7.8 User Context Unavailable in Async Threads

`UserContextHolder` is based on `TransmittableThreadLocal`; plain child threads inherit it, but thread pools must be wrapped:

```java
executor.submit(TtlRunnable.get(() -> {
    Long userId = UserContextHolder.getUserId(); // TTL wrapping is required
}));
```

`AsyncExecutorConfig` in `user-service` already adds a `TaskDecorator` to the thread pool; if you create your own `new ThreadPoolExecutor(...)`, you must wrap it yourself with `TtlExecutors.getTtlExecutorService(...)` or `TtlRunnable.get(...)`.

### 7.9 Missing Nacos Configuration Causes Startup Failure

`application.yml` imports only:

```
nacos:application-${APP_ENV:dev}.yml
nacos:${spring.application.name}-${APP_ENV:dev}.yml
```

Missing configuration fails fast instead of starting with "half a set of configuration". The default namespace should contain `application-dev.yml` and `user-service-dev.yml`. On first Docker startup they are imported by `init-sql/nacos-mysql.sql`.

## 8. Submodule Documentation

- [user-common](user-common/README.en.md)
- [user-auth](user-auth/README.en.md)
- [user-client](user-client/README.en.md)
- [user-pc](user-pc/README.en.md)
- [user-app](user-app/README.en.md)
- [user-service](user-service/README.en.md)
