# order-service：订单示例服务

`order-service` 是 `examples/ping-order` 下的示例业务服务，演示一个普通 Servlet 服务如何接入认证、权限、Dubbo、链路 ID 和异步上下文。它不是权限内核，可直接复制或删除。

## 1. 启动

```bash
# 先在根目录执行 mvn clean install -DskipTests
mvn -pl examples/ping-order/order-service spring-boot:run

# 配置命名空间默认跟随 APP_ENV（dev）；如需 public：
# NACOS_CONFIG_NAMESPACE="" mvn -pl examples/ping-order/order-service spring-boot:run
```

- 端口：8100
- 管理端口：8102
- 启动类：`OrderServiceApplication`（`@EnableDiscoveryClient`、`@EnableDubbo`）
- 配置：Nacos `application-${APP_ENV}.yml` + `order-service-${APP_ENV}.yml`

## 2. 代码结构

```
com.ping.order
├── OrderServiceApplication
├── config/
│   ├── AsyncExecutorConfig      # ttAsyncExecutor + MDC + TTL
│   ├── DubboPermissionProvider  # 权限兜底：Dubbo 调 user-service
│   ├── MyBatisPlusConfig        # MapperScan、分页、乐观锁、自动填充
│   └── OrderMetaObjectHandler   # 审计字段 + create_time/update_time
├── controller/
│   ├── app/OrderAppController   # /order/app/**，App 登录域
│   ├── pc/OrderPcController     # /order/pc/**，后台登录域（示例）
│   └── pc/OrderPcOrderController# /order/pc/orders/**，订单管理
├── entity/
│   ├── BizOrder                 # ping_order，订单归属 + 最后修改人
│   └── BizOrderOperationLog     # ping_order_operation_log
├── mapper/
│   ├── BizOrderMapper
│   └── BizOrderOperationLogMapper
├── model/
│   ├── OrderStatus              # 状态机
│   ├── OrderOperation           # 操作类型常量
│   ├── CreateOrderRequest / UpdateOrderRequest
│   ├── OrderResponse
│   └── OrderNameResponse
├── service/
│   ├── OrderLocalService
│   ├── OrderService / impl/OrderServiceImpl   # 订单读写、状态机、审计日志
│   └── OrderDubboServiceImpl                  # 暴露 OrderDubboClient
```

## 3. 接口

在线文档：启动后访问 `http://localhost:9999/docs`，切换到「订单服务」。本服务只输出 OpenAPI JSON（`/v3/api-docs`），UI 由网关聚合；生产环境默认关闭。

### 3.1 PC：`/order/pc/**`

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

- 类级 `@PreAuthorize("hasAuthority('system:order:list')")`；
- 网关对 `/order/**` GET 的规则是 `system:order:list`（`sys_api_resource`）；
- `@LoginOnlyApi` 只影响网关（跳过权限码），不会绕过类级 `@PreAuthorize`；
- 通过 `OrderLocalServiceImpl` → `UserDubboClient.getUserNameById()` 调 user-service。

### 3.2 App：`/order/app/**`

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

- 类级 `@AppLoginOnlyApi`：网关只校验 App 登录；
- 方法级 `@PublicApi` 覆盖类级：`/v1` 免登录；
- 用 `AppUserDubboClient` 查 App 用户，避免和 `sys_user` 串号。

### 3.3 订单读写：`/order/pc/orders` 与 `/order/app/orders`

后台管理接口在 `OrderPcOrderController`，逐方法声明权限码。订单只能由 App 用户创建，后台不提供 `POST /order/pc/orders`：

| 方法与路径 | 权限码 |
| --- | --- |
| `GET /order/pc/orders` | `system:order:list` |
| `GET /order/pc/orders/{id}` | `system:order:list` |
| `GET /order/pc/orders/{id}/logs` | `system:order:list` |
| `PUT /order/pc/orders/{id}` | `system:order:edit` |
| `POST /order/pc/orders/{id}/pay` | `system:order:edit` |
| `POST /order/pc/orders/{id}/cancel` | `system:order:edit` |
| `POST /order/pc/orders/{id}/complete` | `system:order:edit` |
| `DELETE /order/pc/orders/{id}` | `system:order:delete` |

App 接口在 `OrderAppController`，只允许操作自己的订单：

```java
@GetMapping("/orders")            // 当前用户的订单分页
@GetMapping("/orders/{id}")       // 非本人 → 403
@PostMapping("/orders")           // 归属人 = 当前 App 用户
@PutMapping("/orders/{id}")       // 仅待支付
@PostMapping("/orders/{id}/pay")  // 待支付 → 已支付
@PostMapping("/orders/{id}/cancel") // 待支付 → 已取消
```

