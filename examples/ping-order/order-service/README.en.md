# order-service: Order sample service

`order-service` is the sample business service under `examples/ping-order`, demonstrating how an ordinary Servlet service integrates authentication, authorization, Dubbo, trace ID, and async context. It is not the permission core and can be copied or deleted directly.

## 1. Startup

```bash
# First run mvn clean install -DskipTests in the root directory
mvn -pl examples/ping-order/order-service spring-boot:run

# The config namespace follows APP_ENV (dev) by default; for public:
# NACOS_CONFIG_NAMESPACE="" mvn -pl examples/ping-order/order-service spring-boot:run
```

- Port: 8100
- Management port: 8102
- Startup class: `OrderServiceApplication` (`@EnableDiscoveryClient`, `@EnableDubbo`)
- Configuration: Nacos `application-${APP_ENV}.yml` + `order-service-${APP_ENV}.yml`

## 2. Code structure

```
com.ping.order
├── OrderServiceApplication
├── config/
│   ├── AsyncExecutorConfig      # ttAsyncExecutor + MDC + TTL
│   ├── DubboPermissionProvider  # Permission fallback: call user-service via Dubbo
│   ├── MyBatisPlusConfig        # MapperScan, pagination, optimistic locking, auto-fill
│   └── OrderMetaObjectHandler   # Audit fields + create_time/update_time
├── controller/
│   ├── app/OrderAppController   # /order/app/**, App login domain
│   ├── pc/OrderPcController     # /order/pc/**, backend login domain (sample)
│   └── pc/OrderPcOrderController# /order/pc/orders/**, order management
├── entity/
│   ├── BizOrder                 # ping_order, order ownership + last modifier
│   └── BizOrderOperationLog     # ping_order_operation_log
├── mapper/
│   ├── BizOrderMapper
│   └── BizOrderOperationLogMapper
├── model/
│   ├── OrderStatus              # State machine
│   ├── OrderOperation           # Operation type constants
│   ├── CreateOrderRequest / UpdateOrderRequest
│   ├── OrderResponse
│   └── OrderNameResponse
├── service/
│   ├── OrderLocalService
│   ├── OrderService / impl/OrderServiceImpl   # Order read/write, state machine, audit logs
│   └── OrderDubboServiceImpl                  # Exposes OrderDubboClient
```

## 3. Endpoints

Online docs: open `http://localhost:9999/docs` after startup and switch to "订单服务". This service only exposes OpenAPI JSON (`/v3/api-docs`); the UI is aggregated by the gateway and disabled in production by default.

### 3.1 PC: `/order/pc/**`

```java
@RestController
@RequestMapping("/order/pc")
@PreAuthorize("hasAuthority('system:order:list')")
public class OrderPcController {

    @GetMapping("/queryOrderByUser")
    @LoginOnlyApi
    public ApiResponse<OrderDTO> queryOrderByUser() {
        Long userId = UserContextHolder.getUserId();
        ...
    }

    @GetMapping("/{userId}")
    public ApiResponse<OrderDTO> getOrder(@PathVariable Long userId) {
        return ApiResponse.ok(orderLocalService.getOrderByUserId(userId));
    }
}
```

- Class-level `@PreAuthorize("hasAuthority('system:order:list')")`;
- The gateway rule for GET `/order/**` is `system:order:list` (`sys_api_resource`);
- `@LoginOnlyApi` only affects the gateway (skips the permission code) and does not bypass the class-level `@PreAuthorize`;
- Calls user-service via `OrderLocalServiceImpl` → `UserDubboClient.getUserNameById()`.

### 3.2 App: `/order/app/**`

```java
@RestController
@RequestMapping("/order/app")
@AppLoginOnlyApi
public class OrderAppController {

    @DubboReference(check = false, timeout = 3000)
    private AppUserDubboClient appUserDubboClient;

    @GetMapping("/queryOrderByUser")
    public ApiResponse<OrderDTO> queryOrderByUser() {
        Long userId = UserContextHolder.getUserId();
        String username = appUserDubboClient.getNicknameById(userId);
        ...
    }

    @GetMapping("/queryOrderByUser/v1")
    @PublicApi
    public ApiResponse<OrderDTO> queryOrderByUserV1() { ... }
}
```

- Class-level `@AppLoginOnlyApi`: the gateway only validates App login;
- Method-level `@PublicApi` overrides class-level: `/v1` is login-free;
- Uses `AppUserDubboClient` to query App users, avoiding ID confusion with `sys_user`.

### 3.3 Order read/write: `/order/pc/orders` and `/order/app/orders`

Backend management endpoints are in `OrderPcOrderController`, declaring permission codes per method. Orders can only be created by App users; the backend does not provide `POST /order/pc/orders`:

| Method and path | Permission code |
| --- | --- |
| `GET /order/pc/orders` | `system:order:list` |
| `GET /order/pc/orders/{id}` | `system:order:list` |
| `GET /order/pc/orders/{id}/logs` | `system:order:list` |
| `PUT /order/pc/orders/{id}` | `system:order:edit` |
| `POST /order/pc/orders/{id}/pay` | `system:order:edit` |
| `POST /order/pc/orders/{id}/cancel` | `system:order:edit` |
| `POST /order/pc/orders/{id}/complete` | `system:order:edit` |
| `DELETE /order/pc/orders/{id}` | `system:order:delete` |

App endpoints are in `OrderAppController` and only allow operating on the caller's own orders:

