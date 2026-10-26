# gateway-service：Spring Cloud Gateway

`gateway-service` 是后端唯一入口，负责路由、认证、接口级鉴权、跨域和链路 ID。它基于 **Spring Cloud Gateway（WebFlux）**，不能引入 Spring MVC。

## 1. 启动与配置

```bash
# 先在根目录执行 mvn clean install -DskipTests
mvn -pl ping-gateway/gateway-service spring-boot:run

# 配置命名空间默认跟随 APP_ENV（dev）；如需 public：
# NACOS_CONFIG_NAMESPACE="" mvn -pl ping-gateway/gateway-service spring-boot:run
```

- 端口：9999
- 管理端口：8081（`/actuator/health`、`/actuator/prometheus`、`/actuator/gateway`）
- 启动类：`GatewayApplication`（`@EnableDiscoveryClient` + `@EnableScheduling`）

`src/main/resources/application.yml` 只保留 Nacos 引导配置；路由、端口、Dubbo、Sa-Token 策略都在 Nacos：

- `application-${APP_ENV}.yml`：Redis、Sa-Token（`token-name: ping-token`）、Dubbo registry、Actuator；
- `gateway-service-${APP_ENV}.yml`：端口、路由、`satoken.permit.paths`、`satoken.api-permissions.*`。

## 2. 路由

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

修改路由只需要改 Nacos 配置并发布，不需要重新打包网关（`spring-cloud-starter-alibaba-nacos-config` 支持刷新；是否动态生效取决于配置项）。

## 3. 鉴权流水线

入口是 `SaTokenConfigure` 中的 `SaReactorFilter`：

```
addInclude("/**")
addExclude("/favicon.ico", "/actuator/**", satoken.permit.paths...)
```

对每个请求按以下顺序处理：

| 步骤 | 判断 | 动作 |
| --- | --- | --- |
| 0 | `OPTIONS` | 直接放行（CORS 预检） |
| 0.1 | `@PublicApi`（Redis 注解策略） | 放行 |
| 0.2 | `sys_api_whitelist`（DB 白名单） | 放行，不要求登录 |
| 1.1 | `@AppLoginOnlyApi` | `StpAppUtil.checkLogin()`，通过后**直接返回** |
| 1.2 | 其他请求 | `StpUtil.checkLogin()` |
| 1.3 | 强制改密标记 | 只允许 `/user/pc/auth/password`、`/logout`、`/me` |
| 2 | `@LoginOnlyApi` | 通过后**直接返回**，不校验权限码 |
| 3 | 数据库规则 `sys_api_resource` | `StpUtil.checkPermission(rule.code)` |

关键点：

- **注解策略优先于数据库规则。** `@LoginOnlyApi` 会让网关跳过权限码校验；敏感接口不要用它。
- **App 接口在权限规则之前返回。** 因此 `/order/app/**` 只要 `@AppLoginOnlyApi` 就能通过，不需要 `system:order:list`。
- **静态排除路径**（`satoken.permit.paths`）连 Sa-Token 过滤器都不进。当前包含登录/注册接口。

### 3.1 失败即关闭（fail-closed）

`GatewaySecurityProperties`：

```yaml
satoken:
  api-permissions:
    fail-closed: true
    refresh-interval-ms: 60000
    whitelist-refresh-ms: 10000
```

`checkApiPermission` 的行为：

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

两种 403 的含义不同：

- **「鉴权规则未加载」**：规则列表为空，通常是网关启动时 user-service 还没提供 `PermissionDubboClient`，或 Dubbo 调用失败；
- **「接口未配置权限规则」**：规则加载正常，但这个 `method + path` 没有登记，且没有注解策略。

### 3.2 错误响应

```java
setError(e -> {
    int status = 500;
    if (e instanceof NotLoginException) status = 401;
    else if (e instanceof NotPermissionException) status = 403;
    return SaResult.error(e.getMessage());
});
```

返回体是 Sa-Token 的 `SaResult`：

```json
{"code": 403, "msg": "接口未配置权限规则，拒绝访问", "data": null}
```

注意字段是 `msg`，不是 `ApiResponse` 的 `message`。前端如果要统一处理，需要兼容两种结构。

## 4. 规则加载

### 4.1 `ApiPermissionRegistry`

来源：`PermissionDubboClient`

| 方法 | 来源表 | 刷新 |
| --- | --- | --- |
| `listEnabledWhitelists()` | `sys_api_whitelist`（`status=1`） | 10 秒 |
| `listApiPermissionRules()` | `sys_api_resource`（`status=1`） | 60 秒 |

实现细节：