订单状态机：`待支付 → 已支付/已取消`，`已支付 → 已完成`。App 不能完成或删除订单。

每次写操作都会向 `ping_order_operation_log` 插入一条记录（操作类型、操作人类型/ID/名称、前后状态、变更摘要）；主表同时更新 `update_by_type/id/name` 和 `version`。乐观锁冲突（`updateById` 返回 0）会提示“订单已被他人修改，请刷新后重试”。

## 4. 权限兜底：`DubboPermissionProvider`

```java
@DubboReference(check = false, timeout = 3000)
private PermissionDubboClient permissionDubboClient;

@Override
public List<String> roles(Long userId) {
    UserInfoDTO info = permissionDubboClient.getUserInfoById(userId.intValue());
    return info == null ? List.of() : info.getRoles();
}
```

作用：当 token session 中没有 `ROLES` / `PERMS` 时，`SaTokenAuthFilter` 调用它兜底，并回写会话。

- 正常登录流程不会走到这里（user-service 登录时已写入缓存）；
- 旧 token、缓存被清理、跨服务 token 等场景才会触发；
- 调用失败时返回空列表，不会阻塞请求。

## 5. 异步与上下文

`AsyncExecutorConfig`：

```java
@Bean("ttAsyncExecutor")
public AsyncTaskExecutor orderAsyncExecutor(...) {
    executor.setTaskDecorator(runnable -> mdcTaskDecorator.decorate(TtlRunnable.get(runnable)));
}
```

- `TtlRunnable`：传递 `UserContextHolder`（用户 ID / 角色 / 权限）；
- `mdcTaskDecorator`：复制 MDC（`bizTid`）；
- 拒绝策略 `CallerRunsPolicy`。

示例：

```java
CompletableFuture.runAsync(() -> {
    Long userId = UserContextHolder.getUserId(); // 有值
    log.info("[trace] bizTid={}", MDC.get("bizTid")); // 有值
}, orderAsyncExecutor);
```

如果你自己创建线程池，需要同样的包装，否则上下文和 `bizTid` 都会丢。

## 6. 配置

Nacos 的 `order-service-${APP_ENV}.yml`：

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
# 订单表数据源（与 user-service 共用 ping 库）
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

订单表在同一个 `ping` 库中，由本服务启动时的 Flyway 迁移创建（`src/main/resources/db/migration/V1__init_order_schema.sql`，包含 `ping_order`、`ping_order_operation_log`）；订单接口的权限规则由 user-service 的迁移写入 `sys_api_resource`。

公共配置 `application-${APP_ENV}.yml` 提供 Redis、Sa-Token、Dubbo registry、Actuator。

## 7. 常见坑

### 7.1 启动时 Dubbo 找不到 user-service

`@DubboReference(check = false)` 允许启动，但调用时会失败。确认：

- user-service 已启动并注册到同一 Nacos 命名空间；
- `dubbo.registry.parameters.namespace` 一致（`dubbo-${APP_ENV}`）；
- 网络/端口可达。

`DubboPermissionProvider` 会捕获异常并返回空权限，不会让请求直接 500，但会导致权限不足。

### 7.2 网关 403，直连正常

说明网关规则没匹配或 token 权限不足。检查：

```sql
SELECT permission_code, method, path FROM sys_api_resource WHERE path LIKE '/order%';
```

当前种子数据（网关按精确路径优先、再按最长匹配）：

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

`/order/app/**` 由 `@AppLoginOnlyApi` 标注，网关只校验 App 登录，不匹配这些权限码。没有精确匹配的 PC 路径会 fail-closed 返回 `403 接口未配置权限规则，拒绝访问`（例如已删除的 `POST /order/pc/orders`）。

### 7.3 App 接口用错用户客户端

```java
// 错误：App 用户 ID 传给了后台用户契约
userDubboClient.getUserNameById(UserContextHolder.getUserId());

// 正确
appUserDubboClient.getNicknameById(UserContextHolder.getUserId());
```

### 7.4 异步线程里没有用户/链路

见第 5 节。必须通过 `ttAsyncExecutor` 或自己包 `TtlRunnable`；`CompletableFuture.runAsync` 默认使用 ForkJoinPool，不会经过自定义 `TaskDecorator`。

### 7.5 `@LoginOnlyApi` 和类级 `@PreAuthorize` 冲突

`OrderPcController.queryOrderByUser` 就是例子：网关跳过权限校验，但服务内类级 `@PreAuthorize` 仍要求 `system:order:list`。如果预期是“登录即可”，需要调整注解位置或去掉类级 `@PreAuthorize`。

## 8. 依赖方式

`order-service` 是启动模块，通常不被其他模块依赖。其他服务需要订单能力时依赖 `order-client` 走 Dubbo。
