# ping-core: Base models and SPI

`ping-core` is the "language layer" shared by all modules: unified response, pagination, user context, API access policy annotations, App login domain utilities, and extensible SPI.

It **does not depend on Spring Boot**, only on Sa-Token core, TransmittableThreadLocal, and Lombok, so the gateway (WebFlux), servlet services, and even plain Java modules can safely reference it.

## 1. Contents

```
com.ping.core
├── annotation/
│   ├── PublicApi           # No login required
│   ├── LoginOnlyApi        # Backend login domain, login only
│   └── AppLoginOnlyApi     # App login domain, login only
├── context/
│   ├── UserContext         # Immutable user context (ID + roles + permissions)
│   └── UserContextHolder   # TransmittableThreadLocal holder
├── dto/
│   ├── ApiResponse         # {code, message, data}
│   └── PageResult          # {list, total}
├── id/
│   └── DistributedIdGenerator   # nextId() SPI
├── security/
│   ├── SecurityConstants   # admin / *:*:*
│   ├── PermissionProvider  # Role/permission data source SPI
│   └── StpAppUtil          # StpLogic("app") utility
└── trace/
    └── BizTraceIdGenerator # generate() SPI
```

## 2. API access policy annotations

### 2.1 Three policies

```java
@PublicApi          // No login required: guests can access
@LoginOnlyApi       // Backend login domain: StpUtil logged in is enough, permission codes not checked
@AppLoginOnlyApi    // App login domain: StpAppUtil logged in is enough, permission codes not checked
```

All of them can be applied to a **method** or a **Controller class**.

```java
@RestController
@RequestMapping("/user/pc/auth")
public class AuthController {

    @PublicApi
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(...) { ... }

    @LoginOnlyApi
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() { ... }
}
```

### 2.2 Priority

Method-level takes precedence over class-level, and `@PublicApi` has the highest priority. Full order:

1. `@PublicApi` on the method → public
2. `@AppLoginOnlyApi` on the method → App login
3. `@LoginOnlyApi` on the method → backend login
4. `@PublicApi` on the class → public
5. `@AppLoginOnlyApi` on the class → App login
6. `@LoginOnlyApi` on the class → backend login
7. None of the above → no policy declared (delegated to database rules or default deny)

Example:

```java
@PublicApi
@RestController
public class MixedController {

    @LoginOnlyApi
    @GetMapping("/private")
    public String privateApi() { ... }   // Method-level LoginOnly overrides class-level Public

    @GetMapping("/public")
    public String publicApi() { ... }    // Inherits class-level Public
}
```

### 2.3 The annotations themselves do not authorize

The annotations are only "declarations". To actually take effect, they need:

- **Server side**: `AnnotatedApiScanner` of `ping-web-spring-boot-starter` scans and writes to Redis; `ApiAccessInterceptor` / `AppApiAccessInterceptor` provide a local fallback;
- **Gateway**: `AnnotatedApiRegistry` aggregates from Redis every 10 seconds, and `SaTokenConfigure` allows or verifies according to the policy.

Including only `ping-core` produces no interception. When connecting directly to a service, the local interceptor only checks login for `@LoginOnlyApi` / `@AppLoginOnlyApi` and does not verify permission codes.

## 3. `UserContext` / `UserContextHolder`

### 3.1 Structure

```java
public final class UserContext {
    private final Long userId;
    private final Set<String> roles;
    private final Set<String> permissions;
}
```

It is immutable and safe to pass across threads. The App context additionally carries an `"app"` role marker.

### 3.2 Common methods

```java
Long userId = UserContextHolder.getUserId();
boolean login = UserContextHolder.isLogin();       // Backend or App
boolean app = UserContextHolder.isAppUser();       // Has the "app" role
boolean pc = UserContextHolder.isPcUser();

Set<String> roles = UserContextHolder.getRoles();
boolean admin = UserContextHolder.hasRole("admin");
boolean canEdit = UserContextHolder.hasPermission("system:user:edit");
```

`hasPermission` supports wildcards:

```java
UserContextHolder.hasPermission("system:user:add"); // Returns true if permissions contain system:user:* as well
```

### 3.3 Who writes the context

| Scenario | Writer |
| --- | --- |
| PC HTTP request | `SaTokenAuthFilter` (`ping-web-spring-boot-starter`) |
| App HTTP request | `AppTokenAuthFilter` |
| Dubbo provider | `UserContextProviderFilter` (`ping-dubbo-spring-boot-starter`) |
| Async thread | Requires TTL wrapping (`TaskDecorator` / `TtlRunnable`) |

### 3.4 Cross-thread

`UserContextHolder` uses `TransmittableThreadLocal`:

- Regular child threads: inherit automatically;
- Thread pools / `@Async` / `CompletableFuture`: tasks must be wrapped with `TtlRunnable` / `TtlExecutors`.

