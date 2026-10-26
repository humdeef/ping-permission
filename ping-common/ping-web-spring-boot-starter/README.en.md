# ping-web-spring-boot-starter: Web security starter

This starter packages the common security capabilities of servlet microservices so that they "take effect as soon as the dependency is added":

- Bridges the Sa-Token login state to Spring Security, supporting `@PreAuthorize`;
- Scans `@PublicApi` / `@LoginOnlyApi` / `@AppLoginOnlyApi` and automatically registers them to Redis for gateway aggregation;
- App login domain bridging and local fallback;
- Unified exception handling;
- Cross-thread user context propagation (`TaskDecorator`).

> **Only applicable to servlet applications.** The gateway is WebFlux and must not include this starter (it would pull in Spring MVC and servlet filters).

## 1. Auto-configuration contents

`AutoConfiguration.imports`:

```
com.ping.web.config.CommonSecurityAutoConfiguration
com.ping.web.config.AnnotatedApiAutoConfiguration
```

### 1.1 `CommonSecurityAutoConfiguration`

Conditions:

```java
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnClass({HttpSecurity.class, StpUtil.class})
```

Registered beans:

| Bean | Purpose |
| --- | --- |
| `SecurityFilterChain` | **All HTTP-layer requests `permitAll`**; authentication and authorization are delegated to the gateway + method-level annotations |
| `PasswordEncoder` | BCrypt |
| `SaTokenAuthFilter` | Reads `ROLES` / `PERMS` from the token session and bridges to Spring Security |
| `AuthorizationService` (`@authz`) | `@PreAuthorize("@authz.has(...)")` |
| `ApiAccessInterceptor` | In-service login fallback for `@LoginOnlyApi` |
| `AppTokenAuthFilter` | App login state bridging (`StpAppUtil`) |
| `AppApiAccessInterceptor` | In-service login fallback for `@AppLoginOnlyApi` |
| `TaskDecorator` | `TtlRunnable::get`, allowing `@Async` to propagate the user context |

### 1.2 `AnnotatedApiAutoConfiguration`

Conditions:

```java
@ConditionalOnClass({StringRedisTemplate.class, RequestMappingHandlerMapping.class})
```

It creates `AnnotatedApiScanner`:

- Scans all `@RequestMapping` methods after application startup;
- Resolves policies by the rule "method-level takes precedence over class-level";
- Writes `METHOD|path` into a Redis Set;
- Rewrites every 30 seconds with a TTL of 120 seconds;
- Deletes the current service's key when the application shuts down.

Redis keys:

```
ping:gateway:api:public:{serviceName}
ping:gateway:api:login:{serviceName}
ping:gateway:api:app-login:{serviceName}
```

Examples:

```
SADD ping:gateway:api:login:user-service "GET|/user/pc/auth/me"
SADD ping:gateway:api:public:user-service "POST|/user/pc/auth/login"
SADD ping:gateway:api:app-login:user-service "GET|/user/app/auth/me"
```

Path variables are normalized to `*`:

```
/order/app/{id}  →  /order/app/*
```

The gateway's `AnnotatedApiRegistry` scans these keys every 10 seconds and aggregates them into in-memory rules.

## 2. Usage

### 2.1 Add the dependency

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-web-spring-boot-starter</artifactId>
</dependency>
```

Also required (usually already brought in by the business module):

- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-data-redis`
- `sa-token-spring-boot4-starter`
- `sa-token-redis-jackson`

### 2.2 Write a Controller

```java
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    @PublicApi
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @LoginOnlyApi
    @GetMapping("/me")
    public ApiResponse<Long> me() {
        return ApiResponse.ok(UserContextHolder.getUserId());
    }

    @PreAuthorize("@authz.has('demo:list')")
    @GetMapping("/list")
    public ApiResponse<List<String>> list() {
        return ApiResponse.ok(List.of("a", "b"));
    }
}
```

- `@PublicApi`: the gateway allows it directly;
- `@LoginOnlyApi`: the gateway verifies backend login but does not check permission codes;
- `@PreAuthorize`: in-service method-level verification; the gateway side also needs to register the `demo:list` rule in `sys_api_resource`, otherwise the request is denied by the gateway by default.

### 2.3 Permission expressions

```java
@PreAuthorize("@authz.has('system:user:list')")   // Administrators pass automatically
@PreAuthorize("@authz.hasRole('ops')")            // Role
@PreAuthorize("@authz.isAdmin()")                 // Administrator only
```

You can also use native Spring Security:

```java
@PreAuthorize("hasAuthority('system:user:list')") // Administrators do not pass automatically
```

It is recommended to use `@authz` uniformly to avoid repeating administrator checks in every endpoint.

### 2.4 Custom permission data source

If the service does not write permissions into the token session, implement `PermissionProvider`:

