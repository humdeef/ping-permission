# ping-web-spring-boot-starter：Web 安全 starter

这个 starter 把 servlet 微服务的通用安全能力打包成“引依赖即生效”：

- 把 Sa-Token 登录态桥接到 Spring Security，支持 `@PreAuthorize`；
- 扫描 `@PublicApi` / `@LoginOnlyApi` / `@AppLoginOnlyApi`，自动注册到 Redis 供网关聚合；
- App 登录域桥接与本地兜底；
- 统一异常处理；
- 用户上下文跨线程传递（`TaskDecorator`）。

> **只适用于 servlet 应用。** 网关是 WebFlux，不能引用本 starter（会引入 Spring MVC 与 servlet 过滤器）。

## 1. 自动装配内容

`AutoConfiguration.imports`：

```
com.ping.web.config.CommonSecurityAutoConfiguration
com.ping.web.config.AnnotatedApiAutoConfiguration
```

### 1.1 `CommonSecurityAutoConfiguration`

条件：

```java
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnClass({HttpSecurity.class, StpUtil.class})
```

注册的 Bean：

| Bean | 作用 |
| --- | --- |
| `SecurityFilterChain` | **HTTP 层全部 `permitAll`**，认证鉴权交给网关 + 方法级注解 |
| `PasswordEncoder` | BCrypt |
| `SaTokenAuthFilter` | 读 token session 的 `ROLES` / `PERMS`，桥接到 Spring Security |
| `AuthorizationService`（`@authz`） | `@PreAuthorize("@authz.has(...)")` |
| `ApiAccessInterceptor` | `@LoginOnlyApi` 的服务内登录兜底 |
| `AppTokenAuthFilter` | App 登录态桥接（`StpAppUtil`） |
| `AppApiAccessInterceptor` | `@AppLoginOnlyApi` 的服务内登录兜底 |
| `TaskDecorator` | `TtlRunnable::get`，让 `@Async` 传递用户上下文 |

### 1.2 `AnnotatedApiAutoConfiguration`

条件：

```java
@ConditionalOnClass({StringRedisTemplate.class, RequestMappingHandlerMapping.class})
```

它创建 `AnnotatedApiScanner`：

- 应用启动后扫描所有 `@RequestMapping` 方法；
- 按“方法级优先于类级”的规则解析策略；
- 把 `METHOD|path` 写入 Redis Set；
- 每 30 秒续写一次，TTL 120 秒；
- 应用关闭时删除当前服务的 key。

Redis key：

```
ping:gateway:api:public:{serviceName}
ping:gateway:api:login:{serviceName}
ping:gateway:api:app-login:{serviceName}
```

示例：

```
SADD ping:gateway:api:login:user-service "GET|/user/pc/auth/me"
SADD ping:gateway:api:public:user-service "POST|/user/pc/auth/login"
SADD ping:gateway:api:app-login:user-service "GET|/user/app/auth/me"
```

路径变量会被规范化为 `*`：

```
/order/app/{id}  →  /order/app/*
```

网关的 `AnnotatedApiRegistry` 每 10 秒 scan 这些 key，聚合为内存规则。

## 2. 使用方式

### 2.1 引入依赖

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-web-spring-boot-starter</artifactId>
</dependency>
```

同时需要（通常已由业务模块带入）：

- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-data-redis`
- `sa-token-spring-boot4-starter`
- `sa-token-redis-jackson`

### 2.2 写 Controller

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

- `@PublicApi`：网关直接放行；
- `@LoginOnlyApi`：网关校验后台登录，不校验权限码；
- `@PreAuthorize`：服务内方法级校验；网关侧还需要在 `sys_api_resource` 登记 `demo:list` 规则，否则请求会被网关默认拒绝。

### 2.3 权限表达式

```java
@PreAuthorize("@authz.has('system:user:list')")   // 管理员自动通过
@PreAuthorize("@authz.hasRole('ops')")            // 角色
@PreAuthorize("@authz.isAdmin()")                 // 仅管理员
```

也可以使用原生 Spring Security：

```java
@PreAuthorize("hasAuthority('system:user:list')") // 管理员不会自动通过
```

推荐统一用 `@authz`，避免每个接口重复写管理员判断。

### 2.4 自定义权限数据源

如果服务没有把权限写进 token session，可以实现 `PermissionProvider`：

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

`SaTokenAuthFilter` 在 token session 为空时会调用它，并回写会话。order-service 的 `DubboPermissionProvider` 就是通过 Dubbo 调 user-service。

### 2.5 自定义异常响应

