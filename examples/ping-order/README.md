# examples/ping-order：订单示例业务模块

> 本模块位于 `examples/` 下，只用于演示业务服务如何接入权限底座，不是权限内核的一部分。二开时直接复制或删除。

`examples/ping-order` 是一个**可直接替换/删除的示例业务模块**，用来演示：

- 一个普通业务服务如何接入权限底座；
- 如何通过 `user-client` 调用用户服务；
- PC 接口与 App 接口如何分别声明访问策略；
- 异步线程/线程池如何传递用户上下文与 `bizTid`。

它不属于权限内核。二开时你可以：

- 保留它作为模板，改造成自己的业务；
- 或者删掉 `examples/`、根 `pom.xml` 的 `<module>examples</module>`、`docker-compose.yml` 的 `order-service`，以及网关的 `/order/**` 路由。

## 1. 模块结构

```
examples/
├── pom.xml                  # examples 聚合模块
└── ping-order/
    ├── pom.xml              # 订单聚合模块，groupId com.ping.order
    ├── order-client/        # 对外 Dubbo 契约（OrderDubboClient、OrderDTO）
    └── order-service/       # 可启动业务服务
```

## 2. 依赖关系

```
order-service
 ├─ order-client
 ├─ user-client                 # 只依赖用户域契约，不依赖 user-pc / user-app
 ├─ ping-web-spring-boot-starter
 ├─ ping-dubbo-spring-boot-starter
 ├─ ping-log-spring-boot-starter
 ├─ ping-id-spring-boot-starter
 ├─ Nacos discovery/config
 ├─ Dubbo + Nacos registry
 └─ Spring Security / Sa-Token / Redis
```

`order-client` 不依赖任何业务实现，供其他服务引用。

## 3. 演示了什么

| 能力 | 位置 |
| --- | --- |
| PC 接口 + `@PreAuthorize` | `OrderPcController` |
| App 接口 + `@AppLoginOnlyApi` | `OrderAppController` |
| 公开方法覆盖类级策略 | `OrderAppController.queryOrderByUserV1` 的 `@PublicApi` |
| 通过 Dubbo 调 user-service | `OrderLocalServiceImpl`（`UserDubboClient`） |
| 通过 Dubbo 查 App 用户 | `OrderAppController`（`AppUserDubboClient`） |
| 权限数据兜底 | `DubboPermissionProvider` |
| 异步线程 + TTL + MDC | `AsyncExecutorConfig`、`OrderPcController.getOrderAsync` |
| 对外暴露 Dubbo 服务 | `OrderDubboServiceImpl` |
| 链路 ID | `logback.xml` 的 `%X{bizTid}` |
| 订单归属与最后修改人审计 | `BizOrder`、`OrderMetaObjectHandler` |
| 订单状态机与操作日志 | `OrderServiceImpl`、`ping_order_operation_log` |

### 3.1 订单模型：App 用户下单，后台只做管理

`examples/ping-order` 只表达一个业务事实：**App 用户下单，后台负责查看和流转状态**。

| 字段 | 含义 |
| --- | --- |
| `user_id` / `user_name` | 下单 App 用户（订单归属人，`app_user.id` 及其名称快照） |
| `update_by_type` / `update_by_id` / `update_by_name` | 最后一次修改人（可能是 App 用户，也可能是后台管理员） |

- 创建订单只允许 App 登录域，`OrderServiceImpl.create` 会拒绝 `OperatorType.PC`；
- 主表不再重复存「创建人」：创建人就是 `user_id`，PC 端不允许创建，存两组字段只会让二开者误以为后台也能下单；
- `update_by_*` 记录**最后操作人**，类型编码来自 `OperatorType`：

```text
1 = PC（sys_user）
2 = APP（app_user）
3 = SYSTEM（定时任务/消息消费）
4 = ANONYMOUS（未登录）
```

名称是**快照**，不是外键。用户改名后，历史订单仍显示当时的名称；需要完整轨迹时查 `ping_order_operation_log`，每条日志都记录 `operator_type` / `operator_id` / `operator_name`。

`OrderMetaObjectHandler` 自动填充 `update_by_*` 和 `create_time` / `update_time`，业务层不需要手写这些字段。

### 3.2 数据可见性：App 按 `user_id`，后台按接口权限

订单不是部门资产，因此订单表不引入 `dept_id`，也不使用 `@DataScope` 行级数据权限：

| 调用方 | 可见范围 | 实现方式 |
| --- | --- | --- |
| App 用户 | 只有自己的订单 | `OrderServiceImpl.page` 追加 `user_id = 当前 App 用户`；详情/改单/支付/取消先校验归属 |
| 后台管理员 | 全部订单 | `system:order:*` 接口权限，拥有该权限的角色可见 |

App 端越权访问表现为 `403 只能访问自己的订单`；后台未授权角色（如 tom / jerry / lisa）在网关层就是 `403 无此权限：system:order:list`，菜单也不会下发。