- 启动时 `ApplicationReadyEvent` 立即加载一次；
- `@Scheduled(fixedDelayString = ...)` 定时刷新；
- Dubbo 调用放到 `userRolePermThreadPool`，`get(5, TimeUnit.SECONDS)` 超时；
- **加载失败保留上一批规则**，不会因为一次失败把所有规则清空；
- 规则排序：精确路径优先，其次更长的模式优先。

排序示例：

```
/user/pc/users/*/reset-password   （精确，优先）
/user/pc/users/**
/**
```

避免 `/**` 抢先匹配导致更具体的规则失效。

### 4.2 `AnnotatedApiRegistry`

来源：Redis 中各个服务写入的 Set：

```
ping:gateway:api:public:*
ping:gateway:api:login:*
ping:gateway:api:app-login:*
```

- 每 10 秒 scan 一次；
- 成员格式 `METHOD|path`；
- 路径变量已被服务端规范化为 `*`；
- 更长的路径优先匹配。

### 4.3 规则刷新延迟

| 变更 | 生效延迟 |
| --- | --- |
| 改 `sys_api_resource` | ≤ 60 秒 |
| 改 `sys_api_whitelist` | ≤ 10 秒 |
| 给接口加/改注解并重启服务 | 服务写入 Redis 后 ≤ 10 秒（注解 key TTL 120 秒，服务每 30 秒续写） |
| 改路由 | 取决于 Nacos 配置刷新 |

排查时不要“改完立刻断言没生效”，先等一个刷新周期。

## 5. 用户权限从哪来

`SaStpInterfaceImpl` 只读 token session：

```java
@Override
public List<String> getPermissionList(Object loginId, String loginType) {
    Object value = StpUtil.getTokenSession().get("PERMS");
    return value instanceof String s ? SaFoxUtil.convertStringToList(s) : List.of();
}
```

- 登录时 `user-service` 的 `PermissionCacheServiceImpl` 写入 `ROLES` / `PERMS`；
- 网关不查库、不调 Dubbo，避免阻塞事件循环；
- 缓存缺失时返回空，`StpUtil.checkPermission` 抛 `NotPermissionException`（403）。

因此：

```text
登录成功但网关 403 → 先检查 token session 里有没有 PERMS
```

### 5.1 管理员不会在网关自动放行

服务内的 `@authz.has(...)` 对 `admin` 角色 / `*:*:*` 直接放行；但**网关只认 `PERMS` 里的权限码**。

这意味着：

- 新增一个权限码后，即使 `admin` 角色在服务内能通过 `@authz`，如果该权限没有分配给 admin 角色、没有进入 token session，网关仍会 403；
- 要么把新权限分配给 admin 角色，要么让 admin 的 `PERMS` 包含通配（需确认 Sa-Token 的匹配策略），要么给接口加 `@LoginOnlyApi`（不推荐用于敏感接口）。

## 6. 路径规范化

`PathNormalizeWebFilter` 在最高优先级执行：

```java
/order//api/./query/../list/  →  /order/app/list
```

规则：

- 折叠连续斜杠；
- 解析 `.` / `..`；
- 去除末尾斜杠；
- **把规范化后的路径写回请求**，保证鉴权匹配和转发路径一致。

`SaTokenConfigure` 在匹配前再调用一次 `PathNormalizeWebFilter.normalize(path)`（幂等），避免绕过。

## 7. 其他过滤器

| 类 | 作用 |
| --- | --- |
| `CorsConfig` | `CorsWebFilter`，允许任意 origin pattern、携带凭证、常用方法 |
| `TraceIdGlobalFilter` | 生成/继承 `X-Trace-Id`，生成 `X-Request-Id`，写入下游请求头（`HIGHEST_PRECEDENCE + 1`） |
| `ThreadPoolConfig` | `userRolePermThreadPool`，供规则加载使用 |
| `PermitConfig` | 绑定 `satoken.permit.paths` |
| `GatewaySecurityProperties` | 绑定 `satoken.api-permissions.*` |

`TraceIdGlobalFilter` 与 `ping-log` 的 `bizTid` 是两套标识：网关生成 `X-Trace-Id`，业务服务生成/继承 `X-Biz-Tid`。

## 8. 新增一条路由

在 Nacos 的 `gateway-service-${APP_ENV}.yml` 中追加：

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

同时确保：

- `payment-service` 已注册到 Nacos（同一命名空间）；
- 新接口在 `sys_api_resource` 登记权限码，或使用注解策略；
- 如果走 `@PreAuthorize`，payment-service 也要引入 `ping-web-spring-boot-starter`。

