# ping-core：基础模型与 SPI

`ping-core` 是所有模块共享的“语言层”：统一响应、分页、用户上下文、接口访问策略注解、App 登录域工具、可扩展 SPI。

它**不依赖 Spring Boot**，只依赖 Sa-Token core、TransmittableThreadLocal、Lombok，因此网关（WebFlux）、servlet 服务、甚至普通 Java 模块都可以安全引用。

## 1. 内容一览

```
com.ping.core
├── annotation/
│   ├── PublicApi           # 免登录
│   ├── LoginOnlyApi        # 后台登录域，仅登录
│   └── AppLoginOnlyApi     # App 登录域，仅登录
├── context/
│   ├── UserContext         # 不可变用户上下文（ID + 角色 + 权限）
│   └── UserContextHolder   # TransmittableThreadLocal 持有器
├── dto/
│   ├── ApiResponse         # {code, message, data}
│   └── PageResult          # {list, total}
├── id/
│   └── DistributedIdGenerator   # nextId() SPI
├── security/
│   ├── SecurityConstants   # admin / *:*:*
│   ├── PermissionProvider  # 角色/权限数据来源 SPI
│   └── StpAppUtil          # StpLogic("app") 工具
└── trace/
    └── BizTraceIdGenerator # generate() SPI
```

## 2. 接口访问策略注解

### 2.1 三种策略

```java
@PublicApi          // 免登录：游客也能访问
@LoginOnlyApi       // 后台登录域：StpUtil 已登录即可，不校验权限码
@AppLoginOnlyApi    // App 登录域：StpAppUtil 已登录即可，不校验权限码
```

都可以标注在**方法**或**Controller 类**上。

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

### 2.2 优先级

方法级优先于类级，`@PublicApi` 优先级最高。完整顺序：

1. 方法上的 `@PublicApi` → 公开
2. 方法上的 `@AppLoginOnlyApi` → App 登录
3. 方法上的 `@LoginOnlyApi` → 后台登录
4. 类上的 `@PublicApi` → 公开
5. 类上的 `@AppLoginOnlyApi` → App 登录
6. 类上的 `@LoginOnlyApi` → 后台登录
7. 都没有 → 不声明策略（交给数据库规则或默认拒绝）

示例：

```java
@PublicApi
@RestController
public class MixedController {

    @LoginOnlyApi
    @GetMapping("/private")
    public String privateApi() { ... }   // 方法级 LoginOnly 覆盖类级 Public

    @GetMapping("/public")
    public String publicApi() { ... }    // 继承类级 Public
}
```

### 2.3 注解本身不会鉴权

注解只是“声明”。真正生效需要：

- **服务端**：`ping-web-spring-boot-starter` 的 `AnnotatedApiScanner` 扫描并写入 Redis；`ApiAccessInterceptor` / `AppApiAccessInterceptor` 做本地兜底；
- **网关**：`AnnotatedApiRegistry` 每 10 秒从 Redis 聚合，`SaTokenConfigure` 按策略放行或校验。

只引 `ping-core` 不会产生任何拦截。直连服务时，本地拦截器只会对 `@LoginOnlyApi` / `@AppLoginOnlyApi` 检查登录，不会校验权限码。

## 3. `UserContext` / `UserContextHolder`

### 3.1 结构

```java
public final class UserContext {
    private final Long userId;
    private final Set<String> roles;
    private final Set<String> permissions;
}
```

不可变，可安全跨线程传递。App 上下文额外带一个 `"app"` 角色标记。

### 3.2 常用方法

```java
Long userId = UserContextHolder.getUserId();
boolean login = UserContextHolder.isLogin();       // 后台或 App
boolean app = UserContextHolder.isAppUser();       // 带 "app" 角色
boolean pc = UserContextHolder.isPcUser();

Set<String> roles = UserContextHolder.getRoles();
boolean admin = UserContextHolder.hasRole("admin");
boolean canEdit = UserContextHolder.hasPermission("system:user:edit");
```

`hasPermission` 支持通配：

```java
UserContextHolder.hasPermission("system:user:add"); // 权限里有 system:user:* 也会返回 true
```

### 3.3 谁写入上下文

| 场景 | 写入者 |
| --- | --- |
| PC HTTP 请求 | `SaTokenAuthFilter`（`ping-web-spring-boot-starter`） |
| App HTTP 请求 | `AppTokenAuthFilter` |
| Dubbo 提供方 | `UserContextProviderFilter`（`ping-dubbo-spring-boot-starter`） |
| 异步线程 | 需要 TTL 包装（`TaskDecorator` / `TtlRunnable`） |

### 3.4 跨线程

`UserContextHolder` 使用 `TransmittableThreadLocal`：