**什么时候才需要 `@DataScope`？** 当业务表属于后台组织（例如 `sys_user` 按部门隔离），并且需要「本部门 / 本部门及下级 / 仅本人」的数据范围时。写法参考 `SysUserMapper`（`ping-data-scope-spring-boot-starter`）。纯 App 域的表按 `user_id` 过滤即可，不要为了套用 `@DataScope` 硬加一个 `dept_id` —— 那会让后续二开者误以为所有 App 表都必须带部门字段。

## 4. 运行

```bash
# 先在根目录执行 mvn clean install -DskipTests
mvn -pl examples/ping-order/order-service spring-boot:run

# 配置命名空间默认跟随 APP_ENV（dev）；如需 public：
# NACOS_CONFIG_NAMESPACE="" mvn -pl examples/ping-order/order-service spring-boot:run
```

- 端口：8100
- 管理端口：8102
- 依赖：Nacos、Redis、user-service（Dubbo 提供方）

Docker 编排中，order-service 会在 MySQL/Redis/Nacos healthy 后启动，gateway 会等 order-service healthy 后再启动。

## 5. 接口与权限

| 接口 | 网关策略 | 服务内校验 |
| --- | --- | --- |
| `GET /order/pc/queryOrderByUser` | `@LoginOnlyApi`（仅登录） | 类级 `@PreAuthorize("hasAuthority('system:order:list')")` |
| `GET /order/pc/{userId}` | `sys_api_resource`：GET `/order/**` → `system:order:list` | 类级 `@PreAuthorize` |
| `GET /order/app/queryOrderByUser` | `@AppLoginOnlyApi`（App 登录） | 无 |
| `GET /order/app/queryOrderByUser/v1` | 方法级 `@PublicApi`（公开） | 无 |

注意几个容易误解的点：

### 5.1 `@LoginOnlyApi` 不会绕过服务内的 `@PreAuthorize`

`OrderPcController` 类上有：

```java
@PreAuthorize("hasAuthority('system:order:list')")
```

`/order/pc/queryOrderByUser` 方法上又有 `@LoginOnlyApi`。网关看到 `@LoginOnlyApi` 会跳过权限码校验，但请求进入服务后，**类级 `@PreAuthorize` 仍然生效**。所以这个接口实际上仍然要求 `system:order:list`。

如果想让“登录即可访问”，需要把方法移到没有类级 `@PreAuthorize` 的 Controller，或改用 `@authz.has` 的显式判断。

### 5.2 App 接口不查后台权限

`/order/app/**` 标注 `@AppLoginOnlyApi`，网关只校验 App 登录，不查 `system:order:list`。App 用户没有后台权限体系，这是设计目标。

### 5.3 订单读写接口

后台管理接口（`/order/pc/orders`，`OrderPcOrderController`）：

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

App 接口（`/order/app/orders`，`OrderAppController`）：

| 方法与路径 | 说明 |
| --- | --- |
| `GET /order/app/orders` | 只返回当前 App 用户的订单 |
| `GET /order/app/orders/{id}` | 非本人订单返回 403 |
| `GET /order/app/orders/{id}/logs` | 非本人订单返回 403 |
| `POST /order/app/orders` | 创建订单，归属人自动取当前 App 用户 |
| `PUT /order/app/orders/{id}` | 仅待支付状态可改 |
| `POST /order/app/orders/{id}/pay` | 待支付 → 已支付 |
| `POST /order/app/orders/{id}/cancel` | 待支付 → 已取消 |

状态机只允许：`待支付 → 已支付/已取消`、`已支付 → 已完成`。App 用户不能调用 `complete`，也不能删除订单。后台不提供「创建订单」接口：订单只能由 App 用户产生。

`sys_api_resource` 中已为上述 PC 路径配置了精确规则（比 `POST /order/**` 更长，网关按最长匹配优先），所以 `pay`/`cancel`/`complete` 会命中 `system:order:edit`，不会被误判成其他权限码。

## 6. 二开建议

- 新业务服务复制 `examples/ping-order` 的结构：`xxx-client` + `xxx-service`；
- `xxx-service` 引入 `ping-web` / `ping-dubbo` / `ping-log` / `ping-id` 四个 starter；
- 需要行级数据权限的**后台业务表**再引入 `ping-data-scope`，并提供自己的 `DataScopeProvider`（跨服务建议复用 `DataScopeDubboClient`）；纯 App 域的表按 `user_id` 过滤即可，不要为了套用 `@DataScope` 硬加 `dept_id`；
- 需要用户信息时依赖 `user-client`，不要依赖 `user-pc`；
- 每个接口至少选择一种策略：`@PublicApi`、`@LoginOnlyApi`、`@AppLoginOnlyApi`、`sys_api_resource`；
- 服务内敏感操作保留 `@PreAuthorize("@authz.has('...')")`；
- 异步线程池使用 `TtlRunnable` + MDC `TaskDecorator`。

## 7. 子模块文档

- [order-client](order-client/README.md)
- [order-service](order-service/README.md)
