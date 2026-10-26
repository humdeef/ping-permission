# gateway-service: Spring Cloud Gateway

`gateway-service` is the only backend entry point, responsible for routing, authentication, endpoint-level authorization, CORS, and trace ID. It is based on **Spring Cloud Gateway (WebFlux)**, and must not introduce Spring MVC.

## 1. Startup and configuration

```bash
# First run mvn clean install -DskipTests in the root directory
mvn -pl ping-gateway/gateway-service spring-boot:run

# The config namespace follows APP_ENV (dev) by default; for public:
# NACOS_CONFIG_NAMESPACE="" mvn -pl ping-gateway/gateway-service spring-boot:run
```

- Port: 9999
- Management port: 8081 (`/actuator/health`, `/actuator/prometheus`, `/actuator/gateway`)
- Startup class: `GatewayApplication` (`@EnableDiscoveryClient` + `@EnableScheduling`)

`src/main/resources/application.yml` only keeps the Nacos bootstrap configuration; routes, ports, Dubbo, and Sa-Token policies are all in Nacos:

- `application-${APP_ENV}.yml`: Redis, Sa-Token (`token-name: ping-token`), Dubbo registry, Actuator;
- `gateway-service-${APP_ENV}.yml`: ports, routes, `satoken.permit.paths`, `satoken.api-permissions.*`.