```java
@Component
public class MyPermissionProvider implements PermissionProvider {

    @Override
    public List<String> roles(Long userId) {
        return ...;
    }

    @Override
    public List<String> permissions(Long userId) {
        return ...;
    }
}
```

`SaTokenAuthFilter` calls it when the token session is empty and writes the result back to the session. order-service's `DubboPermissionProvider` calls user-service via Dubbo.

### 2.5 Custom exception responses

`GlobalExceptionHandler` already handles:

| Exception | HTTP | code |
| --- | --- | --- |
| `MethodArgumentNotValidException` | 400 | 400 |
| `DuplicateKeyException` | 400 | 400 |
| `AccessDeniedException` | 403 | 403 |
| `RuntimeException` | 400 (`ErrorResponse` keeps the original status) | Corresponding value |
| Other `Exception` | 500 | 500 |

To override, define your own `@RestControllerAdvice` with a higher priority (`@Order`).

## 3. In-service fallback interceptors

`ApiAccessInterceptor` and `AppApiAccessInterceptor` are registered on `/**` and check before the Controller method executes:

```java
// @LoginOnlyApi: backend not logged in → 401
if (isLoginOnly && !isPublic && !StpUtil.isLogin()) { ... }

// @AppLoginOnlyApi: App not logged in → 401
```

They only check **login**, not permission codes. Permission codes are handled by:

- Gateway: `sys_api_resource` rules;
- In-service: `@PreAuthorize`.

Together, the two layers ensure that when "bypassing the gateway to connect directly to the service", sensitive endpoints are still protected by method-level permissions.

## 4. Pitfalls

### 4.1 Assuming `permitAll` in the service is insecure

`SecurityFilterChain` does allow all HTTP requests, but this only delegates "entry authentication" to the gateway. Inside the service there are still:

- `@PreAuthorize` method-level checks;
- `@LoginOnlyApi` / `@AppLoginOnlyApi` interceptors.

If an endpoint has neither an annotation nor `@PreAuthorize`, direct access to the service is completely open. **Sensitive endpoints must have at least one layer of method-level verification.**

### 4.2 Annotations written but the gateway cannot see them

Check in order:

1. Whether the service is a servlet application (WebFlux does not load `AnnotatedApiAutoConfiguration`);
2. Whether there is a `StringRedisTemplate` (Redis connection is healthy);
3. Whether the annotation is on the Controller method/class (annotations on Services are not scanned);
4. Whether the gateway refresh cycle (10 seconds) has been waited for;
5. Whether the Redis key has been written:

```bash
redis-cli -p 16379 smembers ping:gateway:api:login:user-service
```

### 4.3 Multiple instances deleting each other's keys

`AnnotatedApiScanner.stop()` deletes the current service's three keys when the application shuts down. If the same service has multiple instances, an instance going offline briefly deletes the keys; other instances write them back within 30 seconds. Does the gateway have a 120-second TTL in-memory fallback? No—the gateway re-scans every 10 seconds, so in the worst case there is about a 10-second policy gap.

If this is a concern, you can:

- Make `stop()` not delete the keys and rely on the TTL to expire naturally;
- Or add an instance dimension to the keys and take the union during gateway aggregation.

The current implementation is suitable for single-instance/small-scale deployments; evaluate before scaling out.

### 4.4 `@PublicApi` overridden by a method-level annotation

```java
@PublicApi
@RestController
public class DemoController {

    @LoginOnlyApi
    @GetMapping("/private")
    public String privateApi() { ... }  // Method-level LoginOnly takes effect, not public
}
```

This is intentional: method-level is more specific. Do not treat class-level `@PublicApi` as an absolute guarantee that "everything in the class is public".

### 4.5 Empty permissions cause 403

`SaTokenAuthFilter` reads `ROLES` / `PERMS` from the token session. If:

- The user logged in with an older version;
- The permission cache was cleared;
- The token session in Redis has expired;

then authorities are empty and `@PreAuthorize` will deny. When a `PermissionProvider` is configured, it falls back to querying once and writes back; if not configured, the user must log in again.

### 4.6 Including it in a WebFlux service

Symptoms: startup reports an MVC/WebFlux conflict, or the Sa-Token servlet filter does not take effect. For the gateway, depend only on `ping-core` and use its own `SaReactorFilter` and `SaStpInterfaceImpl`.

### 4.7 Custom `TaskDecorator` overrides the default implementation

```java
@Bean
public TaskDecorator myDecorator() { ... }
```

The default `TaskDecorator` in `CommonSecurityAutoConfiguration` carries `@ConditionalOnMissingBean` and will be overridden by your bean. If your implementation only passes the MDC and not the `UserContext`, `UserContextHolder` will be empty in async tasks.

## 5. Adding the dependency

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-web-spring-boot-starter</artifactId>
</dependency>
```

Applicable to: servlet business services such as `user-service` and `order-service`. **Not applicable to**: gateway (WebFlux).