```java
@GetMapping("/orders")            // Current user's order pagination
@GetMapping("/orders/{id}")       // Not the owner → 403
@PostMapping("/orders")           // Owner = current App user
@PutMapping("/orders/{id}")       // Pending payment only
@PostMapping("/orders/{id}/pay")  // Pending payment → paid
@PostMapping("/orders/{id}/cancel") // Pending payment → cancelled
```

Order state machine: `pending payment → paid/cancelled`, `paid → completed`. App users cannot complete or delete orders.

Every write operation inserts a record into `ping_order_operation_log` (operation type, operator type/ID/name, before/after status, change summary); the main table simultaneously updates `update_by_type/id/name` and `version`. An optimistic locking conflict (`updateById` returns 0) reports "Order has been modified by someone else, please refresh and retry".

## 4. Permission fallback: `DubboPermissionProvider`

```java
@DubboReference(check = false, timeout = 3000)
private PermissionDubboClient permissionDubboClient;

@Override
public List<String> roles(Long userId) {
    UserInfoDTO info = permissionDubboClient.getUserInfoById(userId.intValue());
    return info == null ? List.of() : info.getRoles();
}
```

Purpose: when the token session has no `ROLES` / `PERMS`, `SaTokenAuthFilter` calls it as a fallback and writes back to the session.

- The normal login flow does not reach here (user-service already wrote the cache at login);
- Triggered only in scenarios such as old tokens, cleared caches, and cross-service tokens;
- On call failure, returns an empty list and does not block the request.

## 5. Async and context

`AsyncExecutorConfig`:

```java
@Bean("ttAsyncExecutor")
public AsyncTaskExecutor orderAsyncExecutor(...) {
    executor.setTaskDecorator(runnable -> mdcTaskDecorator.decorate(TtlRunnable.get(runnable)));
}
```

- `TtlRunnable`: propagates `UserContextHolder` (user ID / roles / permissions);
- `mdcTaskDecorator`: copies MDC (`bizTid`);
- Rejection policy `CallerRunsPolicy`.

Example:

```java
CompletableFuture.runAsync(() -> {
    Long userId = UserContextHolder.getUserId(); // has a value
    log.info("[trace] bizTid={}", MDC.get("bizTid")); // has a value
}, orderAsyncExecutor);
```

If you create your own thread pool, you need the same wrapping; otherwise both the context and `bizTid` will be lost.

## 6. Configuration

Nacos `order-service-${APP_ENV}.yml`:

```yaml
server:
  port: 8100
management:
  server:
    port: 8102
dubbo:
  application:
    name: dubbo-order-service
  protocol:
    name: tri
    port: 20881
    host: ${DUBBO_HOST:127.0.0.1}
# Order table datasource (shares the ping database with user-service)
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:ping}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
mybatis-plus:
  mapper-locations: classpath*:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

The order tables live in the same `ping` database and are created by this service's Flyway migration at startup (`src/main/resources/db/migration/V1__init_order_schema.sql`, which contains `ping_order` and `ping_order_operation_log`); the permission rules for the order APIs are written to `sys_api_resource` by the user-service migration.

The shared configuration `application-${APP_ENV}.yml` provides Redis, Sa-Token, Dubbo registry, Actuator.

## 7. Pitfalls

### 7.1 Dubbo cannot find user-service at startup

`@DubboReference(check = false)` allows startup, but calls will fail. Confirm:

- user-service is started and registered in the same Nacos namespace;
- `dubbo.registry.parameters.namespace` is consistent (`dubbo-${APP_ENV}`);
- Network/port is reachable.

`DubboPermissionProvider` catches exceptions and returns empty permissions, so requests will not directly 500, but it will result in insufficient permissions.

### 7.2 Gateway 403, direct connection works

This means the gateway rules did not match or the token permissions are insufficient. Check:

```sql
SELECT permission_code, method, path FROM sys_api_resource WHERE path LIKE '/order%';
```

Current seed data (the gateway prioritizes exact paths, then longest matches):

```
system:order:list   GET    /order/**
system:order:edit   PUT    /order/**
system:order:delete DELETE /order/**
system:order:list   GET    /order/pc/orders/**
system:order:edit   PUT    /order/pc/orders/**
system:order:delete DELETE /order/pc/orders/**
system:order:edit   POST   /order/pc/orders/*/pay
system:order:edit   POST   /order/pc/orders/*/cancel
system:order:edit   POST   /order/pc/orders/*/complete
```

`/order/app/**` is annotated with `@AppLoginOnlyApi`; the gateway only validates App login and does not match these permission codes. PC paths without an exact match will fail-closed and return `403 接口未配置权限规则，拒绝访问` ("no permission rule configured for this endpoint; access denied", for example the removed `POST /order/pc/orders`).

### 7.3 App endpoints use the wrong user client

```java
// Wrong: App user ID passed to the backend user contract
userDubboClient.getUserNameById(UserContextHolder.getUserId());

// Correct
appUserDubboClient.getNicknameById(UserContextHolder.getUserId());
```

### 7.4 No user/trace in async threads

See section 5. You must use `ttAsyncExecutor` or wrap with `TtlRunnable` yourself; `CompletableFuture.runAsync` uses ForkJoinPool by default and does not go through the custom `TaskDecorator`.

### 7.5 `@LoginOnlyApi` conflicts with class-level `@PreAuthorize`

`OrderPcController.queryOrderByUser` is an example: the gateway skips permission validation, but the class-level `@PreAuthorize` inside the service still requires `system:order:list`. If the expectation is "accessible upon login", adjust the annotation placement or remove the class-level `@PreAuthorize`.

## 8. Dependency approach

`order-service` is a startup module and is usually not depended on by other modules. When other services need order capabilities, they depend on `order-client` and use Dubbo.