## 2. Routes

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          discovery:
            locator:
              enabled: true
              lower-case-service-id: true
          routes:
            - id: user-pc
              uri: lb://user-service
              order: 1
              predicates:
                - Path=/user/pc/**
            - id: user-app
              uri: lb://user-service
              order: 2
              predicates:
                - Path=/user/app/**
            - id: order-service
              uri: lb://order-service
              order: 3
              predicates:
                - Path=/order/**
```

To change routes, only modify the Nacos configuration and publish it; no need to repackage the gateway (`spring-cloud-starter-alibaba-nacos-config` supports refresh; whether it takes effect dynamically depends on the configuration item).

## 3. Authorization pipeline

The entry point is `SaReactorFilter` in `SaTokenConfigure`:

```
addInclude("/**")
addExclude("/favicon.ico", "/actuator/**", satoken.permit.paths...)
```

Each request is processed in the following order:

| Step | Check | Action |
| --- | --- | --- |
| 0 | `OPTIONS` | Pass through directly (CORS preflight) |
| 0.1 | `@PublicApi` (Redis annotation strategy) | Pass through |
| 0.2 | `sys_api_whitelist` (DB whitelist) | Pass through, login not required |
| 1.1 | `@AppLoginOnlyApi` | `StpAppUtil.checkLogin()`, then **return directly** |
| 1.2 | Other requests | `StpUtil.checkLogin()` |
| 1.3 | Forced password change flag | Only `/user/pc/auth/password`, `/logout`, `/me` allowed |
| 2 | `@LoginOnlyApi` | After passing, **return directly**, without validating the permission code |
| 3 | Database rule `sys_api_resource` | `StpUtil.checkPermission(rule.code)` |

Key points:

- **Annotation strategies take precedence over database rules.** `@LoginOnlyApi` makes the gateway skip permission code validation; do not use it for sensitive endpoints.
- **App endpoints return before permission rules.** Therefore `/order/app/**` passes as long as it has `@AppLoginOnlyApi`, without needing `system:order:list`.
- **Static excluded paths** (`satoken.permit.paths`) do not even enter the Sa-Token filter. Currently includes login/registration endpoints.

### 3.1 Fail-closed

`GatewaySecurityProperties`:

```yaml
satoken:
  api-permissions:
    fail-closed: true
    refresh-interval-ms: 60000
    whitelist-refresh-ms: 10000
```

Behavior of `checkApiPermission`:

```java
List<ApiPermissionRuleDTO> rules = apiPermissionRegistry.getRules();

if (failClosed && rules.isEmpty()) {
    throw new NotPermissionException("鉴权规则未加载，拒绝访问");
}

for (rule : rules) {
    if (match(method, path)) {
        StpUtil.checkPermission(rule.getCode());
        return;
    }
}

throw new NotPermissionException("接口未配置权限规则，拒绝访问");
```

The two 403s have different meanings:

- **"Authorization rules not loaded"**: the rule list is empty, usually because user-service was not yet providing `PermissionDubboClient` when the gateway started, or the Dubbo call failed;
- **"No permission rule configured for endpoint"**: rules loaded normally, but this `method + path` is not registered, and there is no annotation strategy.

### 3.2 Error responses

```java
setError(e -> {
    int status = 500;
    if (e instanceof NotLoginException) status = 401;
    else if (e instanceof NotPermissionException) status = 403;
    return SaResult.error(e.getMessage());
});
```

The response body is Sa-Token's `SaResult`:

```json
{"code": 403, "msg": "接口未配置权限规则，拒绝访问", "data": null}
```

Note that the field is `msg`, not `message` from `ApiResponse`. If the frontend wants unified handling, it needs to support both structures.

## 4. Rule loading

### 4.1 `ApiPermissionRegistry`

Source: `PermissionDubboClient`

| Method | Source table | Refresh |
| --- | --- | --- |
| `listEnabledWhitelists()` | `sys_api_whitelist` (`status=1`) | 10 seconds |
| `listApiPermissionRules()` | `sys_api_resource` (`status=1`) | 60 seconds |

Implementation details:

- `ApplicationReadyEvent` loads once immediately at startup;
- `@Scheduled(fixedDelayString = ...)` refreshes periodically;
- Dubbo calls are placed in `userRolePermThreadPool`, with a `get(5, TimeUnit.SECONDS)` timeout;
- **A failed load keeps the previous batch of rules**; one failure does not clear all rules;
- Rule ordering: exact paths first, then longer patterns first.

Sorting example:

```
/user/pc/users/*/reset-password   (exact, first)
/user/pc/users/**
/**
```

This prevents `/**` from matching first and invalidating more specific rules.

### 4.2 `AnnotatedApiRegistry`

Source: the Sets written by each service in Redis:

```
ping:gateway:api:public:*
ping:gateway:api:login:*
ping:gateway:api:app-login:*
```

- Scanned once every 10 seconds;
- Member format `METHOD|path`;
- Path variables have already been normalized to `*` by the service side;
- Longer paths take precedence.

### 4.3 Rule refresh latency

| Change | Effective latency |
| --- | --- |
| Modify `sys_api_resource` | ≤ 60 seconds |
| Modify `sys_api_whitelist` | ≤ 10 seconds |
| Add/modify an annotation on an endpoint and restart the service | ≤ 10 seconds after the service writes to Redis (annotation key TTL is 120 seconds; the service rewrites it every 30 seconds) |
| Modify routes | Depends on Nacos configuration refresh |

When troubleshooting, do not "assert it did not take effect immediately after the change"; wait for one refresh cycle first.

## 5. Where user permissions come from

`SaStpInterfaceImpl` only reads the token session:

```java
@Override
public List<String> getPermissionList(Object loginId, String loginType) {
    Object value = StpUtil.getTokenSession().get("PERMS");
    return value instanceof String s ? SaFoxUtil.convertStringToList(s) : List.of();
}
```

- At login, `PermissionCacheServiceImpl` in `user-service` writes `ROLES` / `PERMS`;
- The gateway does not query the database or call Dubbo, avoiding blocking the event loop;
- When the cache is missing, it returns empty, and `StpUtil.checkPermission` throws `NotPermissionException` (403).

Therefore:

```text
Login succeeds but gateway returns 403 → first check whether PERMS exists in the token session
```

### 5.1 Admins are not automatically allowed at the gateway

`@authz.has(...)` inside services allows the `admin` role / `*:*:*` directly; but **the gateway only recognizes permission codes in `PERMS`**.

This means:

- After adding a new permission code, even if the `admin` role can pass `@authz` inside the service, if that permission is not assigned to the admin role and does not enter the token session, the gateway will still return 403;
- Either assign the new permission to the admin role, or make admin's `PERMS` include a wildcard (need to confirm Sa-Token's matching strategy), or add `@LoginOnlyApi` to the endpoint (not recommended for sensitive endpoints).

## 6. Path normalization

`PathNormalizeWebFilter` runs at the highest priority:

```java
/order//api/./query/../list/  →  /order/app/list
```

Rules:

- Collapse consecutive slashes;
- Resolve `.` / `..`;
- Remove trailing slashes;
- **Write the normalized path back to the request**, ensuring authorization matching and the forwarded path are consistent.

`SaTokenConfigure` calls `PathNormalizeWebFilter.normalize(path)` once more before matching (idempotent), to prevent bypasses.

## 7. Other filters

| Class | Purpose |
| --- | --- |
| `CorsConfig` | `CorsWebFilter`, allows any origin pattern, credentials, and common methods |
| `TraceIdGlobalFilter` | Generates/inherits `X-Trace-Id`, generates `X-Request-Id`, writes them to downstream request headers (`HIGHEST_PRECEDENCE + 1`) |
| `ThreadPoolConfig` | `userRolePermThreadPool`, used for rule loading |
| `PermitConfig` | Binds `satoken.permit.paths` |
| `GatewaySecurityProperties` | Binds `satoken.api-permissions.*` |

`TraceIdGlobalFilter` and `bizTid` from `ping-log` are two separate identifiers: the gateway generates `X-Trace-Id`, and business services generate/inherit `X-Biz-Tid`.

## 8. Adding a route

Append to `gateway-service-${APP_ENV}.yml` in Nacos:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: payment-service
              uri: lb://payment-service
              order: 4
              predicates:
                - Path=/payment/**
```

Also ensure:

- `payment-service` is registered in Nacos (same namespace);
- The new endpoints register permission codes in `sys_api_resource`, or use an annotation strategy;
- If using `@PreAuthorize`, payment-service must also include `ping-web-spring-boot-starter`.

## 9. Debugging checklist

### 9.1 First distinguish "gateway rejection" from "service rejection"

```bash
# Through the gateway
curl -i http://localhost:9999/user/pc/users -H 'ping-token: Bearer <token>'

# Direct to the service (confirm whether the service itself is healthy)
curl -i http://localhost:8099/user/pc/users -H 'ping-token: Bearer <token>'
```

- Gateway 403 + `接口未配置权限规则` ("no permission rule configured for this endpoint") → rule/annotation issue;
- Gateway 401 → token missing/expired/wrong login domain;
- Direct connection also 403 → insufficient `@PreAuthorize` permission;
- Direct connection 200, gateway 403 → gateway rule or token session issue.

### 9.2 Check whether rules are loaded

```bash
docker compose logs gateway | grep -E '\[SaToken\]|\[ApiAnnotation\]'
```

Normal logs:

```
[SaToken] 接口权限规则已加载: 58 条
[SaToken] 白名单已刷新: 3 条
```

### 9.3 Check annotation strategies

```bash
redis-cli -p 16379 --scan --pattern 'ping:gateway:api:*'
redis-cli -p 16379 smembers ping:gateway:api:public:user-service
redis-cli -p 16379 smembers ping:gateway:api:app-login:user-service
```

### 9.4 Check database rules

```sql
SELECT permission_code, method, path, status
FROM sys_api_resource
WHERE status = 1
ORDER BY sort, id;
```

### 9.5 Check the token session

If the gateway returns 403 and the logs show insufficient permissions, confirm whether `PERMS` is written after the user logs in. You can check the user-service logs:

```
[PermCache] 已缓存 userId=1, roles=[admin], perms=[...]
```

## 10. Pitfalls

### 10.1 Gateway starts first with empty rules → site-wide 403

Docker Compose already uses healthcheck to make the gateway wait for user-service / order-service to be healthy. When starting manually locally, start user-service first, then the gateway; otherwise all requests in the first minute may be:

```json
{"code":403,"msg":"鉴权规则未加载，拒绝访问"}
```

Rules recover automatically after loading succeeds; no gateway restart is needed.

### 10.2 Treating `@LoginOnlyApi` as "authorized"

It only validates login; the gateway passes the request directly after it, without querying `sys_api_resource`. Any admin login user can access it. Sensitive endpoints must use database rules + `@PreAuthorize`.

### 10.3 403 after a new endpoint goes live

Check in order:

1. Does it have `@PublicApi` / `@LoginOnlyApi` / `@AppLoginOnlyApi`?
2. If there is no annotation, is `method + path` registered in `sys_api_resource`?
3. Have the rules refreshed within 60 seconds?
4. Does the user's token session have the corresponding permission code?
5. Does the path differ from the rule after gateway normalization?

### 10.4 Gateway 401, but the user is clearly logged in

- PC token calling App endpoints (or vice versa) → login domain mismatch;
- Incorrect `ping-token` request header format: `token-prefix: Bearer` is configured, so `Bearer ` must be included;
- Token has expired (`timeout: 86400`, `active-timeout: 1800`);
- The user was kicked offline by `invalidateCache`.

### 10.5 Wrong rule path

```sql
-- Assume the endpoint is GET /user/pc/users/1/profile
INSERT INTO sys_api_resource(permission_code, path, method, status)
VALUES ('system:user:list', '/user/pc/users', 'GET', 1);  -- does not match subpaths
```

It should be written as:

```sql
'/user/pc/users/**'
```

Or written exactly:

```sql
'/user/pc/users/*/profile'
```

In Ant style, `*` matches a single segment, and `**` matches multiple segments.

### 10.6 Accessing service ports directly bypasses the gateway

Gateway authorization only applies to requests through 9999. In production you should:

- Expose only the gateway port;
- Do not expose user-service / order-service ports externally;
- Keep `@PreAuthorize` inside services as a fallback.

### 10.7 CORS and credentials

`CorsConfig` uses `allowedOriginPattern("*") + allowCredentials(true)`. If the frontend uses cookies instead of tokens, ensure the browser allows credentials; the current main flow uses the `ping-token` request header and does not rely on cookies.

### 10.8 No `bizTid` in gateway logs

The gateway is WebFlux and does not load `ping-log-spring-boot-starter`. The gateway uses `X-Trace-Id`; business services use `X-Biz-Tid`. If unification is needed, additionally write `X-Biz-Tid` in the gateway.

## 11. Dependency approach

The gateway only depends on:

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-core</artifactId>
</dependency>
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-client</artifactId>
</dependency>
```

**Do not** depend on `ping-web-spring-boot-starter`, `user-pc`, or `user-app`; otherwise Spring MVC / MyBatis / business implementations would be brought into the WebFlux gateway.