```java
executor.submit(TtlRunnable.get(() -> {
    Long userId = UserContextHolder.getUserId();
    // ...
}));
```

`ping-web-spring-boot-starter` registers a default `TaskDecorator` (`TtlRunnable::get`); when using a custom thread pool, add it yourself.

## 4. `StpAppUtil`

```java
public final class StpAppUtil {
    public static final String TYPE = "app";
    public static final StpLogic LOGIC = new StpLogic(TYPE);

    login / isLogin / checkLogin / getLoginId / getTokenValue / getTokenInfo / logout
}
```

Differences from `StpUtil`:

```java
StpUtil.login(1L);            // Backend login domain
StpAppUtil.login(1L);         // App login domain (loginType = "app")
```

Tokens and sessions in Redis are isolated by `loginType`. The two domains can use the same numeric ID, but they do not cross over.

## 5. `ApiResponse` / `PageResult`

```java
return ApiResponse.ok(data);
return ApiResponse.ok();
return ApiResponse.error(400, "参数错误");
return ApiResponse.error("参数错误");  // code=400
```

Structure:

```json
{"code": 200, "message": "success", "data": {...}}
```

`PageResult`:

```java
PageResult<UserListItemDTO> page = PageResult.of(list, total);
// {"list": [...], "total": 42}
```

Note: `ApiResponse.code` is a **business code**; the HTTP status code is determined by `ResponseEntity`. For example, a failed login returns:

```java
ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error(401, "用户名或密码错误"));
```

## 6. SPI

### 6.1 `PermissionProvider`

```java
public interface PermissionProvider {
    List<String> roles(Long userId);
    List<String> permissions(Long userId);
}
```

Purpose: when `SaTokenAuthFilter` finds no `ROLES` / `PERMS` in the token session, it uses this to fall back to querying permissions and writes them back.

- user-service: `DbPermissionProvider` (queries the database);
- order-service: `DubboPermissionProvider` (calls user-service via Dubbo);
- When no implementation exists: the filter skips the fallback and permissions are empty.

### 6.2 `DistributedIdGenerator`

```java
public interface DistributedIdGenerator {
    String nextId();
}
```

The implementation is provided by `ping-id-spring-boot-starter`.

### 6.3 `BizTraceIdGenerator`

```java
public interface BizTraceIdGenerator {
    String generate();
}
```

The implementation is provided by `ping-log-spring-boot-starter`: it prefers `DistributedIdGenerator`, otherwise UUID.

## 7. `SecurityConstants`

```java
public static final String ADMIN_ROLE = "admin";
public static final String SUPER_PERMISSION = "*:*:*";
```

`AuthorizationService` uses the following when determining an administrator:

```java
ROLE_admin or *:*:* → isAdmin() = true
```

## 8. Pitfalls

### 8.1 Assuming `@PublicApi` automatically allows direct service access

Inside the service, `@PublicApi` means "perform no checks" in `ApiAccessInterceptor`, so direct connections are not blocked anyway; but whether the gateway allows the request depends on whether `AnnotatedApiRegistry` has read the registration entry from Redis. If Redis is unavailable or the service has not started scanning, the gateway still denies by default.

### 8.2 Using `UserContextHolder` in the gateway

Gateway authorization happens in `SaReactorFilter` and does **not** go through the servlet `SaTokenAuthFilter`, so `UserContextHolder` is usually empty in gateway request threads. When the gateway needs user information:

- Read the token with `StpUtil` / `StpAppUtil`;
- Or call `StpUtil.getTokenSession()` to read session fields;
- Do not carry servlet context assumptions over to WebFlux.

### 8.3 Forgetting to clean up after `new UserContext`

```java
UserContextHolder.set(ctx);
try {
    // ...
} finally {
    UserContextHolder.clear();
}
```

Thread pools reuse threads; failing to clean up causes the next request to read the previous user. Existing filters clean up in `finally`; follow the same rule when writing your own filters/interceptors.

### 8.4 Using `UserContextHolder.getUserId()` to determine "backend user"

App users also have a `userId`. Use `isAppUser()` / `isPcUser()` when you need to distinguish, and do not infer the domain from "whether userId exists".

### 8.5 Returning Long IDs directly to the frontend

Snowflake IDs are 19 digits, exceeding the JavaScript safe integer range. `AppUserResponse.userId` and `AppLoginResponse.userId` already use `String`; keep this convention for new DTOs.

### 8.6 Inconsistent permission wildcard syntax

`UserContext.hasPermission` only recognizes the `xxx:*` suffix wildcard (e.g. `system:*` matches `system:user:add`); it does not support mid-string `*` matching or regular expressions. Keep the "prefix + colon" hierarchy when designing permission codes.

## 9. Adding the dependency

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-core</artifactId>
</dependency>
```

All modules should add it through the dependency management of the root `pom.xml`, without a version number.