- 普通子线程：自动继承；
- 线程池 / `@Async` / `CompletableFuture`：任务必须经过 `TtlRunnable` / `TtlExecutors` 包装。

```java
executor.submit(TtlRunnable.get(() -> {
    Long userId = UserContextHolder.getUserId();
    // ...
}));
```

`ping-web-spring-boot-starter` 会注册默认的 `TaskDecorator`（`TtlRunnable::get`）；自定义线程池时要自己加。

## 4. `StpAppUtil`

```java
public final class StpAppUtil {
    public static final String TYPE = "app";
    public static final StpLogic LOGIC = new StpLogic(TYPE);

    login / isLogin / checkLogin / getLoginId / getTokenValue / getTokenInfo / logout
}
```

与 `StpUtil` 的区别：

```java
StpUtil.login(1L);            // 后台登录域
StpAppUtil.login(1L);         // App 登录域（loginType = "app"）
```

Redis 中的 token、session 按 `loginType` 隔离。两个域可以使用相同的数值 ID，但不会互相串门。

## 5. `ApiResponse` / `PageResult`

```java
return ApiResponse.ok(data);
return ApiResponse.ok();
return ApiResponse.error(400, "参数错误");
return ApiResponse.error("参数错误");  // code=400
```

结构：

```json
{"code": 200, "message": "success", "data": {...}}
```

`PageResult`：

```java
PageResult<UserListItemDTO> page = PageResult.of(list, total);
// {"list": [...], "total": 42}
```

注意：`ApiResponse.code` 是**业务码**，HTTP 状态码由 `ResponseEntity` 决定。例如登录失败返回：

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

用途：`SaTokenAuthFilter` 发现 token session 里没有 `ROLES` / `PERMS` 时，用它兜底查权限并回写。

- user-service：`DbPermissionProvider`（查库）；
- order-service：`DubboPermissionProvider`（Dubbo 调 user-service）；
- 没有实现时：过滤器跳过兜底，权限为空。

### 6.2 `DistributedIdGenerator`

```java
public interface DistributedIdGenerator {
    String nextId();
}
```

实现由 `ping-id-spring-boot-starter` 提供。

### 6.3 `BizTraceIdGenerator`

```java
public interface BizTraceIdGenerator {
    String generate();
}
```

实现由 `ping-log-spring-boot-starter` 提供：优先用 `DistributedIdGenerator`，否则 UUID。

## 7. `SecurityConstants`

```java
public static final String ADMIN_ROLE = "admin";
public static final String SUPER_PERMISSION = "*:*:*";
```

`AuthorizationService` 判断管理员时使用：

```java
ROLE_admin 或 *:*:* → isAdmin() = true
```

## 8. 常见坑

### 8.1 以为 `@PublicApi` 会自动放行直连服务

`@PublicApi` 在服务内部的 `ApiAccessInterceptor` 里是“不做任何校验”，所以直连本来就不会被拦；但网关是否放行取决于 `AnnotatedApiRegistry` 是否读到了 Redis 里的注册项。Redis 不可用或服务未启动扫描时，网关照旧默认拒绝。

### 8.2 在网关里使用 `UserContextHolder`

网关的鉴权发生在 `SaReactorFilter` 中，**不会**经过 servlet 的 `SaTokenAuthFilter`，所以网关请求线程里 `UserContextHolder` 通常为空。网关需要用户信息时：

- 用 `StpUtil` / `StpAppUtil` 读取 token；
- 或调用 `StpUtil.getTokenSession()` 读取会话字段；
- 不要把 servlet 的上下文假设搬到 WebFlux。

### 8.3 直接 `new UserContext` 后忘记清理

```java
UserContextHolder.set(ctx);
try {
    // ...
} finally {
    UserContextHolder.clear();
}
```

线程池会复用线程，不清理会导致下一个请求读到上一个用户。已有的过滤器都在 `finally` 中清理；自己写过滤器/拦截器时要遵守。

### 8.4 用 `UserContextHolder.getUserId()` 判断“后台用户”

App 用户也有 `userId`。需要区分时用 `isAppUser()` / `isPcUser()`，不要用“userId 是否存在”推断域。

### 8.5 Long 型 ID 直接返回前端

雪花 ID 是 19 位，超过 JS 安全整数范围。`AppUserResponse.userId`、`AppLoginResponse.userId` 已经用 `String`；新增 DTO 时保持这个约定。

### 8.6 权限通配写法不统一

`UserContext.hasPermission` 只识别 `xxx:*` 后缀通配（如 `system:*` 匹配 `system:user:add`），不支持 `*` 中间匹配或正则。权限码设计时保持“前缀 + 冒号”的层级结构。

## 9. 依赖方式

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-core</artifactId>
</dependency>
```

所有模块都应通过根 `pom.xml` 的依赖管理引入，不写版本号。