`GlobalExceptionHandler` 已处理：

| 异常 | HTTP | code |
| --- | --- | --- |
| `MethodArgumentNotValidException` | 400 | 400 |
| `DuplicateKeyException` | 400 | 400 |
| `AccessDeniedException` | 403 | 403 |
| `RuntimeException` | 400（`ErrorResponse` 保留原状态） | 对应值 |
| 其他 `Exception` | 500 | 500 |

需要覆盖时，定义自己的 `@RestControllerAdvice` 并提高优先级（`@Order`）。

## 3. 服务内兜底拦截器

`ApiAccessInterceptor` 和 `AppApiAccessInterceptor` 注册在 `/**`，在 Controller 方法执行前检查：

```java
// @LoginOnlyApi：后台未登录 → 401
if (isLoginOnly && !isPublic && !StpUtil.isLogin()) { ... }

// @AppLoginOnlyApi：App 未登录 → 401
```

它们只检查**登录**，不检查权限码。权限码由：

- 网关：`sys_api_resource` 规则；
- 服务内：`@PreAuthorize`。

两层共同保证“绕过网关直连服务”时，敏感接口仍然受方法级权限保护。

## 4. 常见坑

### 4.1 以为服务内 `permitAll` 就是不安全

`SecurityFilterChain` 确实放行所有 HTTP 请求，但这只是把“入口认证”交给网关。服务内仍有：

- `@PreAuthorize` 方法级校验；
- `@LoginOnlyApi` / `@AppLoginOnlyApi` 拦截器。

如果某个接口既没有注解也没有 `@PreAuthorize`，直连服务就是完全开放的。**敏感接口必须至少有一层方法级校验。**

### 4.2 注解写了但网关看不到

检查顺序：

1. 服务是否为 servlet 应用（WebFlux 不会加载 `AnnotatedApiAutoConfiguration`）；
2. 是否有 `StringRedisTemplate`（Redis 连接正常）；
3. 注解是否在 Controller 方法/类上（Service 上的注解不会被扫描）；
4. 是否等待了网关刷新周期（10 秒）；
5. Redis key 是否被写入：

```bash
redis-cli -p 16379 smembers ping:gateway:api:login:user-service
```

### 4.3 多个实例互相删 key

`AnnotatedApiScanner.stop()` 在应用关闭时删除当前服务的三个 key。如果同一服务有多个实例，某个实例下线会短暂删掉 key；其他实例会在 30 秒内重新写入。网关有 120 秒 TTL 的内存兜底吗？没有——网关每 10 秒重新 scan，所以最坏情况下有约 10 秒的策略空窗。

如果对此敏感，可以：

- 让 `stop()` 不删除 key，依赖 TTL 自然过期；
- 或给 key 增加实例维度，网关聚合时取并集。

当前实现适合单实例/小规模部署，扩容前需要评估。

### 4.4 `@PublicApi` 被方法级注解覆盖

```java
@PublicApi
@RestController
public class DemoController {

    @LoginOnlyApi
    @GetMapping("/private")
    public String privateApi() { ... }  // 方法级 LoginOnly 生效，不是公开
}
```

这是有意为之：方法级更具体。不要把类级 `@PublicApi` 当成“类内全部公开”的绝对保证。

### 4.5 权限为空导致 403

`SaTokenAuthFilter` 从 token session 读 `ROLES` / `PERMS`。如果：

- 用户是用旧版本登录的；
- 权限缓存被清理；
- Redis 中 token session 过期；

则 authorities 为空，`@PreAuthorize` 会拒绝。配置了 `PermissionProvider` 时会兜底查一次并回写；没有配置就只能重新登录。

### 4.6 在 WebFlux 服务里引用

症状：启动报 MVC/WebFlux 冲突，或 Sa-Token servlet filter 不生效。网关请只依赖 `ping-core`，使用自己的 `SaReactorFilter` 和 `SaStpInterfaceImpl`。

### 4.7 自定义 `TaskDecorator` 覆盖了默认实现

```java
@Bean
public TaskDecorator myDecorator() { ... }
```

`CommonSecurityAutoConfiguration` 的默认 `TaskDecorator` 带 `@ConditionalOnMissingBean`，会被你的 Bean 覆盖。如果你的实现只传 MDC 不传 `UserContext`，异步任务里 `UserContextHolder` 就会为空。

## 5. 依赖方式

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-web-spring-boot-starter</artifactId>
</dependency>
```

适用：`user-service`、`order-service` 等 servlet 业务服务。**不适用**：gateway（WebFlux）。