## 9. 调试清单

### 9.1 先区分“网关拒绝”还是“服务拒绝”

```bash
# 经网关
curl -i http://localhost:9999/user/pc/users -H 'ping-token: Bearer <token>'

# 直连服务（确认服务本身是否正常）
curl -i http://localhost:8099/user/pc/users -H 'ping-token: Bearer <token>'
```

- 网关 403 + `接口未配置权限规则` → 规则/注解问题；
- 网关 401 → token 缺失/过期/登录域不对；
- 直连也 403 → `@PreAuthorize` 权限不足；
- 直连 200、网关 403 → 网关规则或 token session 问题。

### 9.2 检查规则是否加载

```bash
docker compose logs gateway | grep -E '\[SaToken\]|\[ApiAnnotation\]'
```

正常日志：

```
[SaToken] 接口权限规则已加载: 58 条
[SaToken] 白名单已刷新: 3 条
```

### 9.3 检查注解策略

```bash
redis-cli -p 16379 --scan --pattern 'ping:gateway:api:*'
redis-cli -p 16379 smembers ping:gateway:api:public:user-service
redis-cli -p 16379 smembers ping:gateway:api:app-login:user-service
```

### 9.4 检查数据库规则

```sql
SELECT permission_code, method, path, status
FROM sys_api_resource
WHERE status = 1
ORDER BY sort, id;
```

### 9.5 检查 token 会话

如果网关 403 且日志显示权限不足，确认用户登录后 `PERMS` 是否写入。可以看 user-service 日志：

```
[PermCache] 已缓存 userId=1, roles=[admin], perms=[...]
```

## 10. 常见坑

### 10.1 网关先启动，规则为空 → 全站 403

Docker Compose 已通过 healthcheck 让 gateway 等待 user-service / order-service healthy。本地手动启动时，先起 user-service，再起 gateway；否则第一分钟内可能所有请求都是：

```json
{"code":403,"msg":"鉴权规则未加载，拒绝访问"}
```

规则加载成功后会自动恢复，不需要重启网关。

### 10.2 把 `@LoginOnlyApi` 当成“有权限”

它只校验登录，网关在它之后直接放行，不查 `sys_api_resource`。任何后台登录用户都能访问。敏感接口必须用数据库规则 + `@PreAuthorize`。

### 10.3 新接口上线后 403

按顺序检查：

1. 有没有 `@PublicApi` / `@LoginOnlyApi` / `@AppLoginOnlyApi`？
2. 没有注解的话，`sys_api_resource` 有没有登记 `method + path`？
3. 规则是否在 60 秒内刷新？
4. 用户 token session 里有没有对应权限码？
5. 路径是否被网关规范化后与规则不一致？

### 10.4 网关 401，但用户明明登录了

- PC token 调 App 接口（或反之）→ 登录域不匹配；
- `ping-token` 请求头格式不对：配置了 `token-prefix: Bearer`，要带 `Bearer `；
- token 已过期（`timeout: 86400`，`active-timeout: 1800`）；
- 用户被 `invalidateCache` 踢下线。

### 10.5 规则路径写错

```sql
-- 假设接口是 GET /user/pc/users/1/profile
INSERT INTO sys_api_resource(permission_code, path, method, status)
VALUES ('system:user:list', '/user/pc/users', 'GET', 1);  -- 匹配不到子路径
```

应该写：

```sql
'/user/pc/users/**'
```

或者精确写：

```sql
'/user/pc/users/*/profile'
```

Ant 风格中 `*` 匹配单段，`**` 匹配多段。

### 10.6 直接访问服务端口绕过网关

网关的鉴权只对经过 9999 的请求生效。生产环境应：

- 只暴露网关端口；
- user-service / order-service 端口不对外；
- 服务内保留 `@PreAuthorize` 作为兜底。

### 10.7 CORS 与凭证

`CorsConfig` 使用 `allowedOriginPattern("*") + allowCredentials(true)`。如果前端用 cookie 而不是 token，需要确保浏览器允许凭证；当前主流程是 `ping-token` 请求头，不依赖 cookie。

### 10.8 网关日志里没有 `bizTid`

网关是 WebFlux，不加载 `ping-log-spring-boot-starter`。网关用 `X-Trace-Id`；业务服务用 `X-Biz-Tid`。需要统一时在网关额外写入 `X-Biz-Tid`。

## 11. 依赖方式

网关只依赖：

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

**不要**依赖 `ping-web-spring-boot-starter`、`user-pc`、`user-app`，否则会把 Spring MVC / MyBatis / 业务实现带进 WebFlux 网关。
